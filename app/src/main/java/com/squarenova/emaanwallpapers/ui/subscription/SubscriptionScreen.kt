package com.squarenova.emaanwallpapers.ui.subscription

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.razorpay.Checkout
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.network.SubscriptionApi
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.json.JSONObject

object RazorpayConfig {
    const val KEY_ID = "rzp_live_SUkaGbslh0IvIZ"
    const val PLAN_ID = "plan_SUjMMDAgQKiHOy"
}
@Composable
fun SubscriptionScreen(navController: NavController) {

    val context = LocalContext.current
    val activity = context as Activity
    val dataStore = DataStoreManager(context)
    val scope = rememberCoroutineScope()

    var phone by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        phone = dataStore.phoneNumber.firstOrNull() ?: ""
        Checkout.preload(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {

        Spacer(modifier = Modifier.height(40.dp))

        Text("Premium Subscription")

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {

                isLoading = true
                error = null

                scope.launch {

                    val result = SubscriptionApi.createSubscription(
                        RazorpayConfig.PLAN_ID
                    )

                    result.fold(
                        onSuccess = { subscriptionId ->

                            val checkout = Checkout()
                            checkout.setKeyID(RazorpayConfig.KEY_ID)

                            val options = JSONObject().apply {
                                put("name", "Emaan Wallpapers")
                                put("description", "Premium Subscription")
                                put("currency", "INR")
                                put("subscription_id", subscriptionId)
                            }

                            checkout.open(activity, options)
                        },
                        onFailure = {
                            error = it.message
                        }
                    )

                    isLoading = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Text("Subscribe Now")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
    }
}