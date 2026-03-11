package com.squarenova.emaanwallpapers.ui.subscription

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// ─────────────────────────────────────────
// SubscriptionManager
// Razorpay callbacks come to MainActivity,
// this object bridges them to SubscriptionScreen
// ─────────────────────────────────────────
object SubscriptionManager {

    // ✅ SharedFlow — emits payment result to whoever is listening
    private val _paymentResult = MutableSharedFlow<PaymentResult>(extraBufferCapacity = 1)
    val paymentResult = _paymentResult.asSharedFlow()

    fun onPaymentSuccess(paymentId: String) {
        Log.d("SUBSCRIPTION", "Payment Success: $paymentId")
        _paymentResult.tryEmit(PaymentResult.Success(paymentId))
    }

    fun onPaymentError(description: String) {
        Log.e("SUBSCRIPTION", "Payment Error: $description")
        _paymentResult.tryEmit(PaymentResult.Error(description))
    }
}

sealed class PaymentResult {
    data class Success(val paymentId: String) : PaymentResult()
    data class Error(val message: String) : PaymentResult()
}