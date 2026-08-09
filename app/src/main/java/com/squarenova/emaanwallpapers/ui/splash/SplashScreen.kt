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
import com.squarenova.emaanwallpapers.BuildConfig
import com.squarenova.emaanwallpapers.analytics.AnalyticsEvents
import com.squarenova.emaanwallpapers.analytics.AnalyticsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import com.squarenova.emaanwallpapers.R
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.data.EntitlementDebugLog
import com.squarenova.emaanwallpapers.data.MandateDebugLog
import com.squarenova.emaanwallpapers.data.SubscriptionEntitlement
import com.squarenova.emaanwallpapers.data.UserSubscriptionSyncManager
import com.squarenova.emaanwallpapers.network.SubscriptionApi
import com.squarenova.emaanwallpapers.network.SupabaseClient
import com.squarenova.emaanwallpapers.subscription.SubscriptionOrchestrator
import io.github.jan.supabase.postgrest.postgrest
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
                animationSpec = tween(1200)
            )

            delay(1200)

            // Resume any interrupted post-payment confirmation before routing. If a paid mandate is
            // still confirming, the orchestrator updates the entitlement source of truth in the
            // background; routing below reflects the latest state and the subscription screen shows
            // the confirming overlay if premium hasn't landed yet.
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

                else -> {
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

                    val subscriptionSyncManager = UserSubscriptionSyncManager(dataStoreManager)
                    val syncedSubscribed = subscriptionSyncManager.syncUserSubscription(phone)
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "SplashScreen",
                            "App-start subscription sync: subscribed=$syncedSubscribed cached=$cachedSubscribed"
                        )
                    }

                    val row = try {
                        SupabaseClient.client
                            .postgrest["users"]
                            .select { filter { eq("phone_number", phone) } }
                            .decodeList<SubscriptionUserRow>()
                            .firstOrNull()
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) {
                            Log.e("SplashScreen", "users fetch failed", e)
                        }
                        null
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
                                try {
                                    SupabaseClient.client
                                        .postgrest["users"]
                                        .select { filter { eq("phone_number", phone) } }
                                        .decodeList<SubscriptionUserRow>()
                                        .firstOrNull()
                                } catch (_: Exception) {
                                    row
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
                            } else if (refreshed == null && syncedSubscribed) {
                                EntitlementDebugLog.navigation(
                                    source = "SplashScreen:sync_fallback",
                                    destination = "home",
                                    subscriptionStatus = null,
                                    trialEnd = null,
                                    hasPremiumAccess = true,
                                )
                                navController.navigate("home") {
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
                            if (syncedSubscribed && row == null) {
                                EntitlementDebugLog.navigation(
                                    source = "SplashScreen:offline_sync",
                                    destination = "home",
                                    subscriptionStatus = null,
                                    trialEnd = null,
                                    hasPremiumAccess = true,
                                )
                                navController.navigate("home") {
                                    popUpTo("splash") { inclusive = true }
                                }
                            } else {
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
