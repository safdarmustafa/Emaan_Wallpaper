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
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.network.Fast2SmsConfig
import com.squarenova.emaanwallpapers.network.RetrofitClient
import com.squarenova.emaanwallpapers.network.SupabaseClient
import com.squarenova.emaanwallpapers.ui.profile.UserRow
import io.github.jan.supabase.postgrest.postgrest
import android.util.Log
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

// Keep same dummy-mode as LoginScreen for your test number(s).
private val TEST_NUMBERS = listOf("7856906972")
private const val DUMMY_OTP = 123456

@Composable
fun OtpScreen(
    navController: NavController,
    sentOtp: String,
    phone: String
) {

    var enteredOtp by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    // Latest OTP (initially the one passed from Login, then updates after resend).
    var currentOtp by remember { mutableStateOf(sentOtp) }

    val context = LocalContext.current
    val dataStoreManager = DataStoreManager(context)
    val scope = rememberCoroutineScope()

    // Resend OTP countdown
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
                    text = "Code sent to $phone",
                    fontSize = 14.sp,
                    color = Color(0xFF0F5132)
                )

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = enteredOtp,
                    onValueChange = { input ->
                        // Digits only, max 6 chars
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
                        // TODO: REMOVE "123456" BEFORE PRODUCTION
                        if (enteredOtp == currentOtp || enteredOtp == "123456") {
                            scope.launch {
                                isVerifying = true
                                try {
                                    // ✅ Save phone to DataStore
                                    dataStoreManager.saveLogin(phone)

                                    // ✅ Check if user exists in Supabase
                                    val result = SupabaseClient.client
                                        .postgrest["users"]
                                        .select {
                                            filter {
                                                eq("phone_number", phone)
                                            }
                                        }
                                        .decodeList<UserRow>()

                                    val existingUser = result.firstOrNull()

                                    // ✅ FIXED: Check first_name, not just row existence.
                                    // A user row may exist but have no name if they
                                    // previously skipped setup or it failed mid-way.
                                    if (existingUser != null && !existingUser.first_name.isNullOrEmpty()) {
                                        // ✅ Returning user — check subscription
                                        dataStoreManager.setProfileCompleted()
                                        if (existingUser.is_subscribed == true) {
                                            // Already subscribed → Home
                                            dataStoreManager.setSubscribed()
                                            navController.navigate("home") {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        } else {
                                            // Not subscribed → Subscription screen
                                            navController.navigate("subscription") {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        }
                                    } else {
                                        // ✅ New user → Profile setup first
                                        navController.navigate("profile_setup") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    }

                                } catch (e: Exception) {
                                    errorMessage = "Something went wrong. Try again."
                                    e.printStackTrace()
                                } finally {
                                    isVerifying = false
                                }
                            }
                        } else {
                            errorMessage = "Invalid OTP. Try again."
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
                                // Generate a new OTP (matches LoginScreen logic)
                                val cleanPhone = phone.filter { it.isDigit() }
                                val isDummyMode = cleanPhone in TEST_NUMBERS
                                val newOtp = if (isDummyMode) DUMMY_OTP else (100000..999999).random()
                                val message = "Your OTP for Emaan Wallpapers is $newOtp"

                                if (isDummyMode) {
                                    // No SMS in dummy mode
                                    Log.d("OTP_SCREEN", "DUMMY MODE resend OTP=$newOtp for $cleanPhone")
                                } else {
                                    RetrofitClient.api.sendOtp(
                                        authorization = Fast2SmsConfig.API_KEY,
                                        message = message,
                                        numbers = cleanPhone,
                                        senderId = Fast2SmsConfig.DLT_SENDER_ID,
                                        peId = Fast2SmsConfig.DLT_PE_ID,
                                        templateId = Fast2SmsConfig.DLT_TE_ID
                                    )
                                }

                                currentOtp = newOtp.toString()
                                resendMessage = "OTP sent successfully."
                                // Restart countdown
                                resendTimerKey += 1
                            } catch (e: Exception) {
                                Log.e("OTP_SCREEN_RESEND", e.message ?: "Unknown", e)
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