package com.squarenova.emaanwallpapers.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import com.squarenova.emaanwallpapers.R
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.network.SubscriptionApi
import com.squarenova.emaanwallpapers.network.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable

@Serializable
private data class SubscriptionUserRow(
    val phone_number: String? = null,
    val is_subscribed: Boolean? = false,
    val razorpay_subscription_id: String? = null
)

@Composable
fun SplashScreen(navController: NavController) {

    val context = LocalContext.current
    val dataStoreManager = DataStoreManager(context)

    val alphaAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {

        alphaAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(1200)
        )

        delay(1200)

        val loggedIn = dataStoreManager.isLoggedIn.first()
        val profileCompleted = dataStoreManager.isProfileCompleted.first()

        when {
            !loggedIn -> {
                navController.navigate("login") {
                    popUpTo("splash") { inclusive = true }
                }
            }

            loggedIn && !profileCompleted -> {
                navController.navigate("profile_setup") {
                    popUpTo("splash") { inclusive = true }
                }
            }

            else -> {
                val phone = dataStoreManager.phoneNumber.firstOrNull()

                if (phone.isNullOrBlank()) {
                    navController.navigate("login") {
                        popUpTo("splash") { inclusive = true }
                    }
                    return@LaunchedEffect
                }

                val row = try {
                    SupabaseClient.client
                        .postgrest["users"]
                        .select { filter { eq("phone_number", phone) } }
                        .decodeList<SubscriptionUserRow>()
                        .firstOrNull()
                } catch (e: Exception) {
                    Log.e("SplashScreen", "users fetch", e)
                    null
                }

                when {
                    row?.is_subscribed == true -> {
                        navController.navigate("home") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }

                    !row?.razorpay_subscription_id.isNullOrBlank() -> {
                        Log.i(
                            "SPLASH_TRIAL_RECOVERY",
                            "razorpay_subscription_id set but is_subscribed false — activateTrialWithRetries"
                        )
                        SubscriptionApi.activateTrialWithRetries(phone).fold(
                            onSuccess = {
                                val nowSubscribed = try {
                                    SupabaseClient.client
                                        .postgrest["users"]
                                        .select { filter { eq("phone_number", phone) } }
                                        .decodeList<SubscriptionUserRow>()
                                        .firstOrNull()
                                        ?.is_subscribed == true
                                } catch (e: Exception) {
                                    Log.e("SPLASH_TRIAL_RECOVERY", "refetch", e)
                                    false
                                }
                                if (nowSubscribed) {
                                    navController.navigate("home") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                } else {
                                    Log.w(
                                        "SPLASH_TRIAL_RECOVERY",
                                        "Still not subscribed after recovery — subscription screen"
                                    )
                                    navController.navigate("subscription") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            },
                            onFailure = { e ->
                                Log.w(
                                    "SPLASH_TRIAL_RECOVERY",
                                    "activateTrialWithRetries failed: ${e.message}"
                                )
                                navController.navigate("subscription") {
                                    popUpTo("splash") { inclusive = true }
                                }
                            }
                        )
                    }

                    else -> {
                        navController.navigate("subscription") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Image(
            painter = painterResource(id = R.drawable.mosque),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF064E3B).copy(alpha = 0.6f))
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer(alpha = alphaAnim.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "Emaan Wallpapers",
                fontSize = 28.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Where Faith Meets Beauty",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}