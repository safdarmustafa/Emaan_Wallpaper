package com.squarenova.emaanwallpapers.ui.splash

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.squarenova.emaanwallpapers.BuildConfig
import com.squarenova.emaanwallpapers.R
import com.squarenova.emaanwallpapers.analytics.AnalyticsEvents
import com.squarenova.emaanwallpapers.analytics.AnalyticsManager
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.data.EntitlementDebugLog
import com.squarenova.emaanwallpapers.data.LocalSession
import com.squarenova.emaanwallpapers.data.UserAccountLookup
import com.squarenova.emaanwallpapers.data.UserAccountQueries
import com.squarenova.emaanwallpapers.data.UserSubscriptionSyncManager
import com.squarenova.emaanwallpapers.subscription.EntitlementRepository
import com.squarenova.emaanwallpapers.subscription.SubscriptionOrchestrator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable

@Serializable
private data class SubscriptionUserRow(
    val phone_number: String? = null,
)

@Composable
fun SplashScreen(navController: NavController) {
    val context = LocalContext.current
    val dataStoreManager = DataStoreManager(context)
    val alphaAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        try {
            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(1200),
            )
            delay(1200)

            SubscriptionOrchestrator.recover()

            val loggedIn = dataStoreManager.isLoggedIn.first()
            val profileCompleted = dataStoreManager.isProfileCompleted.first()

            when {
                !loggedIn -> {
                    navController.navigate("login") {
                        popUpTo("splash") { inclusive = true }
                    }
                    return@LaunchedEffect
                }

                loggedIn && !profileCompleted -> {
                    navController.navigate("profile_setup") {
                        popUpTo("splash") { inclusive = true }
                    }
                    return@LaunchedEffect
                }
            }

            val phone = dataStoreManager.phoneNumber.firstOrNull()
            if (phone.isNullOrBlank()) {
                navController.navigate("login") {
                    popUpTo("splash") { inclusive = true }
                }
                return@LaunchedEffect
            }

            AnalyticsManager.identify(phone)

            val cachedSubscribed = try {
                dataStoreManager.isSubscribed.first()
            } catch (_: Exception) {
                false
            }

            when (UserAccountQueries.lookupByPhone<SubscriptionUserRow>(phone)) {
                UserAccountLookup.Missing -> {
                    LocalSession.clear(context)
                    navController.navigate("login") {
                        popUpTo("splash") { inclusive = true }
                    }
                    return@LaunchedEffect
                }

                UserAccountLookup.Unreachable,
                is UserAccountLookup.Found -> {
                    routeLoggedInUser(
                        navController = navController,
                        phone = phone,
                        cachedSubscribed = cachedSubscribed,
                        dataStoreManager = dataStoreManager,
                    )
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e("SplashScreen", "startup routing failed", e)
            }
            val fallbackLoggedIn = try {
                dataStoreManager.isLoggedIn.first() &&
                    dataStoreManager.isProfileCompleted.first()
            } catch (_: Exception) {
                false
            }
            if (fallbackLoggedIn) {
                navController.navigate("subscription") {
                    popUpTo("splash") { inclusive = true }
                }
            } else {
                navController.navigate("login") {
                    popUpTo("splash") { inclusive = true }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.mosque),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF064E3B).copy(alpha = 0.6f)),
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer(alpha = alphaAnim.value),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Emaan Wallpapers",
                fontSize = 28.sp,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Where Faith Meets Beauty",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

private suspend fun routeLoggedInUser(
    navController: NavController,
    phone: String,
    cachedSubscribed: Boolean,
    dataStoreManager: DataStoreManager,
) {
    // Cashfree subscription-status (via EntitlementRepository) is the only premium source;
    // offline it falls back to the cached value.
    val hasPremium = UserSubscriptionSyncManager(dataStoreManager).syncUserSubscription(phone)
    val status = EntitlementRepository.lastStatus
    if (BuildConfig.DEBUG) {
        Log.d(
            "SplashScreen",
            "App-start subscription sync: subscribed=$hasPremium cached=$cachedSubscribed status=$status",
        )
    }

    if (hasPremium) {
        EntitlementDebugLog.navigation(
            source = "SplashScreen",
            destination = "home",
            subscriptionStatus = status,
            trialEnd = EntitlementRepository.subscription.value?.endDate,
            hasPremiumAccess = true,
        )
        navController.navigate("home") {
            popUpTo("splash") { inclusive = true }
        }
        return
    }

    if (status?.lowercase() == "expired") {
        AnalyticsManager.trackOnce(
            key = "trial_expired",
            eventName = AnalyticsEvents.TRIAL_EXPIRED,
        )
    }
    EntitlementDebugLog.navigation(
        source = "SplashScreen:default",
        destination = "subscription",
        subscriptionStatus = status,
        trialEnd = EntitlementRepository.subscription.value?.endDate,
        hasPremiumAccess = false,
    )
    navController.navigate("subscription") {
        popUpTo("splash") { inclusive = true }
    }
}
