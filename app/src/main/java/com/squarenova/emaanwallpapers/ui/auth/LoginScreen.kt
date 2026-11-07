package com.squarenova.emaanwallpapers.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.squarenova.emaanwallpapers.R
import com.squarenova.emaanwallpapers.network.OtpApi
import com.squarenova.emaanwallpapers.theme.BackgroundCream
import com.squarenova.emaanwallpapers.theme.ProfileEmerald
import com.squarenova.emaanwallpapers.theme.ProfileEmeraldLight
import com.squarenova.emaanwallpapers.theme.ProfileGold
import com.squarenova.emaanwallpapers.theme.ProfileGoldLight
import com.squarenova.emaanwallpapers.ui.language.LoginLanguageButton
import com.squarenova.emaanwallpapers.ui.legal.LegalUrlOpener
import com.squarenova.emaanwallpapers.util.SecureLog
import kotlinx.coroutines.launch

private val LoginGlassSurface = BackgroundCream.copy(alpha = 0.88f)
private val LoginFieldSurface = Color.White.copy(alpha = 0.92f)

@Composable
fun LoginScreen(navController: NavController) {
    var phoneNumber by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.mosque),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ProfileEmerald.copy(alpha = 0.38f),
                            ProfileEmerald.copy(alpha = 0.22f),
                            Color(0xFF064E3B).copy(alpha = 0.48f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LoginBrandHeader()

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = LoginGlassSurface,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.login_welcome),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ProfileEmerald,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.login_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ProfileEmerald.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { input ->
                            val digitsOnly = input.filter { it.isDigit() }.take(10)
                            phoneNumber = digitsOnly
                            if (errorMessage.isNotEmpty()) errorMessage = ""
                            if (digitsOnly.length == 10) {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        },
                        label = {
                            Text(
                                stringResource(R.string.login_phone_label),
                                color = ProfileEmerald.copy(alpha = 0.65f),
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = ProfileEmerald.copy(alpha = 0.75f),
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        prefix = {
                            Text(
                                text = "+91 ",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = ProfileEmerald,
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            color = ProfileEmerald,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = LoginFieldSurface,
                            unfocusedContainerColor = LoginFieldSurface,
                            focusedTextColor = ProfileEmerald,
                            unfocusedTextColor = ProfileEmerald,
                            focusedBorderColor = ProfileGold,
                            unfocusedBorderColor = ProfileEmerald.copy(alpha = 0.22f),
                            cursorColor = ProfileGold,
                            focusedLabelColor = ProfileEmerald.copy(alpha = 0.8f),
                            unfocusedLabelColor = ProfileEmerald.copy(alpha = 0.55f),
                            focusedLeadingIconColor = ProfileEmerald,
                            unfocusedLeadingIconColor = ProfileEmerald.copy(alpha = 0.6f),
                        ),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.login_sms_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = ProfileEmerald.copy(alpha = 0.62f),
                        lineHeight = 15.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (errorMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = errorMessage,
                            color = Color(0xFFB3261E),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            if (isLoading) return@Button

                            val cleanNumber = phoneNumber.filter { it.isDigit() }

                            if (cleanNumber.length != 10) {
                                errorMessage = context.getString(R.string.login_invalid_phone)
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
                                            ?: context.getString(R.string.login_otp_failed)
                                    }
                                } catch (e: Exception) {
                                    SecureLog.e("LOGIN", "sendOtp failed", e)
                                    errorMessage = context.getString(R.string.login_network_error)
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ProfileGold,
                            contentColor = ProfileEmerald,
                            disabledContainerColor = ProfileGold.copy(alpha = 0.45f),
                            disabledContentColor = ProfileEmerald.copy(alpha = 0.55f),
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 1.dp,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = ProfileEmerald,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp),
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.login_continue),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    LoginLegalConsentText(
                        onTermsClick = { LegalUrlOpener.openTermsAndConditions(context) },
                        onPrivacyClick = { LegalUrlOpener.openPrivacyPolicy(context) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LoginTrustIndicators()
        }

        LoginLanguageButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .zIndex(2f)
                .statusBarsPadding()
                .padding(top = 12.dp, end = 16.dp),
        )
    }
}

@Composable
private fun LoginBrandHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .border(2.dp, ProfileGold.copy(alpha = 0.55f), CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(ProfileEmeraldLight, ProfileEmerald),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(52.dp),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.app_name),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 0.2.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.login_tagline),
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = ProfileGoldLight.copy(alpha = 0.95f),
            letterSpacing = 0.4.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoginLegalConsentText(
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
) {
    val bodyColor = ProfileEmerald.copy(alpha = 0.72f)
    val linkStyle = SpanStyle(
        color = ProfileEmerald,
        fontWeight = FontWeight.SemiBold,
    )
    val linkColors = TextLinkStyles(style = linkStyle)

    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = bodyColor, fontSize = 11.sp)) {
                append(stringResource(R.string.login_consent_prefix))
            }
            withLink(
                LinkAnnotation.Clickable(
                    tag = "terms",
                    styles = linkColors,
                    linkInteractionListener = { onTermsClick() },
                ),
            ) {
                append(stringResource(R.string.login_consent_terms))
            }
            withStyle(SpanStyle(color = bodyColor, fontSize = 11.sp)) {
                append(stringResource(R.string.login_consent_and))
            }
            withLink(
                LinkAnnotation.Clickable(
                    tag = "privacy",
                    styles = linkColors,
                    linkInteractionListener = { onPrivacyClick() },
                ),
            ) {
                append(stringResource(R.string.login_consent_privacy))
            }
            withStyle(SpanStyle(color = bodyColor, fontSize = 11.sp)) {
                append(".")
            }
        },
        style = TextStyle(
            textAlign = TextAlign.Center,
            lineHeight = 17.sp,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
    )
}

@Composable
private fun LoginTrustIndicators() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        LoginTrustChip(
            icon = Icons.Default.VerifiedUser,
            label = stringResource(R.string.login_trust_otp),
        )
        LoginTrustChip(
            icon = Icons.Default.Speed,
            label = stringResource(R.string.login_trust_fast),
        )
        LoginTrustChip(
            icon = Icons.Default.Shield,
            label = stringResource(R.string.login_trust_privacy),
        )
    }
}

@Composable
private fun LoginTrustChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.18f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
        ) {
            Box(
                modifier = Modifier.padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ProfileGoldLight,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.88f),
            textAlign = TextAlign.Center,
            lineHeight = 12.sp,
            maxLines = 2,
        )
    }
}
