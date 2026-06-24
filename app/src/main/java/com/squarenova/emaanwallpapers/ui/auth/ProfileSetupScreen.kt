package com.squarenova.emaanwallpapers.ui.auth

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.squarenova.emaanwallpapers.analytics.AnalyticsManager
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.data.EntitlementDebugLog
import com.squarenova.emaanwallpapers.data.SubscriptionEntitlement
import com.squarenova.emaanwallpapers.network.SupabaseClient
import com.squarenova.emaanwallpapers.ui.profile.UserRow
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
private data class SubscriptionCheckRow(
    val phone_number: String? = null,
    val is_subscribed: Boolean? = false,
    val subscription_status: String? = null,
    val trial_end: String? = null,
)

@Serializable
data class NewUserRow(
    val phone_number: String,
    val first_name: String,
    val last_name: String
)

@Composable
fun ProfileSetupScreen(navController: NavController) {

    val context = LocalContext.current
    val dataStoreManager = DataStoreManager(context)
    val scope = rememberCoroutineScope()

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val bgGradient = Brush.verticalGradient(
        listOf(Color(0xFF064E3B), Color(0xFF0D3B2E))
    )
    val goldColor = Color(0xFFD4AF37)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = bgGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // 🌙 Header
            Text("🌙", fontSize = 48.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Complete Your Profile",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Let's personalise your Emaan experience",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            // ✅ Form Card
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A4A38)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    SetupField(
                        label = "First Name *",
                        value = firstName,
                        onValueChange = { firstName = it }
                    )

                    SetupField(
                        label = "Last Name *",
                        value = lastName,
                        onValueChange = { lastName = it }
                    )
                }
            }

            // Error message
            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage,
                    color = Color(0xFFFF6B6B),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ✅ Save Button
            Button(
                onClick = {
                    AnalyticsManager.trackEvent("Profile Setup - Save & Continue Tapped")
                    // Validate required fields
                    if (firstName.trim().isEmpty()) {
                        errorMessage = "First name is required"
                        return@Button
                    }
                    if (lastName.trim().isEmpty()) {
                        errorMessage = "Last name is required"
                        return@Button
                    }

                    errorMessage = ""
                    isSaving = true

                    scope.launch {
                        try {
                            val phone = dataStoreManager.phoneNumber.firstOrNull()
                            if (phone.isNullOrEmpty()) {
                                errorMessage = "Session expired. Please login again."
                                isSaving = false
                                return@launch
                            }

                            // ✅ INSERT new user row into Supabase
                            SupabaseClient.client
                                .postgrest["users"]
                                .insert(
                                    NewUserRow(
                                        phone_number = phone,
                                        first_name = firstName.trim(),
                                        last_name = lastName.trim()
                                    )
                                )

                            // ✅ Mark profile as completed and go home
                            dataStoreManager.setProfileCompleted()

                            val subRow = try {
                                SupabaseClient.client
                                    .postgrest["users"]
                                    .select { filter { eq("phone_number", phone) } }
                                    .decodeList<SubscriptionCheckRow>()
                                    .firstOrNull()
                            } catch (e: Exception) {
                                null
                            }
                            val hasPremium = SubscriptionEntitlement.hasPremiumAccess(
                                subscriptionStatus = subRow?.subscription_status,
                                trialEndIso = subRow?.trial_end,
                            )

                            EntitlementDebugLog.navigation(
                                source = "ProfileSetupScreen",
                                destination = if (hasPremium) "home" else "subscription",
                                subscriptionStatus = subRow?.subscription_status,
                                trialEnd = subRow?.trial_end,
                                hasPremiumAccess = hasPremium,
                            )

                            if (hasPremium) {
                                navController.navigate("home") {
                                    popUpTo("profile_setup") { inclusive = true }
                                }
                            } else {
                                navController.navigate("subscription") {
                                    popUpTo("profile_setup") { inclusive = true }
                                }
                            }

                        } catch (e: Exception) {
                            Log.e("PROFILE_SETUP_ERROR", e.message ?: "Unknown")
                            errorMessage = "Failed to save profile. Try again."
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = goldColor)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "Save & Continue",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Skip option (optional — remove if you want to force setup)
            TextButton(onClick = {
                AnalyticsManager.trackEvent("Profile Setup - Skip Tapped")
                scope.launch {
                    val phone = dataStoreManager.phoneNumber.firstOrNull()
                    if (!phone.isNullOrEmpty()) {
                        // Create a minimal row so subscription webhooks can update this user by phone.
                        val safeFirst = firstName.trim().ifEmpty { "User" }
                        val safeLast = lastName.trim().ifEmpty { "User" }
                        try {
                            SupabaseClient.client
                                .postgrest["users"]
                                .insert(
                                    NewUserRow(
                                        phone_number = phone,
                                        first_name = safeFirst,
                                        last_name = safeLast
                                    )
                                )
                        } catch (_: Exception) {
                            // Ignore insert failures (e.g., duplicate row).
                        }
                    }
                    dataStoreManager.setProfileCompleted()
                    navController.navigate("subscription") {
                        popUpTo("profile_setup") { inclusive = true }
                    }
                }
            }) {
                Text(
                    "Skip for now",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun SetupField(
    label: String,
    value: String,
    isNumber: Boolean = false,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.White.copy(alpha = 0.65f)) },
        singleLine = true,
        keyboardOptions = if (isNumber)
            KeyboardOptions(keyboardType = KeyboardType.Number)
        else KeyboardOptions.Default,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Color(0xFFD4AF37),
            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
            cursorColor = Color(0xFFD4AF37)
        ),
        modifier = Modifier.fillMaxWidth()
    )
}