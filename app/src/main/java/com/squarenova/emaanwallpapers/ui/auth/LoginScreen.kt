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
import com.squarenova.emaanwallpapers.analytics.AnalyticsManager
import com.squarenova.emaanwallpapers.network.OtpApi
import com.squarenova.emaanwallpapers.util.SecureLog

@Composable
fun LoginScreen(navController: NavController) {

    var phoneNumber by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

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
                .background(Color(0xFF064E3B).copy(alpha = 0.55f))
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

                Text(
                    text = "We send a one-time code via SMS. Standard message rates may apply.",
                    fontSize = 11.sp,
                    color = Color(0xFF0F5132).copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth(),
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
                        if (isLoading) return@Button
                        AnalyticsManager.trackEvent("Login - Continue Tapped")

                        val cleanNumber = phoneNumber.filter { it.isDigit() }

                        if (cleanNumber.length != 10) {
                            errorMessage = "Enter valid 10 digit number"
                            return@Button
                        }

                        errorMessage = ""
                        isLoading = true

                        scope.launch {
                            try {
                                val result = OtpApi.sendOtp(cleanNumber, caller = "login")
                                if (result.isSuccess) {
                                    navController.navigate("otp/$cleanNumber")
                                } else {
                                    errorMessage = result.exceptionOrNull()?.message
                                        ?: "Failed to send OTP"
                                }
                            } catch (e: Exception) {
                                SecureLog.e("LOGIN", "sendOtp failed", e)
                                errorMessage = "Network error"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    enabled = !isLoading,
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
