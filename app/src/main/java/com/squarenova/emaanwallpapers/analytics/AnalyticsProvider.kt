package com.squarenova.emaanwallpapers.analytics

interface AnalyticsProvider {

    fun track(
        event: String,
        props: Map<String, Any?> = emptyMap()
    )

    fun identify(userId: String)

    fun reset()

    fun flush()
}