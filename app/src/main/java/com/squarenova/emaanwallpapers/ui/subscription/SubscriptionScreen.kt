package com.squarenova.emaanwallpapers.ui.subscription

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
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
import org.json.JSONObject

object RazorpayConfig {
    const val KEY_ID = "rzp_live_SUkaGbslh0IvIZ"
}

@Composable
fun SubscriptionScreen(navController: NavController) {

    val context = LocalContext.current
    val activity = context as Activity
    val dataStoreManager = DataStoreManager(context)
    val scope = rememberCoroutineScope()

    var phone by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var bannerIsSuccess by remember { mutableStateOf(true) }

    // ✅ Load phone + preload Razorpay
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

    // 🔥 LISTEN PAYMENT RESULT
    LaunchedEffect(Unit) {
        SubscriptionManager.paymentResult.collect { result ->

            when (result) {

                is PaymentResult.Success -> {

                    // 🔥 PAYMENT SUCCESS → GO HOME IMMEDIATELY
                    scope.launch {
                        // Persist "has access" immediately so redirects don't kick in.
                        dataStoreManager.setSubscribed()
                        // Create Razorpay subscription in background (no need to block navigation).
                        launch { SubscriptionApi.createSubscription(phone) }
                        navController.navigate("home") {
                            popUpTo("subscription") { inclusive = true }
                        }
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

    val darkGreen = Color(0xFF064E3B)
    val tealGreen = Color(0xFF0F766E)
    val gold = Color(0xFFD4AF37)
    val background = Color(0xFFE0F2F1)
    val gradient = Brush.verticalGradient(colors = listOf(darkGreen, tealGreen))

    fun startCheckout() {
        if (phone.isBlank()) {
            error = "Session expired. Please login again."
            bannerIsSuccess = false
            bannerMessage = error
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
                        // 🔥 PREFILL (smooth UX)
                        put("prefill.contact", phone)
                    }

                    checkout.open(activity, options)
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
            .background(background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush = gradient)
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        .clickable { navController.popBackStack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Go Premium",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Unlock the best of Emaan Wallpapers",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Premium card
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Premium Subscription",
                                    color = darkGreen,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "3‑day trial • then billed by Razorpay",
                                    color = Color(0xFF64748B),
                                    fontSize = 12.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(gold.copy(alpha = 0.18f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "PREMIUM",
                                    color = darkGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            PremiumBenefitRow(title = "Exclusive premium wallpapers", subtitle = "New designs added regularly")
                            PremiumBenefitRow(title = "Higher quality downloads", subtitle = "Crisp wallpapers for your device")
                            PremiumBenefitRow(title = "Premium experience", subtitle = "Support the app’s growth")
                        }
                    }
                }

                // Price + CTA
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Start trial",
                                    color = darkGreen,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Pay ₹5 to start",
                                    color = Color(0xFF64748B),
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = "₹5",
                                color = darkGreen,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { startCheckout() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            enabled = !isLoading,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = gold,
                                disabledContainerColor = gold.copy(alpha = 0.7f)
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Processing...",
                                    color = Color.Black,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    text = "Start ₹5 Trial",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "You can cancel anytime from Profile. Access remains till billing period ends.",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Error inline (kept for existing logic)
                error?.let {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = it,
                            color = Color(0xFFB71C1C),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }

        // Top banner (success/error)
        bannerMessage?.let { msg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = if (bannerIsSuccess) darkGreen else Color(0xFFB00020),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(msg, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun PremiumBenefitRow(
    title: String,
    subtitle: String
) {
    val darkGreen = Color(0xFF064E3B)
    val gold = Color(0xFFD4AF37)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(gold),
            contentAlignment = Alignment.Center
        ) {
            Text("✓", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = darkGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subtitle,
                color = Color(0xFF64748B),
                fontSize = 12.sp
            )
        }
    }
}