package com.squarenova.emaanwallpapers.data

import android.content.Context
import androidx.navigation.NavController
import com.squarenova.emaanwallpapers.analytics.AnalyticsManager
import com.squarenova.emaanwallpapers.subscription.SubscriptionOrchestrator

/**
 * Clears on-device login when the server account no longer exists.
 * Does not touch Razorpay or remote billing.
 */
object LocalSession {

    suspend fun clear(context: Context) {
        try {
            AnalyticsManager.reset()
        } catch (_: Exception) {
            // Analytics must never block logout.
        }
        SubscriptionOrchestrator.onLogout()
        DataStoreManager(context).logout()
    }

    fun goToLogin(navController: NavController) {
        navController.navigate("login") {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }
}
