package com.squarenova.emaanwallpapers.ui.profile

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.squarenova.emaanwallpapers.analytics.AnalyticsManager
import com.squarenova.emaanwallpapers.ui.components.smoothClickable
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.data.model.User
import com.squarenova.emaanwallpapers.network.SupabaseClient
import com.squarenova.emaanwallpapers.network.SubscriptionApi
import com.squarenova.emaanwallpapers.theme.AppBackground
import com.squarenova.emaanwallpapers.theme.AppDivider
import com.squarenova.emaanwallpapers.theme.AppSurface
import com.squarenova.emaanwallpapers.theme.AppTextPrimary
import com.squarenova.emaanwallpapers.theme.AppTextSecondary
import com.squarenova.emaanwallpapers.theme.AppTextTertiary
import com.squarenova.emaanwallpapers.theme.BrandGreen
import com.squarenova.emaanwallpapers.theme.BrandGreenDark
import com.squarenova.emaanwallpapers.ui.subscription.PremiumBadge
import com.squarenova.emaanwallpapers.ui.subscription.PremiumSubscriptionColors
import com.squarenova.emaanwallpapers.ui.subscription.TrialCountdown
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class UserRow(
    val id: String? = null,
    val created_at: String? = null,
    val phone_number: String,
    val first_name: String? = null,
    val last_name: String? = null,
    val avatar_url: String? = null,
    val is_subscribed: Boolean? = null,
    val razorpay_subscription_id: String? = null,
    val subscription_status: String? = null,
    val trial_end: String? = null
)

@Serializable
data class UserUpdateRow(
    val first_name: String? = null,
    val last_name: String? = null,
    val avatar_url: String? = null  // ✅ persisted avatar
)

@Serializable
data class SubscriptionStatusPatch(
    val subscription_status: String
)

@Composable
fun ProfileScreen(navController: NavController) {

    val context = LocalContext.current
    val dataStoreManager = DataStoreManager(context)
    val scope = rememberCoroutineScope()

    var user by remember { mutableStateOf<User?>(null) }
    var avatarUrl by remember { mutableStateOf<String?>(null) }  // ✅ loaded from Supabase
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSubscribed by remember { mutableStateOf(false) }
    var razorpaySubscriptionId by remember { mutableStateOf<String?>(null) }
    var subscriptionStatus by remember { mutableStateOf<String?>(null) }
    var trialEnd by remember { mutableStateOf<String?>(null) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var isCancelling by remember { mutableStateOf(false) }

    suspend fun refreshSubscriptionState() {
        val phone = dataStoreManager.phoneNumber.firstOrNull()
        if (phone.isNullOrEmpty()) return

        try {
            val result = SupabaseClient.client
                .postgrest["users"]
                .select {
                    filter { eq("phone_number", phone) }
                }
                .decodeSingle<UserRow>()

            isSubscribed = result.is_subscribed == true
            razorpaySubscriptionId = result.razorpay_subscription_id
            subscriptionStatus = result.subscription_status
            trialEnd = result.trial_end
        } catch (e: Exception) {
            Log.e("PROFILE_SUBSCRIPTION_REFRESH", e.message ?: "Unknown")
        }
    }

    // ✅ Gallery picker — uploads immediately on pick
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            isUploadingAvatar = true
            scope.launch {
                try {
                    val phone = dataStoreManager.phoneNumber.firstOrNull()
                    if (phone.isNullOrEmpty()) return@launch

                    // ✅ Read bytes from URI
                    val inputStream = context.contentResolver.openInputStream(selectedUri)
                    val bytes = inputStream?.readBytes() ?: return@launch
                    inputStream.close()

                    // ✅ Upload to Supabase Storage avatars bucket
                    // Use a unique filename per upload to avoid image caching issues
                    val fileName = "avatar_${phone}_${System.currentTimeMillis()}.jpg"
                    SupabaseClient.client.storage
                        .from("avatars")
                        .upload(fileName, bytes, upsert = true)

                    // ✅ Get public URL
                    val publicUrl = SupabaseClient.client.storage
                        .from("avatars")
                        .publicUrl(fileName)

                    // ✅ Save URL to users table
                    SupabaseClient.client
                        .postgrest["users"]
                        .update(UserUpdateRow(avatar_url = publicUrl)) {
                            filter { eq("phone_number", phone) }
                        }

                    // ✅ Verify DB update (so Home screen reflects the change)
                    val updated = SupabaseClient.client
                        .postgrest["users"]
                        .select {
                            filter { eq("phone_number", phone) }
                        }
                        .decodeSingle<UserRow>()

                    Log.d("AVATAR_DB_VERIFY", "uploaded=$publicUrl db=${updated.avatar_url}")

                    avatarUrl = publicUrl
                    saveMessage = if (updated.avatar_url == publicUrl) {
                        "Profile picture updated ✅"
                    } else {
                        "Uploaded image, but DB avatar_url did not update ❌"
                    }

                } catch (e: Exception) {
                    Log.e("AVATAR_UPLOAD_ERROR", e.message ?: "Unknown")
                    saveMessage = "Failed to upload picture ❌"
                } finally {
                    isUploadingAvatar = false
                }
            }
        }
    }

    // 🔥 FETCH USER FROM SUPABASE
    LaunchedEffect(Unit) {
        try {
            val phone = dataStoreManager.phoneNumber.firstOrNull()
            if (!phone.isNullOrEmpty()) {
                val result = SupabaseClient.client
                    .postgrest["users"]
                    .select {
                        filter { eq("phone_number", phone) }
                    }
                    .decodeSingle<UserRow>()

                user = User(
                    phone_number = result.phone_number,
                    first_name = result.first_name ?: "",
                    last_name = result.last_name ?: ""
                )

                // ✅ Load saved avatar URL
                avatarUrl = result.avatar_url
                isSubscribed = result.is_subscribed == true
                razorpaySubscriptionId = result.razorpay_subscription_id
                subscriptionStatus = result.subscription_status
                trialEnd = result.trial_end
            }
        } catch (e: Exception) {
            Log.e("PROFILE_ERROR", e.message ?: "Unknown error")
        } finally {
            isLoading = false
        }
    }

    saveMessage?.let {
        LaunchedEffect(it) {
            delay(2500)
            saveMessage = null
        }
    }

    if (showEditDialog) {
        EditProfileDialog(
            user = user,
            isSaving = isSaving,
            onDismiss = {
                AnalyticsManager.trackEvent("Profile - Edit Dialog Cancel Tapped")
                showEditDialog = false
            },
            onSave = { updatedUser ->
                isSaving = true
                scope.launch {
                    try {
                        val phone = dataStoreManager.phoneNumber.firstOrNull()
                        if (!phone.isNullOrEmpty()) {
                            SupabaseClient.client
                                .postgrest["users"]
                                .update(
                                    UserUpdateRow(
                                        first_name = updatedUser.first_name,
                                        last_name = updatedUser.last_name
                                    )
                                ) {
                                    filter { eq("phone_number", phone) }
                                }

                            user = updatedUser
                            isSaving = false
                            showEditDialog = false
                            saveMessage = "Profile updated successfully ✅"
                        } else {
                            isSaving = false
                            saveMessage = "Phone number not found ❌"
                        }
                    } catch (e: Exception) {
                        isSaving = false
                        saveMessage = "Failed: ${e.message} ❌"
                        Log.e("PROFILE_SAVE_ERROR", e.message ?: "Unknown")
                    }
                }
            }
        )
    }

    if (showCancelDialog) {
        CancelSubscriptionDialog(
            isCancelling = isCancelling,
            isTrial = subscriptionStatus?.equals("trial", ignoreCase = true) == true,
            onDismiss = { showCancelDialog = false },
            onConfirm = {
                scope.launch {
                    isCancelling = true
                    try {
                        val phone = dataStoreManager.phoneNumber.firstOrNull()
                        if (phone.isNullOrBlank()) {
                            saveMessage = "Cancel failed ❌"
                            return@launch
                        }
                        val row = try {
                            SupabaseClient.client
                                .postgrest["users"]
                                .select { filter { eq("phone_number", phone) } }
                                .decodeSingle<UserRow>()
                        } catch (_: Exception) {
                            null
                        }
                        if (row == null) {
                            saveMessage = "Cancel failed ❌"
                            return@launch
                        }

                        if (row.subscription_status?.equals("cancel_requested", ignoreCase = true) == true) {
                            saveMessage = "Already cancelled. No further charges will be made ✅"
                            showCancelDialog = false
                            return@launch
                        }

                        if (row.subscription_status?.equals("trial", ignoreCase = true) == true) {
                            SupabaseClient.client
                                .postgrest["users"]
                                .update(SubscriptionStatusPatch(subscription_status = "cancel_requested")) {
                                    filter { eq("phone_number", phone) }
                                }
                            AnalyticsManager.track("subscription_cancel_requested")
                            saveMessage = "Trial cancelled. You will not be charged ₹99"
                            showCancelDialog = false
                            refreshSubscriptionState()
                            return@launch
                        }

                        val subId = row.razorpay_subscription_id
                        if (subId.isNullOrBlank()) {
                            saveMessage = "Cancel failed: no subscription on file ❌"
                            Log.e("PROFILE_CANCEL", "razorpay_subscription_id missing for phone=$phone")
                            return@launch
                        }

                        val result = SubscriptionApi.cancelSubscription(subId)
                        if (result.isSuccess) {
                            AnalyticsManager.track("subscription_cancel_requested")
                            saveMessage = "Cancellation requested"
                            showCancelDialog = false
                            refreshSubscriptionState()
                        } else {
                            val reason = result.exceptionOrNull()?.message?.take(180) ?: "unknown"
                            Log.e("PROFILE_CANCEL", "API error: $reason")
                            saveMessage = "Cancel failed: $reason ❌"
                        }
                    } catch (e: Exception) {
                        Log.e("PROFILE_CANCEL", e.message ?: "cancel", e)
                        saveMessage = "Cancel failed: ${e.message?.take(120) ?: "unknown"} ❌"
                    } finally {
                        isCancelling = false
                    }
                }
            }
        )
    }

    val appBg = AppBackground
    val surface = AppSurface
    val primary = AppTextPrimary
    val secondary = AppTextSecondary
    val divider = AppDivider
    val brand = BrandGreen

    val avatarInitial = user?.first_name
        ?.firstOrNull()
        ?.uppercaseChar()
        ?.toString()
        ?: "?"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                color = surface,
                tonalElevation = 0.dp,
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(bottom = 20.dp)
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .smoothClickable {
                                    AnalyticsManager.trackEvent("Profile - Back Tapped")
                                    navController.popBackStack()
                                }
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(surface)
                                .border(1.dp, divider, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = BrandGreenDark,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Text(
                            text = "My Profile",
                            color = primary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(modifier = Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(112.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, brand, CircleShape)
                                    .smoothClickable {
                                        AnalyticsManager.trackEvent("Profile - Avatar Tapped")
                                        galleryLauncher.launch("image/*")
                                    }
                            ) {
                                when {
                                    isUploadingAvatar -> {
                                        Surface(
                                            shape = CircleShape,
                                            color = brand.copy(alpha = 0.12f),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(
                                                    color = brand,
                                                    modifier = Modifier.size(32.dp),
                                                    strokeWidth = 3.dp
                                                )
                                            }
                                        }
                                    }
                                    else -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                                .background(Color(0xFFF0F4F2)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!avatarUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = avatarUrl,
                                                    contentDescription = "Profile Picture",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(CircleShape)
                                                )
                                            } else if (isLoading) {
                                                CircularProgressIndicator(
                                                    color = brand,
                                                    modifier = Modifier.size(28.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Text(
                                                    text = avatarInitial,
                                                    fontSize = 40.sp,
                                                    color = primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .align(Alignment.BottomEnd)
                                    .shadow(3.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(brand)
                                    .border(2.dp, surface, CircleShape)
                                    .smoothClickable {
                                        AnalyticsManager.trackEvent("Profile - Camera Badge Tapped")
                                        galleryLauncher.launch("image/*")
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("📷", fontSize = 12.sp, color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (isLoading) "Loading..."
                            else "${user?.first_name ?: ""} ${user?.last_name ?: ""}".trim()
                                .ifEmpty { "No Name" },
                            color = primary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val sub = subscriptionStatus?.lowercase()
                        val showSubscriptionCard = when {
                            sub == "cancelled" -> false
                            isSubscribed -> true
                            sub == "trial" || sub == "cancel_requested" -> true
                            else -> false
                        }
                        if (showSubscriptionCard) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = PremiumSubscriptionColors.SurfaceElevated,
                                border = BorderStroke(
                                    1.dp,
                                    PremiumSubscriptionColors.Gold.copy(alpha = 0.35f)
                                ),
                                shadowElevation = 8.dp
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 18.dp, vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "MEMBERSHIP",
                                        color = PremiumSubscriptionColors.TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.8.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val statusLabel = when (sub) {
                                        "trial" -> "Trial"
                                        "cancel_requested" -> "Cancel requested"
                                        else -> if (isSubscribed) "Active" else ""
                                    }
                                    if (statusLabel.isNotEmpty()) {
                                        Text(
                                            text = "Status · $statusLabel",
                                            color = PremiumSubscriptionColors.TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                    }
                                    PremiumBadge(
                                        isSubscribed = isSubscribed,
                                        subscriptionStatus = subscriptionStatus,
                                        compact = true
                                    )
                                    if (subscriptionStatus?.equals("trial", ignoreCase = true) == true) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        TrialCountdown(trialEndIso = trialEnd, compact = true)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = appBg,
                            border = BorderStroke(1.dp, divider.copy(alpha = 0.7f))
                        ) {
                            Text(
                                text = user?.phone_number?.ifEmpty { "Member of Emaan Wallpapers" }
                                    ?: "Member of Emaan Wallpapers",
                                color = secondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Account",
                color = secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileItem(
                    icon = "✏️",
                    title = "Edit Profile",
                    subtitle = "Update your name details",
                    containerColor = surface,
                    titleColor = primary
                ) {
                    AnalyticsManager.trackEvent("Profile - Edit Profile Tapped")
                    showEditDialog = true
                }
                ProfileItem(
                    icon = "👑",
                    title = run {
                        val s = subscriptionStatus?.lowercase()
                        val canCancel =
                            (isSubscribed || s == "trial") && s != "cancel_requested"
                        when {
                            canCancel -> "Cancel Subscription"
                            s == "cancel_requested" -> "Subscription"
                            else -> "Upgrade to Premium"
                        }
                    },
                    subtitle = run {
                        val s = subscriptionStatus?.lowercase()
                        when (s) {
                            "cancel_requested" ->
                                "Cancellation recorded — access until period ends"
                            "cancelled" -> "Your plan will end after the current billing period"
                            "trial" ->
                                "You can cancel before the trial ends — no ₹99 charge if you cancel in time"
                            else -> if (isSubscribed) {
                                "Stop future premium payments"
                            } else {
                                "Unlock exclusive wallpapers"
                            }
                        }
                    },
                    containerColor = surface,
                    titleColor = primary,
                    enabled = run {
                        val s = subscriptionStatus?.lowercase()
                        val canCancel =
                            (isSubscribed || s == "trial") && s != "cancel_requested"
                        when (s) {
                            "cancel_requested" -> false
                            else -> canCancel || (!isSubscribed && s != "trial")
                        }
                    }
                ) {
                    val s = subscriptionStatus?.lowercase()
                    val canCancel =
                        (isSubscribed || s == "trial") && s != "cancel_requested"
                    AnalyticsManager.trackEvent(
                        if (canCancel) "Profile - Cancel Subscription Tapped"
                        else "Profile - Upgrade Premium Tapped"
                    )
                    if (canCancel) {
                        showCancelDialog = true
                    } else if (!isSubscribed && s != "trial") {
                        navController.navigate("subscription") {
                            launchSingleTop = true
                        }
                    }
                }
                ProfileItem(
                    icon = "📤",
                    title = "Share App",
                    subtitle = "Invite friends to Emaan Wallpapers",
                    containerColor = surface,
                    titleColor = primary
                ) {
                    AnalyticsManager.trackEvent("Profile - Share App Tapped")
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Danger Zone",
                    color = secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                ProfileItem(
                    icon = "🚪",
                    title = "Logout",
                    subtitle = "Sign out of your account",
                    containerColor = Color(0xFFFFEBEE),
                    titleColor = Color(0xFFB71C1C)
                ) {
                    AnalyticsManager.trackEvent("Profile - Logout Tapped")
                    scope.launch {
                        AnalyticsManager.reset()
                        dataStoreManager.logout()
                        navController.navigate("login") { popUpTo("home") { inclusive = true } }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "Emaan Wallpapers v1.0",
                color = AppTextTertiary.copy(alpha = 0.65f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Snackbar
        saveMessage?.let { msg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = if (msg.contains("✅")) BrandGreenDark else Color(0xFFB00020),
                shape = RoundedCornerShape(14.dp)
            ) { Text(msg, color = Color.White, fontWeight = FontWeight.Medium) }
        }
    }
}


@Composable
fun EditProfileDialog(user: User?, isSaving: Boolean, onDismiss: () -> Unit, onSave: (User) -> Unit) {
    var firstName by remember { mutableStateOf(user?.first_name ?: "") }
    var lastName by remember { mutableStateOf(user?.last_name ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = AppSurface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Edit Profile",
                    color = AppTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider(color = AppDivider)
                EditField("First Name *", firstName) { firstName = it }
                EditField("Last Name *", lastName) { lastName = it }

                error?.let { msg ->
                    Text(
                        text = msg,
                        color = Color(0xFFB3261E),
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            AnalyticsManager.trackEvent("Profile - Edit Dialog Cancel Tapped")
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTextPrimary),
                        border = BorderStroke(1.dp, AppDivider)
                    ) { Text("Cancel") }

                    Button(
                        onClick = {
                            if (firstName.trim().isEmpty()) {
                                error = "First name is required"
                                return@Button
                            }
                            if (lastName.trim().isEmpty()) {
                                error = "Last name is required"
                                return@Button
                            }
                            AnalyticsManager.trackEvent("Profile - Edit Dialog Save Tapped")
                            onSave(
                                User(
                                    phone_number = user?.phone_number ?: "",
                                    first_name = firstName.trim(),
                                    last_name = lastName.trim()
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditField(label: String, value: String, isNumber: Boolean = false, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = AppTextSecondary) },
        singleLine = true,
        keyboardOptions = if (isNumber) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AppTextPrimary,
            unfocusedTextColor = AppTextPrimary,
            focusedBorderColor = BrandGreen,
            unfocusedBorderColor = AppDivider,
            cursorColor = BrandGreen
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun ProfileItem(
    icon: String,
    title: String,
    subtitle: String = "",
    containerColor: Color = AppSurface,
    titleColor: Color = AppTextPrimary,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Card(
        onClick = { onClick?.invoke() },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF1F5F9)),
                    contentAlignment = Alignment.Center
                ) { Text(icon, fontSize = 20.sp) }
                Column {
                    Text(
                        title,
                        color = titleColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
                            color = AppTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Text(
                "›",
                color = AppTextTertiary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Light
            )
        }
    }
}

@Composable
fun CancelSubscriptionDialog(
    isCancelling: Boolean,
    isTrial: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = AppSurface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Cancel Premium Subscription",
                    color = AppTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider(color = AppDivider)
                Text(
                    if (isTrial) {
                        "Your subscription will not be renewed after trial."
                    } else {
                        "We will cancel your premium at the end of the current billing cycle."
                    },
                    color = AppTextSecondary,
                    fontSize = 13.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppTextPrimary),
                        border = BorderStroke(1.dp, AppDivider)
                    ) { Text("No") }

                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        enabled = !isCancelling,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                    ) {
                        if (isCancelling) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Yes, cancel", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}