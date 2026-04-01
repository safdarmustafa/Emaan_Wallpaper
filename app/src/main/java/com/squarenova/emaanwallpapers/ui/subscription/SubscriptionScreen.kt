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
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.network.SubscriptionApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import org.json.JSONObject

object RazorpayConfig {
    const val KEY_ID = "rzp_live_SUkaGbslh0IvIZ"
}

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

    /** Full-screen loader after ₹5, before mandate Razorpay. */
    var showFullScreenSetupLoader by remember { mutableStateOf(false) }

    /** Post–mandate success; user taps Continue → Home. */
    var showTrialSuccessScreen by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        phone = dataStoreManager.phoneNumber.firstOrNull() ?: ""
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

    fun navigateBackToLogin() {
        scope.launch {
            showFullScreenSetupLoader = false
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
            showFullScreenSetupLoader = false
            isLoading = false
            bannerIsSuccess = false
            bannerMessage = "Cannot open AutoPay screen. Restart the app."
            Log.e("SUBSCRIPTION", "openMandateCheckout: activity null")
            return
        }
        showFullScreenSetupLoader = false
        currentCheckoutKind = CheckoutKind.MANDATE
        SubscriptionManager.prepareCheckout(CheckoutKind.MANDATE)
        Log.i("SUBSCRIPTION", "Opening mandate checkout subscriptionId=$subscriptionId")
        try {
            val checkout = Checkout()
            checkout.setKeyID(RazorpayConfig.KEY_ID)
            val options = JSONObject().apply {
                put("subscription_id", subscriptionId)
                put("name", "Emaan Wallpapers")
                put("description", "Approve AutoPay · Confirm subscription")
                put("prefill.contact", phone)
            }
            checkout.open(hostActivity, options)
        } catch (e: Exception) {
            Log.e("SUBSCRIPTION", "openMandateCheckout", e)
            currentCheckoutKind = CheckoutKind.NONE
            isLoading = false
            showFullScreenSetupLoader = false
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
            isLoading = true
            error = null
            entryFeeSuccessHandled = false

            val orderResult = SubscriptionApi.createOrder()
            orderResult.fold(
                onSuccess = { orderId ->
                    try {
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
                        }
                        checkout.open(hostActivity, options)
                    } catch (e: Exception) {
                        Log.e("SUBSCRIPTION", "startCheckout open", e)
                        currentCheckoutKind = CheckoutKind.NONE
                        isLoading = false
                        bannerIsSuccess = false
                        bannerMessage = e.message ?: "Could not open payment"
                    }
                },
                onFailure = {
                    error = it.message ?: "Order failed"
                    bannerIsSuccess = false
                    bannerMessage = error
                    isLoading = false
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        SubscriptionManager.paymentResult.collect { result ->
            when (result) {
                is PaymentResult.Success -> {
                    scope.launch {
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
                            showFullScreenSetupLoader = false
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
                                entryFeeSuccessHandled = true

                                val payId = result.paymentId
                                if (payId.isBlank()) {
                                    entryFeeSuccessHandled = false
                                    currentCheckoutKind = CheckoutKind.NONE
                                    isLoading = false
                                    bannerIsSuccess = false
                                    bannerMessage = "Missing payment id from Razorpay"
                                    return@launch
                                }
                                val created = SubscriptionApi.createSubscription(phoneForApi, payId)
                                created.fold(
                                    onSuccess = { subId ->
                                        withContext(Dispatchers.Main) {
                                            showFullScreenSetupLoader = true
                                            isLoading = true
                                            Log.i(
                                                "SUBSCRIPTION",
                                                "Pre-mandate full-screen loader (800ms) before mandate checkout"
                                            )
                                            delay(800)
                                            openMandateCheckout(subId)
                                        }
                                    },
                                    onFailure = { e ->
                                        entryFeeSuccessHandled = false
                                        currentCheckoutKind = CheckoutKind.NONE
                                        isLoading = false
                                        showFullScreenSetupLoader = false
                                        bannerIsSuccess = false
                                        bannerMessage = e.message
                                            ?: "Could not create subscription after payment. Contact support with your payment id."
                                        Log.e("SUBSCRIPTION", "createSubscription failed", e)
                                    }
                                )
                            }

                            CheckoutKind.MANDATE -> {
                                Log.i("SUBSCRIPTION", "MANDATE success")
                                val activated =
                                    SubscriptionApi.activateTrialWithRetries(phoneForApi)
                                activated.fold(
                                    onSuccess = {
                                        isLoading = false
                                        showFullScreenSetupLoader = false
                                        currentCheckoutKind = CheckoutKind.NONE
                                        entryFeeSuccessHandled = false
                                        showTrialSuccessScreen = true
                                        Log.i("SUBSCRIPTION", "Trial activated — showing success screen")
                                    },
                                    onFailure = { e ->
                                        currentCheckoutKind = CheckoutKind.NONE
                                        isLoading = false
                                        showFullScreenSetupLoader = false
                                        bannerIsSuccess = false
                                        val reason = e.message
                                            ?: "Trial could not be activated after 3 attempts."
                                        bannerMessage = reason
                                        Log.e(
                                            "SUBSCRIPTION",
                                            "activateTrialWithRetries final failure: $reason",
                                            e
                                        )
                                    }
                                )
                            }

                            CheckoutKind.NONE -> {
                                isLoading = false
                                showFullScreenSetupLoader = false
                                Log.w(
                                    "SUBSCRIPTION",
                                    "Ignoring Success: phase NONE (stale or unknown callback)"
                                )
                            }
                        }
                    }
                }

                is PaymentResult.Error -> {
                    error = result.message
                    isLoading = false
                    showFullScreenSetupLoader = false
                    showTrialSuccessScreen = false
                    bannerIsSuccess = false
                    bannerMessage = result.message
                    currentCheckoutKind = CheckoutKind.NONE
                    entryFeeSuccessHandled = false
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
                    loading = isLoading && !showFullScreenSetupLoader
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

        SubscriptionSetupFullScreenOverlay(visible = showFullScreenSetupLoader)

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
