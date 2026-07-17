package com.squarenova.emaanwallpapers.analytics

import android.content.Context
import android.util.Log
import com.facebook.FacebookSdk
import com.squarenova.emaanwallpapers.BuildConfig
import com.squarenova.emaanwallpapers.analytics.provider.MixpanelProvider
import java.util.concurrent.ConcurrentHashMap
import com.squarenova.emaanwallpapers.analytics.providers.MetaAnalyticsProvider

object AnalyticsManager {

    private const val TAG = "AnalyticsManager"

    private val firedOnceKeys = ConcurrentHashMap.newKeySet<String>()

    private val providers = mutableListOf<AnalyticsProvider>()

    /**
     * Call from Application.onCreate
     */
    fun init(
        context: Context,
        mixpanelToken: String
    ) {

        if (providers.isNotEmpty()) return

        if (
            mixpanelToken.isNotBlank() &&
            mixpanelToken != "YOUR_MIXPANEL_PROJECT_TOKEN"
        ) {
            providers.add(
                MixpanelProvider(
                    context = context,
                    token = mixpanelToken
                )
            )
        }

        if (FacebookSdk.isInitialized()) {
            providers.add(MetaAnalyticsProvider(context))
        }

        if (providers.isEmpty() && BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "Analytics providers disabled — Mixpanel token missing/placeholder and/or " +
                    "Facebook SDK not initialized. track/identify/flush will no-op.",
            )
        }
    }

    /**
     * Track screen views
     */
    fun trackScreen(screenName: String) {

        track(
            eventName = AnalyticsEvents.SCREEN_VIEWED,
            props = mapOf(
                "screen_name" to screenName
            )
        )
    }

    /**
     * Generic event tracking
     */
    fun trackEvent(
        eventName: String,
        properties: Map<String, Any?> = emptyMap()
    ) {

        track(
            eventName = eventName,
            props = properties
        )
    }

    /**
     * Preferred tracking API
     */
    fun track(
        eventName: String,
        props: Map<String, Any?> = emptyMap()
    ) {
        try {
            providers.forEach {
                it.track(
                    event = eventName,
                    props = props
                )
            }
        } catch (_: Exception) {
            // Analytics must never affect app functionality.
        }
    }

    /**
     * Track only once per app process
     */
    fun trackOnce(
        key: String,
        eventName: String,
        props: Map<String, Any?> = emptyMap()
    ) {

        if (!firedOnceKeys.add(key)) return

        track(
            eventName = eventName,
            props = props
        )
    }

    /**
     * Standard purchase / revenue event. Future payment code should use this API only.
     */
    fun trackPurchase(
        amount: Double,
        currency: String,
        props: Map<String, Any?> = emptyMap(),
    ) {
        try {
            providers.forEach {
                it.purchase(
                    amount = amount,
                    currency = currency,
                    props = props,
                )
            }
        } catch (_: Exception) {
            // Analytics must never affect app functionality.
        }
    }

    /**
     * Purchase once per [key] within this process (same dedupe store as [trackOnce]).
     */
    fun trackPurchaseOnce(
        key: String,
        amount: Double,
        currency: String,
        props: Map<String, Any?> = emptyMap(),
    ) {
        if (!firedOnceKeys.add(key)) return
        trackPurchase(
            amount = amount,
            currency = currency,
            props = props,
        )
    }

    /**
     * Meta standard StartTrial once per [key]. Mixpanel no-ops via provider default — existing
     * [AnalyticsEvents.TRIAL_STARTED] via [trackOnce] is unchanged.
     */
    fun trackStartTrialOnce(
        key: String,
        props: Map<String, Any?> = emptyMap(),
    ) {
        if (!firedOnceKeys.add(key)) return
        try {
            providers.forEach {
                it.startTrial(props = props)
            }
        } catch (_: Exception) {
            // Analytics must never affect app functionality.
        }
    }

    /**
     * Identify logged in user
     */
    fun identify(userId: String) {

        if (userId.isBlank()) return

        try {
            providers.forEach {
                it.identify(userId)
            }
        } catch (_: Exception) {
            // Analytics must never affect app functionality.
        }
    }

    /**
     * Reset analytics state
     */
    fun reset() {

        firedOnceKeys.clear()

        providers.forEach {

            it.reset()
        }
    }

    /**
     * Flush pending events immediately
     */
    fun flush() {

        try {
            providers.forEach {
                it.flush()
            }
        } catch (_: Exception) {
            // Analytics must never affect app functionality.
        }
    }
}
