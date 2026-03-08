package com.squarenova.emaanwallpapers.ui.profile

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
    val age: Int? = null,
    val country: String? = null,
    val city: String? = null,
    val gender: String? = null,
    val avatar_url: String? = null  // ✅ persisted avatar
)

@Serializable
data class UserUpdateRow(
    val first_name: String? = null,
    val last_name: String? = null,
    val age: Int? = null,
    val country: String? = null,
    val city: String? = null,
    val gender: String? = null,
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
                    val fileName = "avatar_${phone}.jpg"
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
                    last_name = result.last_name ?: "",
                    age = result.age ?: 0,
                    country = result.country ?: "",
                    city = result.city ?: "",
                    gender = result.gender ?: ""
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
                                        last_name = updatedUser.last_name,
                                        age = updatedUser.age,
                                        country = updatedUser.country,
                                        city = updatedUser.city,
                                        gender = updatedUser.gender
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

    val headerGradient = Brush.verticalGradient(listOf(Color(0xFF1B5E20), Color(0xFF0D3B2E)))
    val goldColor = Color(0xFFD4AF37)
    val darkGreen = Color(0xFF0D3B2E)
    val cardGreen = Color(0xFF1F4F3D)
    val deepCard = Color(0xFF154734)

    Box(modifier = Modifier.fillMaxSize().background(darkGreen)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
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
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.25f),
                                    Color.White.copy(alpha = 0.08f)
                                )
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = "My Profile",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp)
                )

                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = 70.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.size(124.dp), contentAlignment = Alignment.Center) {

                        // Glow ring
                        Box(
                            modifier = Modifier.size(124.dp).clip(CircleShape)
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
                                    Surface(shape = CircleShape, color = goldColor.copy(alpha = 0.3f), modifier = Modifier.fillMaxSize()) {
                                        Box(contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(color = goldColor, modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                                        }
                                    }
                                }
                                // ✅ Show saved avatar from Supabase
                                avatarUrl != null -> {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = "Profile Picture",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                }
                                // Show initial letter fallback
                                else -> {
                                    Surface(shape = CircleShape, color = goldColor, modifier = Modifier.fillMaxSize()) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (isLoading) {
                                                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                                            } else {
                                                Text(
                                                    text = user?.first_name?.firstOrNull()?.toString() ?: "?",
                                                    fontSize = 42.sp, color = Color.Black, fontWeight = FontWeight.Bold
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
                        else "${user?.first_name ?: ""} ${user?.last_name ?: ""}".trim().ifEmpty { "No Name" },
                        color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = user?.phone_number?.ifEmpty { "Member of Emaan Wallpapers" } ?: "Member of Emaan Wallpapers",
                            color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(90.dp))

            // Stats card
            Card(
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardGreen),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 22.dp, horizontal = 16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem(icon = "🌍", label = "Country", value = user?.country?.ifEmpty { "—" } ?: "—")
                    StatDivider()
                    StatItem(icon = "🏙️", label = "City", value = user?.city?.ifEmpty { "—" } ?: "—")
                    StatDivider()
                    StatItem(icon = "🎂", label = "Age", value = if ((user?.age ?: 0) > 0) user!!.age.toString() else "—")
                    StatDivider()
                    StatItem(icon = "👤", label = "Gender", value = user?.gender?.ifEmpty { "—" } ?: "—")
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Account",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileItem(icon = "✏️", title = "Edit Profile", subtitle = "Update your personal information", containerColor = deepCard) {
                    AnalyticsManager.trackEvent("Profile - Edit Profile Tapped")
                    showEditDialog = true
                }
                ProfileItem(icon = "👑", title = "Upgrade to Premium", subtitle = "Unlock exclusive wallpapers", containerColor = Color(0xFF2A3D1F), titleColor = goldColor) {
                    AnalyticsManager.trackEvent("Profile - Upgrade Premium Tapped")
                }
                ProfileItem(icon = "📤", title = "Share App", subtitle = "Invite friends to Emaan Wallpapers", containerColor = deepCard) {
                    AnalyticsManager.trackEvent("Profile - Share App Tapped")
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Danger Zone",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                ProfileItem(icon = "🚪", title = "Logout", subtitle = "Sign out of your account", containerColor = Color(0xFF3D1515), titleColor = Color(0xFFFF6B6B)) {
                    AnalyticsManager.trackEvent("Profile - Logout Tapped")
                    scope.launch {
                        AnalyticsManager.reset()
                        dataStoreManager.logout()
                        navController.navigate("login") { popUpTo("home") { inclusive = true } }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
            Text(text = "Emaan Wallpapers v1.0", color = Color.White.copy(alpha = 0.25f), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Snackbar
        saveMessage?.let { msg ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
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
    var age by remember { mutableStateOf(if ((user?.age ?: 0) > 0) user!!.age.toString() else "") }
    var country by remember { mutableStateOf(user?.country ?: "") }
    var city by remember { mutableStateOf(user?.city ?: "") }
    var gender by remember { mutableStateOf(user?.gender ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A4A38)),
            modifier = Modifier.fillMaxWidth().padding(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Edit Profile", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                EditField("First Name", firstName) { firstName = it }
                EditField("Last Name", lastName) { lastName = it }
                EditField("Age", age, isNumber = true) { age = it }
                EditField("Country", country) { country = it }
                EditField("City", city) { city = it }

                Text("Gender", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Male", "Female", "Other").forEach { option ->
                        FilterChip(
                            selected = gender == option,
                            onClick = {
                                AnalyticsManager.trackEvent("Profile - Edit Dialog Gender Selected", mapOf("gender" to option))
                                gender = option
                            },
                            label = { Text(option, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFD4AF37),
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0xFF0D3B2E),
                                labelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            AnalyticsManager.trackEvent("Profile - Edit Dialog Save Tapped")
                            onSave(User(
                                phone_number = user?.phone_number ?: "",
                                first_name = firstName.trim(),
                                last_name = lastName.trim(),
                                age = age.toIntOrNull() ?: 0,
                                country = country.trim(),
                                city = city.trim(),
                                gender = gender
                            ))
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
fun StatItem(icon: String, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, letterSpacing = 0.5.sp)
        Spacer(modifier = Modifier.height(3.dp))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StatDivider() {
    Box(modifier = Modifier.width(1.dp).height(40.dp).background(Color.White.copy(alpha = 0.12f)))
}

@Composable
fun ProfileItem(
    icon: String,
    title: String,
    subtitle: String = "",
    containerColor: Color = Color(0xFF154734),
    titleColor: Color = Color.White,
    onClick: (() -> Unit)? = null
) {
    Card(
        onClick = { onClick?.invoke() },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) { Text(icon, fontSize = 20.sp) }
                Column {
                    Text(title, color = titleColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (subtitle.isNotEmpty()) Text(subtitle, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                }
            }
            Text("›", color = Color.White.copy(alpha = 0.35f), fontSize = 24.sp, fontWeight = FontWeight.Light)
        }
    }
}