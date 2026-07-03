package com.squarenova.emaanwallpapers

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.razorpay.PaymentResultListener
import com.squarenova.emaanwallpapers.navigation.AppNavGraph
import com.squarenova.emaanwallpapers.theme.EmaanWallpapersTheme
import com.squarenova.emaanwallpapers.ui.subscription.SubscriptionManager

class MainActivity : ComponentActivity(), PaymentResultListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            EmaanWallpapersTheme {
                AppNavGraph()
            }
        }
    }

    // ✅ PAYMENT SUCCESS (Razorpay may call off main thread; UI + SharedFlow must run on main)
    override fun onPaymentSuccess(razorpayPaymentId: String?) {
        val paymentId = razorpayPaymentId ?: ""
        Log.d("RAZORPAY", "Payment Success: $paymentId")
        Log.d(
            "SubscriptionDebug",
            "1. MainActivity.onPaymentSuccess paymentId=$paymentId " +
                "pendingCheckoutKind=${SubscriptionManager.peekPersistedCheckoutKind()}"
        )
        runOnUiThread {
            SubscriptionManager.onPaymentSuccess(paymentId)
        }
    }

    // ❌ PAYMENT FAILED / CANCELLED
    override fun onPaymentError(errorCode: Int, errorDescription: String?) {
        val errorMsg = errorDescription ?: "Payment failed"
        Log.e("RAZORPAY", "Error [$errorCode]: $errorMsg")
        runOnUiThread {
            SubscriptionManager.onPaymentError(errorCode, errorMsg)
        }
    }
}