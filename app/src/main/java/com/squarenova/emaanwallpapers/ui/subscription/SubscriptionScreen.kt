package com.squarenova.emaanwallpapers.ui.subscription

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.razorpay.Checkout
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.network.SubscriptionApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import android.util.Log
import org.json.JSONObject

object RazorpayConfig {
    const val KEY_ID = "rzp_live_SUkaGbslh0IvIZ"
}

/** Matches Login / bottom bar — deep green, teal, gold accents */
private val AppDarkGreen = Color(0xFF064E3B)
private val AppTeal = Color(0xFF0F766E)
private val AppDeepBar = Color(0xFF0A3528)
private val AppGold = Color(0xFFD4AF37)
private val PageBgTop = Color(0xFFE8F5F3)
private val PageBgBottom = Color(0xFFD1EDE8)
private val MutedText = Color(0xFF64748B)

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

    val headerGradient = remember {
        Brush.verticalGradient(
            colors = listOf(AppDeepBar, AppDarkGreen, AppTeal)
        )
    }
    val pageGradient = remember {
        Brush.verticalGradient(colors = listOf(PageBgTop, PageBgBottom))
    }

    LaunchedEffect(Unit) {
        phone = dataStoreManager.phoneNumber.firstOrNull() ?: ""
        Checkout.preload(context)
    }

    LaunchedEffect(bannerMessage) {
        if (bannerMessage != null) {
            delay(2500)
            bannerMessage = null
        }
    }

    LaunchedEffect(Unit) {
        SubscriptionManager.paymentResult.collect { result ->

            when (result) {

                is PaymentResult.Success -> {
                    scope.launch {
                        val phoneForApi =
                            dataStoreManager.phoneNumber.firstOrNull()?.trim().orEmpty()
                                .ifBlank { phone }
                        if (phoneForApi.isBlank()) {
                            isLoading = false
                            bannerIsSuccess = false
                            bannerMessage = "Session expired. Please login again."
                            Log.e("SUBSCRIPTION", "Payment OK but phone missing from DataStore")
                            return@launch
                        }
                        Log.d("SUBSCRIPTION", "Post-payment: createSubscription + activateTrial for $phoneForApi")
                        val created = SubscriptionApi.createSubscription(phoneForApi)
                        created.fold(
                            onSuccess = {
                                val activated = SubscriptionApi.activateTrial(phoneForApi)
                                activated.fold(
                                    onSuccess = {
                                        dataStoreManager.setSubscribed()
                                        isLoading = false
                                        navController.navigate("home") {
                                            popUpTo("subscription") { inclusive = true }
                                        }
                                    },
                                    onFailure = { e ->
                                        isLoading = false
                                        bannerIsSuccess = false
                                        bannerMessage = e.message ?: "Could not activate trial"
                                        Log.e("SUBSCRIPTION", "activateTrial failed", e)
                                    }
                                )
                            },
                            onFailure = { e ->
                                isLoading = false
                                bannerIsSuccess = false
                                bannerMessage = e.message ?: "Could not start subscription"
                                Log.e("SUBSCRIPTION", "createSubscription failed", e)
                            }
                        )
                    }
                }

                is PaymentResult.Error -> {
                    error = result.message
                    isLoading = false
                    bannerIsSuccess = false
                    bannerMessage = result.message
                }
            }
        }
    }

    fun navigateBackToLogin() {
        scope.launch {
            dataStoreManager.logout()
            navController.navigate("login") {
                popUpTo("subscription") { inclusive = true }
                launchSingleTop = true
            }
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

            val orderResult = SubscriptionApi.createOrder()
            orderResult.fold(
                onSuccess = { orderId ->
                    val checkout = Checkout()
                    checkout.setKeyID(RazorpayConfig.KEY_ID)

                    val options = JSONObject().apply {
                        put("name", "Emaan Wallpapers")
                        put("description", "₹5 Trial")
                        put("currency", "INR")
                        put("order_id", orderId)
                        put("prefill.contact", phone)
                    }

                    checkout.open(hostActivity, options)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            // Top bar: IconButton is not covered by centered title (fixes dead clicks)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush = headerGradient)
                    .statusBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { navigateBackToLogin() },
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to sign in",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Go Premium",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Faith-inspired wallpapers, unlocked",
                                color = Color.White.copy(alpha = 0.88f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        AppGold.copy(alpha = 0.85f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = Color.White,
                    tonalElevation = 2.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Premium",
                                    color = AppDarkGreen,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "3-day trial · then renews via Razorpay",
                                    color = MutedText,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(50.dp),
                                color = AppGold.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, AppGold.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = "POPULAR",
                                    color = AppDarkGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.1.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        HorizontalDivider(color = AppDarkGreen.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(16.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            PremiumBenefitRow(
                                icon = Icons.Filled.List,
                                title = "Exclusive premium wallpapers",
                                subtitle = "New designs added regularly"
                            )
                            PremiumBenefitRow(
                                icon = Icons.Filled.Star,
                                title = "Higher quality downloads",
                                subtitle = "Crisp visuals for your home screen"
                            )
                            PremiumBenefitRow(
                                icon = Icons.Filled.Favorite,
                                title = "Support the app",
                                subtitle = "Help us grow Emaan Wallpapers"
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = Color.White,
                    tonalElevation = 1.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    AppGold.copy(alpha = 0.5f),
                                    AppTeal.copy(alpha = 0.25f),
                                    AppGold.copy(alpha = 0.35f)
                                )
                            ),
                            shape = RoundedCornerShape(26.dp)
                        )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Start your trial",
                                    color = AppDarkGreen,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "One-time ₹5 to begin",
                                    color = MutedText,
                                    fontSize = 13.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "₹5",
                                    color = AppDarkGreen,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "INR",
                                    color = MutedText,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = { startCheckout() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            enabled = !isLoading,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppGold,
                                contentColor = AppDeepBar,
                                disabledContainerColor = AppGold.copy(alpha = 0.65f),
                                disabledContentColor = AppDeepBar.copy(alpha = 0.6f)
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 4.dp,
                                pressedElevation = 2.dp
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = AppDeepBar,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Processing…",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            } else {
                                Text(
                                    text = "Start ₹5 trial",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Cancel anytime from Profile. Access continues until the end of the paid period.",
                            color = MutedText,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 15.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                error?.let {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFFFFEBEE),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = it,
                            color = Color(0xFFB71C1C),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }

        bannerMessage?.let { msg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = if (bannerIsSuccess) AppDarkGreen else Color(0xFFB00020),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(msg, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun PremiumBenefitRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AppGold.copy(alpha = 0.22f))
                .border(1.dp, AppGold.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppDarkGreen,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = AppTeal,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    color = AppDarkGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = MutedText,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(start = 22.dp)
            )
        }
    }
}
