package com.squarenova.emaanwallpapers.network

import android.util.Log
import com.squarenova.emaanwallpapers.BuildConfig
import com.squarenova.emaanwallpapers.data.MandateDebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object SubscriptionApi {
    private const val TAG = "SubscriptionApi"

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

    private val JSON = "application/json".toMediaType()

    private val client = OkHttpClient()
    private const val RETRY_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 1200L

    /** Supabase Edge Functions return JSON like `{"code":"NOT_FOUND","message":"..."}` or `{ "error": "..." }`. */
    private fun parseEdgeFunctionError(body: String, functionName: String? = null): String {
        return try {
            val o = JSONObject(body)
            val code = o.optString("code", "")
            var msg = o.optString("message", "").ifBlank { o.optString("error", "") }
            val details = o.optJSONObject("details")
            if (details != null && msg.isNotBlank()) {
                val inner = details.optJSONObject("error")
                val razDesc = inner?.optString("description")
                    ?: details.optString("description", "")
                if (razDesc.isNotBlank()) msg = "$msg — $razDesc"
            }
            when {
                msg.isNotBlank() && code == "NOT_FOUND" ->
                    if (!functionName.isNullOrBlank()) {
                        "$msg — deploy edge function: $functionName"
                    } else {
                        "$msg — deploy the required edge function"
                    }
                msg.isNotBlank() -> msg
                else -> body.ifBlank { "Request failed" }
            }
        } catch (_: Exception) {
            body.ifBlank { "Request failed" }
        }
    }

    // STEP 1 → CREATE ₹5 ORDER (500 paise)
    suspend fun createOrder(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(functionsBaseUrl() + "create-order")
                .addHeader("Authorization", anonBearer())
                .post("{}".toRequestBody(JSON))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                        Exception(parseEdgeFunctionError(body, "create-order").ifBlank { "createOrder failed" })
                )
            }

            val orderId = JSONObject(body).optString("order_id")
            if (orderId.isBlank()) {
                Result.failure(Exception("No order_id received"))
            } else {
                Result.success(orderId)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyPayment(phone: String, paymentId: String, orderId: String): Result<Unit> =
        withRetry("verifyPayment") {
            try {
                val json = JSONObject().apply {
                    put("phone", phone)
                    put("payment_id", paymentId)
                    put("order_id", orderId)
                }

                val request = Request.Builder()
                    .url(functionsBaseUrl() + "verify-payment")
                    .addHeader("Authorization", anonBearer())
                    .addHeader("Content-Type", "application/json")
                    .post(json.toString().toRequestBody(JSON))
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    return@withRetry Result.failure(
                        Exception(parseEdgeFunctionError(body, "verify-payment").ifBlank { "verifyPayment failed" })
                    )
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun startTrial(phone: String): Result<Unit> =
        withRetry("startTrial") {
            try {
                val json = JSONObject().apply {
                    put("phone", phone)
                }

                val request = Request.Builder()
                    .url(functionsBaseUrl() + "start-trial")
                    .addHeader("Authorization", anonBearer())
                    .addHeader("Content-Type", "application/json")
                    .post(json.toString().toRequestBody(JSON))
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    return@withRetry Result.failure(
                        Exception(parseEdgeFunctionError(body, "start-trial").ifBlank { "startTrial failed" })
                    )
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun createSubscription(phone: String): Result<String> = withRetry("createSubscription") {
        try {
            val json = JSONObject().apply {
                put("phone", phone)
            }

                val request = Request.Builder()
                    .url(functionsBaseUrl() + "create-subscription")
                    .addHeader("Authorization", anonBearer())
                .addHeader("Content-Type", "application/json")
                .post(json.toString().toRequestBody(JSON))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withRetry Result.failure(
                    Exception(parseEdgeFunctionError(body, "create-subscription").ifBlank { "createSubscription failed" })
                )
            }

            val subscriptionId = JSONObject(body).optString("subscription_id")
            if (subscriptionId.isBlank()) {
                Result.failure(Exception("No subscription_id received"))
            } else {
                Result.success(subscriptionId)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun activateTrial(phone: String): Result<String> = withRetry("activateTrial") {
        try {
            MandateDebugLog.activateTrialRequest(phone)
            val json = JSONObject().apply { put("phone", phone) }
            val request = Request.Builder()
                .url(functionsBaseUrl() + "activate-trial")
                .addHeader("Authorization", anonBearer())
                .addHeader("Content-Type", "application/json")
                .post(json.toString().toRequestBody(JSON))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val err = parseEdgeFunctionError(body, "activate-trial").ifBlank { "activateTrial failed" }
                MandateDebugLog.activateTrialResponse(phone, success = false, trialEnd = null, error = err)
                return@withRetry Result.failure(Exception(err))
            }

            val trialEnd = JSONObject(body).optString("trial_end")
                .ifBlank { JSONObject(body).optString("current_period_end") }
            MandateDebugLog.activateTrialResponse(phone, success = true, trialEnd = trialEnd, error = null)
            Result.success(trialEnd)
        } catch (e: Exception) {
            MandateDebugLog.activateTrialResponse(phone, success = false, trialEnd = null, error = e.message)
            Result.failure(e)
        }
    }

    suspend fun verifyMandate(subscriptionId: String): Result<String> = withRetry("verifyMandate") {
        try {
            val json = JSONObject().apply { put("subscription_id", subscriptionId) }
            val request = Request.Builder()
                .url(functionsBaseUrl() + "validate-subscription-status")
                .addHeader("Authorization", anonBearer())
                .addHeader("Content-Type", "application/json")
                .post(json.toString().toRequestBody(JSON))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withRetry Result.failure(
                    Exception(parseEdgeFunctionError(body, "validate-subscription-status").ifBlank { "verifyMandate failed" })
                )
            }

            val status = JSONObject(body).optString("razorpay_status")
            if (status == "authenticated" || status == "active") {
                Result.success(status)
            } else {
                Result.failure(Exception("Mandate not approved yet (status: $status)"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Razorpay status can be eventually consistent right after Paytm/UPI approval.
     * Poll briefly before declaring failure to avoid false-negative UX.
     */
    suspend fun verifyMandateWithPolling(
        subscriptionId: String,
        attempts: Int = 6,
        delayMs: Long = 2500L
    ): Result<String> = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        repeat(attempts) { idx ->
            val attempt = idx + 1
            Log.d(TAG, "event=mandate_poll_attempt attempt=$attempt/$attempts sub=$subscriptionId")
            val r = verifyMandate(subscriptionId)
            val rzStatus = r.getOrNull()
            MandateDebugLog.verifyMandatePoll(attempt, attempts, subscriptionId, rzStatus)
            if (r.isSuccess) {
                Log.i(TAG, "event=mandate_poll_success attempt=$attempt sub=$subscriptionId")
                MandateDebugLog.verifyMandateResult(subscriptionId, true, rzStatus, null)
                return@withContext r
            }
            val message = r.exceptionOrNull()?.message.orEmpty().lowercase()
            lastError = r.exceptionOrNull()

            // Terminal statuses shouldn't be retried.
            if ("cancelled" in message || "halted" in message || "completed" in message) {
                MandateDebugLog.verifyMandateResult(
                    subscriptionId,
                    false,
                    null,
                    r.exceptionOrNull()?.message,
                )
                return@withContext Result.failure(
                    Exception(r.exceptionOrNull()?.message ?: "Mandate verification failed")
                )
            }
            if (attempt < attempts) delay(delayMs)
        }
        MandateDebugLog.verifyMandateResult(
            subscriptionId,
            false,
            null,
            lastError?.message ?: "Mandate verification timed out",
        )
        Result.failure(lastError ?: Exception("Mandate verification timed out"))
    }

    suspend fun refreshSubscriptionStatus(phone: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply { put("phone", phone) }
            val request = Request.Builder()
                .url(functionsBaseUrl() + "validate-subscription-status")
                .addHeader("Authorization", anonBearer())
                .addHeader("Content-Type", "application/json")
                .post(json.toString().toRequestBody(JSON))
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception(parseEdgeFunctionError(body, "validate-subscription-status").ifBlank { "refreshSubscriptionStatus failed" })
                )
            }
            val isApproved = JSONObject(body).optBoolean("is_mandate_approved", false)
            Result.success(isApproved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun <T> withRetry(tag: String, block: suspend () -> Result<T>): Result<T> =
        withContext(Dispatchers.IO) {
            var last: Throwable? = null
            repeat(RETRY_ATTEMPTS) { idx ->
                val attempt = idx + 1
                Log.d(TAG, "event=retry_attempt api=$tag attempt=$attempt/$RETRY_ATTEMPTS")
                val result = block()
                if (result.isSuccess) {
                    Log.i(TAG, "event=retry_success api=$tag attempt=$attempt")
                    return@withContext result
                }
                last = result.exceptionOrNull()
                Log.w(TAG, "event=retry_failure api=$tag attempt=$attempt reason=${last?.message}")
                if (attempt < RETRY_ATTEMPTS) delay(RETRY_DELAY_MS)
            }
            Log.e(TAG, "event=retry_exhausted api=$tag reason=${last?.message}")
            Result.failure(last ?: Exception("$tag failed after $RETRY_ATTEMPTS attempts"))
        }

    // 🔥 STEP 3 → CANCEL SUBSCRIPTION
    suspend fun cancelSubscription(subscriptionId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {

                val json = JSONObject().apply {
                    put("subscription_id", subscriptionId)
                }

                val request = Request.Builder()
                    .url(functionsBaseUrl() + "cancel-subscription")
                    .addHeader("Authorization", anonBearer())
                    .addHeader("Content-Type", "application/json")
                    .post(json.toString().toRequestBody(JSON))
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception(parseEdgeFunctionError(body, "cancel-subscription").ifBlank { "Cancel failed" })
                    )
                }

                Result.success(Unit)

            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}