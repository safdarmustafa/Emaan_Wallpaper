package com.squarenova.emaanwallpapers.ui.splash

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.squarenova.emaanwallpapers.BuildConfig
import com.squarenova.emaanwallpapers.R
import com.squarenova.emaanwallpapers.analytics.AnalyticsEvents
import com.squarenova.emaanwallpapers.analytics.AnalyticsManager
import com.squarenova.emaanwallpapers.analytics.MetaMatchDataRepository
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.data.EntitlementDebugLog
import com.squarenova.emaanwallpapers.data.LocalSession
import com.squarenova.emaanwallpapers.data.UserAccountLookup
import com.squarenova.emaanwallpapers.data.UserAccountQueries
import com.squarenova.emaanwallpapers.data.UserSubscriptionSyncManager
import com.squarenova.emaanwallpapers.subscription.EntitlementRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable

/** Matches splash art emerald so letterbox bars (if any) blend in. */
private val SplashCanvas = Color(0xFF064E3B)

/** Short floor so art is visible; work runs in parallel — no artificial 2–3s wait. */
private const val MIN_SPLASH_MS = 450L

@Serializable
private data class SubscriptionUserRow(
    val phone_number: String? = null,
)

private sealed interface SplashRoute {
    data object Login : SplashRoute
    data object ProfileSetup : SplashRoute
    data object Home : SplashRoute
    data object Subscription : SplashRoute
}

@Composable
fun SplashScreen(navController: NavController) {
    val context = LocalContext.current
    val dataStoreManager = DataStoreManager(context)

    LaunchedEffect(Unit) {
        val route = try {
            coroutineScope {
                val work = async { resolveStartupRoute(context, dataStoreManager) }
                // Show art briefly while routing work runs — do not block on long animations.
                delay(MIN_SPLASH_MS)
                work.await()
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
            if (fallbackLoggedIn) SplashRoute.Subscription else SplashRoute.Login
        }

        val destination = when (route) {
            SplashRoute.Login -> "login"
            SplashRoute.ProfileSetup -> "profile_setup"
            SplashRoute.Home -> "home"
            SplashRoute.Subscription -> "subscription"
        }
        navController.navigate(destination) {
            popUpTo("splash") { inclusive = true }
        }
    }

    // Full artwork visible — Fit never crops edges; emerald fills any side gaps.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashCanvas),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.splash_brand),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private suspend fun resolveStartupRoute(
    context: android.content.Context,
    dataStoreManager: DataStoreManager,
): SplashRoute {
    val loggedIn = dataStoreManager.isLoggedIn.first()
    val profileCompleted = dataStoreManager.isProfileCompleted.first()

    when {
        !loggedIn -> return SplashRoute.Login
        loggedIn && !profileCompleted -> return SplashRoute.ProfileSetup
    }

    val phone = dataStoreManager.phoneNumber.firstOrNull()
    if (phone.isNullOrBlank()) return SplashRoute.Login

    AnalyticsManager.identify(phone)
    // Refresh Meta match keys for signed-in users (CAPI Purchase while app is closed).
    MetaMatchDataRepository.saveAsync(context, phone)

    val cachedSubscribed = try {
        dataStoreManager.isSubscribed.first()
    } catch (_: Exception) {
        false
    }

    return when (UserAccountQueries.lookupByPhone<SubscriptionUserRow>(phone)) {
        UserAccountLookup.Missing -> {
            LocalSession.clear(context)
            SplashRoute.Login
        }
        UserAccountLookup.Unreachable,
        is UserAccountLookup.Found -> {
            routeLoggedInUser(
                phone = phone,
                cachedSubscribed = cachedSubscribed,
                dataStoreManager = dataStoreManager,
            )
        }
    }
}

private suspend fun routeLoggedInUser(
    phone: String,
    cachedSubscribed: Boolean,
    dataStoreManager: DataStoreManager,
): SplashRoute {
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
        return SplashRoute.Home
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
    return SplashRoute.Subscription
}
