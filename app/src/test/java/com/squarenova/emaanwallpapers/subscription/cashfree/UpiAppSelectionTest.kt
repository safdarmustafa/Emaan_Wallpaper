package com.squarenova.emaanwallpapers.subscription.cashfree

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpiAppSelectionTest {

    private val gpay = "com.google.android.apps.nbu.paisa.user"
    private val phonepe = "com.phonepe.app"
    private val paytm = "net.one97.paytm"
    private val other = "com.example.upi"

    @Test
    fun emptyInstalledReturnsNull() {
        assertNull(preferDefaultUpiPackage(emptyList(), lastUsedPackageName = gpay))
        assertNull(preferDefaultUpiPackage(listOf("  ", ""), lastUsedPackageName = null))
    }

    @Test
    fun lastUsedWinsWhenStillInstalled() {
        assertEquals(
            paytm,
            preferDefaultUpiPackage(
                installedPackageNames = listOf(gpay, phonepe, paytm),
                lastUsedPackageName = paytm,
            ),
        )
    }

    @Test
    fun lastUsedIgnoredWhenUninstalled() {
        assertEquals(
            gpay,
            preferDefaultUpiPackage(
                installedPackageNames = listOf(gpay, phonepe),
                lastUsedPackageName = "com.missing.upi",
            ),
        )
    }

    @Test
    fun preferredOrderWhenNoLastUsed() {
        assertEquals(
            phonepe,
            preferDefaultUpiPackage(
                installedPackageNames = listOf(other, phonepe, paytm),
                lastUsedPackageName = null,
            ),
        )
        assertEquals(
            gpay,
            preferDefaultUpiPackage(
                installedPackageNames = listOf(paytm, gpay, phonepe),
                lastUsedPackageName = null,
            ),
        )
    }

    @Test
    fun fallsBackToFirstInstalled() {
        assertEquals(
            other,
            preferDefaultUpiPackage(
                installedPackageNames = listOf(other),
                lastUsedPackageName = null,
            ),
        )
    }

    @Test
    fun canStartTrialRequiresSelectionAndIdleState() {
        assertTrue(
            canStartTrialPayment(
                selectedPackageName = gpay,
                appsLoading = false,
                installedCount = 2,
                isLoading = false,
                confirming = false,
            ),
        )
        assertFalse(
            canStartTrialPayment(
                selectedPackageName = null,
                appsLoading = false,
                installedCount = 2,
                isLoading = false,
                confirming = false,
            ),
        )
        assertFalse(
            canStartTrialPayment(
                selectedPackageName = gpay,
                appsLoading = true,
                installedCount = 2,
                isLoading = false,
                confirming = false,
            ),
        )
        assertFalse(
            canStartTrialPayment(
                selectedPackageName = gpay,
                appsLoading = false,
                installedCount = 0,
                isLoading = false,
                confirming = false,
            ),
        )
        assertFalse(
            canStartTrialPayment(
                selectedPackageName = gpay,
                appsLoading = false,
                installedCount = 1,
                isLoading = true,
                confirming = false,
            ),
        )
        assertFalse(
            canStartTrialPayment(
                selectedPackageName = gpay,
                appsLoading = false,
                installedCount = 1,
                isLoading = false,
                confirming = true,
            ),
        )
    }
}
