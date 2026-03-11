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
import androidx.compose.ui.graphics.Brush
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
    val is_subscribed: Boolean? = null
)

@Serializable
data class UserUpdateRow(
    val first_name: String? = null,
    val last_name: String? = null,
    val avatar_url: String? = null  // ✅ persisted avatar
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

                    // ✅ Update UI immediately
                    avatarUrl = publicUrl
                    saveMessage = "Profile picture updated ✅"

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

    // Core app theme colors
    val darkGreen = Color(0xFF064E3B)
    val lightGreen = Color(0xFFE0F2F1)
    val goldColor = Color(0xFFD4AF37)
    val surface = Color(0xFFFFFFFF)
    val background = lightGreen

    val headerGradient = Brush.verticalGradient(listOf(background, background))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {

            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(brush = headerGradient)
                    .statusBarsPadding()
            ) {
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
                        .background(
                            Color.White
                        )
                        .border(1.dp, darkGreen.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = darkGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = "My Profile",
                        color = darkGreen,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 70.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.size(124.dp), contentAlignment = Alignment.Center) {

                        // Glow ring
                        Box(
                            modifier = Modifier
                                .size(124.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(goldColor.copy(alpha = 0.4f), Color.Transparent)
                                    )
                                )
                        )

                        // Avatar circle
                        Box(
                            modifier = Modifier
                                .size(114.dp)
                                .clip(CircleShape)
                                .border(3.dp, goldColor, CircleShape)
                                .smoothClickable {
                                    AnalyticsManager.trackEvent("Profile - Avatar Tapped")
                                    galleryLauncher.launch("image/*")
                                }
                        ) {
                            when {
                                // ✅ Show upload spinner
                                isUploadingAvatar -> {
                                    Surface(
                                        shape = CircleShape,
                                        color = goldColor.copy(alpha = 0.3f),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(
                                                color = goldColor,
                                                modifier = Modifier.size(32.dp),
                                                strokeWidth = 3.dp
                                            )
                                        }
                                    }
                                }
                                // ✅ Show saved avatar from Supabase
                                avatarUrl != null -> {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = "Profile Picture",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                    )
                                }
                                // Show initial letter fallback
                                else -> {
                                    Surface(
                                        shape = CircleShape,
                                        color = goldColor,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (isLoading) {
                                                CircularProgressIndicator(
                                                    color = Color.Black,
                                                    modifier = Modifier.size(28.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Text(
                                                    text = user?.first_name?.firstOrNull()?.toString() ?: "?",
                                                    fontSize = 42.sp,
                                                    color = Color.Black,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Camera badge
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .align(Alignment.BottomEnd)
                                .shadow(4.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color(0xFF064E3B))
                                .border(2.dp, Color.White, CircleShape)
                                .smoothClickable {
                                    AnalyticsManager.trackEvent("Profile - Camera Badge Tapped")
                                    galleryLauncher.launch("image/*")
                                },
                            contentAlignment = Alignment.Center
                        ) { Text("📷", fontSize = 13.sp) }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (isLoading) "Loading..."
                        else "${user?.first_name ?: ""} ${user?.last_name ?: ""}".trim()
                            .ifEmpty { "No Name" },
                        color = darkGreen,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = user?.phone_number?.ifEmpty { "Member of Emaan Wallpapers" } ?: "Member of Emaan Wallpapers",
                            color = darkGreen.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(72.dp))

            Text(
                text = "Account",
                color = darkGreen.copy(alpha = 0.8f),
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
                    containerColor = Color(0xFFE8F5E9),
                    titleColor = darkGreen
                ) {
                    AnalyticsManager.trackEvent("Profile - Edit Profile Tapped")
                    showEditDialog = true
                }
                ProfileItem(
                    icon = "👑",
                    title = "Upgrade to Premium",
                    subtitle = "Unlock exclusive wallpapers",
                    containerColor = Color(0xFFF1F8E9),
                    titleColor = darkGreen
                ) {
                    AnalyticsManager.trackEvent("Profile - Upgrade Premium Tapped")
                }
                ProfileItem(
                    icon = "📤",
                    title = "Share App",
                    subtitle = "Invite friends to Emaan Wallpapers",
                    containerColor = Color(0xFFE8F5E9),
                    titleColor = darkGreen
                ) {
                    AnalyticsManager.trackEvent("Profile - Share App Tapped")
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Danger Zone",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp,
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
                color = Color.White.copy(alpha = 0.25f),
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
                containerColor = if (msg.contains("✅")) Color(0xFF064E3B) else Color(0xFFB00020),
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A4A38)),
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
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                EditField("First Name *", firstName) { firstName = it }
                EditField("Last Name *", lastName) { lastName = it }

                error?.let { msg ->
                    Text(
                        text = msg,
                        color = Color(0xFFFFB4AB),
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
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37))
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                        } else {
                            Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
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
        label = { Text(label, color = Color.White.copy(alpha = 0.65f)) },
        singleLine = true,
        keyboardOptions = if (isNumber) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
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

@Composable
fun ProfileItem(
    icon: String,
    title: String,
    subtitle: String = "",
    containerColor: Color = Color.White,
    titleColor: Color = Color(0xFF064E3B),
    onClick: (() -> Unit)? = null
) {
    Card(
        onClick = { onClick?.invoke() },
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Text(
                "›",
                color = Color(0xFF94A3B8),
                fontSize = 24.sp,
                fontWeight = FontWeight.Light
            )
        }
    }
}