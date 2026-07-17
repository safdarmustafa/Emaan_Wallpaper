package com.squarenova.emaanwallpapers.analytics

interface AnalyticsProvider {

    fun track(
        event: String,
        props: Map<String, Any?> = emptyMap()
    )

    fun identify(userId: String)

    fun reset()

    fun flush()

    /**
     * Standard purchase / revenue event. Default no-op so providers can opt in without breaking
     * existing implementations.
     */
    fun purchase(
        amount: Double,
        currency: String,
        props: Map<String, Any?> = emptyMap(),
    ) {
        // Default no-op
    }
}