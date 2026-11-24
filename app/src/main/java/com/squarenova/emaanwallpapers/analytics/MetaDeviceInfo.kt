package com.squarenova.emaanwallpapers.analytics

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.WindowManager
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Collects Meta CAPI match keys from the device.
 * [collect] does blocking Advertising ID work — call off the main thread.
 * Never throws; returns null while the Facebook SDK is not initialised.
 */
object MetaDeviceInfo {

    data class Snapshot(
        val anonId: String?,
        val advertisingId: String?,
        val adTrackingEnabled: Boolean,
        val userAgent: String?,
        val extinfo: List<String>,
    )

    private val MADID_REGEX =
        Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    /**
     * Returns a usable advertising id, or null when tracking is limited,
     * the id is empty / all zeros, or the format is invalid.
     * A failed AdvertisingIdClient call counts as limited.
     */
    fun usableAdvertisingId(
        rawId: String?,
        limitAdTrackingEnabled: Boolean,
        callFailed: Boolean = false,
    ): String? {
        if (callFailed || limitAdTrackingEnabled) return null
        val id = rawId?.trim()?.lowercase(Locale.US).orEmpty()
        if (id.isEmpty()) return null
        if (!MADID_REGEX.matches(id)) return null
        if (id.replace("-", "").all { it == '0' }) return null
        return id
    }

    fun buildExtinfo(
        packageName: String,
        versionCode: String,
        versionName: String,
        osVersion: String?,
        model: String,
        localeTag: String,
        timezoneShort: String,
        carrier: String,
        screenWidthPx: String,
        screenHeightPx: String,
        density: String,
        cpuCores: String,
        totalStorageGb: String,
        freeStorageGb: String,
        timezoneId: String,
    ): List<String> {
        val os = osVersion?.trim().orEmpty().ifBlank { "unknown" }
        return listOf(
            "a2",
            packageName,
            versionCode,
            versionName,
            os,
            model,
            localeTag,
            timezoneShort,
            carrier,
            screenWidthPx,
            screenHeightPx,
            density,
            cpuCores,
            totalStorageGb,
            freeStorageGb,
            timezoneId,
        )
    }

    /** Blocking. Prefer [collectAsync]. */
    fun collect(application: Application): Snapshot? {
        return try {
            if (!FacebookSdk.isInitialized()) return null

            val anonId = runCatching {
                AppEventsLogger.getAnonymousAppDeviceGUID(application)
            }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

            var callFailed = false
            var limitTracking = true
            var rawAdId: String? = null
            try {
                val info = AdvertisingIdClient.getAdvertisingIdInfo(application)
                limitTracking = info.isLimitAdTrackingEnabled
                rawAdId = info.id
            } catch (_: Exception) {
                callFailed = true
                limitTracking = true
                rawAdId = null
            }

            val advertisingId = usableAdvertisingId(
                rawId = rawAdId,
                limitAdTrackingEnabled = limitTracking,
                callFailed = callFailed,
            )

            Snapshot(
                anonId = anonId,
                advertisingId = advertisingId,
                adTrackingEnabled = advertisingId != null,
                userAgent = System.getProperty("http.agent")?.trim()?.takeIf { it.isNotEmpty() },
                extinfo = collectExtinfo(application),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun collectAsync(
        application: Application,
        onResult: (Snapshot?) -> Unit,
    ) {
        Executors.newSingleThreadExecutor().execute {
            val snapshot = collect(application)
            onResult(snapshot)
        }
    }

    private fun collectExtinfo(application: Application): List<String> {
        val packageName = application.packageName
        val pm = application.packageManager
        val pInfo = runCatching { pm.getPackageInfo(packageName, 0) }.getOrNull()
        val versionCode = if (Build.VERSION.SDK_INT >= 28) {
            pInfo?.longVersionCode?.toString().orEmpty()
        } else {
            @Suppress("DEPRECATION")
            pInfo?.versionCode?.toString().orEmpty()
        }
        val versionName = pInfo?.versionName.orEmpty()

        val metrics = DisplayMetrics()
        val wm = application.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        @Suppress("DEPRECATION")
        wm?.defaultDisplay?.getRealMetrics(metrics)

        val locale = Locale.getDefault()
        val localeTag = "${locale.language}_${locale.country}"
        val tz = TimeZone.getDefault()
        val tzShort = tz.getDisplayName(false, TimeZone.SHORT, Locale.US)
        val tzId = tz.id

        val carrier = runCatching {
            val tm = application.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            tm?.networkOperatorName.orEmpty()
        }.getOrDefault("")

        val density = String.format(Locale.US, "%.2f", metrics.density)
        val cores = Runtime.getRuntime().availableProcessors().toString()

        val (totalGb, freeGb) = storageGb()

        return buildExtinfo(
            packageName = packageName,
            versionCode = versionCode,
            versionName = versionName,
            osVersion = Build.VERSION.RELEASE,
            model = Build.MODEL.orEmpty(),
            localeTag = localeTag,
            timezoneShort = tzShort,
            carrier = carrier,
            screenWidthPx = metrics.widthPixels.toString(),
            screenHeightPx = metrics.heightPixels.toString(),
            density = density,
            cpuCores = cores,
            totalStorageGb = totalGb,
            freeStorageGb = freeGb,
            timezoneId = tzId,
        )
    }

    private fun storageGb(): Pair<String, String> {
        return try {
            val stat = StatFs(Environment.getDataDirectory().absolutePath)
            val blockSize = stat.blockSizeLong
            val total = stat.blockCountLong * blockSize
            val free = stat.availableBlocksLong * blockSize
            val gb = 1024.0 * 1024.0 * 1024.0
            val totalGb = (total / gb).roundToInt().coerceAtLeast(0).toString()
            val freeGb = (free / gb).roundToInt().coerceAtLeast(0).toString()
            totalGb to freeGb
        } catch (_: Exception) {
            "" to ""
        }
    }
}
