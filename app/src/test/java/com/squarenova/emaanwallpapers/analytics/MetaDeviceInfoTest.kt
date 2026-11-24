package com.squarenova.emaanwallpapers.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetaDeviceInfoTest {

    @Test
    fun extinfoOrderAndLength() {
        val ext = MetaDeviceInfo.buildExtinfo(
            packageName = "com.squarenova.emaanwallpapers",
            versionCode = "17",
            versionName = "1.2.3",
            osVersion = "14",
            model = "Pixel",
            localeTag = "en_IN",
            timezoneShort = "IST",
            carrier = "Jio",
            screenWidthPx = "1080",
            screenHeightPx = "2400",
            density = "2.75",
            cpuCores = "8",
            totalStorageGb = "128",
            freeStorageGb = "64",
            timezoneId = "Asia/Kolkata",
        )
        assertEquals(16, ext.size)
        assertEquals("a2", ext[0])
        assertEquals("com.squarenova.emaanwallpapers", ext[1])
        assertEquals("17", ext[2])
        assertEquals("1.2.3", ext[3])
        assertEquals("14", ext[4])
        assertEquals("Pixel", ext[5])
        assertEquals("en_IN", ext[6])
        assertEquals("IST", ext[7])
        assertEquals("Jio", ext[8])
        assertEquals("1080", ext[9])
        assertEquals("2400", ext[10])
        assertEquals("2.75", ext[11])
        assertEquals("8", ext[12])
        assertEquals("128", ext[13])
        assertEquals("64", ext[14])
        assertEquals("Asia/Kolkata", ext[15])
    }

    @Test
    fun blankOsFallsBackToUnknown() {
        val blank = MetaDeviceInfo.buildExtinfo(
            packageName = "pkg",
            versionCode = "1",
            versionName = "1.0",
            osVersion = "  ",
            model = "m",
            localeTag = "en_US",
            timezoneShort = "UTC",
            carrier = "",
            screenWidthPx = "1",
            screenHeightPx = "1",
            density = "1.00",
            cpuCores = "1",
            totalStorageGb = "1",
            freeStorageGb = "1",
            timezoneId = "UTC",
        )
        assertEquals("unknown", blank[4])

        val nullOs = MetaDeviceInfo.buildExtinfo(
            packageName = "pkg",
            versionCode = "1",
            versionName = "1.0",
            osVersion = null,
            model = "m",
            localeTag = "en_US",
            timezoneShort = "UTC",
            carrier = "",
            screenWidthPx = "1",
            screenHeightPx = "1",
            density = "1.00",
            cpuCores = "1",
            totalStorageGb = "1",
            freeStorageGb = "1",
            timezoneId = "UTC",
        )
        assertEquals("unknown", nullOs[4])
    }

    @Test
    fun usableAdvertisingIdRules() {
        val good = "A1B2C3D4-E5F6-7890-ABCD-EF1234567890"
        assertEquals(
            "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
            MetaDeviceInfo.usableAdvertisingId(good, limitAdTrackingEnabled = false),
        )
        assertNull(
            MetaDeviceInfo.usableAdvertisingId(good, limitAdTrackingEnabled = true),
        )
        assertNull(
            MetaDeviceInfo.usableAdvertisingId(
                good,
                limitAdTrackingEnabled = false,
                callFailed = true,
            ),
        )
        assertNull(
            MetaDeviceInfo.usableAdvertisingId(
                "00000000-0000-0000-0000-000000000000",
                limitAdTrackingEnabled = false,
            ),
        )
        assertNull(
            MetaDeviceInfo.usableAdvertisingId("", limitAdTrackingEnabled = false),
        )
        assertNull(
            MetaDeviceInfo.usableAdvertisingId("not-a-uuid", limitAdTrackingEnabled = false),
        )
        assertNull(
            MetaDeviceInfo.usableAdvertisingId(null, limitAdTrackingEnabled = false),
        )
    }
}
