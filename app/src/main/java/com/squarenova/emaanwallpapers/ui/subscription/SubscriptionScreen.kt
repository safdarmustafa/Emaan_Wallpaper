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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.squarenova.emaanwallpapers.data.MandateDebugLog
import com.squarenova.emaanwallpapers.data.MandateEntitlementResolver
import com.squarenova.emaanwallpapers.data.SubscriptionEntitlement
import com.squarenova.emaanwallpapers.data.UserSubscriptionSyncManager
import com.squarenova.emaanwallpapers.network.SubscriptionApi
import com.squarenova.emaanwallpapers.network.SupabaseClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import android.util.Log
import com.squarenova.emaanwallpapers.BuildConfig
import io.github.jan.supabase.postgrest.postgrest
import org.json.JSONObject
import kotlinx.serialization.Serializable

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
    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var bannerIsSuccess by remember { mutableStateOf(true) }
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
                pendingSubscriptionId = subId
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

    LaunchedEffect(bannerMessage) {
        if (bannerMessage != null) {
            delay(2500)
            bannerMessage = null
        }
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
        paymentErrorDialog = PaymentErrorDialogState(
            type = PaymentErrors.classify(raw, throwable, errorCode, isMandateStep),
            onRetry = onRetry,
        )
        isLoading = false
    }

    suspend fun fetchSubscriptionIdFromServer(): String? {
        val cleanPhone = phone.trim()
        if (cleanPhone.isBlank()) return null
        val row = try {
            SupabaseClient.client
                .postgrest["users"]
                .select { filter { eq("phone_number", cleanPhone) } }
                .decodeList<SubscriptionResumeUserRow>()
                .firstOrNull()
        } catch (_: Exception) {
            null
        } ?: return null
        hasTrialPaid = row.trial_paid == true
        val subId = row.razorpay_subscription_id?.trim().orEmpty()
        if (subId.isNotBlank()) {
            pendingSubscriptionId = subId
        }
        return subId.takeIf { it.isNotBlank() }
    }

    val retrySubscriptionHolder = remember { object { lateinit var action: () -> Unit } }
    fun retrySubscription() = retrySubscriptionHolder.action()

    fun navigateBackToLogin() {
        scope.launch {
            showSetupExplanationScreen = false
            showTrialSuccessScreen = false
            dataStoreManager.logout()
            navController.navigate("login") {
                popUpTo("subscription") { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    fun openMandateCheckout(subscriptionId: String) {
        val hostActivity = activity
        if (hostActivity == null) {
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

    fun startCheckout() {
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
                        checkout.open(hostActivity, options)
                    } catch (e: Exception) {
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
        scope.launch {
            isLoading = true
            val subId = pendingSubscriptionId?.trim().orEmpty()
                .ifBlank { fetchSubscriptionIdFromServer().orEmpty() }
            if (subId.isNotBlank()) {
                hasTrialPaid = true
                entryFeeSuccessHandled = true
                showSetupExplanationScreen = true
                isLoading = false
                openMandateCheckout(subId)
                return@launch
            }
            val created = SubscriptionApi.createSubscription(phoneForApi)
            created.fold(
                onSuccess = { newSubId ->
                    hasTrialPaid = true
                    entryFeeSuccessHandled = true
                    pendingSubscriptionId = newSubId
                    showSetupExplanationScreen = true
                    isLoading = false
                    openMandateCheckout(newSubId)
                },
                onFailure = { e ->
                    isLoading = false
                    showSetupExplanationScreen = true
                    showPaymentError(
                        raw = e.message,
                        throwable = e,
                        isMandateStep = true,
                        onRetry = { retrySubscription() },
                    )
                }
            )
        }
    }

    fun resumeSubscriptionFlow() {
        if (phone.isBlank()) {
            showPaymentError(
                raw = "session_expired",
                onRetry = { navigateBackToLogin() },
            )
            return
        }
        val subId = pendingSubscriptionId?.trim().orEmpty()
        if (hasTrialPaid && subId.isNotBlank()) {
            showSetupExplanationScreen = true
            mandateLaunchHandled = false
            openMandateCheckout(subId)
            return
        }
        if (hasTrialPaid) {
            resumeAfterEntryFeePaid(phone.trim())
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

                            when (phase) {
                            CheckoutKind.ENTRY_FEE -> {
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
                                    onSuccess = { subId ->
                                        AnalyticsManager.track(
                                            "subscription_created",
                                            mapOf(
                                                "subscription_id" to subId,
                                                "plan_id" to "monthly_99"
                                            )
                                        )
                                        AnalyticsManager.track(
                                            "mandate_screen_shown",
                                            mapOf("source" to "after_trial_setup")
                                        )
                                        pendingSubscriptionId = subId
                                        hasTrialPaid = true
                                        showSetupExplanationScreen = true
                                        isLoading = false
                                        mandateLaunchHandled = false
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
                                val subId = pendingSubscriptionId?.trim().orEmpty()
                                    .ifBlank { fetchSubscriptionIdFromServer().orEmpty() }
                                if (subId.isBlank()) {
                                    showSetupExplanationScreen = true
                                    currentCheckoutKind = CheckoutKind.NONE
                                    showPaymentError(
                                        raw = "missing_subscription_id",
                                        isMandateStep = true,
                                        onRetry = { resumeSubscriptionFlow() },
                                    )
                                    return@launch
                                }
                                pendingSubscriptionId = subId
                                MandateDebugLog.note(
                                    "MANDATE PaymentResult.Success — starting verifyMandateWithPolling subId=$subId"
                                )
                                Log.i("SUBSCRIPTION", "MANDATE success callback; verifying mandate status")
                                bannerIsSuccess = true
                                bannerMessage = "Finalizing AutoPay approval..."
                                val mandateVerified = SubscriptionApi.verifyMandateWithPolling(subId)
                                mandateVerified.fold(
                                    onSuccess = { status ->
                                        MandateDebugLog.note(
                                            "verifyMandateWithPolling succeeded razorpay_status=$status — calling activateTrial"
                                        )
                                        AnalyticsManager.track(
                                            "mandate_success",
                                            mapOf("subscription_id" to subId)
                                        )
                                        if (status == "active") {
                                            AnalyticsManager.track(
                                                "subscription_activated",
                                                mapOf("current_period_end" to null)
                                            )
                                        }
                                        scope.launch {
                                            val activated = SubscriptionApi.activateTrial(phoneForApi)
                                            activated.fold(
                                                onSuccess = { trialEnd ->
                                                    MandateDebugLog.note(
                                                        "post-mandate activateTrial success trial_end=$trialEnd — syncing entitlement"
                                                    )
                                                    val syncManager =
                                                        UserSubscriptionSyncManager(dataStoreManager)
                                                    val hasPremium =
                                                        syncManager.syncUserSubscription(phoneForApi)
                                                    if (!hasPremium) {
                                                        MandateDebugLog.note(
                                                            "post-mandate syncUserSubscription returned hasPremium=false " +
                                                                "(expected trial after activate-trial)"
                                                        )
                                                        Log.e(
                                                            "SUBSCRIPTION",
                                                            "Mandate OK but no premium entitlement after activate-trial"
                                                        )
                                                        isLoading = false
                                                        showSetupExplanationScreen = true
                                                        mandatePaymentSuccessReceived = true
                                                        mandateLaunchHandled = false
                                                        showPaymentError(
                                                            raw = "mandate_not_entitled",
                                                            isMandateStep = true,
                                                            onRetry = { resumeSubscriptionFlow() },
                                                        )
                                                        return@launch
                                                    }
                                                    Log.i(
                                                        "SUBSCRIPTION",
                                                        "Mandate verified status=$status; entitlement granted"
                                                    )
                                                    isLoading = false
                                                    showSetupExplanationScreen = false
                                                    currentCheckoutKind = CheckoutKind.NONE
                                                    entryFeeSuccessHandled = false
                                                    mandateLaunchHandled = false
                                                    pendingSubscriptionId = null
                                                    currentOrderId = null
                                                    mandatePaymentSuccessReceived = false
                                                    showTrialSuccessScreen = true
                                                },
                                                onFailure = { e ->
                                                    MandateDebugLog.note(
                                                        "post-mandate activateTrial failed error=${e.message}"
                                                    )
                                                    AnalyticsManager.track(
                                                        "mandate_failed",
                                                        mapOf(
                                                            "error_reason" to (e.message
                                                                ?: "activate_trial_failed")
                                                        )
                                                    )
                                                    Log.e(
                                                        "SUBSCRIPTION",
                                                        "activate-trial after mandate failed",
                                                        e
                                                    )
                                                    isLoading = false
                                                    showSetupExplanationScreen = true
                                                    mandatePaymentSuccessReceived = true
                                                    mandateLaunchHandled = false
                                                    mandateRetryCooldownUntilMs =
                                                        System.currentTimeMillis() + 3000L
                                                    showPaymentError(
                                                        raw = e.message,
                                                        throwable = e,
                                                        isMandateStep = true,
                                                        onRetry = { resumeSubscriptionFlow() },
                                                    )
                                                }
                                            )
                                        }
                                    },
                                    onFailure = { e ->
                                        AnalyticsManager.track(
                                            "mandate_failed",
                                            mapOf("error_reason" to (e.message ?: "verification_failed"))
                                        )
                                        Log.e("SUBSCRIPTION", "Mandate verification failed", e)
                                        showSetupExplanationScreen = true
                                        mandatePaymentSuccessReceived = true
                                        mandateLaunchHandled = false
                                        mandateRetryCooldownUntilMs = System.currentTimeMillis() + 3000L
                                        showPaymentError(
                                            raw = e.message,
                                            throwable = e,
                                            isMandateStep = true,
                                            onRetry = { resumeSubscriptionFlow() },
                                        )
                                    }
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
                    onClick = { navController.navigate("subscription_disclosure") },
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
                val subId = pendingSubscriptionId
                if (subId.isNullOrBlank()) {
                    showSetupExplanationScreen = true
                    currentCheckoutKind = CheckoutKind.NONE
                    showPaymentError(
                        raw = "missing_subscription_id",
                        isMandateStep = true,
                        onRetry = { resumeSubscriptionFlow() },
                    )
                    return@SubscriptionSetupFullScreenOverlay
                }
                mandateLaunchHandled = true
                isLoading = true
                if (mandatePaymentSuccessReceived) {
                    AnalyticsManager.track("mandate_retry_clicked")
                    resumeSubscriptionFlow()
                } else {
                    AnalyticsManager.track("mandate_initiated")
                    openMandateCheckout(subId)
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
                        UserSubscriptionSyncManager(dataStoreManager)
                            .syncUserSubscription(phoneForNav)
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

        bannerMessage?.let { msg ->
            if (bannerIsSuccess) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    containerColor = PremiumSubscriptionColors.Gold.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        msg,
                        color = Color(0xFF0A0A0B),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        PaymentErrorAlertDialog(
            state = paymentErrorDialog,
            onDismiss = { paymentErrorDialog = null },
        )
    }
}
