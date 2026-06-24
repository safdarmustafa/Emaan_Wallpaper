package com.squarenova.emaanwallpapers.util

import android.util.Log
import com.squarenova.emaanwallpapers.BuildConfig

/**
 * Production-safe logging. No PII or payment IDs in release builds.
 */
object SecureLog {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.i(tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }

    fun redactPhone(phone: String?): String {
        if (phone.isNullOrBlank()) return "null"
        return if (phone.length <= 4) "****" else "******${phone.takeLast(4)}"
    }

    fun redactId(id: String?): String {
        if (id.isNullOrBlank()) return "null"
        return if (id.length <= 6) "****" else "${id.take(4)}…${id.takeLast(4)}"
    }
}
