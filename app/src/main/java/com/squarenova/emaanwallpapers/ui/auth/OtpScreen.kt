package com.squarenova.emaanwallpapers.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.squarenova.emaanwallpapers.R
import com.squarenova.emaanwallpapers.analytics.AnalyticsEvents
import com.squarenova.emaanwallpapers.analytics.AnalyticsManager
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.data.EntitlementDebugLog
import com.squarenova.emaanwallpapers.data.UserSubscriptionSyncManager
import com.squarenova.emaanwallpapers.network.OtpApi
import com.squarenova.emaanwallpapers.network.SupabaseClient
import com.squarenova.emaanwallpapers.ui.profile.UserRow
import com.squarenova.emaanwallpapers.util.SecureLog
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PLAY_REVIEW_PHONE = "7856906972"
private const val PLAY_REVIEW_OTP = "123456"

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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val otpFocusRequesters = remember { List(6) { FocusRequester() } }

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

    LaunchedEffect(Unit) {
        otpFocusRequesters[0].requestFocus()
    }

    fun verifyEnteredOtp() {
        if (isVerifying) return
        if (enteredOtp.length != 6) {
            errorMessage = "Enter the 6-digit OTP"
            return
        }
        focusManager.clearFocus()
        keyboardController?.hide()
        scope.launch {
            isVerifying = true
            try {
                val normalizedPhone = OtpApi.normalizePhone(phone)
                val isPlayReviewLogin =
                    normalizedPhone == PLAY_REVIEW_PHONE && enteredOtp == PLAY_REVIEW_OTP

                if (!isPlayReviewLogin) {
                    val verifyResult = OtpApi.verifyOtp(
                        phone,
                        enteredOtp,
                        caller = "login_otp_screen",
                    )
                    if (verifyResult.isFailure) {
                        errorMessage = verifyResult.exceptionOrNull()?.message
                            ?: "Invalid OTP. Try again."
                        return@launch
                    }
                }

                dataStoreManager.saveLogin(phone)
                AnalyticsManager.identify(phone)
                AnalyticsManager.track(AnalyticsEvents.LOGIN_SUCCESS)

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
    }

    fun onOtpDigitChange(index: Int, raw: String) {
        if (errorMessage.isNotEmpty()) errorMessage = ""
        val digits = raw.filter { it.isDigit() }

        when {
            digits.length > 1 -> {
                enteredOtp = digits.take(6)
                if (enteredOtp.length >= 6) {
                    verifyEnteredOtp()
                } else {
                    otpFocusRequesters[enteredOtp.length.coerceIn(0, 5)].requestFocus()
                }
            }
            digits.isEmpty() -> {
                if (index < enteredOtp.length) {
                    enteredOtp = enteredOtp.removeRange(index, index + 1)
                }
                if (index > 0) {
                    otpFocusRequesters[index - 1].requestFocus()
                }
            }
            else -> {
                val digit = digits.first()
                enteredOtp = when {
                    index < enteredOtp.length ->
                        enteredOtp.substring(0, index) + digit + enteredOtp.substring(index + 1)
                    index == enteredOtp.length ->
                        enteredOtp + digit
                    else ->
                        enteredOtp + digit
                }.take(6)

                if (enteredOtp.length >= 6) {
                    verifyEnteredOtp()
                } else if (index < 5) {
                    otpFocusRequesters[index + 1].requestFocus()
                }
            }
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    var focusedIndex by remember { mutableIntStateOf(-1) }

                    repeat(6) { index ->
                        val digit = enteredOtp.getOrNull(index)?.toString().orEmpty()

                        BasicTextField(
                            value = digit,
                            onValueChange = { onOtpDigitChange(index, it) },
                            modifier = Modifier
                                .width(44.dp)
                                .height(52.dp)
                                .focusRequester(otpFocusRequesters[index])
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        focusedIndex = index
                                    } else if (focusedIndex == index) {
                                        focusedIndex = -1
                                    }
                                }
                                .onPreviewKeyEvent { event ->
                                    if (
                                        event.type == KeyEventType.KeyDown &&
                                        event.key == Key.Backspace &&
                                        digit.isEmpty() &&
                                        index > 0
                                    ) {
                                        if (enteredOtp.isNotEmpty()) {
                                            enteredOtp = enteredOtp.dropLast(1)
                                        }
                                        otpFocusRequesters[index - 1].requestFocus()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                .border(
                                    width = 1.5.dp,
                                    color = if (focusedIndex == index) {
                                        Color(0xFFD4AF37)
                                    } else {
                                        Color.Black.copy(alpha = 0.25f)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .background(
                                    color = Color.White,
                                    shape = RoundedCornerShape(12.dp),
                                ),
                            textStyle = TextStyle(
                                color = Color.Black,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(Color.Black),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    innerTextField()
                                }
                            },
                        )
                    }
                }

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
                    onClick = { verifyEnteredOtp() },
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
