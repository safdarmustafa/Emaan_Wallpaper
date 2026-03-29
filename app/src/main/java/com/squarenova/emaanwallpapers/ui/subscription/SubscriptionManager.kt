package com.squarenova.emaanwallpapers.ui.subscription

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

object SubscriptionManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // tryEmit() can drop events when the buffer is full or collectors are slow — use emit() so payment
    // success is never lost after Razorpay calls back.
    private val _paymentResult = MutableSharedFlow<PaymentResult>(extraBufferCapacity = 64)
    val paymentResult = _paymentResult.asSharedFlow()

    fun onPaymentSuccess(paymentId: String) {
        Log.d("SUBSCRIPTION", "Payment Success: $paymentId")
        scope.launch {
            _paymentResult.emit(PaymentResult.Success(paymentId))
        }
    }

    fun onPaymentError(description: String) {
        Log.e("SUBSCRIPTION", "Payment Error: $description")
        scope.launch {
            _paymentResult.emit(PaymentResult.Error(description))
        }
    }
}

sealed class PaymentResult {
    data class Success(val paymentId: String) : PaymentResult()
    data class Error(val message: String) : PaymentResult()
}
