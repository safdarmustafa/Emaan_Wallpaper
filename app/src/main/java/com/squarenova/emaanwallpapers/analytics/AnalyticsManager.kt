package com.squarenova.emaanwallpapers.analytics

import android.content.Context
import com.squarenova.emaanwallpapers.analytics.provider.MixpanelProvider
import java.util.concurrent.ConcurrentHashMap
import com.squarenova.emaanwallpapers.analytics.providers.MetaAnalyticsProvider

object AnalyticsManager {

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
            providers.add(
                MetaAnalyticsProvider(context)
            )
        }
    }

    /**
     * Track screen views
     */
    fun trackScreen(screenName: String) {

        track(
            eventName = "screen_viewed",
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

        providers.forEach {

            it.track(
                event = eventName,
                props = props
            )
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
     * Identify logged in user
     */
    fun identify(userId: String) {

        if (userId.isBlank()) return

        providers.forEach {

            it.identify(userId)
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

        providers.forEach {

            it.flush()
        }
    }
}