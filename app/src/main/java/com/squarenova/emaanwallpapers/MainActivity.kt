package com.squarenova.emaanwallpapers

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cashfree.pg.api.CFPaymentGatewayService
import com.cashfree.pg.core.api.callback.CFSubscriptionResponseCallback
import com.cashfree.pg.core.api.exception.CFException
import com.cashfree.pg.core.api.utils.CFErrorResponse
import com.cashfree.pg.core.api.utils.CFSubscriptionResponse
import com.squarenova.emaanwallpapers.navigation.AppNavGraph
import com.squarenova.emaanwallpapers.subscription.cashfree.SubscriptionCheckoutBridge
import com.squarenova.emaanwallpapers.theme.EmaanWallpapersTheme

class MainActivity : ComponentActivity(), CFSubscriptionResponseCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        try {
            CFPaymentGatewayService.getInstance().setSubscriptionCheckoutCallback(this)
        } catch (e: CFException) {
            Log.e("CASHFREE", "Failed to register Cashfree subscription callback", e)
        }

        setContent {
            EmaanWallpapersTheme {
                AppNavGraph()
            }
        }
    }

    // Cashfree: Verify only means the UPI flow returned — premium is confirmed via subscription-status.
    override fun onSubscriptionVerify(cfSubscriptionResponse: CFSubscriptionResponse) {
        SubscriptionCheckoutBridge.onSubscriptionVerify(cfSubscriptionResponse)
    }

    override fun onSubscriptionFailure(cfErrorResponse: CFErrorResponse) {
        SubscriptionCheckoutBridge.onSubscriptionFailure(cfErrorResponse)
    }
}
