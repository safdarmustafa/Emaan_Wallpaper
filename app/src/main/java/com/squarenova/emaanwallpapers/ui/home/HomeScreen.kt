package com.squarenova.emaanwallpapers.ui.home

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.data.model.User
import com.squarenova.emaanwallpapers.network.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class WallpaperRow(
    val id: Long,
    val category: String,
    val url: String
)

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
    val gender: String? = null
)

// ✅ Downloads image via Coil and sets it as wallpaper
suspend fun setWallpaper(context: Context, url: String): Result<Unit> {
    return withContext(Dispatchers.IO) {
        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false) // Required — hardware bitmaps can't be read for wallpaper
                .build()

            val result = loader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as BitmapDrawable).bitmap
                val wallpaperManager = WallpaperManager.getInstance(context)
                wallpaperManager.setBitmap(bitmap)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to load image"))
            }
        } catch (e: Exception) {
            Log.e("SET_WALLPAPER_ERROR", e.message ?: "Unknown error")
            Result.failure(e)
        }
    }
}

@Composable
fun HomeScreen(navController: NavController) {

    val context = LocalContext.current
    val dataStoreManager = DataStoreManager(context)
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    val screenWidth = configuration.screenWidthDp.dp
    val cardWidth = screenWidth - 24.dp
    val cardHeight = cardWidth * (16f / 9f)

    var user by remember { mutableStateOf<User?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var wallpaperList by remember { mutableStateOf<List<String>>(emptyList()) }
    var isWallpaperLoading by remember { mutableStateOf(true) }

    // ✅ Track which URL is currently being set (shows spinner on that card only)
    var settingWallpaperUrl by remember { mutableStateOf<String?>(null) }

    // ✅ Snackbar state
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var snackbarIsSuccess by remember { mutableStateOf(true) }

    val categories = listOf(
        "kaaba", "Madinah", "Quran", "Mosque",
        "Islamic Quotes", "Ramadan", "Allah"
    )
    var selectedCategory by remember { mutableStateOf("kaaba") }

    // Auto-dismiss snackbar after 2.5s
    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            kotlinx.coroutines.delay(2500)
            snackbarMessage = null
        }
    }

    // Fetch user
    LaunchedEffect(Unit) {
        try {
            val phone = dataStoreManager.phoneNumber.firstOrNull()
            if (!phone.isNullOrEmpty()) {
                val result = SupabaseClient.client
                    .postgrest["users"]
                    .select(columns = Columns.ALL) {
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
            }
        } catch (e: Exception) {
            Log.e("HOME_USER_ERROR", "Failed: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    // Fetch wallpapers
    LaunchedEffect(selectedCategory) {
        isWallpaperLoading = true
        wallpaperList = emptyList()
        try {
            val result = SupabaseClient.client
                .postgrest["wallpapers"]
                .select(columns = Columns.ALL) {
                    filter { eq("category", selectedCategory) }
                }
                .decodeList<WallpaperRow>()

            wallpaperList = result.map { it.url }
        } catch (e: Exception) {
            Log.e("WALLPAPER_ERROR", "Failed: ${e.message}")
            wallpaperList = emptyList()
        } finally {
            isWallpaperLoading = false
        }
    }

    val gradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF064E3B), Color(0xFF0F766E))
    )

    Box(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
        ) {

            // ✅ HEADER
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .background(brush = gradient)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column(modifier = Modifier.align(Alignment.CenterStart)) {
                    Text(
                        text = "Assalamu Alaikum 🌙",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (!isLoading) user?.first_name ?: "Guest" else "",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = { navController.navigate("profile") },
                    modifier = Modifier.size(46.dp).align(Alignment.CenterEnd)
                ) {
                    Surface(shape = CircleShape, color = Color(0xFFD4AF37), modifier = Modifier.size(46.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = user?.first_name?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                color = Color.Black,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ✅ CATEGORY ROW
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                text = category.replaceFirstChar { it.uppercase() },
                                fontSize = 12.sp
                            )
                        },
                        modifier = Modifier.padding(horizontal = 4.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF064E3B),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFFF0F0F0),
                            labelColor = Color.Black
                        )
                    )
                }
            }

            // ✅ WALLPAPER FEED
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp)
            ) {
                if (isWallpaperLoading) {
                    items(3) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(cardHeight)
                                    .background(Color(0xFFE0E0E0)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF064E3B),
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                } else if (wallpaperList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🕌", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No wallpapers yet", color = Color.Gray, fontSize = 16.sp)
                                Text("Coming soon insha'Allah", color = Color.Gray.copy(alpha = 0.6f), fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    items(items = wallpaperList, key = { it }) { url ->
                        WallpaperCard(
                            url = url,
                            cardHeight = cardHeight,
                            isSettingWallpaper = settingWallpaperUrl == url,
                            onSetWallpaper = {
                                // Prevent double-tap
                                if (settingWallpaperUrl != null) return@WallpaperCard
                                settingWallpaperUrl = url
                                scope.launch {
                                    val result = setWallpaper(context, url)
                                    settingWallpaperUrl = null
                                    if (result.isSuccess) {
                                        snackbarIsSuccess = true
                                        snackbarMessage = "Wallpaper set successfully ✅"
                                    } else {
                                        snackbarIsSuccess = false
                                        snackbarMessage = "Failed to set wallpaper ❌"
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // ✅ Snackbar overlay
        AnimatedVisibility(
            visible = snackbarMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Snackbar(
                modifier = Modifier.padding(16.dp),
                containerColor = if (snackbarIsSuccess) Color(0xFF064E3B) else Color(0xFFB00020),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    snackbarMessage ?: "",
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ✅ Extracted wallpaper card with Set Wallpaper button
@Composable
fun WallpaperCard(
    url: String,
    cardHeight: androidx.compose.ui.unit.Dp,
    isSettingWallpaper: Boolean,
    onSetWallpaper: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
        ) {
            // Grey background while loading
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFFE8E8E8)))

            // Wallpaper image
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .crossfade(400)
                    .memoryCacheKey(url)
                    .diskCacheKey(url)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // ✅ Gradient overlay at bottom for button readability
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                        )
                    )
            )

            // ✅ Set Wallpaper button
            Button(
                onClick = onSetWallpaper,
                enabled = !isSettingWallpaper,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .height(42.dp),
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD4AF37),
                    disabledContainerColor = Color(0xFFD4AF37).copy(alpha = 0.7f)
                ),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                if (isSettingWallpaper) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Setting...", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                } else {
                    Text("Set Wallpaper", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}