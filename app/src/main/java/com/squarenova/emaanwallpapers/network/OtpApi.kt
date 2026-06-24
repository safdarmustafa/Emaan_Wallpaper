package com.squarenova.emaanwallpapers.network

import android.util.Log
import com.squarenova.emaanwallpapers.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object OtpApi {

    private const val TAG = "OtpApi"
    const val SEND_OTP_FUNCTION = "send-otp"
    const val VERIFY_OTP_FUNCTION = "verify-otp"

    private val JSON = "application/json".toMediaType()
    private val client = OkHttpClient()

    private fun functionsBaseUrl(): String {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        require(base.isNotEmpty()) { "SUPABASE_URL missing (local.properties)." }
        return "$base/functions/v1/"
    }

    private fun anonBearer(): String {
        val key = BuildConfig.SUPABASE_ANON_KEY.trim()
        require(key.isNotEmpty()) { "SUPABASE_ANON_KEY missing (local.properties)." }
        return "Bearer $key"
    }

    /** Same normalization as login: digits only, last 10 (strips +91 if present). */
    fun normalizePhone(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    private fun parseError(body: String): String {
        return try {
            val o = JSONObject(body)
            o.optString("error", "").ifBlank { o.optString("message", "Request failed") }
        } catch (_: Exception) {
            body.ifBlank { "Request failed" }
        }
    }

    /**
     * Sends OTP via Supabase Edge Function [SEND_OTP_FUNCTION].
     * Used by both login ([LoginScreen]) and delete-account ([ProfileScreen]) flows.
     */
    suspend fun sendOtp(phone: String, caller: String = "unknown"): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanPhone = normalizePhone(phone)
        val functionName = SEND_OTP_FUNCTION
        val url = functionsBaseUrl() + functionName

        Log.d(TAG, "sendOtp caller=$caller phone=$cleanPhone function=$functionName url=$url")

        if (cleanPhone.length != 10) {
            Log.d(TAG, "sendOtp caller=$caller function=$functionName status=client_error body=invalid_phone")
            return@withContext Result.failure(Exception("Valid 10-digit phone required"))
        }

        try {
            val payload = JSONObject().put("phone", cleanPhone).toString()
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", anonBearer())
                .post(payload.toRequestBody(JSON))
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            val status = response.code

            Log.d(TAG, "sendOtp caller=$caller phone=$cleanPhone function=$functionName status=$status body=$body")

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception(parseError(body)))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.d(TAG, "sendOtp caller=$caller phone=$cleanPhone function=$functionName status=exception body=${e.message}")
            Result.failure(e)
        }
    }

    suspend fun verifyOtp(phone: String, otp: String, caller: String = "unknown"): Result<Unit> =
        withContext(Dispatchers.IO) {
            val cleanPhone = normalizePhone(phone)
            val functionName = VERIFY_OTP_FUNCTION
            val url = functionsBaseUrl() + functionName

            Log.d(TAG, "verifyOtp caller=$caller phone=$cleanPhone function=$functionName url=$url")

            try {
                val payload = JSONObject()
                    .put("phone", cleanPhone)
                    .put("otp", otp.filter { it.isDigit() })
                    .toString()
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", anonBearer())
                    .post(payload.toRequestBody(JSON))
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                val status = response.code

                Log.d(TAG, "verifyOtp caller=$caller phone=$cleanPhone function=$functionName status=$status body=$body")

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception(parseError(body)))
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.d(TAG, "verifyOtp caller=$caller phone=$cleanPhone function=$functionName status=exception body=${e.message}")
                Result.failure(e)
            }
        }
}

object AccountDeletionApi {

    private const val TAG = "AccountDeletionApi"
    const val DELETE_USER_DATA_FUNCTION = "delete-user-data"

    private val JSON = "application/json".toMediaType()
    private val client = OkHttpClient()

    private fun functionsBaseUrl(): String {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        require(base.isNotEmpty()) { "SUPABASE_URL missing (local.properties)." }
        return "$base/functions/v1/"
    }

    private fun anonBearer(): String {
        val key = BuildConfig.SUPABASE_ANON_KEY.trim()
        require(key.isNotEmpty()) { "SUPABASE_ANON_KEY missing (local.properties)." }
        return "Bearer $key"
    }

    suspend fun deleteUserData(phone: String, otp: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanPhone = OtpApi.normalizePhone(phone)
        val functionName = DELETE_USER_DATA_FUNCTION
        val url = functionsBaseUrl() + functionName

        Log.d(TAG, "deleteUserData phone=$cleanPhone function=$functionName url=$url")

        try {
            val payload = JSONObject()
                .put("phone", cleanPhone)
                .put("otp", otp.filter { it.isDigit() })
                .toString()
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", anonBearer())
                .post(payload.toRequestBody(JSON))
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            val status = response.code

            Log.d(TAG, "deleteUserData phone=$cleanPhone function=$functionName status=$status body=$body")

            if (!response.isSuccessful) {
                val msg = try {
                    JSONObject(body).optString("error", "Deletion failed")
                } catch (_: Exception) {
                    "Deletion failed"
                }
                return@withContext Result.failure(Exception(msg))
            }
            val deleted = try {
                JSONObject(body).optBoolean("deleted", true)
            } catch (_: Exception) {
                true
            }
            Result.success(deleted)
        } catch (e: Exception) {
            Log.d(TAG, "deleteUserData phone=$cleanPhone function=$functionName status=exception body=${e.message}")
            Result.failure(e)
        }
    }
}
