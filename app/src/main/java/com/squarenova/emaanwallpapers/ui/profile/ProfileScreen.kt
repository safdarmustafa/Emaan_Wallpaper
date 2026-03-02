package com.squarenova.emaanwallpapers.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.data.model.User
import com.squarenova.emaanwallpapers.network.FirebaseClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(navController: NavController) {

    val context = LocalContext.current
    val dataStoreManager = DataStoreManager(context)
    val scope = rememberCoroutineScope()

    var user by remember { mutableStateOf<User?>(null) }
    var profileImageUri by remember { mutableStateOf<Uri?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    // 🖼️ Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { profileImageUri = it }
    }

    // 🔥 FETCH FROM FIREBASE
    LaunchedEffect(Unit) {
        val phone = dataStoreManager.phoneNumber.firstOrNull()
        if (!phone.isNullOrEmpty()) {
            FirebaseClient.db
                .collection("users")
                .document(phone)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        user = User(
                            phone_number = document.getString("phone_number") ?: "",
                            first_name = document.getString("first_name") ?: "",
                            last_name = document.getString("last_name") ?: "",
                            age = document.getLong("age")?.toInt() ?: 0,
                            country = document.getString("country") ?: "",
                            city = document.getString("city") ?: "",
                            gender = document.getString("gender") ?: ""
                        )
                    }
                }
        }
    }

    // Auto-hide snackbar
    saveMessage?.let {
        LaunchedEffect(it) {
            delay(2500)
            saveMessage = null
        }
    }

    // ✅ EDIT PROFILE DIALOG
    if (showEditDialog) {
        EditProfileDialog(
            user = user,
            isSaving = isSaving,
            onDismiss = { showEditDialog = false },
            onSave = { updatedUser ->
                isSaving = true
                scope.launch {
                    val phone = dataStoreManager.phoneNumber.firstOrNull()
                    if (!phone.isNullOrEmpty()) {
                        val updates = hashMapOf(
                            "first_name" to updatedUser.first_name,
                            "last_name" to updatedUser.last_name,
                            "age" to updatedUser.age,
                            "country" to updatedUser.country,
                            "city" to updatedUser.city,
                            "gender" to updatedUser.gender
                        )
                        FirebaseClient.db
                            .collection("users")
                            .document(phone)
                            .set(updates, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener {
                                user = updatedUser  // ✅ Instantly reflect on UI
                                isSaving = false
                                showEditDialog = false
                                saveMessage = "Profile updated successfully ✅"
                            }
                            .addOnFailureListener { e ->
                                isSaving = false
                                saveMessage = "Failed: ${e.message} ❌"
                            }
                    } else {
                        isSaving = false
                        saveMessage = "Phone number not found ❌"
                    }
                }
            }
        )
    }

    val headerGradient = Brush.verticalGradient(
        listOf(Color(0xFF1B5E20), Color(0xFF0D3B2E))
    )

    val goldColor = Color(0xFFD4AF37)
    val darkGreen = Color(0xFF0D3B2E)
    val cardGreen = Color(0xFF1F4F3D)
    val deepCard = Color(0xFF154734)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkGreen)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {

            // ════════════════════════════════════════
            // 🔝 HEADER with gradient
            // ════════════════════════════════════════
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(brush = headerGradient)
                    .statusBarsPadding()
            ) {
                // Back button
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("←", color = Color.White, fontSize = 20.sp)
                    }
                }

                // Screen title
                Text(
                    text = "My Profile",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 14.dp)
                )

                // Avatar + name — offset to overlap stats card
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 70.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    // ✅ FIXED: Outer Box is NOT clipped — camera badge won't overflow
                    Box(
                        modifier = Modifier.size(124.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Glow ring
                        Box(
                            modifier = Modifier
                                .size(124.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            goldColor.copy(alpha = 0.4f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        // ✅ Avatar circle — clipped separately
                        Box(
                            modifier = Modifier
                                .size(114.dp)
                                .clip(CircleShape)
                                .border(3.dp, goldColor, CircleShape)
                                .clickable { galleryLauncher.launch("image/*") }
                        ) {
                            if (profileImageUri != null) {
                                AsyncImage(
                                    model = profileImageUri,
                                    contentDescription = "Profile Picture",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = goldColor,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = user?.first_name
                                                ?.firstOrNull()?.toString() ?: "?",
                                            fontSize = 42.sp,
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // ✅ Camera badge — outside clip, perfectly bottom-end
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .align(Alignment.BottomEnd)
                                .shadow(4.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color(0xFF064E3B))
                                .border(2.dp, Color.White, CircleShape)
                                .clickable { galleryLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📷", fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (user != null)
                            "${user!!.first_name} ${user!!.last_name}".trim()
                        else "Loading...",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    // Phone number badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = user?.phone_number?.ifEmpty { "Member of Emaan Wallpapers" }
                                ?: "Member of Emaan Wallpapers",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Space for avatar overflow
            Spacer(modifier = Modifier.height(90.dp))

            // ════════════════════════════════════════
            // 📊 STATS CARD
            // ════════════════════════════════════════
            Card(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardGreen),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(vertical = 22.dp, horizontal = 16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem(
                        icon = "🌍",
                        label = "Country",
                        value = user?.country?.ifEmpty { "—" } ?: "—"
                    )
                    StatDivider()
                    StatItem(
                        icon = "🏙️",
                        label = "City",
                        value = user?.city?.ifEmpty { "—" } ?: "—"
                    )
                    StatDivider()
                    StatItem(
                        icon = "🎂",
                        label = "Age",
                        value = if ((user?.age ?: 0) > 0) user!!.age.toString() else "—"
                    )
                    StatDivider()
                    StatItem(
                        icon = "👤",
                        label = "Gender",
                        value = user?.gender?.ifEmpty { "—" } ?: "—"
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ════════════════════════════════════════
            // 📋 SECTION TITLE
            // ════════════════════════════════════════
            Text(
                text = "Account",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            // ════════════════════════════════════════
            // 📋 MENU ITEMS
            // ════════════════════════════════════════
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileItem(
                    icon = "✏️",
                    title = "Edit Profile",
                    subtitle = "Update your personal information",
                    containerColor = deepCard
                ) { showEditDialog = true }

                ProfileItem(
                    icon = "👑",
                    title = "Upgrade to Premium",
                    subtitle = "Unlock exclusive wallpapers",
                    containerColor = Color(0xFF2A3D1F),
                    titleColor = goldColor
                )

                ProfileItem(
                    icon = "📤",
                    title = "Share App",
                    subtitle = "Invite friends to Emaan Wallpapers",
                    containerColor = deepCard
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Danger Zone",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                ProfileItem(
                    icon = "🚪",
                    title = "Logout",
                    subtitle = "Sign out of your account",
                    containerColor = Color(0xFF3D1515),
                    titleColor = Color(0xFFFF6B6B)
                ) {
                    scope.launch {
                        dataStoreManager.logout()
                        navController.navigate("login") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // App version
            Text(
                text = "Emaan Wallpapers v1.0",
                color = Color.White.copy(alpha = 0.25f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // ✅ Snackbar
        saveMessage?.let { msg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = if (msg.contains("✅"))
                    Color(0xFF064E3B) else Color(0xFFB00020),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(msg, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ════════════════════════════════════════
// ✅ EDIT PROFILE DIALOG
// ════════════════════════════════════════
@Composable
fun EditProfileDialog(
    user: User?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (User) -> Unit
) {
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
                    text = "Edit Profile",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                EditField("First Name", firstName) { firstName = it }
                EditField("Last Name", lastName) { lastName = it }
                EditField("Age", age, isNumber = true) { age = it }
                EditField("Country", country) { country = it }
                EditField("City", city) { city = it }

                // Gender selector
                Text(
                    "Gender",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Male", "Female", "Other").forEach { option ->
                        FilterChip(
                            selected = gender == option,
                            onClick = { gender = option },
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            onSave(
                                User(
                                    phone_number = user?.phone_number ?: "",
                                    first_name = firstName.trim(),
                                    last_name = lastName.trim(),
                                    age = age.toIntOrNull() ?: 0,
                                    country = country.trim(),
                                    city = city.trim(),
                                    gender = gender
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD4AF37)
                        )
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                "Save",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════
// ✅ REUSABLE COMPONENTS
// ════════════════════════════════════════

@Composable
fun EditField(
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

@Composable
fun StatItem(icon: String, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(40.dp)
            .background(Color.White.copy(alpha = 0.12f))
    )
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
                // Icon background pill
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(icon, fontSize = 20.sp)
                }

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
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Text(
                "›",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 24.sp,
                fontWeight = FontWeight.Light
            )
        }
    }
}