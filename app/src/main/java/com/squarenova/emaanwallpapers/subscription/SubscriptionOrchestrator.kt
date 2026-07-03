package com.squarenova.emaanwallpapers.subscription

import android.content.Context
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.network.SubscriptionApi
import com.squarenova.emaanwallpapers.network.SupabaseClient
import com.squarenova.emaanwallpapers.ui.subscription.CheckoutKind
import com.squarenova.emaanwallpapers.ui.subscription.SubscriptionManager
import com.squarenova.emaanwallpapers.util.SecureLog
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Subscription System V2 — the single orchestration layer for everything that happens AFTER
 * Razorpay's mandate payment-success callback.
 *
 * Design goals (all satisfied here):
 *  - ONE confirmation flow (verify → activate → sync entitlement), owned only by this class.
 *  - Durable: a [ConfirmationTicket] is persisted before any network work, so confirmation
 *    survives restart / process death / reboot / background.
 *  - Automatic recovery: [recover] resumes a persisted (or reconstructable) ticket on app start,
 *    foreground, splash and screen entry — the user never has to manually restore premium.
 *  - Idempotent + single-flight: at most one confirmation job runs; duplicate callbacks/retries
 *    are no-ops while a job is active.
 *  - Graceful timeout: when the active budget elapses while Razorpay is still non-terminal we emit
 *    [SubscriptionState.SoftTimeout] and KEEP the ticket — never an immediate "payment failed".
 *  - Backoff with terminal detection: retries only when appropriate; stops on Razorpay terminal
 *    states.
 *
 * The Activity-coupled Razorpay Checkout launch stays in the UI (it needs an Activity); this
 * orchestrator owns only the confirmation lifecycle and exposes [state] for the UI to observe.
 */
object SubscriptionOrchestrator {

    private const val TAG = "SubscriptionOrchestrator"

    // Active (foreground-blocking overlay) budget before we soften to SoftTimeout UI.
    private const val MAX_ACTIVE_MS = 60_000L
    // Total budget for one run. After this we stop working and retain the ticket; recovery
    // (foreground / restart) picks it up again. Prevents unbounded polling / battery drain.
    private const val HARD_CAP_MS = 300_000L
    private const val BACKOFF_START_MS = 2_000L
    private const val BACKOFF_MAX_MS = 8_000L
    private const val BACKOFF_FACTOR = 1.6
    // Slower cadence during the extended (post-soft-timeout) phase.
    private const val EXTENDED_INTERVAL_MS = 15_000L
    // A ticket older than this is abandoned. If the mandate was never approved within a day the
    // payment did not succeed; and if it did, the server (webhook) + entitlement refresh already
    // grant premium independently, so abandoning the ticket can never cost a paid user access.
    private const val TICKET_MAX_AGE_MS = 24L * 60L * 60L * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Guarantees a single confirmation job (idempotency for duplicate callbacks/retries). */
    private val running = AtomicBoolean(false)
    private val startLock = Mutex()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var ticketStore: ConfirmationTicketStore? = null

    private val _state = MutableStateFlow<SubscriptionState>(SubscriptionState.Idle)
    /** Observable confirmation state machine. */
    val state: StateFlow<SubscriptionState> = _state.asStateFlow()

    @Serializable
    private data class SubRow(
        val razorpay_subscription_id: String? = null,
        val subscription_status: String? = null,
    )

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            ticketStore = ConfirmationTicketStore(context.applicationContext)
            EntitlementRepository.init(context.applicationContext)
        }
    }

    /**
     * Entry point from the UI when a MANDATE Razorpay payment reports success. Persists a durable
     * ticket and starts confirmation. Safe to call multiple times — duplicates are ignored while a
     * confirmation is already running for the same subscription.
     */
    fun onMandatePaymentSuccess(phone: String, subscriptionId: String, paymentId: String) {
        val cleanPhone = phone.trim()
        val cleanSub = subscriptionId.trim()
        if (cleanPhone.isBlank() || cleanSub.isBlank()) {
            SecureLog.w(TAG, "onMandatePaymentSuccess ignored — blank phone/sub")
            return
        }
        scope.launch {
            startLock.withLock {
                if (running.get()) {
                    SecureLog.i(TAG, "confirmation already running — ignoring duplicate mandate success")
                    return@withLock
                }
                val ticket = ConfirmationTicket(
                    ticketId = UUID.randomUUID().toString(),
                    phone = cleanPhone,
                    subscriptionId = cleanSub,
                    paymentId = paymentId,
                    createdAtMs = System.currentTimeMillis(),
                )
                ticketStore?.save(ticket)
                SecureLog.i(TAG, "ticket created sub=${SecureLog.redactId(cleanSub)} — starting confirmation")
                launchConfirmation(ticket)
            }
        }
    }

    /**
     * Automatic recovery. Resumes a persisted ticket, or reconstructs one when a MANDATE checkout
     * was in flight across process death (persisted checkout kind + a pending subscription in the DB).
     * No-op if already premium or nothing to recover. Safe to call from App start, foreground,
     * splash and screen entry.
     */
    fun recover() {
        val store = ticketStore ?: return
        scope.launch {
            startLock.withLock {
                if (running.get()) return@withLock

                val sessionPhone = currentPhone()

                val existing = store.load()
                if (existing != null) {
                    // Safety net: never apply a ticket that belongs to a different (or logged-out)
                    // user — e.g. logout/login or a different account on the same device.
                    if (sessionPhone.isBlank() || sessionPhone != existing.phone.trim()) {
                        SecureLog.w(TAG, "recover: discarding ticket for a non-current user")
                        store.clear()
                        SubscriptionManager.clearPendingCheckout()
                        return@withLock
                    }
                    // Abandon stale tickets so recovery cannot loop forever across sessions.
                    // Entitlement is still driven by the server via the normal refresh paths.
                    if (System.currentTimeMillis() - existing.createdAtMs > TICKET_MAX_AGE_MS) {
                        SecureLog.w(TAG, "recover: ticket expired (>24h) — abandoning; server remains source of truth")
                        store.clear()
                        SubscriptionManager.clearPendingCheckout()
                        return@withLock
                    }
                    SecureLog.i(TAG, "recover: resuming persisted ticket sub=${SecureLog.redactId(existing.subscriptionId)}")
                    launchConfirmation(existing)
                    return@withLock
                }

                // No ticket, but a mandate may have completed while we were dead (UPI app / kill).
                if (SubscriptionManager.peekPersistedCheckoutKind() != CheckoutKind.MANDATE) return@withLock

                val phone = sessionPhone
                if (phone.isBlank()) return@withLock
                val row = fetchSubRow(phone) ?: return@withLock
                val subId = row.razorpay_subscription_id?.trim().orEmpty()
                val status = row.subscription_status?.trim()?.lowercase()
                if (subId.isBlank() || status !in RECONSTRUCT_STATUSES) return@withLock

                val ticket = ConfirmationTicket(
                    ticketId = UUID.randomUUID().toString(),
                    phone = phone,
                    subscriptionId = subId,
                    paymentId = "",
                    createdAtMs = System.currentTimeMillis(),
                )
                store.save(ticket)
                SecureLog.i(TAG, "recover: reconstructed ticket from DB sub=${SecureLog.redactId(subId)} status=$status")
                launchConfirmation(ticket)
            }
        }
    }

    /** Consume a terminal state (Premium / TerminalFailure) once the UI has reacted to it. */
    fun reset() {
        scope.launch {
            ticketStore?.clear()
            if (!running.get()) _state.value = SubscriptionState.Idle
        }
    }

    /**
     * Clears all subscription confirmation state on logout so the next account never inherits a
     * previous user's pending confirmation, cached entitlement or checkout intent.
     */
    fun onLogout() {
        scope.launch {
            ticketStore?.clear()
            SubscriptionManager.clearPendingCheckout()
            EntitlementRepository.clearForLogout()
            _state.value = SubscriptionState.Idle
        }
    }

    // ── Confirmation engine ─────────────────────────────────────────────────

    /** MUST be called while holding [startLock]. Starts the single confirmation job. */
    private fun launchConfirmation(initial: ConfirmationTicket) {
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            try {
                runConfirmation(initial)
            } catch (e: Exception) {
                // Never crash the confirmation engine — leave the ticket for the next recovery.
                SecureLog.e(TAG, "confirmation crashed — ticket retained for recovery", e)
                _state.value = SubscriptionState.SoftTimeout(initial.subscriptionId)
            } finally {
                running.set(false)
            }
        }
    }

    private suspend fun runConfirmation(initial: ConfirmationTicket) {
        val phone = initial.phone
        val subId = initial.subscriptionId
        val store = ticketStore

        // Fast path: the webhook may already have granted premium server-side.
        _state.value = SubscriptionState.Confirming(ConfirmationPhase.SYNCING_ENTITLEMENT, subId, 0)
        if (EntitlementRepository.refresh(phone)) {
            succeed(store, subId)
            return
        }

        var verified = initial.mandateVerified
        var attempt = initial.attempt
        var backoff = BACKOFF_START_MS
        var softAnnounced = false
        val startMs = System.currentTimeMillis()

        while (true) {
            val elapsed = System.currentTimeMillis() - startMs
            if (elapsed >= HARD_CAP_MS) {
                // Stop working this run; keep the ticket. Foreground/restart recovery resumes it.
                SecureLog.i(TAG, "hard cap reached — ticket retained for recovery sub=${SecureLog.redactId(subId)}")
                _state.value = SubscriptionState.SoftTimeout(subId)
                return
            }

            // Past the active window we soften the UI to SoftTimeout but keep confirming (slower).
            val extended = elapsed >= MAX_ACTIVE_MS
            if (extended && !softAnnounced) {
                softAnnounced = true
                SecureLog.i(TAG, "active budget elapsed — soft timeout (still confirming) sub=${SecureLog.redactId(subId)}")
                _state.value = SubscriptionState.SoftTimeout(subId)
            }

            attempt++

            // 1) Confirm the mandate reached authenticated/active at Razorpay.
            if (!verified) {
                if (!extended) {
                    _state.value = SubscriptionState.Confirming(ConfirmationPhase.VERIFYING, subId, attempt)
                }
                val v = SubscriptionApi.verifyMandate(subId)
                if (v.isSuccess) {
                    verified = true
                    store?.save(initial.copy(attempt = attempt, mandateVerified = true, phase = ConfirmationPhase.ACTIVATING.name))
                } else {
                    val msg = v.exceptionOrNull()?.message.orEmpty().lowercase()
                    if (isTerminal(msg)) {
                        fail(store, msg.ifBlank { "subscription_terminal" })
                        return
                    }
                    store?.save(initial.copy(attempt = attempt, phase = ConfirmationPhase.VERIFYING.name))
                    delay(stepDelay(extended, backoff))
                    backoff = nextBackoff(backoff)
                    continue
                }
            }

            // 2) Transition authenticated → trial/active server-side (idempotent).
            if (!extended) {
                _state.value = SubscriptionState.Confirming(ConfirmationPhase.ACTIVATING, subId, attempt)
            }
            SubscriptionApi.activateTrial(phone)

            // 3) Read back the single source of truth.
            if (!extended) {
                _state.value = SubscriptionState.Confirming(ConfirmationPhase.SYNCING_ENTITLEMENT, subId, attempt)
            }
            if (EntitlementRepository.refresh(phone)) {
                succeed(store, subId)
                return
            }

            store?.save(initial.copy(attempt = attempt, mandateVerified = verified, phase = ConfirmationPhase.SYNCING_ENTITLEMENT.name))
            delay(stepDelay(extended, backoff))
            backoff = nextBackoff(backoff)
        }
    }

    private fun stepDelay(extended: Boolean, backoff: Long): Long =
        if (extended) EXTENDED_INTERVAL_MS else backoff

    private suspend fun succeed(store: ConfirmationTicketStore?, subId: String) {
        store?.clear()
        SubscriptionManager.clearPendingCheckout()
        SecureLog.i(TAG, "confirmation SUCCESS — premium granted sub=${SecureLog.redactId(subId)}")
        _state.value = SubscriptionState.Premium(EntitlementRepository.lastStatus)
    }

    private suspend fun fail(store: ConfirmationTicketStore?, reason: String) {
        store?.clear()
        SubscriptionManager.clearPendingCheckout()
        SecureLog.e(TAG, "confirmation TERMINAL FAILURE reason=$reason")
        _state.value = SubscriptionState.TerminalFailure(reason)
    }

    private fun nextBackoff(current: Long): Long =
        (current * BACKOFF_FACTOR).toLong().coerceAtMost(BACKOFF_MAX_MS)

    private fun isTerminal(message: String): Boolean =
        "cancelled" in message || "halted" in message || "completed" in message || "expired" in message

    private suspend fun currentPhone(): String = try {
        val ctx = appContext ?: return ""
        DataStoreManager(ctx).phoneNumber.first()?.trim().orEmpty()
    } catch (_: Exception) {
        ""
    }

    private suspend fun fetchSubRow(phone: String): SubRow? = try {
        SupabaseClient.client
            .postgrest["users"]
            .select { filter { eq("phone_number", phone) } }
            .decodeList<SubRow>()
            .firstOrNull()
    } catch (_: Exception) {
        null
    }

    private val RECONSTRUCT_STATUSES = setOf("created", "authenticated", "active", "pending")
}
