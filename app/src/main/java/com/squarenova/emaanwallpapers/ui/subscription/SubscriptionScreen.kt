package com.squarenova.emaanwallpapers.ui.subscription

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.razorpay.Checkout
import com.squarenova.emaanwallpapers.analytics.AnalyticsManager
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.data.SubscriptionEntitlement
import com.squarenova.emaanwallpapers.network.SubscriptionApi
import com.squarenova.emaanwallpapers.network.SupabaseClient
import com.squarenova.emaanwallpapers.subscription.EntitlementRepository
import com.squarenova.emaanwallpapers.subscription.SubscriptionOrchestrator
import com.squarenova.emaanwallpapers.subscription.SubscriptionState
import com.squarenova.emaanwallpapers.ui.legal.LegalUrlOpener
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import android.util.Log
import com.squarenova.emaanwallpapers.BuildConfig
import io.github.jan.supabase.postgrest.postgrest
import org.json.JSONObject
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicBoolean

/** Process-wide guard: at most one Razorpay Checkout window at a time. */
private val checkoutOpenInFlight = AtomicBoolean(false)

private fun logCheckoutForensic(
    action: String,
    kind: CheckoutKind,
    subscriptionId: String? = null,
    orderId: String? = null,
) {
    Log.d(
        "CheckoutForensic",
        "action=$action kind=$kind subId=$subscriptionId orderId=$orderId " +
            "inFlight=${checkoutOpenInFlight.get()} ts=${System.currentTimeMillis()} " +
            "thread=${Thread.currentThread().name}\n${Log.getStackTraceString(Throwable())}",
    )
}

private fun tryAcquireCheckoutFlight(action: String, kind: CheckoutKind): Boolean {
    val acquired = checkoutOpenInFlight.compareAndSet(false, true)
    logCheckoutForensic(
        if (acquired) "acquire:$action" else "BLOCKED_duplicate:$action",
        kind,
    )
    return acquired
}

private fun releaseCheckoutFlight(reason: String) {
    checkoutOpenInFlight.set(false)
    logCheckoutForensic("release:$reason", CheckoutKind.NONE)
}

object RazorpayConfig {
    /** Set RAZORPAY_KEY_ID in local.properties (use test keys for debug builds if you split by flavor later). */
    val KEY_ID: String get() = BuildConfig.RAZORPAY_KEY_ID.trim()
}

@Serializable
private data class SubscriptionResumeUserRow(
    val phone_number: String? = null,
    val trial_paid: Boolean? = false,
    val razorpay_subscription_id: String? = null,
    val subscription_status: String? = null,
    val trial_end: String? = null,
)

private fun shouldResumeMandateSetup(
    status: String?,
    trialEnd: String?,
    hasTrialPaid: Boolean,
    subId: String,
): Boolean {
    if (!hasTrialPaid || subId.isBlank()) return false
    if (SubscriptionEntitlement.hasPremiumAccess(status, trialEnd)) return false
    return when (status?.trim()?.lowercase()) {
        "created", "authenticated" -> true
        else -> false
    }
}

private fun isEntryFeeAlreadyVerifiedError(message: String?): Boolean {
    val lower = message?.lowercase().orEmpty()
    return "entry fee already verified" in lower ||
        "already verified for this account" in lower
}

@Composable
fun SubscriptionScreen(navController: NavController) {

    val context = LocalContext.current
    val activity = LocalActivity.current
    val dataStoreManager = DataStoreManager(context)
    val scope = rememberCoroutineScope()

    var phone by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var paymentErrorDialog by remember { mutableStateOf<PaymentErrorDialogState?>(null) }

    var currentCheckoutKind by remember { mutableStateOf(CheckoutKind.NONE) }
    var entryFeeSuccessHandled by remember { mutableStateOf(false) }
    var mandateLaunchHandled by remember { mutableStateOf(false) }
    var mandatePaymentSuccessReceived by remember { mutableStateOf(false) }
    var mandateRetryCooldownUntilMs by remember { mutableStateOf(0L) }
    var retryNowMs by remember { mutableStateOf(0L) }
    var currentOrderId by remember { mutableStateOf<String?>(null) }
    var pendingSubscriptionId by remember { mutableStateOf<String?>(null) }
    var hasTrialPaid by remember { mutableStateOf(false) }
    var userStatus by remember { mutableStateOf("new") }

    /** Full-screen explanation screen after ₹5, before mandate Razorpay. */
    var showSetupExplanationScreen by remember { mutableStateOf(false) }

    /** Post–mandate success; user taps Continue → Home. */
    var showTrialSuccessScreen by remember { mutableStateOf(false) }

    /** Durable confirmation is running (orchestrator owns verify → activate → sync). */
    var confirming by remember { mutableStateOf(false) }

    /** Confirmation is taking longer than usual but is still running in the background. */
    var confirmingSoft by remember { mutableStateOf(false) }

    /** Observable V2 confirmation state machine (survives rotation/background/process death). */
    val subState by SubscriptionOrchestrator.state.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        phone = dataStoreManager.phoneNumber.firstOrNull() ?: ""
        if (phone.isNotBlank()) {
            AnalyticsManager.identify(phone)
            val row = try {
                SupabaseClient.client
                    .postgrest["users"]
                    .select { filter { eq("phone_number", phone) } }
                    .decodeList<SubscriptionResumeUserRow>()
                    .firstOrNull()
            } catch (_: Exception) {
                null
            }
            hasTrialPaid = row?.trial_paid == true
            userStatus = if (row == null) "new" else "returning"

            if (SubscriptionEntitlement.hasPremiumAccess(
                    row?.subscription_status,
                    row?.trial_end,
                )
            ) {
                Log.i("SUBSCRIPTION", "Valid entitlement on entry — routing to home")
                navController.navigate("home") {
                    popUpTo("subscription") { inclusive = true }
                }
                return@LaunchedEffect
            }

            val subId = row?.razorpay_subscription_id?.trim().orEmpty()
            if (shouldResumeMandateSetup(
                    row?.subscription_status,
                    row?.trial_end,
                    hasTrialPaid,
                    subId,
                )
            ) {
                // Show setup overlay only — subscription id is resolved via create-subscription on Continue.
                entryFeeSuccessHandled = true
                showSetupExplanationScreen = true
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "SUBSCRIPTION",
                        "Restored mandate setup: status=${row?.subscription_status}"
                    )
                }
            }
        }
        Checkout.preload(context)
        SubscriptionManager.restorePendingCheckoutFromPersistenceIfNeeded()
        val initialKind = SubscriptionManager.peekPersistedCheckoutKind()
        if (initialKind != CheckoutKind.NONE) {
            currentCheckoutKind = initialKind
            Log.d("SUBSCRIPTION", "Restored checkout kind on screen enter: $initialKind")
        }
        // Resume any interrupted confirmation (process death / kill during mandate). No-op otherwise.
        SubscriptionOrchestrator.recover()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                SubscriptionManager.restorePendingCheckoutFromPersistenceIfNeeded()
                val peek = SubscriptionManager.peekPersistedCheckoutKind()
                if (peek != CheckoutKind.NONE) {
                    currentCheckoutKind = peek
                    Log.d("SUBSCRIPTION", "Restored checkout kind on resume (before PaymentResult): $peek")
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(mandateRetryCooldownUntilMs) {
        while (mandateRetryCooldownUntilMs > 0L && System.currentTimeMillis() < mandateRetryCooldownUntilMs) {
            retryNowMs = System.currentTimeMillis()
            delay(400)
        }
        retryNowMs = System.currentTimeMillis()
    }

    fun showPaymentError(
        raw: String? = null,
        throwable: Throwable? = null,
        errorCode: Int? = null,
        isMandateStep: Boolean = false,
        onRetry: () -> Unit,
    ) {
        PaymentErrors.logFailure("SUBSCRIPTION", raw, throwable, errorCode)
        Log.d(
            "SubscriptionDebug",
            "7. showPaymentError reason=$raw errorCode=$errorCode isMandateStep=$isMandateStep " +
                "exception=${throwable?.message} classified=${PaymentErrors.classify(raw, throwable, errorCode, isMandateStep)}"
        )
        if (throwable != null) {
            Log.d("SubscriptionDebug", "7. showPaymentError stackTrace=${Log.getStackTraceString(throwable)}")
        }
        paymentErrorDialog = PaymentErrorDialogState(
            type = PaymentErrors.classify(raw, throwable, errorCode, isMandateStep),
            onRetry = onRetry,
        )
        isLoading = false
    }

    /**
     * Single client entry point for obtaining a mandate-ready subscription id.
     * Always delegates reuse vs. create-new to the create-subscription edge function.
     */
    suspend fun obtainSubscriptionIdFromBackend(phoneForApi: String): Result<SubscriptionApi.CreateSubscriptionResult> {
        val cleanPhone = phoneForApi.trim()
        if (cleanPhone.isBlank()) {
            return Result.failure(IllegalStateException("session_expired"))
        }
        return SubscriptionApi.createSubscription(cleanPhone).also { result ->
            result.onSuccess { created -> pendingSubscriptionId = created.subscriptionId }
        }
    }

    /**
     * When create-subscription reports the mandate is already live at Razorpay, never open Checkout
     * again — refresh entitlement / resume durable confirmation instead.
     */
    suspend fun handleAlreadyActiveMandate(phoneForApi: String): Boolean {
        val mandateOk = SubscriptionApi.refreshSubscriptionStatus(phoneForApi).getOrDefault(false)
        val premium = EntitlementRepository.refresh(phoneForApi)
        if (premium) {
            isLoading = false
            showSetupExplanationScreen = false
            showTrialSuccessScreen = true
            return true
        }
        if (mandateOk) {
            isLoading = false
            showSetupExplanationScreen = false
            confirming = true
            SubscriptionOrchestrator.recover()
            return true
        }
        return false
    }

    val retrySubscriptionHolder = remember { object { lateinit var action: () -> Unit } }
    fun retrySubscription() = retrySubscriptionHolder.action()

    fun navigateBackToLogin() {
        scope.launch {
            showSetupExplanationScreen = false
            showTrialSuccessScreen = false
            SubscriptionOrchestrator.onLogout()
            dataStoreManager.logout()
            navController.navigate("login") {
                popUpTo("subscription") { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    fun openMandateCheckout(subscriptionId: String) {
        if (!tryAcquireCheckoutFlight("openMandateCheckout", CheckoutKind.MANDATE)) {
            Log.w("SUBSCRIPTION", "openMandateCheckout ignored — checkout already in flight")
            return
        }
        val hostActivity = activity
        if (hostActivity == null) {
            releaseCheckoutFlight("openMandateCheckout_activity_null")
            showPaymentError(
                raw = "activity_null",
                isMandateStep = true,
                onRetry = { openMandateCheckout(subscriptionId) },
            )
            showSetupExplanationScreen = false
            Log.e("SUBSCRIPTION", "openMandateCheckout: activity null")
            return
        }
        showSetupExplanationScreen = false
        currentCheckoutKind = CheckoutKind.MANDATE
        SubscriptionManager.prepareCheckout(CheckoutKind.MANDATE)
        isLoading = false
        logCheckoutForensic("Checkout.open", CheckoutKind.MANDATE, subscriptionId = subscriptionId)
        Log.i("SUBSCRIPTION", "Opening mandate checkout subscriptionId=$subscriptionId")
        try {
            val checkout = Checkout()
            checkout.setKeyID(RazorpayConfig.KEY_ID)
            val options = JSONObject().apply {
                put("subscription_id", subscriptionId)
                put("name", "Emaan Wallpapers")
                put("description", "Approve AutoPay ₹99/month")
                put("prefill.contact", phone)
                put("prefill.email", "user@yourapp.com")
            }
            checkout.open(hostActivity, options)
        } catch (e: Exception) {
            releaseCheckoutFlight("openMandateCheckout_exception")
            Log.e("SUBSCRIPTION", "openMandateCheckout", e)
            currentCheckoutKind = CheckoutKind.NONE
            showSetupExplanationScreen = true
            showPaymentError(
                raw = e.message,
                throwable = e,
                isMandateStep = true,
                onRetry = { openMandateCheckout(subscriptionId) },
            )
        }
    }

    fun launchMandateCheckoutViaBackend(
        phoneForApi: String,
        onFailure: () -> Unit = { retrySubscription() },
    ) {
        scope.launch {
            isLoading = true
            obtainSubscriptionIdFromBackend(phoneForApi).fold(
                onSuccess = { created ->
                    if (!created.checkoutRequired || created.alreadyActive) {
                        if (handleAlreadyActiveMandate(phoneForApi)) return@launch
                    }
                    hasTrialPaid = true
                    entryFeeSuccessHandled = true
                    isLoading = false
                    openMandateCheckout(created.subscriptionId)
                },
                onFailure = { e ->
                    isLoading = false
                    showSetupExplanationScreen = true
                    showPaymentError(
                        raw = e.message,
                        throwable = e,
                        isMandateStep = true,
                        onRetry = onFailure,
                    )
                },
            )
        }
    }

    fun startCheckout() {
        // Never launch a checkout while a confirmation is in flight — re-show the overlay instead.
        if (subState is SubscriptionState.Confirming || subState is SubscriptionState.SoftTimeout) {
            confirming = true
            Log.w("SUBSCRIPTION", "startCheckout ignored — confirmation active")
            return
        }
        if (phone.isBlank()) {
            showPaymentError(
                raw = "session_expired",
                onRetry = { navigateBackToLogin() },
            )
            return
        }

        val hostActivity = activity
        if (hostActivity == null) {
            showPaymentError(
                raw = "activity_null",
                onRetry = { retrySubscription() },
            )
            Log.e("SUBSCRIPTION", "LocalActivity is null — cannot open Razorpay")
            return
        }

        scope.launch {
            AnalyticsManager.track(
                "trial_cta_clicked",
                mapOf(
                    "screen_name" to "subscription",
                    "user_status" to userStatus,
                    "has_trial_paid" to hasTrialPaid
                )
            )
            isLoading = true
            paymentErrorDialog = null
            entryFeeSuccessHandled = false
            mandateLaunchHandled = false
            mandatePaymentSuccessReceived = false
            currentOrderId = null
            if (!hasTrialPaid) {
                pendingSubscriptionId = null
            }

            val orderResult = SubscriptionApi.createOrder()
            orderResult.fold(
                onSuccess = { orderId ->
                    if (!tryAcquireCheckoutFlight("startCheckout_entry_fee", CheckoutKind.ENTRY_FEE)) {
                        isLoading = false
                        Log.w("SUBSCRIPTION", "startCheckout ignored — checkout already in flight")
                        return@fold
                    }
                    AnalyticsManager.track(
                        "payment_initiated",
                        mapOf("amount" to 5, "currency" to "INR")
                    )
                    try {
                        currentOrderId = orderId
                        currentCheckoutKind = CheckoutKind.ENTRY_FEE
                        SubscriptionManager.prepareCheckout(CheckoutKind.ENTRY_FEE)
                        val checkout = Checkout()
                        checkout.setKeyID(RazorpayConfig.KEY_ID)
                        val options = JSONObject().apply {
                            put("order_id", orderId)
                            put("name", "Emaan Wallpapers")
                            put("description", "₹5 one-time entry fee")
                            put("currency", "INR")
                            put("prefill.contact", phone)
                            put("prefill.email", "user@yourapp.com")
                        }
                        logCheckoutForensic("Checkout.open", CheckoutKind.ENTRY_FEE, orderId = orderId)
                        checkout.open(hostActivity, options)
                    } catch (e: Exception) {
                        releaseCheckoutFlight("startCheckout_exception")
                        AnalyticsManager.track(
                            "payment_failed",
                            mapOf(
                                "error_reason" to (e.message ?: "open_checkout_failed"),
                                "step" to "entry_fee"
                            )
                        )
                        Log.e("SUBSCRIPTION", "startCheckout open", e)
                        currentCheckoutKind = CheckoutKind.NONE
                        currentOrderId = null
                        showPaymentError(
                            raw = e.message,
                            throwable = e,
                            onRetry = { retrySubscription() },
                        )
                    }
                },
                onFailure = { e ->
                    AnalyticsManager.track(
                        "payment_failed",
                        mapOf(
                            "error_reason" to (e.message ?: "create_order_failed"),
                            "step" to "entry_fee"
                        )
                    )
                    showPaymentError(
                        raw = e.message,
                        throwable = e,
                        onRetry = { retrySubscription() },
                    )
                }
            )
        }
    }

    fun resumeAfterEntryFeePaid(phoneForApi: String) {
        // Mandate checkout opens only from the setup overlay Continue — never auto-launch here.
        showSetupExplanationScreen = true
        mandateLaunchHandled = false
    }

    fun resumeSubscriptionFlow() {
        // A confirmation already owns the flow — do not open a second checkout (duplicate guard).
        if (subState is SubscriptionState.Confirming || subState is SubscriptionState.SoftTimeout) {
            confirming = true
            Log.w("SUBSCRIPTION", "resumeSubscriptionFlow ignored — confirmation active")
            return
        }
        if (phone.isBlank()) {
            showPaymentError(
                raw = "session_expired",
                onRetry = { navigateBackToLogin() },
            )
            return
        }
        if (hasTrialPaid) {
            // Single mandate entry point: overlay Continue only — never auto-open Checkout here.
            showSetupExplanationScreen = true
            mandateLaunchHandled = false
            return
        }
        startCheckout()
    }

    retrySubscriptionHolder.action = { resumeSubscriptionFlow() }

    LaunchedEffect(Unit) {
        SubscriptionManager.paymentResult.collect { result ->
            when (result) {
                is PaymentResult.Success -> {
                    scope.launch {
                        try {
                            if (currentCheckoutKind == CheckoutKind.NONE && result.kind != CheckoutKind.NONE) {
                                currentCheckoutKind = result.kind
                                Log.d(
                                    "SUBSCRIPTION",
                                    "Synced currentCheckoutKind from PaymentResult.kind: ${result.kind}"
                                )
                            }

                            val phoneForApi =
                                dataStoreManager.phoneNumber.firstOrNull()?.trim().orEmpty()
                                    .ifBlank { phone }
                            if (phoneForApi.isBlank()) {
                                isLoading = false
                                showSetupExplanationScreen = false
                                currentCheckoutKind = CheckoutKind.NONE
                                Log.e("SUBSCRIPTION", "Payment OK but phone missing from DataStore")
                                showPaymentError(
                                    raw = "session_expired",
                                    onRetry = { navigateBackToLogin() },
                                )
                                return@launch
                            }

                            val phase = when {
                                currentCheckoutKind != CheckoutKind.NONE -> currentCheckoutKind
                                result.kind != CheckoutKind.NONE -> {
                                    Log.w(
                                        "SUBSCRIPTION",
                                        "currentCheckoutKind was NONE; using result.kind=${result.kind}"
                                    )
                                    result.kind
                                }
                                else -> CheckoutKind.NONE
                            }

                            Log.d(
                                "SUBSCRIPTION",
                                "PaymentResult.Success phase=$phase result.kind=${result.kind} paymentId=${result.paymentId}"
                            )
                            Log.d(
                                "SubscriptionDebug",
                                "3. SubscriptionScreen PaymentResult.Success phase=$phase " +
                                    "resultKind=${result.kind} pendingSubscriptionId=$pendingSubscriptionId"
                            )

                            when (phase) {
                            CheckoutKind.ENTRY_FEE -> {
                                releaseCheckoutFlight("entry_fee_payment_success")
                                if (hasTrialPaid) {
                                    Log.w(
                                        "SUBSCRIPTION",
                                        "ENTRY_FEE success ignored; entry fee already verified — resuming mandate"
                                    )
                                    resumeAfterEntryFeePaid(phoneForApi)
                                    return@launch
                                }
                                if (entryFeeSuccessHandled) {
                                    Log.w(
                                        "SUBSCRIPTION",
                                        "Ignoring duplicate ENTRY_FEE success (createSubscription already started)"
                                    )
                                    return@launch
                                }
                                Log.i("SUBSCRIPTION", "ENTRY_FEE success")
                                AnalyticsManager.track(
                                    "payment_success",
                                    mapOf(
                                        "payment_id" to result.paymentId,
                                        "amount" to 5
                                    )
                                )
                                entryFeeSuccessHandled = true

                                val payId = result.paymentId
                                val orderId = currentOrderId?.trim().orEmpty()
                                if (payId.isBlank()) {
                                    entryFeeSuccessHandled = false
                                    currentCheckoutKind = CheckoutKind.NONE
                                    showPaymentError(
                                        raw = "missing_payment_id",
                                        onRetry = { resumeSubscriptionFlow() },
                                    )
                                    return@launch
                                }
                                if (orderId.isBlank()) {
                                    entryFeeSuccessHandled = false
                                    currentCheckoutKind = CheckoutKind.NONE
                                    showPaymentError(
                                        raw = "missing_order_id",
                                        onRetry = { resumeSubscriptionFlow() },
                                    )
                                    return@launch
                                }

                                val verified = SubscriptionApi.verifyPayment(
                                    phone = phoneForApi,
                                    paymentId = payId,
                                    orderId = orderId
                                )
                                if (verified.isFailure) {
                                    val verifyError = verified.exceptionOrNull()
                                    val verifyMessage = verifyError?.message
                                    AnalyticsManager.track(
                                        "payment_failed",
                                        mapOf(
                                            "error_reason" to (verifyMessage ?: "verify_failed"),
                                            "step" to "entry_fee"
                                        )
                                    )
                                    entryFeeSuccessHandled = false
                                    currentCheckoutKind = CheckoutKind.NONE
                                    if (isEntryFeeAlreadyVerifiedError(verifyMessage)) {
                                        hasTrialPaid = true
                                        resumeAfterEntryFeePaid(phoneForApi)
                                    } else {
                                        showPaymentError(
                                            raw = verifyMessage,
                                            throwable = verifyError,
                                            onRetry = { resumeSubscriptionFlow() },
                                        )
                                    }
                                    return@launch
                                }

                                hasTrialPaid = true

                                val trialStarted = SubscriptionApi.startTrial(phoneForApi)
                                if (trialStarted.isFailure) {
                                    val trialError = trialStarted.exceptionOrNull()
                                    AnalyticsManager.track(
                                        "trial_start_failed",
                                        mapOf("error_message" to (trialError?.message ?: "unknown"))
                                    )
                                    entryFeeSuccessHandled = false
                                    currentCheckoutKind = CheckoutKind.NONE
                                    showPaymentError(
                                        raw = trialError?.message,
                                        throwable = trialError,
                                        onRetry = { resumeSubscriptionFlow() },
                                    )
                                    return@launch
                                }
                                AnalyticsManager.track(
                                    "trial_started",
                                    mapOf(
                                        "trial_end_timestamp" to (System.currentTimeMillis() + 3L * 24L * 60L * 60L * 1000L),
                                        "user_id" to phoneForApi
                                    )
                                )

                                val created = SubscriptionApi.createSubscription(phoneForApi)
                                created.fold(
                                    onSuccess = { result ->
                                        AnalyticsManager.track(
                                            "subscription_created",
                                            mapOf(
                                                "subscription_id" to result.subscriptionId,
                                                "plan_id" to "monthly_99"
                                            )
                                        )
                                        AnalyticsManager.track(
                                            "mandate_screen_shown",
                                            mapOf("source" to "after_trial_setup")
                                        )
                                        pendingSubscriptionId = result.subscriptionId
                                        hasTrialPaid = true
                                        showSetupExplanationScreen = true
                                        isLoading = false
                                        mandateLaunchHandled = false
                                        releaseCheckoutFlight("entry_fee_success_before_mandate_overlay")
                                    },
                                    onFailure = { e ->
                                        AnalyticsManager.track(
                                            "subscription_creation_failed",
                                            mapOf("error_message" to (e.message ?: "unknown"))
                                        )
                                        entryFeeSuccessHandled = false
                                        currentCheckoutKind = CheckoutKind.NONE
                                        showSetupExplanationScreen = true
                                        showPaymentError(
                                            raw = e.message,
                                            throwable = e,
                                            isMandateStep = true,
                                            onRetry = { resumeSubscriptionFlow() },
                                        )
                                        Log.e("SUBSCRIPTION", "createSubscription failed", e)
                                    }
                                )
                            }

                            CheckoutKind.MANDATE -> {
                                releaseCheckoutFlight("mandate_payment_success")
                                var subId = pendingSubscriptionId?.trim().orEmpty()
                                if (subId.isBlank()) {
                                    val resolved = obtainSubscriptionIdFromBackend(phoneForApi)
                                    if (resolved.isFailure) {
                                        showSetupExplanationScreen = true
                                        currentCheckoutKind = CheckoutKind.NONE
                                        showPaymentError(
                                            raw = resolved.exceptionOrNull()?.message,
                                            throwable = resolved.exceptionOrNull(),
                                            isMandateStep = true,
                                            onRetry = { resumeSubscriptionFlow() },
                                        )
                                        return@launch
                                    }
                                    val created = resolved.getOrThrow()
                                    if (!created.checkoutRequired || created.alreadyActive) {
                                        if (handleAlreadyActiveMandate(phoneForApi)) return@launch
                                    }
                                    subId = created.subscriptionId
                                }
                                pendingSubscriptionId = subId
                                Log.i("SUBSCRIPTION", "MANDATE success — handing off to durable confirmation")
                                AnalyticsManager.track(
                                    "mandate_success",
                                    mapOf("subscription_id" to subId)
                                )
                                // Hand the whole verify → activate → sync lifecycle to the orchestrator.
                                // It persists a durable ticket and survives restart/process death; the
                                // orchestrator state collector below drives the confirming/success/failure UI.
                                currentCheckoutKind = CheckoutKind.NONE
                                mandateLaunchHandled = false
                                mandatePaymentSuccessReceived = true
                                SubscriptionOrchestrator.onMandatePaymentSuccess(
                                    phone = phoneForApi,
                                    subscriptionId = subId,
                                    paymentId = result.paymentId,
                                )
                            }

                            CheckoutKind.NONE -> {
                                isLoading = false
                                showSetupExplanationScreen = false
                                Log.w(
                                    "SUBSCRIPTION",
                                    "Ignoring Success: phase NONE (stale or unknown callback)"
                                )
                            }
                        }
                        } catch (t: Throwable) {
                            Log.e("SUBSCRIPTION", "Crash prevented in payment success handler", t)
                            showSetupExplanationScreen = false
                            showTrialSuccessScreen = false
                            currentCheckoutKind = CheckoutKind.NONE
                            entryFeeSuccessHandled = false
                            mandateLaunchHandled = false
                            showPaymentError(
                                raw = t.message,
                                throwable = t,
                                onRetry = { resumeSubscriptionFlow() },
                            )
                        }
                    }
                }

                is PaymentResult.Error -> {
                    releaseCheckoutFlight("payment_error")
                    val failedStep = when {
                        result.checkoutKind == CheckoutKind.MANDATE -> "mandate"
                        result.checkoutKind == CheckoutKind.ENTRY_FEE -> "entry_fee"
                        currentCheckoutKind == CheckoutKind.MANDATE -> "mandate"
                        else -> "entry_fee"
                    }
                    val isMandateStep = failedStep == "mandate"
                    if (isMandateStep) {
                        AnalyticsManager.track(
                            "mandate_failed",
                            mapOf("error_reason" to result.message)
                        )
                    } else {
                        AnalyticsManager.track(
                            "payment_failed",
                            mapOf(
                                "error_reason" to result.message,
                                "step" to "entry_fee"
                            )
                        )
                    }
                    showTrialSuccessScreen = false
                    currentCheckoutKind = CheckoutKind.NONE
                    entryFeeSuccessHandled = false
                    mandatePaymentSuccessReceived = false
                    mandateLaunchHandled = false
                    if (isMandateStep) {
                        showSetupExplanationScreen = true
                    } else {
                        showSetupExplanationScreen = false
                    }
                    showPaymentError(
                        raw = result.message,
                        errorCode = result.errorCode.takeIf { it >= 0 },
                        isMandateStep = isMandateStep,
                        onRetry = { resumeSubscriptionFlow() },
                    )
                    Log.e("SUBSCRIPTION", "PaymentResult.Error: ${result.message}")
                }
            }
        }
    }

    // Single place the whole post-payment confirmation lifecycle drives the UI.
    LaunchedEffect(subState) {
        when (val s = subState) {
            is SubscriptionState.Confirming -> {
                confirming = true
                confirmingSoft = false
                isLoading = false
                showSetupExplanationScreen = false
                showTrialSuccessScreen = false
                paymentErrorDialog = null
                Log.d("SUBSCRIPTION", "confirmation phase=${s.phase} attempt=${s.attempt}")
            }

            is SubscriptionState.Premium -> {
                confirming = false
                confirmingSoft = false
                isLoading = false
                showSetupExplanationScreen = false
                currentCheckoutKind = CheckoutKind.NONE
                entryFeeSuccessHandled = false
                mandateLaunchHandled = false
                mandatePaymentSuccessReceived = false
                pendingSubscriptionId = null
                currentOrderId = null
                AnalyticsManager.track("subscription_confirmed")
                showTrialSuccessScreen = true
                SubscriptionOrchestrator.reset()
            }

            is SubscriptionState.SoftTimeout -> {
                // Graceful, NOT a failure — the durable job keeps confirming in the background.
                // We must NOT surface a retry that reopens Razorpay while it runs (duplicate risk).
                confirming = true
                confirmingSoft = true
                isLoading = false
                showSetupExplanationScreen = false
                paymentErrorDialog = null
            }

            is SubscriptionState.TerminalFailure -> {
                confirming = false
                confirmingSoft = false
                isLoading = false
                showSetupExplanationScreen = true
                mandatePaymentSuccessReceived = true
                mandateLaunchHandled = false
                mandateRetryCooldownUntilMs = System.currentTimeMillis() + 3000L
                showPaymentError(
                    raw = s.reason,
                    isMandateStep = true,
                    onRetry = { resumeSubscriptionFlow() },
                )
                SubscriptionOrchestrator.reset()
            }

            SubscriptionState.Idle -> {
                confirming = false
                confirmingSoft = false
            }
        }
    }

    val goldBrush = remember {
        Brush.horizontalGradient(
            listOf(
                PremiumSubscriptionColors.Gold.copy(alpha = 0.9f),
                PremiumSubscriptionColors.GoldLight
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PremiumScreenBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            // Compact premium header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PremiumSubscriptionColors.Surface.copy(alpha = 0.92f))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navigateBackToLogin() },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = PremiumSubscriptionColors.TextPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Text(text = "👑", fontSize = 18.sp)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = "Go Premium",
                            color = PremiumSubscriptionColors.TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Unlock unlimited wallpapers",
                        color = PremiumSubscriptionColors.TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(goldBrush)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PremiumSubscriptionColors.SurfaceElevated,
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, PremiumSubscriptionColors.BorderSubtle)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        PremiumFeatureRow(
                            icon = Icons.Filled.Star,
                            title = "Unlimited access",
                            subtitle = "Browse every premium wallpaper"
                        )
                        PremiumFeatureRow(
                            icon = Icons.Filled.Favorite,
                            title = "No ads",
                            subtitle = "Distraction-free experience"
                        )
                        PremiumFeatureRow(
                            icon = Icons.AutoMirrored.Filled.List,
                            title = "HD downloads",
                            subtitle = "Crisp visuals for your screen"
                        )
                        PremiumFeatureRow(
                            icon = Icons.Filled.Check,
                            title = "New wallpapers daily",
                            subtitle = "Fresh designs on the regular"
                        )
                    }
                }

                PremiumPricingHighlightCard()

                Text(
                    text = "You’ll pay ₹5 now to start your trial. Then you’ll confirm AutoPay for ₹99/month — no charge until after your 3-day trial.",
                    color = PremiumSubscriptionColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                PremiumGradientCtaButton(
                    text = if (hasTrialPaid) "Continue AutoPay Setup" else "Start ₹5 Trial",
                    onClick = { resumeSubscriptionFlow() },
                    enabled = !showTrialSuccessScreen && !showSetupExplanationScreen,
                    loading = isLoading && !showSetupExplanationScreen
                )

                Text(
                    text = "Cancel anytime from Profile → Manage Subscription. " +
                        "Subscription auto-renews at ₹99/month unless cancelled 24h before renewal.",
                    color = PremiumSubscriptionColors.TextSecondary.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                TextButton(
                    onClick = {
                        AnalyticsManager.trackEvent("Subscription - Disclosure Link Tapped")
                        LegalUrlOpener.openSubscriptionDisclosure(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "View subscription terms & auto-renewal disclosure",
                        color = PremiumSubscriptionColors.TextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        SubscriptionSetupFullScreenOverlay(
            visible = showSetupExplanationScreen,
            ctaText = if (mandatePaymentSuccessReceived) "Retry AutoPay Approval" else "Continue",
            ctaEnabled = !mandatePaymentSuccessReceived || retryNowMs >= mandateRetryCooldownUntilMs,
            onContinue = {
                if (mandateLaunchHandled) return@SubscriptionSetupFullScreenOverlay
                mandateLaunchHandled = true
                isLoading = true
                if (mandatePaymentSuccessReceived) {
                    AnalyticsManager.track("mandate_retry_clicked")
                    mandateLaunchHandled = false
                    launchMandateCheckoutViaBackend(phone.trim())
                    return@SubscriptionSetupFullScreenOverlay
                }
                AnalyticsManager.track("mandate_initiated")
                val phoneForApi = phone.trim()
                if (phoneForApi.isBlank()) {
                    mandateLaunchHandled = false
                    isLoading = false
                    showPaymentError(
                        raw = "session_expired",
                        onRetry = { navigateBackToLogin() },
                    )
                    return@SubscriptionSetupFullScreenOverlay
                }
                scope.launch {
                    obtainSubscriptionIdFromBackend(phoneForApi).fold(
                        onSuccess = { created ->
                            if (!created.checkoutRequired || created.alreadyActive) {
                                if (handleAlreadyActiveMandate(phoneForApi)) return@launch
                            }
                            isLoading = false
                            openMandateCheckout(created.subscriptionId)
                        },
                        onFailure = { e ->
                            mandateLaunchHandled = false
                            isLoading = false
                            showSetupExplanationScreen = true
                            showPaymentError(
                                raw = e.message,
                                throwable = e,
                                isMandateStep = true,
                                onRetry = { resumeSubscriptionFlow() },
                            )
                        },
                    )
                }
            }
        )
        TrialActivatedSuccessOverlay(
            visible = showTrialSuccessScreen,
            onContinue = {
                scope.launch {
                    val phoneForNav = dataStoreManager.phoneNumber.firstOrNull()?.trim().orEmpty()
                        .ifBlank { phone }
                    val hasPremium = if (phoneForNav.isNotBlank()) {
                        EntitlementRepository.refresh(phoneForNav)
                    } else {
                        false
                    }
                    showTrialSuccessScreen = false
                    if (hasPremium) {
                        Log.i("SUBSCRIPTION", "Navigating to home (entitlement confirmed)")
                        navController.navigate("home") {
                            popUpTo("subscription") { inclusive = true }
                        }
                    } else {
                        Log.w("SUBSCRIPTION", "Trial success dismissed without premium entitlement")
                        showSetupExplanationScreen = true
                        showPaymentError(
                            raw = "mandate_not_entitled",
                            isMandateStep = true,
                            onRetry = { resumeSubscriptionFlow() },
                        )
                    }
                }
            }
        )

        SubscriptionConfirmingOverlay(
            visible = confirming,
            soft = confirmingSoft,
            onContinueInBackground = {
                // Let the user browse the paywall; the durable job + recovery keep confirming and
                // will surface success automatically once entitlement lands.
                confirming = false
            },
        )

        PaymentErrorAlertDialog(
            state = paymentErrorDialog,
            onDismiss = { paymentErrorDialog = null },
        )
    }
}

/**
 * Blocking, reassuring overlay shown while the orchestrator confirms the subscription. Replaces the
 * old "Finalizing…" snackbar so the user is never shown a premature failure during confirmation.
 */
@Composable
private fun SubscriptionConfirmingOverlay(
    visible: Boolean,
    soft: Boolean,
    onContinueInBackground: () -> Unit,
) {
    if (!visible) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE60A0A0B)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp),
        ) {
            CircularProgressIndicator(color = PremiumSubscriptionColors.Gold)
            Spacer(Modifier.height(20.dp))
            Text(
                text = if (soft) "Still confirming your subscription…" else "Confirming your subscription…",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (soft) {
                    "This is taking a little longer than usual. We’ll finish automatically in the " +
                        "background — you won’t be charged twice and you won’t lose premium."
                } else {
                    "Please keep the app open. This can take a few moments — you won’t be charged twice."
                },
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            if (soft) {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onContinueInBackground) {
                    Text(
                        text = "Continue browsing",
                        color = PremiumSubscriptionColors.Gold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
