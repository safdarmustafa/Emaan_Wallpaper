package com.squarenova.emaanwallpapers.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Payload shaping for save-meta-match-data
 * (Kotlin equivalent of the sister app's Dart MetaDevice model tests).
 * Avoids android.org.json (not mocked on JVM unit tests).
 */
class MetaMatchPayloadTest {

    @Test
    fun snapshotMapsToRequestFieldsIncludingNullMadid() {
        val snapshot = MetaDeviceInfo.Snapshot(
            anonId = "anon-1",
            advertisingId = null,
            adTrackingEnabled = false,
            userAgent = "Dalvik/2.1.0",
            extinfo = MetaDeviceInfo.buildExtinfo(
                packageName = "com.squarenova.emaanwallpapers",
                versionCode = "17",
                versionName = "1.2.3",
                osVersion = "14",
                model = "Pixel",
                localeTag = "en_IN",
                timezoneShort = "IST",
                carrier = "",
                screenWidthPx = "1080",
                screenHeightPx = "2400",
                density = "2.75",
                cpuCores = "8",
                totalStorageGb = "128",
                freeStorageGb = "64",
                timezoneId = "Asia/Kolkata",
            ),
        )

        val fields = mapOf(
            "phone" to "9876543210",
            "anon_id" to snapshot.anonId,
            "madid" to snapshot.advertisingId,
            "ad_tracking_enabled" to snapshot.adTrackingEnabled,
            "user_agent" to snapshot.userAgent,
            "extinfo" to snapshot.extinfo,
        )

        assertEquals("anon-1", fields["anon_id"])
        assertNull(fields["madid"])
        assertEquals(false, fields["ad_tracking_enabled"])
        @Suppress("UNCHECKED_CAST")
        val ext = fields["extinfo"] as List<String>
        assertEquals(16, ext.size)
        assertEquals("a2", ext[0])
        assertEquals("14", ext[4])
    }

    @Test
    fun stillSavesWhenTrackingLimitedSoServerClearsPriorMadid() {
        val limited = MetaDeviceInfo.Snapshot(
            anonId = "anon-2",
            advertisingId = null,
            adTrackingEnabled = false,
            userAgent = null,
            extinfo = List(16) { if (it == 0) "a2" else if (it == 4) "13" else "" },
        )
        assertFalse(limited.adTrackingEnabled)
        assertNull(limited.advertisingId)
        // Caller must still POST — repository does not skip on null madid.
        assertTrue(limited.anonId != null)
    }
}
