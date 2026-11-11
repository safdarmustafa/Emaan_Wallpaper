package com.squarenova.emaanwallpapers.analytics

import android.content.Context

/**
 * App Open / App Install for Mixpanel. Survives logout (separate prefs from DataStore).
 * Meta install/open stays on Facebook SDK ActivateApp — do not duplicate there.
 */
object AnalyticsLifecycle {
    private const val PREFS = "emaan_analytics"
    private const val KEY_INSTALL_TRACKED = "app_install_tracked"

    fun trackLaunch(context: Context) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_INSTALL_TRACKED, false)) {
                AnalyticsManager.track(
                    eventName = AnalyticsEvents.APP_INSTALL,
                    props = mapOf("source" to "android"),
                )
                prefs.edit().putBoolean(KEY_INSTALL_TRACKED, true).apply()
            }
            AnalyticsManager.track(
                eventName = AnalyticsEvents.APP_OPEN,
                props = mapOf("source" to "android"),
            )
            AnalyticsManager.flush()
        } catch (_: Exception) {
            // Analytics must never affect app startup.
        }
    }
}
