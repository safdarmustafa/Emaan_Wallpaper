package com.squarenova.emaanwallpapers.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyAnalyticsTest {

    @Test
    fun authMapsToPurchaseWithThreeRupees() {
        val event = MoneyAnalytics.forPaymentKind("AUTH")!!
        assertEquals(AnalyticsEvents.PURCHASE, event.eventName)
        assertEquals(3.0, event.value, 0.0)
        assertEquals("INR", event.currency)
    }

    @Test
    fun chargeIsNotTracked() {
        assertNull(MoneyAnalytics.forPaymentKind("CHARGE"))
        assertNull(MoneyAnalytics.forPaymentKind("charge"))
    }

    @Test
    fun kindIsCaseInsensitive() {
        assertEquals(AnalyticsEvents.PURCHASE, MoneyAnalytics.forPaymentKind("auth")!!.eventName)
    }

    @Test
    fun unknownKindIsIgnored() {
        assertNull(MoneyAnalytics.forPaymentKind("REFUND"))
        assertNull(MoneyAnalytics.forPaymentKind(""))
    }
}
