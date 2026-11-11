package com.squarenova.emaanwallpapers.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyAnalyticsTest {

    @Test
    fun authMapsToStartTrialWithThreeRupees() {
        val event = MoneyAnalytics.forPaymentKind("AUTH")!!
        assertEquals(AnalyticsEvents.START_TRIAL, event.eventName)
        assertEquals(3.0, event.value, 0.0)
        assertEquals("INR", event.currency)
    }

    @Test
    fun chargeMapsToPurchaseWithFourHundredNinetyNine() {
        val event = MoneyAnalytics.forPaymentKind("CHARGE")!!
        assertEquals(AnalyticsEvents.PURCHASE, event.eventName)
        assertEquals(499.0, event.value, 0.0)
        assertEquals("INR", event.currency)
    }

    @Test
    fun kindIsCaseInsensitive() {
        assertEquals(AnalyticsEvents.START_TRIAL, MoneyAnalytics.forPaymentKind("auth")!!.eventName)
        assertEquals(AnalyticsEvents.PURCHASE, MoneyAnalytics.forPaymentKind("charge")!!.eventName)
    }

    @Test
    fun unknownKindIsIgnored() {
        assertNull(MoneyAnalytics.forPaymentKind("REFUND"))
        assertNull(MoneyAnalytics.forPaymentKind(""))
    }
}
