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
private data class SubscriptionAnalyticsUserRow(
    val phone_number: String? = null,
    val trial_paid: Boolean? = false
)

@Composable
fun SubscriptionScreen(navController: NavController) {

    val context = LocalContext.current
    val activity = LocalActivity.current
    val dataStoreManager = DataStoreManager(context)
    val scope = rememberCoroutineScope()

    var phone by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var bannerIsSuccess by remember { mutableStateOf(true) }

    var currentCheckoutKind by remember { mutableStateOf(CheckoutKind.NONE) }
    var entryFeeSuccessHandled by remember { mutableStateOf(false) }
    var mandateLaunchHandled by remember { mutableStateOf(false) }
    var mandatePaymentSuccessReceived by remember { mutableStateOf(false) }
    var mandateVerificationError by remember { mutableStateOf<String?>(null) }
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
                    .decodeList<SubscriptionAnalyticsUserRow>()
                    .firstOrNull()
            } catch (_: Exception) {
                null
            }
            hasTrialPaid = row?.trial_paid == true
            userStatus = if (row == null) "new" else "returning"
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
            showSetupExplanationScreen = false
            isLoading = false
            bannerIsSuccess = false
            bannerMessage = "Cannot open AutoPay screen. Restart the app."
            Log.e("SUBSCRIPTION", "openMandateCheckout: activity null")
            return
        }
        showSetupExplanationScreen = false
        currentCheckoutKind = CheckoutKind.MANDATE
        SubscriptionManager.prepareCheckout(CheckoutKind.MANDATE)
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
            isLoading = false
            showSetupExplanationScreen = false
            bannerIsSuccess = false
            bannerMessage = e.message ?: "Could not open subscription confirmation"
        }
    }

    fun startCheckout() {
        if (phone.isBlank()) {
            error = "Session expired. Please login again."
            bannerIsSuccess = false
            bannerMessage = error
            return
        }

        val hostActivity = activity
        if (hostActivity == null) {
            error = "Cannot open payment. Restart the app."
            bannerIsSuccess = false
            bannerMessage = error
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
            error = null
            entryFeeSuccessHandled = false
            mandateLaunchHandled = false
            mandatePaymentSuccessReceived = false
            mandateVerificationError = null
            currentOrderId = null
            pendingSubscriptionId = null

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
                        isLoading = false
                        currentOrderId = null
                        bannerIsSuccess = false
                        bannerMessage = e.message ?: "Could not open payment"
                    }
                },
                onFailure = {
                    AnalyticsManager.track(
                        "payment_failed",
                        mapOf(
                            "error_reason" to (it.message ?: "create_order_failed"),
                            "step" to "entry_fee"
                        )
                    )
                    error = it.message ?: "Order failed"
                    bannerIsSuccess = false
                    bannerMessage = error
                    isLoading = false
                }
            )
        }
    }

    fun friendlyPaymentError(raw: String?): String {
        val msg = raw?.trim().orEmpty()
        if (msg.isBlank()) return "Payment verification failed. Please try again."
        val lower = msg.lowercase()
        return when {
            "refund" in lower || "refunded" in lower ->
                "This payment appears refunded/invalid. Please make a fresh payment and try again."
            "replay" in lower || "already verified" in lower ->
                "This payment was already used. Please start again with a new payment."
            else -> msg
        }
    }

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
                                bannerIsSuccess = false
                                bannerMessage = "Session expired. Please login again."
                                currentCheckoutKind = CheckoutKind.NONE
                                Log.e("SUBSCRIPTION", "Payment OK but phone missing from DataStore")
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
                                    isLoading = false
                                    bannerIsSuccess = false
                                    bannerMessage = "Missing payment id from Razorpay"
                                    return@launch
                                }
                                if (orderId.isBlank()) {
                                    entryFeeSuccessHandled = false
                                    currentCheckoutKind = CheckoutKind.NONE
                                    isLoading = false
                                    bannerIsSuccess = false
                                    bannerMessage = "Order reference missing. Please retry payment."
                                    return@launch
                                }

                                val verified = SubscriptionApi.verifyPayment(
                                    phone = phoneForApi,
                                    paymentId = payId,
                                    orderId = orderId
                                )
                                if (verified.isFailure) {
                                    AnalyticsManager.track(
                                        "payment_failed",
                                        mapOf(
                                            "error_reason" to (verified.exceptionOrNull()?.message ?: "verify_failed"),
                                            "step" to "entry_fee"
                                        )
                                    )
                                    entryFeeSuccessHandled = false
                                    currentCheckoutKind = CheckoutKind.NONE
                                    isLoading = false
                                    bannerIsSuccess = false
                                    bannerMessage = friendlyPaymentError(
                                        verified.exceptionOrNull()?.message
                                    )
                                    return@launch
                                }

                                val trialStarted = SubscriptionApi.startTrial(phoneForApi)
                                if (trialStarted.isFailure) {
                                    AnalyticsManager.track(
                                        "trial_start_failed",
                                        mapOf("error_message" to (trialStarted.exceptionOrNull()?.message ?: "unknown"))
                                    )
                                    entryFeeSuccessHandled = false
                                    currentCheckoutKind = CheckoutKind.NONE
                                    isLoading = false
                                    bannerIsSuccess = false
                                    bannerMessage = trialStarted.exceptionOrNull()?.message
                                        ?: "Could not start trial."
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
                                        showSetupExplanationScreen = true
                                        isLoading = false
                                        mandateLaunchHandled = false
                                        mandateVerificationError = null
                                    },
                                    onFailure = { e ->
                                        AnalyticsManager.track(
                                            "subscription_creation_failed",
                                            mapOf("error_message" to (e.message ?: "unknown"))
                                        )
                                        entryFeeSuccessHandled = false
                                        currentCheckoutKind = CheckoutKind.NONE
                                        isLoading = false
                                        showSetupExplanationScreen = false
                                        bannerIsSuccess = false
                                        bannerMessage = e.message
                                            ?: "Could not create subscription after payment. Contact support with your payment id."
                                        Log.e("SUBSCRIPTION", "createSubscription failed", e)
                                    }
                                )
                            }

                            CheckoutKind.MANDATE -> {
                                val subId = pendingSubscriptionId?.trim().orEmpty()
                                if (subId.isBlank()) {
                                    isLoading = false
                                    showSetupExplanationScreen = false
                                    bannerIsSuccess = false
                                    bannerMessage = "Subscription reference missing. Please retry."
                                    currentCheckoutKind = CheckoutKind.NONE
                                    return@launch
                                }
                                Log.i("SUBSCRIPTION", "MANDATE success callback; verifying mandate status")
                                bannerIsSuccess = true
                                bannerMessage = "Finalizing AutoPay approval..."
                                val mandateVerified = SubscriptionApi.verifyMandateWithPolling(subId)
                                mandateVerified.fold(
                                    onSuccess = { status ->
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
                                        Log.i("SUBSCRIPTION", "Mandate verified status=$status; allowing navigation")
                                        isLoading = false
                                        showSetupExplanationScreen = false
                                        currentCheckoutKind = CheckoutKind.NONE
                                        entryFeeSuccessHandled = false
                                        mandateLaunchHandled = false
                                        pendingSubscriptionId = null
                                        currentOrderId = null
                                        mandatePaymentSuccessReceived = false
                                        mandateVerificationError = null
                                        showTrialSuccessScreen = true
                                    },
                                    onFailure = { e ->
                                        AnalyticsManager.track(
                                            "mandate_failed",
                                            mapOf("error_reason" to (e.message ?: "verification_failed"))
                                        )
                                        Log.e("SUBSCRIPTION", "Mandate verification failed", e)
                                        isLoading = false
                                        showSetupExplanationScreen = true
                                        mandatePaymentSuccessReceived = true
                                        mandateLaunchHandled = false
                                        mandateRetryCooldownUntilMs = System.currentTimeMillis() + 3000L
                                        mandateVerificationError =
                                            "AutoPay approval failed. Please try again.\n" +
                                                "UPI app may have declined or timed out. Retry with the same/default account."
                                        bannerIsSuccess = false
                                        bannerMessage = "AutoPay approval failed. Please try again."
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
                            isLoading = false
                            showSetupExplanationScreen = false
                            showTrialSuccessScreen = false
                            bannerIsSuccess = false
                            bannerMessage = "Something went wrong after payment. Please reopen subscription and retry."
                            currentCheckoutKind = CheckoutKind.NONE
                            entryFeeSuccessHandled = false
                            mandateLaunchHandled = false
                        }
                    }
                }

                is PaymentResult.Error -> {
                    val failedStep = if (currentCheckoutKind == CheckoutKind.MANDATE) "mandate" else "entry_fee"
                    if (failedStep == "mandate") {
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
                    error = result.message
                    isLoading = false
                    showSetupExplanationScreen = false
                    showTrialSuccessScreen = false
                    bannerIsSuccess = false
                    bannerMessage = result.message
                    currentCheckoutKind = CheckoutKind.NONE
                    entryFeeSuccessHandled = false
                    mandatePaymentSuccessReceived = false
                    mandateVerificationError = null
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
                    text = "Start ₹5 Trial",
                    onClick = { startCheckout() },
                    enabled = !showTrialSuccessScreen,
                    loading = isLoading && !showSetupExplanationScreen
                )

                Text(
                    text = "Cancel anytime from Profile. Access continues through the end of your paid period.",
                    color = PremiumSubscriptionColors.TextSecondary.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                error?.let {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0x33FF5252),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = it,
                            color = Color(0xFFFFCDD2),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }

        SubscriptionSetupFullScreenOverlay(
            visible = showSetupExplanationScreen,
            ctaText = if (mandatePaymentSuccessReceived) "Retry AutoPay Approval" else "Continue",
            ctaEnabled = !mandatePaymentSuccessReceived || retryNowMs >= mandateRetryCooldownUntilMs,
            errorText = mandateVerificationError,
            onContinue = {
                if (mandateLaunchHandled) return@SubscriptionSetupFullScreenOverlay
                val subId = pendingSubscriptionId
                if (subId.isNullOrBlank()) {
                    bannerIsSuccess = false
                    bannerMessage = "Subscription setup incomplete. Please retry."
                    showSetupExplanationScreen = false
                    isLoading = false
                    currentCheckoutKind = CheckoutKind.NONE
                    return@SubscriptionSetupFullScreenOverlay
                }
                mandateLaunchHandled = true
                isLoading = true
                if (mandatePaymentSuccessReceived) {
                    AnalyticsManager.track("mandate_retry_clicked")
                    scope.launch {
                        bannerIsSuccess = true
                        bannerMessage = "Retrying AutoPay verification..."
                        SubscriptionApi.verifyMandateWithPolling(subId).fold(
                            onSuccess = {
                                AnalyticsManager.track(
                                    "mandate_success",
                                    mapOf("subscription_id" to subId)
                                )
                                isLoading = false
                                showSetupExplanationScreen = false
                                currentCheckoutKind = CheckoutKind.NONE
                                entryFeeSuccessHandled = false
                                mandateLaunchHandled = false
                                pendingSubscriptionId = null
                                currentOrderId = null
                                mandatePaymentSuccessReceived = false
                                mandateVerificationError = null
                                showTrialSuccessScreen = true
                            },
                            onFailure = { e ->
                                AnalyticsManager.track(
                                    "mandate_failed",
                                    mapOf("error_reason" to (e.message ?: "verification_failed"))
                                )
                                isLoading = false
                                showSetupExplanationScreen = true
                                mandateLaunchHandled = false
                                mandatePaymentSuccessReceived = true
                                mandateRetryCooldownUntilMs = System.currentTimeMillis() + 3000L
                                mandateVerificationError =
                                    "AutoPay approval failed. Please try again.\n" +
                                        "UPI app may have declined or timed out. Retry with the same/default account."
                                bannerIsSuccess = false
                                bannerMessage = "AutoPay approval failed. Please try again."
                            }
                        )
                    }
                } else {
                    AnalyticsManager.track("mandate_initiated")
                    openMandateCheckout(subId)
                }
            }
        )
        TrialActivatedSuccessOverlay(
            visible = showTrialSuccessScreen,
            onContinue = {
                showTrialSuccessScreen = false
                Log.i("SUBSCRIPTION", "Navigating to home")
                navController.navigate("home") {
                    popUpTo("subscription") { inclusive = true }
                }
            }
        )

        bannerMessage?.let { msg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = if (bannerIsSuccess) PremiumSubscriptionColors.Gold.copy(alpha = 0.92f) else Color(0xFFB00020),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    msg,
                    color = if (bannerIsSuccess) Color(0xFF0A0A0B) else Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
