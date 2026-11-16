package com.squarenova.emaanwallpapers.subscription.cashfree

/**
 * Pure helpers for inline UPI selection on the paywall.
 * Keeps defaulting logic testable without Android UI.
 */

/** Preferred UPI packages when the user has no last-used choice (India). */
internal val PREFERRED_UPI_PACKAGES = listOf(
    "com.google.android.apps.nbu.paisa.user", // Google Pay
    "com.phonepe.app", // PhonePe
    "net.one97.paytm", // Paytm
    "in.org.npci.upiapp", // BHIM
)

/**
 * Picks the package to pre-select.
 * Order: lastUsed (if still installed) → preferred list → first installed → null.
 */
fun preferDefaultUpiPackage(
    installedPackageNames: List<String>,
    lastUsedPackageName: String?,
): String? {
    val installed = installedPackageNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (installed.isEmpty()) return null

    val lastUsed = lastUsedPackageName?.trim().orEmpty()
    if (lastUsed.isNotEmpty() && installed.any { it.equals(lastUsed, ignoreCase = true) }) {
        return installed.first { it.equals(lastUsed, ignoreCase = true) }
    }

    for (preferred in PREFERRED_UPI_PACKAGES) {
        val match = installed.firstOrNull { it.equals(preferred, ignoreCase = true) }
        if (match != null) return match
    }

    return installed.first()
}

/**
 * CTA may start trial only when a UPI app is selected and nothing else is in-flight.
 */
fun canStartTrialPayment(
    selectedPackageName: String?,
    appsLoading: Boolean,
    installedCount: Int,
    isLoading: Boolean,
    confirming: Boolean,
): Boolean {
    if (appsLoading || isLoading || confirming) return false
    if (installedCount <= 0) return false
    return !selectedPackageName.isNullOrBlank()
}
