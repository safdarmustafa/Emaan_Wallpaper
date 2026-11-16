package com.squarenova.emaanwallpapers

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
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
import com.squarenova.emaanwallpapers.ui.language.LanguageChangeFade

class MainActivity : AppCompatActivity(), CFSubscriptionResponseCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val emerald = ContextCompat.getColor(this, R.color.splash_background)
            window.statusBarColor = emerald
            window.navigationBarColor = emerald
        }

        enableEdgeToEdge()

        try {
            CFPaymentGatewayService.getInstance().setSubscriptionCheckoutCallback(this)
        } catch (e: CFException) {
            Log.e("CASHFREE", "Failed to register Cashfree subscription callback", e)
        }

        setContent {
            EmaanWallpapersTheme {
                LanguageChangeFade {
                    AppNavGraph()
                }
            }
        }

        window.decorView.post {
            val cream = ColorDrawable(ContextCompat.getColor(this, R.color.window_background))
            window.setBackgroundDrawable(cream)
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
