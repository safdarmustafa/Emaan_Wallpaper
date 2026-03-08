package com.squarenova.emaanwallpapers.analytics

import android.content.Context
import com.mixpanel.android.mpmetrics.MixpanelAPI
import org.json.JSONObject

/**
 * Centralized Mixpanel analytics wrapper.
 * Tracks screen views and button/action events across the app.
 * Uses Application context and fetches MixpanelAPI per call to avoid static reference leak.
 */
object AnalyticsManager {

    private var appContext: Context? = null
    private var token: String? = null

    /** Call from Application.onCreate. Token from BuildConfig.MIXPANEL_TOKEN */
    fun init(context: Context, mixpanelToken: String) {
        if (appContext != null) return
        if (mixpanelToken.isBlank() || mixpanelToken == "YOUR_MIXPANEL_PROJECT_TOKEN") return
        appContext = context.applicationContext
        token = mixpanelToken
    }

    private fun getMixpanel(): MixpanelAPI? {
        val ctx = appContext ?: return null
        val t = token ?: return null
        return MixpanelAPI.getInstance(ctx, t, false)
    }

    private fun track(eventName: String, properties: Map<String, Any> = emptyMap()) {
        val props = JSONObject().apply {
            properties.forEach { (key, value) ->
                put(key, value)
            }
        }
        getMixpanel()?.track(eventName, props)
    }

    /** Track screen view — call when user enters a screen */
    fun trackScreen(screenName: String) {
        track("Screen Viewed", mapOf("screen_name" to screenName))
    }

    /** Track a button or action event with optional properties */
    fun trackEvent(eventName: String, properties: Map<String, Any> = emptyMap()) {
        track(eventName, properties)
    }

    /** Identify user for Mixpanel People (optional, for user-level analytics) */
    fun identify(phone: String) {
        getMixpanel()?.identify(phone)
    }

    /** Reset on logout */
    fun reset() {
        getMixpanel()?.reset()
    }

    /** Force send queued events immediately (Mixpanel batches every 60s by default) */
    fun flush() {
        getMixpanel()?.flush()
    }
}
