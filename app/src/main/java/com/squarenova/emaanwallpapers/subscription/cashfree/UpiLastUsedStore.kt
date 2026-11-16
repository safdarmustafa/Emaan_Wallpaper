package com.squarenova.emaanwallpapers.subscription.cashfree

import android.content.Context

/** Remembers the last UPI app the user successfully launched for trial auth. */
object UpiLastUsedStore {
    private const val PREFS = "upi_last_used"
    private const val KEY_PACKAGE = "package_name"

    fun read(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PACKAGE, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun write(context: Context, packageName: String) {
        val value = packageName.trim()
        if (value.isEmpty()) return
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PACKAGE, value)
            .apply()
    }
}
