package com.squarenova.emaanwallpapers.analytics.providers

import android.content.Context
import com.facebook.appevents.AppEventsLogger
import com.squarenova.emaanwallpapers.analytics.AnalyticsProvider
import android.os.Bundle


class MetaAnalyticsProvider(
    context: Context
) : AnalyticsProvider {

    private val logger = AppEventsLogger.newLogger(
        context.applicationContext
    )

    override fun track(
        event: String,
        props: Map<String, Any?>
    ) {

        val bundle = Bundle()

        props.forEach { (key, value) ->

            when (value) {

                is String -> bundle.putString(key, value)

                is Int -> bundle.putInt(key, value)

                is Double -> bundle.putDouble(key, value)

                is Float -> bundle.putFloat(key, value)

                is Boolean -> bundle.putBoolean(key, value)

                is Long -> bundle.putLong(key, value)
            }
        }

        logger.logEvent(
            event,
            bundle
        )
    }

    override fun identify(userId: String) {

        // Meta SDK does not support direct identify like Mixpanel
        // You can later use advanced matching if needed
    }

    override fun reset() {

        // No reset API needed for Meta currently
    }

    override fun flush() {

        logger.flush()
    }
}