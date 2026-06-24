package com.squarenova.emaanwallpapers.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.squarenova.emaanwallpapers.R
import com.squarenova.emaanwallpapers.analytics.AnalyticsManager
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.data.EntitlementDebugLog
import com.squarenova.emaanwallpapers.data.UserSubscriptionSyncManager
import com.squarenova.emaanwallpapers.network.OtpApi
import com.squarenova.emaanwallpapers.network.SupabaseClient
import com.squarenova.emaanwallpapers.ui.profile.UserRow
import com.squarenova.emaanwallpapers.util.SecureLog
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun OtpScreen(
    navController: NavController,
    phone: String
) {

    var enteredOtp by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val dataStoreManager = DataStoreManager(context)
    val scope = rememberCoroutineScope()

    var resendTimerKey by remember { mutableIntStateOf(0) }
    var remainingSeconds by remember { mutableIntStateOf(30) }
    var isResending by remember { mutableStateOf(false) }
    var resendMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(resendTimerKey) {
        remainingSeconds = 30
        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds -= 1
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Image(
            painter = painterResource(id = R.drawable.mosque),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF064E3B).copy(alpha = 0.6f))
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.95f)
            ),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp)
                .fillMaxWidth()
        ) {

            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = "Verify OTP",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF064E3B)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Code sent to +91 $phone",
                    fontSize = 14.sp,
                    color = Color(0xFF0F5132)
                )

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = enteredOtp,
                    onValueChange = { input ->
                        val digitsOnly = input.filter { it.isDigit() }.take(6)
                        enteredOtp = digitsOnly
                        if (errorMessage.isNotEmpty()) errorMessage = ""
                    },
                    label = { Text("Enter OTP", color = Color.Black.copy(alpha = 0.65f)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = Color.Black),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedBorderColor = Color(0xFFD4AF37),
                        unfocusedBorderColor = Color.Black.copy(alpha = 0.25f),
                        cursorColor = Color.Black,
                        focusedLabelColor = Color.Black.copy(alpha = 0.65f),
                        unfocusedLabelColor = Color.Black.copy(alpha = 0.65f)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = Color.Red,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = {
                        if (enteredOtp.length != 6) {
                            errorMessage = "Enter the 6-digit OTP"
                            return@Button
                        }
                        scope.launch {
                            isVerifying = true
                            try {
                                val verifyResult = OtpApi.verifyOtp(phone, enteredOtp, caller = "login_otp_screen")
                                if (verifyResult.isFailure) {
                                    errorMessage = verifyResult.exceptionOrNull()?.message
                                        ?: "Invalid OTP. Try again."
                                    return@launch
                                }

                                dataStoreManager.saveLogin(phone)
                                AnalyticsManager.identify(phone)

                                val subscriptionSyncManager =
                                    UserSubscriptionSyncManager(dataStoreManager)
                                val isSubscribed =
                                    subscriptionSyncManager.syncUserSubscription(phone)
                                SecureLog.d(
                                    "OTP_SCREEN",
                                    "Post-login subscription sync: subscribed=$isSubscribed"
                                )

                                val result = SupabaseClient.client
                                    .postgrest["users"]
                                    .select {
                                        filter {
                                            eq("phone_number", phone)
                                        }
                                    }
                                    .decodeList<UserRow>()

                                val existingUser = result.firstOrNull()

                                if (existingUser != null && !existingUser.first_name.isNullOrEmpty()) {
                                    dataStoreManager.setProfileCompleted()
                                    EntitlementDebugLog.navigation(
                                        source = "OtpScreen",
                                        destination = if (isSubscribed) "home" else "subscription",
                                        subscriptionStatus = existingUser.subscription_status,
                                        trialEnd = existingUser.trial_end,
                                        hasPremiumAccess = isSubscribed,
                                    )
                                    if (isSubscribed) {
                                        navController.navigate("home") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    } else {
                                        navController.navigate("subscription") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    }
                                } else {
                                    navController.navigate("profile_setup") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }

                            } catch (e: Exception) {
                                SecureLog.e("OTP_SCREEN", "verify failed", e)
                                errorMessage = "Something went wrong. Try again."
                            } finally {
                                isVerifying = false
                            }
                        }
                    },
                    enabled = !isVerifying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD4AF37)
                    )
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Verify",
                            color = Color.Black,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    enabled = remainingSeconds == 0 && !isResending,
                    onClick = {
                        if (isResending) return@TextButton
                        resendMessage = null
                        errorMessage = ""

                        isResending = true
                        scope.launch {
                            try {
                                val cleanPhone = phone.filter { it.isDigit() }
                                val response = OtpApi.sendOtp(cleanPhone, caller = "login_resend")

                                if (response.isSuccess) {
                                    resendMessage = "OTP sent successfully."
                                    resendTimerKey += 1
                                } else {
                                    resendMessage = response.exceptionOrNull()?.message
                                        ?: "Failed to resend OTP. Try again."
                                }
                            } catch (e: Exception) {
                                SecureLog.e("OTP_SCREEN_RESEND", "resend failed", e)
                                resendMessage = "Failed to resend OTP. Try again."
                            } finally {
                                isResending = false
                            }
                        }
                    }
                ) {
                    Text(
                        text = if (remainingSeconds > 0) {
                            "Resend OTP in ${remainingSeconds}s"
                        } else {
                            if (isResending) "Resending..." else "Resend OTP"
                        },
                        color = Color(0xFF0F5132),
                        fontSize = 13.sp
                    )
                }

                if (!resendMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = resendMessage ?: "",
                        color = if (resendMessage?.contains("Failed", ignoreCase = true) == true) Color.Red else Color(0xFF0F5132),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
