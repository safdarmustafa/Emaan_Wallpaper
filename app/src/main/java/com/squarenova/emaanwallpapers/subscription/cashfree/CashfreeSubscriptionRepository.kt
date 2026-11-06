package com.squarenova.emaanwallpapers.subscription.cashfree

import com.squarenova.emaanwallpapers.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * Invokes the Cashfree subscription Edge Functions only.
 * Does NOT unlock Premium and does NOT talk to Cashfree directly.
 */
object CashfreeSubscriptionRepository {

    data class CreateSubscriptionResult(
        val subscriptionId: String?,
        val subscriptionSessionId: String?,
        val alreadyExists: Boolean,
        val status: String?,
    )

    data class SubscriptionStatus(
        val isPremium: Boolean,
        val status: String?,
        val planType: String?,
        val startDate: String?,
        val endDate: String?,
        val nextBillingDate: String?,
        val autopayEnabled: Boolean,
    )

    data class CancelResult(
        val alreadyCancelled: Boolean,
        val alreadyCancelling: Boolean,
    )

    class EdgeFunctionException(
        message: String,
        val code: String? = null,
        val httpStatus: Int? = null,
    ) : Exception(message)

    private const val FUNCTION_CREATE_SUBSCRIPTION = "create-subscription"
    private const val FUNCTION_SUBSCRIPTION_STATUS = "subscription-status"
    private const val FUNCTION_CANCEL_SUBSCRIPTION = "cancel-subscription"

    private const val MAX_ATTEMPTS = 3
    private val RETRY_DELAYS_MS = longArrayOf(1_000L, 2_000L)

    private val JSON = "application/json".toMediaType()
    private val client = OkHttpClient()

    suspend fun createSubscription(phone: String): Result<CreateSubscriptionResult> =
        invoke(FUNCTION_CREATE_SUBSCRIPTION, phone) { data ->
            CreateSubscriptionResult(
                subscriptionId = data?.optStringOrNull("subscription_id"),
                subscriptionSessionId = data?.optStringOrNull("subscription_session_id"),
                alreadyExists = data?.optBoolean("alreadyExists", false) ?: false,
                status = data?.optStringOrNull("status"),
            )
        }

    suspend fun getSubscriptionStatus(phone: String): Result<SubscriptionStatus> =
        invoke(FUNCTION_SUBSCRIPTION_STATUS, phone) { data ->
            if (data == null) throw EdgeFunctionException("Empty subscription-status response.")
            SubscriptionStatus(
                isPremium = data.optBoolean("isPremium", false),
                status = data.optStringOrNull("status"),
                planType = data.optStringOrNull("plan_type"),
                startDate = data.optStringOrNull("start_date"),
                endDate = data.optStringOrNull("end_date"),
                nextBillingDate = data.optStringOrNull("next_billing_date"),
                autopayEnabled = data.optBoolean("autopay_enabled", false),
            )
        }

    suspend fun cancelSubscription(phone: String): Result<CancelResult> =
        invoke(FUNCTION_CANCEL_SUBSCRIPTION, phone) { data ->
            CancelResult(
                alreadyCancelled = data?.optBoolean("alreadyCancelled", false) ?: false,
                alreadyCancelling = data?.optBoolean("alreadyCancelling", false) ?: false,
            )
        }

    /** Retries network failures and 5xx responses; 4xx are returned immediately. */
    private suspend fun <T> invoke(
        functionName: String,
        phone: String,
        mapData: (JSONObject?) -> T,
    ): Result<T> {
        var lastError: Throwable = EdgeFunctionException("Request failed")
        for (attempt in 0 until MAX_ATTEMPTS) {
            try {
                return Result.success(mapData(post(functionName, phone)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                val retryable = e is IOException ||
                    (e is EdgeFunctionException && (e.httpStatus ?: 0) >= 500)
                if (!retryable || attempt == MAX_ATTEMPTS - 1) break
                delay(RETRY_DELAYS_MS[attempt])
            }
        }
        return Result.failure(lastError)
    }

    /** Returns the `data` object of a successful `{ success, message, data }` envelope. */
    private suspend fun post(functionName: String, phone: String): JSONObject? =
        withContext(Dispatchers.IO) {
            val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
            val anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
            require(baseUrl.isNotEmpty() && anonKey.isNotEmpty()) {
                "SUPABASE_URL / SUPABASE_ANON_KEY missing (local.properties)."
            }

            val request = Request.Builder()
                .url("$baseUrl/functions/v1/$functionName")
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .post(JSONObject().put("phone", phone).toString().toRequestBody(JSON))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val envelope = runCatching { JSONObject(body) }.getOrNull()

                if (!response.isSuccessful || envelope?.optBoolean("success", false) != true) {
                    val error = envelope?.optJSONObject("error")
                    val message = envelope?.optStringOrNull("message")
                        ?: error?.optStringOrNull("details")
                        ?: body.take(200).ifBlank { "$functionName failed (HTTP ${response.code})" }
                    throw EdgeFunctionException(
                        message = message,
                        code = error?.optStringOrNull("code"),
                        httpStatus = response.code,
                    )
                }
                envelope.optJSONObject("data")
            }
        }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).trim().ifBlank { null }
}
