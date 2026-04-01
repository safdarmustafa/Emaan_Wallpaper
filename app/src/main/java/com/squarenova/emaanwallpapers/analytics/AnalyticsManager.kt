package com.squarenova.emaanwallpapers.analytics

import android.content.Context
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.squarenova.emaanwallpapers.BuildConfig
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized Mixpanel analytics wrapper.
 * Tracks screen views and button/action events across the app.
 * Uses Application context and fetches MixpanelAPI per call to avoid static reference leak.
 */
object AnalyticsManager {

    private var appContext: Context? = null
    private var token: String? = null
    private val firedOnceKeys = ConcurrentHashMap.newKeySet<String>()

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

    private fun globalProperties(): Map<String, Any> = mapOf(
        "app_version" to BuildConfig.VERSION_NAME,
        "platform" to "android",
        "environment" to if (BuildConfig.DEBUG) "dev" else "prod"
    )

    private fun trackInternal(eventName: String, properties: Map<String, Any?> = emptyMap()) {
        val merged = LinkedHashMap<String, Any?>()
        merged.putAll(globalProperties())
        merged.putAll(properties)
        val props = JSONObject().apply {
            merged.forEach { (key, value) ->
                when (value) {
                    null -> Unit
                    is String -> if (value.isNotBlank()) put(key, value)
                    else -> put(key, value)
                }
            }
        }
        getMixpanel()?.track(eventName, props)
    }

    /** Track screen view — call when user enters a screen */
    fun trackScreen(screenName: String) {
        trackInternal("Screen Viewed", mapOf("screen_name" to screenName))
    }

    /** Track a button or action event with optional properties */
    fun trackEvent(eventName: String, properties: Map<String, Any?> = emptyMap()) {
        trackInternal(eventName, properties)
    }

    /** Preferred API for new events */
    fun track(eventName: String, props: Map<String, Any?> = emptyMap()) {
        trackInternal(eventName, props)
    }

    /** Fires once per app process for provided key. */
    fun trackOnce(key: String, eventName: String, props: Map<String, Any?> = emptyMap()) {
        if (!firedOnceKeys.add(key)) return
        trackInternal(eventName, props)
    }

    /** Identify user for Mixpanel People (optional, for user-level analytics) */
    fun identify(phone: String) {
        if (phone.isBlank()) return
        getMixpanel()?.identify(phone)
        val people = JSONObject().apply {
            put("\$phone", phone)
            put("platform", "android")
            put("environment", if (BuildConfig.DEBUG) "dev" else "prod")
            put("app_version", BuildConfig.VERSION_NAME)
        }
        getMixpanel()?.people?.set(people)
    }

    /** Reset on logout */
    fun reset() {
        firedOnceKeys.clear()
        getMixpanel()?.reset()
    }

    /** Force send queued events immediately (Mixpanel batches every 60s by default) */
    fun flush() {
        getMixpanel()?.flush()
    }
}
