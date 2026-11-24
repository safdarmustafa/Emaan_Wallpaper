package com.squarenova.emaanwallpapers.analytics

import android.app.Application
import android.content.Context
import com.squarenova.emaanwallpapers.BuildConfig
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.util.SecureLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Fire-and-forget upload of Meta device match keys to save-meta-match-data.
 * Errors are logged only — never shown, never block UI or payment.
 */
object MetaMatchDataRepository {

    private const val TAG = "MetaMatchData"
    private const val FUNCTION_NAME = "save-meta-match-data"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val JSON = "application/json".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    /** Unawaited. Safe to call from UI / payment / splash. */
    fun saveAsync(context: Context, phoneOverride: String? = null) {
        val app = context.applicationContext as? Application ?: return
        scope.launch {
            try {
                saveNow(app, phoneOverride)
            } catch (e: Exception) {
                SecureLog.w(TAG, "saveAsync failed", e)
            }
        }
    }

    suspend fun saveNow(application: Application, phoneOverride: String? = null) {
        withContext(Dispatchers.IO) {
            val phone = phoneOverride?.trim()?.takeIf { it.isNotEmpty() }
                ?: DataStoreManager(application).phoneNumber.firstOrNull()?.trim().orEmpty()
            if (phone.isBlank()) {
                SecureLog.d(TAG, "skip: no phone")
                return@withContext
            }

            val snapshot = MetaDeviceInfo.collect(application)
            if (snapshot == null) {
                SecureLog.d(TAG, "skip: Meta SDK not ready / collect null")
                return@withContext
            }

            post(phone, snapshot)
        }
    }

    private fun post(phone: String, snapshot: MetaDeviceInfo.Snapshot) {
        val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
        val anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
        if (baseUrl.isEmpty() || anonKey.isEmpty()) {
            SecureLog.w(TAG, "skip: missing Supabase config")
            return
        }

        val body = JSONObject()
            .put("phone", phone)
            .put("anon_id", snapshot.anonId)
            .put("madid", snapshot.advertisingId)
            .put("ad_tracking_enabled", snapshot.adTrackingEnabled)
            .put("user_agent", snapshot.userAgent)
            .put("extinfo", JSONArray(snapshot.extinfo))

        val request = Request.Builder()
            .url("$baseUrl/functions/v1/$FUNCTION_NAME")
            .addHeader("Authorization", "Bearer $anonKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                SecureLog.w(TAG, "HTTP ${response.code}")
            } else {
                SecureLog.d(
                    TAG,
                    "saved hasMadid=${snapshot.advertisingId != null} tracking=${snapshot.adTrackingEnabled}",
                )
            }
        }
    }
}
