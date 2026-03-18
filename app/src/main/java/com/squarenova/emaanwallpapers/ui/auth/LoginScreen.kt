package com.squarenova.emaanwallpapers.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.squarenova.emaanwallpapers.R
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.squarenova.emaanwallpapers.network.RetrofitClient
import com.squarenova.emaanwallpapers.analytics.AnalyticsManager
import com.squarenova.emaanwallpapers.network.Fast2SmsConfig

// TODO: REMOVE BEFORE PRODUCTION
private val TEST_NUMBERS = listOf("7856906972")
private const val DUMMY_OTP = 123456

@Composable
fun LoginScreen(navController: NavController) {

    var phoneNumber by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {

        // 🌙 Mosque Background
        Image(
            painter = painterResource(id = R.drawable.mosque),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // 🌿 Dark Green Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF064E3B).copy(alpha = 0.55f))
        )

        // ✨ Glass Card
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
                    text = "Emaan Wallpapers",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF064E3B)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Bring Noor to Your Screen",
                    fontSize = 14.sp,
                    color = Color(0xFF0F5132)
                )

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { input ->
                        // Digits only, max 10 chars
                        val digitsOnly = input.filter { it.isDigit() }.take(10)
                        phoneNumber = digitsOnly
                        if (errorMessage.isNotEmpty()) errorMessage = ""
                    },
                    label = { Text("Phone Number", color = Color.Black.copy(alpha = 0.65f)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone
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

                Spacer(modifier = Modifier.height(8.dp))

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = Color.Red
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        AnalyticsManager.trackEvent("Login - Continue Tapped")

                        val cleanNumber = phoneNumber.filter { it.isDigit() }

                        if (cleanNumber.length != 10) {
                            errorMessage = "Enter valid 10 digit number"
                            return@Button
                        }

                        errorMessage = ""
                        isLoading = true

                        // TODO: REMOVE BEFORE PRODUCTION
                        val isDummyMode = cleanNumber in TEST_NUMBERS
                        val otp = if (isDummyMode) DUMMY_OTP else (100000..999999).random()
                        val message = "Your OTP for Emaan Wallpapers is $otp"

                        scope.launch {
                            try {
                                if (isDummyMode) {
                                    // ✅ Skip real SMS — saves Fast2SMS tokens
                                    println("DUMMY MODE: OTP for $cleanNumber is $otp (no SMS sent)")
                                    isLoading = false
                                    navController.navigate("otp/$otp/$cleanNumber")
                                } else {
                                    // ✅ Real SMS for all other numbers
                                    val response = RetrofitClient.api.sendOtp(
                                        authorization = Fast2SmsConfig.API_KEY,
                                        message = message,
                                        numbers = cleanNumber,
                                        senderId = Fast2SmsConfig.DLT_SENDER_ID,
                                        peId = Fast2SmsConfig.DLT_PE_ID,
                                        templateId = Fast2SmsConfig.DLT_TE_ID
                                    )

                                    println("HTTP Code: ${response.code()}")
                                    println("Response Body: ${response.body()}")

                                    isLoading = false

                                    if (response.isSuccessful) {
                                        navController.navigate("otp/$otp/$cleanNumber")
                                    } else {
                                        errorMessage = "Failed to send OTP"
                                    }
                                }

                            } catch (e: Exception) {
                                isLoading = false
                                errorMessage = "Network error"
                                println("EXCEPTION: ${e.message}")
                            }
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD4AF37)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = "Continue",
                            color = Color.Black,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}