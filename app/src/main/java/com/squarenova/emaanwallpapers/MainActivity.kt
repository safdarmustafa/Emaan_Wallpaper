package com.squarenova.emaanwallpapers

import android.os.Bundle
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

    // ✅ Razorpay calls this on successful payment
    override fun onPaymentSuccess(razorpayPaymentId: String?) {
        SubscriptionManager.onPaymentSuccess(razorpayPaymentId ?: "")
    }

    // ✅ Razorpay calls this on failed/cancelled payment
    override fun onPaymentError(errorCode: Int, errorDescription: String?) {
        SubscriptionManager.onPaymentError(errorDescription ?: "Payment failed")
    }
}