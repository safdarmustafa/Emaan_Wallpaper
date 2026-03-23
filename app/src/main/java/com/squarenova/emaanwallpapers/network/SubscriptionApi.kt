package com.squarenova.emaanwallpapers.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject

object SubscriptionApi {

    private const val URL =
        "https://uxodfjjytcsrjnxwqbvg.supabase.co/functions/v1/razorpay-subscription"

    private val client = OkHttpClient()

    suspend fun createSubscription(planId: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {

                val body = JSONObject()
                    .put("plan_id", planId)
                    .toString()
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url(URL)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val res = response.body?.string() ?: ""

                Log.e("API_RESPONSE", res)

                val subId = JSONObject(res).optString("id")

                if (subId.isEmpty()) {
                    return@withContext Result.failure(Exception("No subscription_id"))
                }

                Result.success(subId)

            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}