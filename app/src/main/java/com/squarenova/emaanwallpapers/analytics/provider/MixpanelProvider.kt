package com.squarenova.emaanwallpapers.analytics.provider

import android.content.Context
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.squarenova.emaanwallpapers.BuildConfig
import com.squarenova.emaanwallpapers.analytics.AnalyticsEvents
import com.squarenova.emaanwallpapers.analytics.AnalyticsProvider
import org.json.JSONObject

class MixpanelProvider(
    context: Context,
    token: String
) : AnalyticsProvider {

    private val mixpanel: MixpanelAPI =
        MixpanelAPI.getInstance(
            context.applicationContext,
            token,
            false
        )

    private fun globalProperties(): Map<String, Any> = mapOf(
        "app_version" to BuildConfig.VERSION_NAME,
        "platform" to "android",
        "environment" to if (BuildConfig.DEBUG) "dev" else "prod"
    )

    override fun track(
        event: String,
        props: Map<String, Any?>
    ) {

        val merged = LinkedHashMap<String, Any?>()

        merged.putAll(globalProperties())
        merged.putAll(props)

        val json = JSONObject()

        merged.forEach { (key, value) ->

            when (value) {

                null -> Unit

                is String -> {
                    if (value.isNotBlank()) {
                        json.put(key, value)
                    }
                }

                else -> {
                    json.put(key, value)
                }
            }
        }

        mixpanel.track(event, json)
    }

    override fun purchase(
        amount: Double,
        currency: String,
        props: Map<String, Any?>,
    ) {
        if (amount <= 0.0 || currency.isBlank()) return

        val merged = LinkedHashMap<String, Any?>()
        merged.putAll(globalProperties())
        merged.put("amount", amount)
        merged.put("currency", currency.trim().uppercase())
        merged.putAll(props)

        val json = JSONObject()
        merged.forEach { (key, value) ->
            when (value) {
                null -> Unit
                is String -> {
                    if (value.isNotBlank()) {
                        json.put(key, value)
                    }
                }
                else -> json.put(key, value)
            }
        }

        mixpanel.track(AnalyticsEvents.PURCHASE, json)
    }

    override fun startTrial(props: Map<String, Any?>) {
        track(AnalyticsEvents.START_TRIAL, props)
    }

    override fun identify(userId: String) {

        if (userId.isBlank()) return

        mixpanel.identify(userId)

        val people = JSONObject().apply {

            put("\$phone", userId)

            put("platform", "android")

            put(
                "environment",
                if (BuildConfig.DEBUG) "dev" else "prod"
            )

            put(
                "app_version",
                BuildConfig.VERSION_NAME
            )
        }

        mixpanel.people.set(people)
    }

    override fun reset() {
        mixpanel.reset()
    }

    override fun flush() {
        mixpanel.flush()
    }
}