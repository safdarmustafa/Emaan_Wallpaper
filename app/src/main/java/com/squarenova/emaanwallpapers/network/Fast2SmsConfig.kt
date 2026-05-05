package com.squarenova.emaanwallpapers.network

import com.squarenova.emaanwallpapers.BuildConfig

object Fast2SmsConfig {
    /** Set FAST2SMS_API_KEY in local.properties — never commit real keys. */
    val API_KEY: String get() = BuildConfig.FAST2SMS_API_KEY.trim()

    // ✅ DLT Registration Details (required by TRAI for India)
    const val DLT_SENDER_ID = "SPCTEK"
    const val DLT_PE_ID     = "1201176779722977287"
    const val DLT_TE_ID     = "1207176874999607244"
}