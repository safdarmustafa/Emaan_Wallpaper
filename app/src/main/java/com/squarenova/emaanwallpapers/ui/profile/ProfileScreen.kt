package com.squarenova.emaanwallpapers.ui.profile

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.squarenova.emaanwallpapers.BuildConfig
import com.squarenova.emaanwallpapers.analytics.AnalyticsManager
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.data.SubscriptionEntitlement
import com.squarenova.emaanwallpapers.data.model.User
import com.squarenova.emaanwallpapers.network.AccountDeletionApi
import com.squarenova.emaanwallpapers.subscription.SubscriptionOrchestrator
import com.squarenova.emaanwallpapers.network.OtpApi
import com.squarenova.emaanwallpapers.network.SubscriptionApi
import com.squarenova.emaanwallpapers.network.SupabaseClient
import com.squarenova.emaanwallpapers.ui.legal.LegalUrlOpener
import com.squarenova.emaanwallpapers.ui.legal.LegalUrls
import com.squarenova.emaanwallpapers.theme.AppDivider
import com.squarenova.emaanwallpapers.theme.AppSurface
import com.squarenova.emaanwallpapers.theme.AppTextPrimary
import com.squarenova.emaanwallpapers.theme.AppTextSecondary
import com.squarenova.emaanwallpapers.theme.BackgroundCream
import com.squarenova.emaanwallpapers.theme.BrandGreen
import com.squarenova.emaanwallpapers.theme.ProfileDanger
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
    val trial_end: String? = null,
)

@Serializable
data class UserUpdateRow(
    val first_name: String? = null,
    val last_name: String? = null,
    val avatar_url: String? = null,
)

@Serializable
data class SubscriptionStatusPatch(
    val subscription_status: String,
)

private data class ProfileMessage(val text: String, val isSuccess: Boolean)

@Composable
fun ProfileScreen(navController: NavController) {
    val context = LocalContext.current
    val dataStoreManager = DataStoreManager(context)
    val scope = rememberCoroutineScope()

    var user by remember { mutableStateOf<User?>(null) }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var profileMessage by remember { mutableStateOf<ProfileMessage?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var subscriptionStatus by remember { mutableStateOf<String?>(null) }
    var trialEnd by remember { mutableStateOf<String?>(null) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var deleteOtp by remember { mutableStateOf("") }
    var isSendingDeleteOtp by remember { mutableStateOf(false) }
    var deleteOtpStatusMessage by remember { mutableStateOf<String?>(null) }
    var deleteOtpSentSuccessfully by remember { mutableStateOf(false) }
    var deleteStatusIsError by remember { mutableStateOf(false) }
    var isCancelling by remember { mutableStateOf(false) }

    val hasPremium = SubscriptionEntitlement.hasPremiumAccess(subscriptionStatus, trialEnd)

    suspend fun refreshSubscriptionState() {
        val phone = dataStoreManager.phoneNumber.firstOrNull() ?: return
        try {
            val result = SupabaseClient.client
                .postgrest["users"]
                .select { filter { eq("phone_number", phone) } }
                .decodeSingle<UserRow>()
            subscriptionStatus = result.subscription_status
            trialEnd = result.trial_end
        } catch (e: Exception) {
            Log.e("PROFILE_SUBSCRIPTION_REFRESH", e.message ?: "Unknown")
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            isUploadingAvatar = true
            scope.launch {
                try {
                    val phone = dataStoreManager.phoneNumber.firstOrNull() ?: return@launch
                    val bytes = context.contentResolver.openInputStream(selectedUri)?.use { it.readBytes() }
                        ?: return@launch
                    val fileName = "avatar_${phone}_${System.currentTimeMillis()}.jpg"
                    SupabaseClient.client.storage.from("avatars").upload(fileName, bytes, upsert = true)
                    val publicUrl = SupabaseClient.client.storage.from("avatars").publicUrl(fileName)
                    SupabaseClient.client
                        .postgrest["users"]
                        .update(UserUpdateRow(avatar_url = publicUrl)) {
                            filter { eq("phone_number", phone) }
                        }
                    avatarUrl = publicUrl
                    profileMessage = ProfileMessage("Profile picture updated", isSuccess = true)
                } catch (e: Exception) {
                    Log.e("AVATAR_UPLOAD_ERROR", e.message ?: "Unknown")
                    profileMessage = ProfileMessage("Failed to upload picture", isSuccess = false)
                } finally {
                    isUploadingAvatar = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            val phone = dataStoreManager.phoneNumber.firstOrNull()
            if (!phone.isNullOrEmpty()) {
                val result = SupabaseClient.client
                    .postgrest["users"]
                    .select { filter { eq("phone_number", phone) } }
                    .decodeSingle<UserRow>()
                user = User(
                    phone_number = result.phone_number,
                    first_name = result.first_name ?: "",
                    last_name = result.last_name ?: "",
                )
                avatarUrl = result.avatar_url
                subscriptionStatus = result.subscription_status
                trialEnd = result.trial_end
            }
        } catch (e: Exception) {
            Log.e("PROFILE_ERROR", e.message ?: "Unknown error")
        } finally {
            isLoading = false
        }
    }

    profileMessage?.let { msg ->
        LaunchedEffect(msg) {
            delay(2800)
            profileMessage = null
        }
    }

    val fullName = "${user?.first_name ?: ""} ${user?.last_name ?: ""}".trim()
    val phone = user?.phone_number.orEmpty()
    val avatarInitial = user?.first_name?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    val statusLower = subscriptionStatus?.lowercase()
    val canCancelSubscription = hasPremium && statusLower != "cancel_requested"

    fun showComingSoon(feature: String) {
        AnalyticsManager.trackEvent("Profile - $feature Tapped")
        profileMessage = ProfileMessage("$feature coming soon", isSuccess = true)
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
                        val userPhone = dataStoreManager.phoneNumber.firstOrNull()
                        if (!userPhone.isNullOrEmpty()) {
                            SupabaseClient.client
                                .postgrest["users"]
                                .update(
                                    UserUpdateRow(
                                        first_name = updatedUser.first_name,
                                        last_name = updatedUser.last_name,
                                    ),
                                ) { filter { eq("phone_number", userPhone) } }
                            user = updatedUser
                            showEditDialog = false
                            profileMessage = ProfileMessage("Profile updated successfully", isSuccess = true)
                        } else {
                            profileMessage = ProfileMessage("Phone number not found", isSuccess = false)
                        }
                    } catch (e: Exception) {
                        profileMessage = ProfileMessage("Failed to save profile", isSuccess = false)
                        Log.e("PROFILE_SAVE_ERROR", e.message ?: "Unknown")
                    } finally {
                        isSaving = false
                    }
                }
            },
        )
    }

    if (showAboutDialog) {
        AboutAppDialog(
            onDismiss = {
                AnalyticsManager.trackEvent("Profile - About Dialog Dismissed")
                showAboutDialog = false
            },
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
                        val userPhone = dataStoreManager.phoneNumber.firstOrNull()
                        if (userPhone.isNullOrBlank()) {
                            profileMessage = ProfileMessage("Cancel failed", isSuccess = false)
                            return@launch
                        }
                        val row = try {
                            SupabaseClient.client
                                .postgrest["users"]
                                .select { filter { eq("phone_number", userPhone) } }
                                .decodeSingle<UserRow>()
                        } catch (_: Exception) {
                            null
                        }
                        if (row == null) {
                            profileMessage = ProfileMessage("Cancel failed", isSuccess = false)
                            return@launch
                        }
                        if (row.subscription_status?.equals("cancel_requested", ignoreCase = true) == true) {
                            profileMessage = ProfileMessage(
                                "Already cancelled. No further charges will be made.",
                                isSuccess = true,
                            )
                            showCancelDialog = false
                            return@launch
                        }
                        val isTrialCancel =
                            row.subscription_status?.equals("trial", ignoreCase = true) == true
                        val subId = row.razorpay_subscription_id
                        if (subId.isNullOrBlank()) {
                            profileMessage = ProfileMessage("No subscription on file", isSuccess = false)
                            return@launch
                        }
                        // Single cancellation path: trial AND paid both go through the edge function
                        // (Razorpay + DB). The client never writes subscription_status directly.
                        val result = SubscriptionApi.cancelSubscription(subId)
                        if (result.isSuccess) {
                            AnalyticsManager.track("subscription_cancel_requested")
                            profileMessage = ProfileMessage(
                                if (isTrialCancel) {
                                    "Trial cancelled. You will not be charged ₹99"
                                } else {
                                    "Cancellation requested"
                                },
                                isSuccess = true,
                            )
                            showCancelDialog = false
                            refreshSubscriptionState()
                        } else {
                            profileMessage = ProfileMessage(
                                "Could not cancel subscription. Please try again.",
                                isSuccess = false,
                            )
                        }
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG) Log.e("PROFILE_CANCEL", "cancel failed", e)
                        profileMessage = ProfileMessage(
                            "Could not cancel subscription. Please try again.",
                            isSuccess = false,
                        )
                    } finally {
                        isCancelling = false
                    }
                }
            },
        )
    }

    suspend fun requestDeleteAccountOtp(): Result<Unit> {
        val phone = OtpApi.normalizePhone(dataStoreManager.phoneNumber.firstOrNull().orEmpty())
        if (phone.length != 10) {
            return Result.failure(Exception("Phone number not available"))
        }
        return OtpApi.sendOtp(phone, caller = "delete_account")
    }

    LaunchedEffect(showDeleteDialog) {
        if (!showDeleteDialog) {
            deleteOtpStatusMessage = null
            deleteOtpSentSuccessfully = false
            deleteStatusIsError = false
            return@LaunchedEffect
        }
        deleteOtp = ""
        deleteOtpStatusMessage = null
        deleteOtpSentSuccessfully = false
        deleteStatusIsError = false
        isSendingDeleteOtp = true
        val result = requestDeleteAccountOtp()
        isSendingDeleteOtp = false
        if (result.isSuccess) {
            deleteOtpStatusMessage = "OTP sent to +91 ${OtpApi.normalizePhone(dataStoreManager.phoneNumber.firstOrNull().orEmpty())}"
            deleteOtpSentSuccessfully = true
            deleteStatusIsError = false
        } else {
            deleteOtpStatusMessage = result.exceptionOrNull()?.message ?: "Failed to send OTP"
            deleteOtpSentSuccessfully = false
            deleteStatusIsError = true
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeletingAccount) {
                    showDeleteDialog = false
                    deleteOtp = ""
                }
            },
            title = { Text("Delete Account", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "This permanently deletes your profile, subscription data, and preferences. " +
                            "We sent a confirmation OTP to your registered number.",
                        color = AppTextSecondary,
                    )
                    if (isSendingDeleteOtp) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Text("Sending OTP…", color = AppTextSecondary, fontSize = 13.sp)
                        }
                    }
                    deleteOtpStatusMessage?.let { msg ->
                        Text(
                            text = msg,
                            color = if (deleteStatusIsError) ProfileDanger else BrandGreen,
                            fontSize = 13.sp,
                        )
                    }
                    OutlinedTextField(
                        value = deleteOtp,
                        onValueChange = { deleteOtp = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("Confirmation OTP") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = {
                            scope.launch {
                                isSendingDeleteOtp = true
                                deleteOtpStatusMessage = null
                                val result = requestDeleteAccountOtp()
                                isSendingDeleteOtp = false
                                if (result.isSuccess) {
                                    val phone = OtpApi.normalizePhone(
                                        dataStoreManager.phoneNumber.firstOrNull().orEmpty(),
                                    )
                                    deleteOtpStatusMessage = "OTP sent to +91 $phone"
                                    deleteOtpSentSuccessfully = true
                                    deleteStatusIsError = false
                                } else {
                                    deleteOtpStatusMessage = result.exceptionOrNull()?.message
                                        ?: "Failed to send OTP"
                                    deleteOtpSentSuccessfully = false
                                    deleteStatusIsError = true
                                }
                            }
                        },
                        enabled = !isSendingDeleteOtp && !isDeletingAccount,
                    ) {
                        Text(
                            if (isSendingDeleteOtp) "Sending…" else "Resend OTP",
                            color = BrandGreen,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!deleteOtpSentSuccessfully) {
                            deleteOtpStatusMessage = "Wait for OTP to be sent, or tap Resend OTP"
                            deleteStatusIsError = true
                            return@TextButton
                        }
                        if (deleteOtp.length != 6) {
                            deleteOtpStatusMessage = "Enter the 6-digit OTP"
                            deleteStatusIsError = true
                            return@TextButton
                        }
                        scope.launch {
                            isDeletingAccount = true
                            try {
                                val phone = OtpApi.normalizePhone(
                                    dataStoreManager.phoneNumber.firstOrNull().orEmpty(),
                                )
                                if (phone.length != 10) {
                                    deleteOtpStatusMessage = "Not logged in"
                                    deleteStatusIsError = true
                                    return@launch
                                }
                                val result = AccountDeletionApi.deleteUserData(phone, deleteOtp)
                                if (result.isSuccess) {
                                    AnalyticsManager.reset()
                                    SubscriptionOrchestrator.onLogout()
                                    dataStoreManager.logout()
                                    showDeleteDialog = false
                                    deleteOtp = ""
                                    navController.navigate("login") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                } else {
                                    deleteOtpStatusMessage = result.exceptionOrNull()?.message
                                        ?: "Could not delete account. Try again or email support."
                                    deleteStatusIsError = true
                                }
                            } catch (e: Exception) {
                                if (BuildConfig.DEBUG) Log.e("PROFILE_DELETE", "delete failed", e)
                                deleteOtpStatusMessage = "Could not delete account. Try again or email support."
                                deleteStatusIsError = true
                            } finally {
                                isDeletingAccount = false
                            }
                        }
                    },
                    enabled = deleteOtpSentSuccessfully && !isDeletingAccount && !isSendingDeleteOtp,
                ) {
                    if (isDeletingAccount) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Delete", color = ProfileDanger)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        deleteOtp = ""
                    },
                    enabled = !isDeletingAccount,
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundCream),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            ProfileTopBar(
                onBack = {
                    AnalyticsManager.trackEvent("Profile - Back Tapped")
                    navController.popBackStack()
                },
                modifier = Modifier.statusBarsPadding(),
            )

            ProfileHeaderSection(
                fullName = fullName,
                phoneNumber = phone,
                avatarUrl = avatarUrl,
                avatarInitial = avatarInitial,
                isLoading = isLoading,
                isUploadingAvatar = isUploadingAvatar,
                subscriptionStatus = subscriptionStatus,
                trialEndIso = trialEnd,
                onEditProfile = {
                    AnalyticsManager.trackEvent("Profile - Edit Profile Tapped")
                    showEditDialog = true
                },
                onAvatarClick = {
                    AnalyticsManager.trackEvent("Profile - Avatar Tapped")
                    galleryLauncher.launch("image/*")
                },
            )

            Spacer(modifier = Modifier.height(ProfileSectionSpacing))

            ProfileSubscriptionCard(
                subscriptionStatus = subscriptionStatus,
                trialEndIso = trialEnd,
                onManageSubscription = {
                    AnalyticsManager.trackEvent("Profile - Manage Subscription Tapped")
                    when {
                        statusLower == "cancel_requested" -> profileMessage = ProfileMessage(
                            "Cancellation already recorded",
                            isSuccess = true,
                        )
                        canCancelSubscription -> showCancelDialog = true
                        else -> navController.navigate("subscription") { launchSingleTop = true }
                    }
                },
                modifier = Modifier.padding(horizontal = ProfileHorizontalPadding),
            )

            Spacer(modifier = Modifier.height(ProfileSectionSpacing))

            ProfileAccountSection(
                items = listOf(
                    ProfileAccountItem(
                        title = "Edit Profile",
                        subtitle = "Update your name and photo",
                        icon = Icons.Default.Person,
                        onClick = {
                            AnalyticsManager.trackEvent("Profile - Edit Profile Tapped")
                            showEditDialog = true
                        },
                    ),
                    ProfileAccountItem(
                        title = "About App",
                        subtitle = "About Emaan Wallpapers · v${BuildConfig.VERSION_NAME}",
                        icon = Icons.Default.Info,
                        onClick = {
                            AnalyticsManager.trackEvent("Profile - About App Tapped")
                            showAboutDialog = true
                        },
                    ),
                    ProfileAccountItem(
                        title = "Share App",
                        subtitle = "Invite friends to Emaan Wallpapers",
                        icon = Icons.Default.Share,
                        onClick = {
                            AnalyticsManager.trackEvent("Profile - Share App Tapped")
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Discover beautiful Islamic wallpapers with Emaan Wallpapers.",
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Emaan Wallpapers"))
                        },
                    ),
                ),
            )

            Spacer(modifier = Modifier.height(ProfileSectionSpacing))

            ProfileLegalSupportSection(
                items = listOf(
                    ProfileAccountItem(
                        title = "Privacy Policy",
                        subtitle = "squarenovatech.com/privacy-policy",
                        icon = Icons.Default.Security,
                        opensExternal = true,
                        onClick = {
                            AnalyticsManager.trackEvent("Profile - Privacy Policy Tapped")
                            LegalUrlOpener.openPrivacyPolicy(context)
                        },
                    ),
                    ProfileAccountItem(
                        title = "Terms & Conditions",
                        subtitle = "squarenovatech.com/terms-and-conditions",
                        icon = Icons.Default.Gavel,
                        opensExternal = true,
                        onClick = {
                            AnalyticsManager.trackEvent("Profile - Terms Tapped")
                            LegalUrlOpener.openTermsAndConditions(context)
                        },
                    ),
                    ProfileAccountItem(
                        title = "Subscription Disclosure",
                        subtitle = "3-day free trial, ₹99/month auto-renewal, refunds",
                        icon = Icons.Default.WorkspacePremium,
                        opensExternal = true,
                        onClick = {
                            AnalyticsManager.trackEvent("Profile - Subscription Disclosure Tapped")
                            LegalUrlOpener.openSubscriptionDisclosure(context)
                        },
                    ),
                    ProfileAccountItem(
                        title = "Contact Us",
                        subtitle = LegalUrls.SUPPORT_EMAIL,
                        icon = Icons.AutoMirrored.Filled.Help,
                        onClick = {
                            AnalyticsManager.trackEvent("Profile - Contact Us Tapped")
                            navController.navigate("contact_us")
                        },
                    ),
                ),
            )

            Spacer(modifier = Modifier.height(ProfileSectionSpacing))

            ProfilePremiumUpsellCard(
                visible = !hasPremium,
                onUpgrade = {
                    AnalyticsManager.trackEvent("Profile - Upgrade Premium Tapped")
                    navController.navigate("subscription") { launchSingleTop = true }
                },
            )

            Spacer(modifier = Modifier.height(ProfileSectionSpacing))

            ProfileDangerZoneSection(
                onLogout = {
                    AnalyticsManager.trackEvent("Profile - Logout Tapped")
                    scope.launch {
                        AnalyticsManager.reset()
                        SubscriptionOrchestrator.onLogout()
                        dataStoreManager.logout()
                        navController.navigate("login") { popUpTo("home") { inclusive = true } }
                    }
                },
                onDeleteAccount = {
                    AnalyticsManager.trackEvent("Profile - Delete Account Tapped")
                    showDeleteDialog = true
                },
            )

            ProfileFooter(versionLabel = "Emaan Wallpapers v${BuildConfig.VERSION_NAME}")
        }

        profileMessage?.let { msg ->
            ProfileMessageSnackbar(
                message = msg.text,
                isSuccess = msg.isSuccess,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
fun EditProfileDialog(
    user: User?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (User) -> Unit,
) {
    var firstName by remember { mutableStateOf(user?.first_name ?: "") }
    var lastName by remember { mutableStateOf(user?.last_name ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = AppSurface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "Edit Profile",
                    color = AppTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                HorizontalDivider(color = AppDivider)
                EditField("First Name", firstName) { firstName = it }
                EditField("Last Name", lastName) { lastName = it }
                error?.let {
                    Text(it, color = ProfileDanger, fontSize = 12.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, AppDivider),
                    ) { Text("Cancel") }
                    Button(
                        onClick = {
                            when {
                                firstName.trim().isEmpty() -> error = "First name is required"
                                lastName.trim().isEmpty() -> error = "Last name is required"
                                else -> onSave(
                                    User(
                                        phone_number = user?.phone_number ?: "",
                                        first_name = firstName.trim(),
                                        last_name = lastName.trim(),
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Save", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditField(
    label: String,
    value: String,
    isNumber: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = AppTextSecondary) },
        singleLine = true,
        keyboardOptions = if (isNumber) {
            KeyboardOptions(keyboardType = KeyboardType.Number)
        } else {
            KeyboardOptions.Default
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AppTextPrimary,
            unfocusedTextColor = AppTextPrimary,
            focusedBorderColor = BrandGreen,
            unfocusedBorderColor = AppDivider,
            cursorColor = BrandGreen,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun CancelSubscriptionDialog(
    isCancelling: Boolean,
    isTrial: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = AppSurface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "Cancel Subscription",
                    color = AppTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                HorizontalDivider(color = AppDivider)
                Text(
                    if (isTrial) {
                        "Your subscription will not renew after the trial period ends."
                    } else {
                        "Premium access continues until the end of your current billing period."
                    },
                    color = AppTextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, AppDivider),
                    ) { Text("Keep Plan") }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        enabled = !isCancelling,
                        colors = ButtonDefaults.buttonColors(containerColor = ProfileDanger),
                    ) {
                        if (isCancelling) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Confirm", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutAppDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = AppSurface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(scrollState),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = BrandGreen,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = "About Emaan Wallpapers",
                        color = AppTextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                HorizontalDivider(
                    color = AppDivider,
                    modifier = Modifier.padding(vertical = 14.dp),
                )

                Text(
                    text = "Emaan Wallpapers is a premium Islamic wallpaper application designed to bring " +
                        "inspiration, peace, and beauty to your device through carefully curated Islamic " +
                        "wallpapers, calligraphy, architecture, and spiritual artwork.",
                    color = AppTextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Our mission is to help users stay connected with faith by providing meaningful " +
                        "wallpapers that inspire remembrance, reflection, and positivity throughout the day.",
                    color = AppTextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Features include HD wallpapers, premium collections, secure account management, " +
                        "and a seamless experience built for the Muslim community.",
                    color = AppTextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp,
                )

                Spacer(modifier = Modifier.height(18.dp))

                AboutInfoRow(
                    icon = Icons.Default.Info,
                    label = "Version",
                    value = BuildConfig.VERSION_NAME,
                )
                HorizontalDivider(color = AppDivider)
                AboutInfoRow(
                    icon = Icons.Default.Business,
                    label = "Developer",
                    value = "Square Nova TECH",
                )
                HorizontalDivider(color = AppDivider)
                AboutInfoRow(
                    icon = Icons.Default.Language,
                    label = "Website",
                    value = "squarenovatech.com",
                    onClick = { LegalUrlOpener.openWebsite(context) },
                )
                HorizontalDivider(color = AppDivider)
                AboutInfoRow(
                    icon = Icons.Default.Email,
                    label = "Support",
                    value = "support@squarenovatech.com",
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:support@squarenovatech.com")
                            putExtra(Intent.EXTRA_SUBJECT, "Emaan Wallpapers Support")
                        }
                        context.startActivity(Intent.createChooser(intent, "Contact support"))
                    },
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                ) {
                    Text("Close", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun AboutInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 12.dp)

    val content = @Composable {
        Row(
            modifier = rowModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BrandGreen,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = AppTextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    color = AppTextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }

    if (onClick != null) {
        androidx.compose.material3.Surface(
            onClick = onClick,
            color = Color.Transparent,
            content = { content() },
        )
    } else {
        content()
    }
}
