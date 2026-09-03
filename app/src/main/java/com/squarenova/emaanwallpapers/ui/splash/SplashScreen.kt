package com.squarenova.emaanwallpapers.ui.splash

import android.content.Context
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
import com.squarenova.emaanwallpapers.data.MandateDebugLog
import com.squarenova.emaanwallpapers.data.SubscriptionEntitlement
import com.squarenova.emaanwallpapers.data.UserAccountLookup
import com.squarenova.emaanwallpapers.data.UserAccountQueries
import com.squarenova.emaanwallpapers.data.UserSubscriptionSyncManager
import com.squarenova.emaanwallpapers.network.SubscriptionApi
import com.squarenova.emaanwallpapers.subscription.SubscriptionOrchestrator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable

private val MANDATE_PENDING_STATUSES = setOf("created", "authenticated", "pending")

@Serializable
private data class SubscriptionUserRow(
    val phone_number: String? = null,
    val is_subscribed: Boolean? = false,
    val razorpay_subscription_id: String? = null,
    val subscription_status: String? = null,
    val trial_end: String? = null,
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

            when (val account = UserAccountQueries.lookupByPhone<SubscriptionUserRow>(phone)) {
                UserAccountLookup.Missing -> {
                    LocalSession.clear(context)
                    navController.navigate("login") {
                        popUpTo("splash") { inclusive = true }
                    }
                    return@LaunchedEffect
                }

                UserAccountLookup.Unreachable -> {
                    routeLoggedInUser(
                        navController = navController,
                        context = context,
                        phone = phone,
                        row = null,
                        cachedSubscribed = cachedSubscribed,
                        dataStoreManager = dataStoreManager,
                    )
                }

                is UserAccountLookup.Found -> {
                    routeLoggedInUser(
                        navController = navController,
                        context = context,
                        phone = phone,
                        row = account.row,
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
    context: Context,
    phone: String,
    row: SubscriptionUserRow?,
    cachedSubscribed: Boolean,
    dataStoreManager: DataStoreManager,
) {
    val syncedSubscribed = UserSubscriptionSyncManager(dataStoreManager).syncUserSubscription(phone)
    if (BuildConfig.DEBUG) {
        Log.d(
            "SplashScreen",
            "App-start subscription sync: subscribed=$syncedSubscribed cached=$cachedSubscribed",
        )
    }

    val hasPremium = SubscriptionEntitlement.hasPremiumAccess(
        subscriptionStatus = row?.subscription_status,
        trialEndIso = row?.trial_end,
    ) || syncedSubscribed

    MandateDebugLog.entitlementCheck(
        source = "SplashScreen:routing",
        subscriptionStatus = row?.subscription_status,
        trialPaid = null,
        trialEnd = row?.trial_end,
        hasPremiumAccess = hasPremium,
    )
    MandateDebugLog.note("SplashScreen syncedSubscribed=$syncedSubscribed")

    when {
        hasPremium -> {
            EntitlementDebugLog.navigation(
                source = "SplashScreen",
                destination = "home",
                subscriptionStatus = row?.subscription_status,
                trialEnd = row?.trial_end,
                hasPremiumAccess = true,
            )
            navController.navigate("home") {
                popUpTo("splash") { inclusive = true }
            }
        }

        !row?.razorpay_subscription_id.isNullOrBlank() &&
            row?.subscription_status?.trim()?.lowercase() in MANDATE_PENDING_STATUSES -> {
            val refreshOk = SubscriptionApi.refreshSubscriptionStatus(phone).isSuccess
            val refreshed = if (refreshOk) {
                when (val again = UserAccountQueries.lookupByPhone<SubscriptionUserRow>(phone)) {
                    UserAccountLookup.Missing -> null
                    UserAccountLookup.Unreachable -> row
                    is UserAccountLookup.Found -> again.row
                }
            } else {
                row
            }
            if (SubscriptionEntitlement.hasPremiumAccess(
                    refreshed?.subscription_status,
                    refreshed?.trial_end,
                )
            ) {
                EntitlementDebugLog.navigation(
                    source = "SplashScreen:mandate_refresh",
                    destination = "home",
                    subscriptionStatus = refreshed?.subscription_status,
                    trialEnd = refreshed?.trial_end,
                    hasPremiumAccess = true,
                )
                navController.navigate("home") {
                    popUpTo("splash") { inclusive = true }
                }
            } else if (refreshOk && refreshed == null) {
                LocalSession.clear(context)
                navController.navigate("login") {
                    popUpTo("splash") { inclusive = true }
                }
            } else {
                val reason = when (refreshed?.subscription_status?.lowercase()) {
                    "expired" -> "expired"
                    else -> "not_subscribed"
                }
                if (reason == "expired") {
                    AnalyticsManager.trackOnce(
                        key = "trial_expired",
                        eventName = AnalyticsEvents.TRIAL_EXPIRED,
                    )
                }
                EntitlementDebugLog.navigation(
                    source = "SplashScreen:mandate_refresh",
                    destination = "subscription",
                    subscriptionStatus = refreshed?.subscription_status,
                    trialEnd = refreshed?.trial_end,
                    hasPremiumAccess = false,
                )
                navController.navigate("subscription") {
                    popUpTo("splash") { inclusive = true }
                }
            }
        }

        else -> {
            EntitlementDebugLog.navigation(
                source = "SplashScreen:default",
                destination = "subscription",
                subscriptionStatus = row?.subscription_status,
                trialEnd = row?.trial_end,
                hasPremiumAccess = false,
            )
            navController.navigate("subscription") {
                popUpTo("splash") { inclusive = true }
            }
        }
    }
}
