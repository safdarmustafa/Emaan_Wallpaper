package com.squarenova.emaanwallpapers.ui.subscription

import android.app.Activity
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.razorpay.Checkout
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.network.SubscriptionApi
import com.squarenova.emaanwallpapers.network.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.json.JSONObject

// ─────────────────────────────────────────
// Config
// ─────────────────────────────────────────
object RazorpayConfig {
    const val KEY_ID       = "rzp_live_Rt9t3l917uFzM3"
    const val PLAN_ID      = "plan_S5eLwJ9kl1tA4k"
    const val CURRENCY     = "INR"
    const val COMPANY_NAME = "Emaan Wallpapers"
    const val DESCRIPTION  = "Monthly Premium Subscription"
}

@Serializable
data class SubscriptionUpdateRow(
    val is_subscribed: Boolean = true,
    val razorpay_payment_id: String
)

// ─────────────────────────────────────────
// SubscriptionScreen
// ─────────────────────────────────────────
@Composable
fun SubscriptionScreen(navController: NavController) {

    val context    = LocalContext.current
    val activity   = context as Activity
    val dataStore  = DataStoreManager(context)
    val scope      = rememberCoroutineScope()

    var isLoading     by remember { mutableStateOf(false) }
    var errorMessage  by remember { mutableStateOf<String?>(null) }
    var successMsg    by remember { mutableStateOf<String?>(null) }
    var phone         by remember { mutableStateOf("") }

    // ✅ Load phone + preload Razorpay SDK
    LaunchedEffect(Unit) {
        phone = dataStore.phoneNumber.firstOrNull() ?: ""
        Checkout.preload(context)
    }

    // ✅ Listen to Razorpay payment result from MainActivity
    LaunchedEffect(Unit) {
        SubscriptionManager.paymentResult.collect { result ->
            when (result) {
                is PaymentResult.Success -> {
                    isLoading = true
                    successMsg = "Payment successful! Activating subscription..."

                    scope.launch {
                        try {
                            // ✅ Update Supabase
                            SupabaseClient.client
                                .postgrest["users"]
                                .update(
                                    SubscriptionUpdateRow(
                                        is_subscribed = true,
                                        razorpay_payment_id = result.paymentId
                                    )
                                ) {
                                    filter { eq("phone_number", phone) }
                                }

                            // ✅ Save locally in DataStore
                            dataStore.setSubscribed()

                            // ✅ Navigate to home — subscription done!
                            navController.navigate("home") {
                                popUpTo("subscription") { inclusive = true }
                            }

                        } catch (e: Exception) {
                            Log.e("SUBSCRIPTION_ERROR", e.message ?: "Unknown")
                            errorMessage = "Subscription activated but sync failed. Please restart."
                            isLoading = false
                        }
                    }
                }

                is PaymentResult.Error -> {
                    isLoading = false
                    errorMessage = when {
                        result.message.contains("cancel", ignoreCase = true) ->
                            "Payment cancelled. Subscribe to continue."
                        result.message.contains("network", ignoreCase = true) ->
                            "Network error. Check connection and try again."
                        else -> "Payment failed. Please try again."
                    }
                }
            }
        }
    }

    // Auto hide error
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            kotlinx.coroutines.delay(3500)
            errorMessage = null
        }
    }

    val gradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF064E3B), Color(0xFF0A5C46), Color(0xFF0D3B2E))
    )
    val goldColor = Color(0xFFD4AF37)
    val darkGreen = Color(0xFF064E3B)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = gradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(24.dp))

            // App icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(goldColor.copy(alpha = 0.15f))
                    .border(2.dp, goldColor.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("🕌", fontSize = 38.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "Emaan Wallpapers",
                color = goldColor,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                "Bring Noor to Your Screen",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            // ── Premium card ─────────────────────
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.08f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, goldColor.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    // Premium badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(goldColor)
                            .padding(horizontal = 16.dp, vertical = 5.dp)
                    ) {
                        Text(
                            "✨ PREMIUM",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Monthly Plan",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Price
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "₹99",
                            color = goldColor,
                            fontSize = 52.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "/month",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                        )
                    }

                    Text(
                        "Cancel anytime",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    Spacer(modifier = Modifier.height(20.dp))

                    // Features
                    val features = listOf(
                        "🖼️" to "Unlimited HD Wallpapers",
                        "🌀" to "Live Wallpapers (MP4)",
                        "🎬" to "Islamic Reels",
                        "⬇️" to "Download & Share",
                        "🌙" to "New content every week",
                        "📵" to "Ad-free experience"
                    )

                    features.forEach { (emoji, text) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(emoji, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text, color = Color.White, fontSize = 14.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("✓", color = goldColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Subscribe Button ─────────────────
            Button(
                onClick = {
                    if (phone.isEmpty()) {
                        errorMessage = "Session expired. Please login again."
                        return@Button
                    }

                    isLoading = true
                    errorMessage = null

                    scope.launch {
                        val result = SubscriptionApi.createSubscription(phone)
                        result.fold(
                            onSuccess = { subscriptionId ->
                                Log.d("RAZORPAY", "subscription_id=$subscriptionId")
                                try {
                                    val checkout = Checkout()
                                    checkout.setKeyID(RazorpayConfig.KEY_ID)

                                    val options = JSONObject().apply {
                                        put("name", RazorpayConfig.COMPANY_NAME)
                                        put("description", RazorpayConfig.DESCRIPTION)
                                        put("theme.color", "#064E3B")
                                        put("currency", RazorpayConfig.CURRENCY)
                                        put("prefill.contact", phone)
                                        put("subscription_id", subscriptionId)
                                        put("recurring", 1)
                                    }

                                    checkout.open(activity, options)
                                } catch (e: Exception) {
                                    Log.e("RAZORPAY_ERROR", e.message ?: "Unknown")
                                    errorMessage = "Could not open payment. Try again."
                                }
                            },
                            onFailure = { e ->
                                Log.e("SUBSCRIPTION_API", "createSubscription failed", e)
                                errorMessage = e.message ?: "Failed to start subscription. Try again."
                            }
                        )
                        isLoading = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = goldColor,
                    disabledContainerColor = goldColor.copy(alpha = 0.6f)
                ),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        successMsg ?: "Processing...",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                } else {
                    Text(
                        "Subscribe Now — ₹99/month",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("🔒 ", fontSize = 12.sp)
                Text(
                    "Secure payment powered by Razorpay",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "By subscribing you agree to our Terms & Privacy Policy",
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Error snackbar
        AnimatedVisibility(
            visible = errorMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Snackbar(
                containerColor = Color(0xFFB00020),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(errorMessage ?: "", color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}