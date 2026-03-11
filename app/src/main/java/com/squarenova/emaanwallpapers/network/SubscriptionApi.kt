package com.squarenova.emaanwallpapers.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Calls the Supabase Edge Function to create a Razorpay subscription
 * and returns the subscription_id for checkout.open().
 */
object SubscriptionApi {

    private const val TAG = "SUBSCRIPTION_API"

    private const val EDGE_FUNCTION_URL =
        "https://mfxkexboiwhsmpstqbih.supabase.co/functions/v1/create-razorpay-subscription"

    // Supabase anon key (same as in SupabaseClient) — required for Edge Function auth
    private const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1meGtleGJvaXdoc21wc3RxYmloIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzIxMzE5MTksImV4cCI6MjA4NzcwNzkxOX0.GkCec2EIpkTRFTh9fCLA8usN2_QLxMKHKaYXAxoBWww"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Creates a Razorpay subscription via Edge Function.
     * @return Result.success(subscription_id) or Result.failure with error message.
     */
    suspend fun createSubscription(phoneNumber: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("phone_number", phoneNumber)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(EDGE_FUNCTION_URL)
                .addHeader("Authorization", "Bearer $ANON_KEY")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            Log.d(TAG, "createSubscription responseCode=${response.code} body=$responseBody")

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val obj = JSONObject(responseBody)
                    obj.optString("error", obj.optJSONObject("error")?.optString("description") ?: responseBody)
                } catch (_: Exception) {
                    responseBody
                }
                return@withContext Result.failure(Exception(errorMsg.ifEmpty { "Request failed (${response.code})" }))
            }

            val subscriptionId = try {
                JSONObject(responseBody).optString("subscription_id")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse subscription_id", e)
                return@withContext Result.failure(Exception("Invalid response format"))
            }

            if (subscriptionId.isBlank()) {
                return@withContext Result.failure(Exception("No subscription_id in response"))
            }

            Result.success(subscriptionId)
        } catch (e: Exception) {
            Log.e(TAG, "createSubscription error", e)
            Result.failure(e)
        }
    }
}
