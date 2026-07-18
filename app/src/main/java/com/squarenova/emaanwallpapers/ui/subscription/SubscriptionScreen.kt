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
import com.squarenova.emaanwallpapers.analytics.AnalyticsEvents
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
    var mandateLaunchHandled by remember { mutableStateOf(false) }
    var mandatePaymentSuccessReceived by remember { mutableStateOf(false) }
    var mandateRetryCooldownUntilMs by remember { mutableStateOf(0L) }
    var retryNowMs by remember { mutableStateOf(0L) }
    var pendingSubscriptionId by remember { mutableStateOf<String?>(null) }
    var hasTrialPaid by remember { mutableStateOf(false) }

    /** Full-screen explanation shown before the mandate (AutoPay) Razorpay checkout. */
    var showSetupExplanationScreen by remember { mutableStateOf(false) }

    /** Post–mandate success; user taps Continue → Home. */
    var showTrialSuccessScreen by remember { mutableStateOf(false) }

    /** Durable confirmation is running (orchestrator owns verify → activate → sync). */
    var confirming by remember { mutableStateOf(false) }

    /** Confirmation is taking longer than usual but is still running in the background. */
    var confirmingSoft by remember { mutableStateOf(false) }

    /** Subscription created but AutoPay mandate never completed — offer to finish it. */
    var showMandatePending by remember { mutableStateOf(false) }

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
     * Called when create-subscription reports the subscription already has a LIVE mandate
     * (authenticated/active/pending → checkout_required=false). Opening Checkout again would fail
     * ("The id provided does not exist"), so we resolve via the entitlement source of truth and, if
     * not yet premium, hand off to the durable orchestrator confirmation. NEVER opens Checkout.
     * Always "handles" (returns true) so callers never fall through to a doomed mandate checkout.
     */
    suspend fun handleAlreadyActiveMandate(phoneForApi: String, subscriptionId: String): Boolean {
        isLoading = false
        showSetupExplanationScreen = false
        // Orchestrator fast-path performs the single entitlement refresh (webhook may already
        // have granted trial). A refresh here duplicated that work on authenticated retry paths
        // where create-subscription already confirmed live mandate status at Razorpay.
        confirming = true
        SubscriptionOrchestrator.onMandatePaymentSuccess(
            phone = phoneForApi,
            subscriptionId = subscriptionId,
            paymentId = "",
        )
        return true
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
                put("description", "Approve AutoPay ₹249/month")
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
                        handleAlreadyActiveMandate(phoneForApi, created.subscriptionId)
                        return@launch
                    }
                    hasTrialPaid = true
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
        // Mandate-first onboarding: Subscribe enters the mandate pipeline directly —
        // createSubscription → openMandateCheckout → SubscriptionOrchestrator → activateTrial → Premium.
        launchMandateCheckoutViaBackend(phone.trim())
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
                                        handleAlreadyActiveMandate(phoneForApi, created.subscriptionId)
                                        return@launch
                                    }
                                    subId = created.subscriptionId
                                }
                                pendingSubscriptionId = subId
                                Log.i("SUBSCRIPTION", "MANDATE success — handing off to durable confirmation")
                                AnalyticsManager.track(
                                    AnalyticsEvents.MANDATE_SUCCESS,
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
                    // Mandate is the only checkout in the flow, so every failure is a mandate step.
                    AnalyticsManager.track(
                        AnalyticsEvents.MANDATE_FAILED,
                        mapOf("error_reason" to result.message)
                    )
                    showTrialSuccessScreen = false
                    currentCheckoutKind = CheckoutKind.NONE
                    mandatePaymentSuccessReceived = false
                    mandateLaunchHandled = false
                    showSetupExplanationScreen = true
                    showPaymentError(
                        raw = result.message,
                        errorCode = result.errorCode.takeIf { it >= 0 },
                        isMandateStep = true,
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
                showMandatePending = false
                isLoading = false
                showSetupExplanationScreen = false
                showTrialSuccessScreen = false
                paymentErrorDialog = null
                Log.d("SUBSCRIPTION", "confirmation phase=${s.phase} attempt=${s.attempt}")
            }

            is SubscriptionState.Premium -> {
                confirming = false
                confirmingSoft = false
                showMandatePending = false
                isLoading = false
                showSetupExplanationScreen = false
                currentCheckoutKind = CheckoutKind.NONE
                mandateLaunchHandled = false
                mandatePaymentSuccessReceived = false
                pendingSubscriptionId = null
                AnalyticsManager.track(AnalyticsEvents.SUBSCRIPTION_CONFIRMED)
                showTrialSuccessScreen = true
                SubscriptionOrchestrator.reset()
            }

            is SubscriptionState.SoftTimeout -> {
                // Graceful, NOT a failure — the durable job keeps confirming in the background.
                // We must NOT surface a retry that reopens Razorpay while it runs (duplicate risk).
                confirming = true
                confirmingSoft = true
                showMandatePending = false
                isLoading = false
                showSetupExplanationScreen = false
                paymentErrorDialog = null
            }

            is SubscriptionState.MandatePending -> {
                // Subscription created, but AutoPay mandate never completed. Not a failure.
                // Retain the durable ticket (survives restart); offer a single resume action.
                confirming = false
                confirmingSoft = false
                showMandatePending = true
                isLoading = false
                showSetupExplanationScreen = false
                showTrialSuccessScreen = false
                paymentErrorDialog = null
                hasTrialPaid = true
                mandatePaymentSuccessReceived = false
                mandateLaunchHandled = false
                pendingSubscriptionId = s.subscriptionId
                // Deliberately do NOT reset() — the ticket must persist for restart recovery.
            }

            is SubscriptionState.TerminalFailure -> {
                confirming = false
                confirmingSoft = false
                showMandatePending = false
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
                showMandatePending = false
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
                    text = "Start your 1-day free trial now by approving AutoPay for ₹249/month — you won’t be charged until your trial ends.",
                    color = PremiumSubscriptionColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                PremiumGradientCtaButton(
                    text = if (hasTrialPaid) "Continue AutoPay Setup" else "Start Free Trial",
                    onClick = { resumeSubscriptionFlow() },
                    enabled = !showTrialSuccessScreen && !showSetupExplanationScreen,
                    loading = isLoading && !showSetupExplanationScreen
                )

                Text(
                    text = "Cancel anytime from Profile → Manage Subscription. " +
                        "Subscription auto-renews at ₹249/month unless cancelled 24h before renewal.",
                    color = PremiumSubscriptionColors.TextSecondary.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                TextButton(
                    onClick = {
                        AnalyticsManager.trackEvent(AnalyticsEvents.SUBSCRIPTION_DISCLOSURE_LINK_TAPPED)
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
                    AnalyticsManager.track(AnalyticsEvents.MANDATE_RETRY_CLICKED)
                    mandateLaunchHandled = false
                    launchMandateCheckoutViaBackend(phone.trim())
                    return@SubscriptionSetupFullScreenOverlay
                }
                AnalyticsManager.track(AnalyticsEvents.MANDATE_INITIATED)
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
                                handleAlreadyActiveMandate(phoneForApi, created.subscriptionId)
                                return@launch
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

        MandatePendingOverlay(
            visible = showMandatePending,
            loading = isLoading,
            onCompleteSetup = {
                val phoneForApi = phone.trim()
                if (phoneForApi.isBlank()) {
                    showMandatePending = false
                    showPaymentError(
                        raw = "session_expired",
                        onRetry = { navigateBackToLogin() },
                    )
                    return@MandatePendingOverlay
                }
                showMandatePending = false
                AnalyticsManager.track(AnalyticsEvents.MANDATE_PENDING_RESUME_CLICKED)
                // Resumes THIS subscription (create-subscription reuses created/authenticated) and
                // opens ONLY the mandate checkout — never charges twice.
                launchMandateCheckoutViaBackend(phoneForApi)
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

/**
 * Shown when the subscription was created but the AutoPay mandate was never completed (the user
 * exited/cancelled the mandate checkout). This is a clear resolvable state — not a failure and not
 * an endless "confirming". A single action resumes the SAME subscription's mandate checkout; the
 * user is never charged twice. The durable ticket keeps this state alive across restarts.
 */
@Composable
private fun MandatePendingOverlay(
    visible: Boolean,
    loading: Boolean,
    onCompleteSetup: () -> Unit,
) {
    if (!visible) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF2000000)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = PremiumSubscriptionColors.SurfaceElevated,
            tonalElevation = 0.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, PremiumSubscriptionColors.Gold.copy(alpha = 0.3f)),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "✅", fontSize = 34.sp)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Finish AutoPay setup",
                    color = PremiumSubscriptionColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Your subscription was created, but AutoPay setup wasn’t finished. " +
                        "Complete it now to activate your 1-day free trial and premium access.",
                    color = PremiumSubscriptionColors.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "You won’t be charged today.",
                    color = PremiumSubscriptionColors.Gold.copy(alpha = 0.95f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                PremiumGradientCtaButton(
                    text = "Complete AutoPay Setup",
                    onClick = onCompleteSetup,
                    enabled = !loading,
                    loading = loading,
                )
            }
        }
    }
}
