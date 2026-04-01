package com.squarenova.emaanwallpapers.network

import android.util.Log
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

    private const val BASE_URL =
        "https://uxodfjjytcsrjnxwqbvg.supabase.co/functions/v1/"

    // ✅ FULL ANON KEY (PASTE YOUR COMPLETE KEY HERE)
    private const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InV4b2Rmamp5dGNzcmpueHdxYnZnIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM2OTgzMjEsImV4cCI6MjA4OTI3NDMyMX0.i3BlgJkG9iq0-vgTs9cZ9ndDSVH7T3TH8uEhbdIOWW0"

    private val JSON = "application/json".toMediaType()

    private val client = OkHttpClient()

    /** Supabase Edge Functions return JSON like `{"code":"NOT_FOUND","message":"..."}` or `{ "error": "..." }`. */
    private fun parseEdgeFunctionError(body: String): String {
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
                    "$msg — deploy the function: supabase functions deploy activate-trial"
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
                .url(BASE_URL + "create-order")
                .addHeader("Authorization", "Bearer $ANON_KEY")
                .post("{}".toRequestBody(JSON))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception(parseEdgeFunctionError(body).ifBlank { "createOrder failed" })
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

    /**
     * STEP 3 — After ₹5 payment success. Persists [razorpay_payment_id] and creates Razorpay subscription.
     */
    suspend fun createSubscription(phone: String, razorpayPaymentId: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {

                val json = JSONObject().apply {
                    put("phone", phone)
                    put("razorpay_payment_id", razorpayPaymentId)
                }

                val request = Request.Builder()
                    .url(BASE_URL + "razorpay-subscription")
                    .addHeader("Authorization", "Bearer $ANON_KEY")
                    .addHeader("Content-Type", "application/json")
                    .post(json.toString().toRequestBody(JSON))
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception(parseEdgeFunctionError(body).ifBlank { "createSubscription failed" })
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

    suspend fun activateTrial(phone: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("phone", phone)
                }

                val request = Request.Builder()
                    .url(BASE_URL + "activate-trial")
                    .addHeader("Authorization", "Bearer $ANON_KEY")
                    .addHeader("Content-Type", "application/json")
                    .post(json.toString().toRequestBody(JSON))
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception(parseEdgeFunctionError(body).ifBlank { "activateTrial failed" })
                    )
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Retries [activateTrial] for transient races (mandate OK, Razorpay status lag).
     * Used after mandate success and for Splash/Home recovery.
     */
    suspend fun activateTrialWithRetries(
        phone: String,
        maxAttempts: Int = 3,
        delayMs: Long = 1500L
    ): Result<Unit> {
        var lastError: Exception? = null
        for (attempt in 1..maxAttempts) {
            Log.d(
                TAG,
                "activateTrialWithRetries attempt $attempt/$maxAttempts phone=$phone"
            )
            val r = activateTrial(phone)
            r.fold(
                onSuccess = {
                    Log.i(
                        TAG,
                        "activateTrialWithRetries success on attempt $attempt phone=$phone"
                    )
                    return Result.success(Unit)
                },
                onFailure = { e ->
                    lastError = e as? Exception ?: Exception(e.message)
                    Log.w(
                        TAG,
                        "activateTrialWithRetries attempt $attempt failed: ${e.message}"
                    )
                    if (attempt < maxAttempts) {
                        Log.d(
                            TAG,
                            "activateTrialWithRetries waiting ${delayMs}ms before retry"
                        )
                        delay(delayMs)
                    }
                }
            )
        }
        Log.e(
            TAG,
            "activateTrialWithRetries failed after $maxAttempts attempts: ${lastError?.message}"
        )
        return Result.failure(
            lastError ?: Exception("activateTrial failed after $maxAttempts attempts")
        )
    }

    // 🔥 STEP 3 → CANCEL SUBSCRIPTION
    suspend fun cancelSubscription(subscriptionId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {

                val json = JSONObject().apply {
                    put("subscription_id", subscriptionId)
                }

                val request = Request.Builder()
                    .url(BASE_URL + "cancel-subscription")
                    .addHeader("Authorization", "Bearer $ANON_KEY")
                    .addHeader("Content-Type", "application/json")
                    .post(json.toString().toRequestBody(JSON))
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception(parseEdgeFunctionError(body).ifBlank { "Cancel failed" })
                    )
                }

                Result.success(Unit)

            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}