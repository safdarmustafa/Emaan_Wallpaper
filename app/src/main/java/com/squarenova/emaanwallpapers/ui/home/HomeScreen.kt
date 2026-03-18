package com.squarenova.emaanwallpapers.ui.home

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.network.SupabaseClient
import com.squarenova.emaanwallpapers.service.GifWallpaperService
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────
// Models
// ─────────────────────────────────────────

@Serializable
data class WallpaperRow(val id: Long, val category: String, val url: String)

@Serializable
data class LiveWallpaperRow(val id: Long, val url: String, val title: String? = null)

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
    val avatar_url: String? = null,
    val is_subscribed: Boolean? = false
)

enum class WallpaperFilter(val label: String, val emoji: String, val description: String) {
    ALL("All", "✦", "Show all wallpapers"),
    STATIC("Static", "🖼️", "Show static images only"),
    LIVE("Live", "🌀", "Show live videos only")
}

// ─────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────

suspend fun setWallpaper(context: Context, url: String): Result<Unit> {
    return withContext(Dispatchers.IO) {
        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as BitmapDrawable).bitmap
                WallpaperManager.getInstance(context).setBitmap(bitmap)
                Result.success(Unit)
            } else Result.failure(Exception("Failed to load image"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

fun setLiveWallpaper(context: Context, videoUrl: String) {
    context.getSharedPreferences("live_wallpaper_prefs", Context.MODE_PRIVATE)
        .edit().putString("gif_url", videoUrl).apply()
    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
        putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(context, GifWallpaperService::class.java)
        )
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

// ─────────────────────────────────────────
// HomeScreen
// ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {

    val context = LocalContext.current
    val dataStoreManager = DataStoreManager(context)
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    val screenWidth = configuration.screenWidthDp.dp
    val cardHeight = (screenWidth - 24.dp) * (16f / 9f)

    var user by remember { mutableStateOf<UserRow?>(null) }
    // Cache so header doesn't show '?' for a moment when returning from Profile.
    var avatarUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var cachedFirstName by rememberSaveable { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val lifecycleOwner = LocalLifecycleOwner.current

    var allWallpapers by remember { mutableStateOf<List<WallpaperRow>>(emptyList()) }
    var liveWallpapers by remember { mutableStateOf<List<LiveWallpaperRow>>(emptyList()) }
    var isWallpaperLoading by remember { mutableStateOf(true) }
    var isLiveLoading by remember { mutableStateOf(true) }

    var settingWallpaperUrl by remember { mutableStateOf<String?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var snackbarIsSuccess by remember { mutableStateOf(true) }

    var activeFilter by remember { mutableStateOf(WallpaperFilter.ALL) }
    var showFilterSheet by remember { mutableStateOf(false) }

    // null = no category selected (show all)
    val categories = listOf("Kaaba", "Madinah", "Quran", "Mosque", "Islamic Quotes", "Ramadan", "Allah")
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    val filteredStatic = remember(selectedCategory, allWallpapers) {
        if (selectedCategory == null) allWallpapers
        else allWallpapers.filter { it.category == selectedCategory }
    }

    LaunchedEffect(snackbarMessage) {
        if (snackbarMessage != null) {
            kotlinx.coroutines.delay(2500)
            snackbarMessage = null
        }
    }

    LaunchedEffect(Unit) {
        try {
            val phone = dataStoreManager.phoneNumber.firstOrNull()
            if (!phone.isNullOrEmpty()) {
                val result = SupabaseClient.client
                    .postgrest["users"]
                    .select(
                        columns = Columns.list(
                            "phone_number",
                            "first_name",
                            "last_name",
                            "avatar_url",
                            "is_subscribed"
                        )
                    ) { filter { eq("phone_number", phone) } }
                    .decodeSingle<UserRow>()
                user = result
                avatarUrl = result.avatar_url
                cachedFirstName = result.first_name
            }
        } catch (e: Exception) {
            Log.e("HOME_USER_ERROR", e.message ?: "Unknown")
        } finally {
            isLoading = false
        }
    }

    // ✅ Refresh avatar when returning from Profile so UI reflects latest image
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    try {
                        val phone = dataStoreManager.phoneNumber.firstOrNull()
                        if (!phone.isNullOrEmpty()) {
                            val result = SupabaseClient.client
                                .postgrest["users"]
                                .select(
                                    columns = Columns.list(
                                        "phone_number",
                                        "first_name",
                                        "last_name",
                                        "avatar_url",
                                        "is_subscribed"
                                    )
                                ) { filter { eq("phone_number", phone) } }
                                .decodeSingle<UserRow>()
                            user = result
                            avatarUrl = result.avatar_url
                            cachedFirstName = result.first_name
                        }
                    } catch (e: Exception) {
                        Log.e("HOME_USER_REFRESH_ERROR", e.message ?: "Unknown")
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        isWallpaperLoading = true
        try {
            allWallpapers = SupabaseClient.client
                .postgrest["wallpapers"]
                .select(columns = Columns.list("id", "category", "url"))
                .decodeList()
        } catch (e: Exception) {
            Log.e("WALLPAPER_ERROR", e.message ?: "Unknown")
        } finally { isWallpaperLoading = false }
    }

    LaunchedEffect(Unit) {
        isLiveLoading = true
        try {
            liveWallpapers = SupabaseClient.client
                .postgrest["live_wallpapers"]
                .select(columns = Columns.list("id", "url", "title"))
                .decodeList()
        } catch (e: Exception) {
            Log.e("LIVE_WALLPAPER_ERROR", e.message ?: "Unknown")
        } finally { isLiveLoading = false }
    }

    val gradient = Brush.verticalGradient(colors = listOf(Color(0xFF064E3B), Color(0xFF0F766E)))
    val darkGreen = Color(0xFF064E3B)
    val goldColor = Color(0xFFD4AF37)

    // ── Filter Bottom Sheet ──────────────────
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            containerColor = Color(0xFF1A4A38),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    "Filter Wallpapers",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Choose what to display",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(20.dp))

                WallpaperFilter.entries.forEach { filter ->
                    val isSelected = activeFilter == filter
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) darkGreen.copy(alpha = 0.6f)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .clickable {
                                activeFilter = filter
                                showFilterSheet = false
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(filter.emoji, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    filter.label,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    filter.description,
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = goldColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {

            // ── Header ───────────────────────────
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
                        "Assalamu Alaikum 🌙",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (!isLoading) (user?.first_name ?: cachedFirstName) ?: "Guest" else "",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = { navController.navigate("profile") },
                    modifier = Modifier.size(46.dp).align(Alignment.CenterEnd)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            // Always show a visible circle even if image fails
                            .background(goldColor),
                        contentAlignment = Alignment.Center
                    ) {
                        val initial = user?.first_name
                            ?.firstOrNull()
                            ?.uppercaseChar()
                            ?.toString()
                            ?: cachedFirstName
                                ?.firstOrNull()
                                ?.uppercaseChar()
                                ?.toString()
                            ?: "?"

                        if (!avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(avatarUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Profile",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                            )
                        }

                        // Draw initials last so they remain visible even if the image can't load.
                        Text(
                            text = initial,
                            color = Color.Black,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ── Category row with filter icon ────
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ✅ Filter gear icon — replaces "All" chip
                item {
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (activeFilter != WallpaperFilter.ALL) darkGreen
                                else Color(0xFFF0F0F0)
                            )
                            .clickable { showFilterSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⚙",
                            fontSize = 17.sp,
                            color = if (activeFilter != WallpaperFilter.ALL) Color.White
                            else Color(0xFF444444)
                        )
                    }
                }

                // Category chips — hidden when Live filter active
                if (activeFilter != WallpaperFilter.LIVE) {
                    items(categories) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = {
                                selectedCategory =
                                    if (selectedCategory == category) null else category
                            },
                            label = {
                                Text(
                                    category.replaceFirstChar { it.uppercase() },
                                    fontSize = 12.sp
                                )
                            },
                            modifier = Modifier.padding(horizontal = 4.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = darkGreen,
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFFF0F0F0),
                                labelColor = Color.Black
                            )
                        )
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .clip(RoundedCornerShape(50.dp))
                                .background(darkGreen)
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "🌀  Live Wallpapers",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // ── Feed ─────────────────────────────
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp)
            ) {

                // Live wallpapers — only show when no category is selected
                if ((activeFilter == WallpaperFilter.ALL && selectedCategory == null) || activeFilter == WallpaperFilter.LIVE) {
                    if (isLiveLoading) {
                        items(2) { ShimmerCard(cardHeight) }
                    } else {
                        items(items = liveWallpapers, key = { "live_${it.id}" }) { liveWallpaper ->
                            LiveWallpaperCard(
                                videoUrl = liveWallpaper.url,
                                title = liveWallpaper.title,
                                cardHeight = cardHeight,
                                onSetLiveWallpaper = {
                                    setLiveWallpaper(context, liveWallpaper.url)
                                    snackbarIsSuccess = true
                                    snackbarMessage = "Opening live wallpaper picker ✅"
                                }
                            )
                        }
                    }
                }

                // Static wallpapers
                if (activeFilter == WallpaperFilter.ALL || activeFilter == WallpaperFilter.STATIC) {
                    if (isWallpaperLoading) {
                        items(3) { ShimmerCard(cardHeight) }
                    } else if (filteredStatic.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(300.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🕌", fontSize = 48.sp)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("No wallpapers yet", color = Color.Gray, fontSize = 16.sp)
                                    Text(
                                        "Coming soon insha'Allah",
                                        color = Color.Gray.copy(alpha = 0.6f),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    } else {
                        items(items = filteredStatic, key = { "static_${it.id}" }) { wallpaper ->
                            WallpaperCard(
                                url = wallpaper.url,
                                cardHeight = cardHeight,
                                isSettingWallpaper = settingWallpaperUrl == wallpaper.url,
                                onSetWallpaper = {
                                    if (settingWallpaperUrl != null) return@WallpaperCard
                                    settingWallpaperUrl = wallpaper.url
                                    scope.launch {
                                        val result = setWallpaper(context, wallpaper.url)
                                        settingWallpaperUrl = null
                                        snackbarIsSuccess = result.isSuccess
                                        snackbarMessage = if (result.isSuccess)
                                            "Wallpaper set successfully ✅"
                                        else "Failed to set wallpaper ❌"
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Snackbar
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

// ─────────────────────────────────────────
// Shimmer placeholder
// ─────────────────────────────────────────

@Composable
fun ShimmerCard(cardHeight: Dp) {
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

// ─────────────────────────────────────────
// Live wallpaper card (MP4 via ExoPlayer)
// ─────────────────────────────────────────

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LiveWallpaperCard(
    videoUrl: String,
    title: String?,
    cardHeight: Dp,
    onSetLiveWallpaper: () -> Unit
) {
    val context = LocalContext.current

    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            repeatMode = ExoPlayer.REPEAT_MODE_ALL
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(videoUrl) {
        onDispose { exoPlayer.release() }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(cardHeight)) {

            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D3B2E)))

            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Bottom gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                        )
                    )
            )

            // LIVE badge
            Box(
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.TopStart)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFD4AF37))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "● LIVE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            // Title + button
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!title.isNullOrEmpty()) {
                    Text(
                        title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Button(
                    onClick = onSetLiveWallpaper,
                    shape = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    modifier = Modifier.height(42.dp)
                ) {
                    Text(
                        "🌀  Set Live Wallpaper",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────
// Static wallpaper card
// ─────────────────────────────────────────

@Composable
fun WallpaperCard(
    url: String,
    cardHeight: Dp,
    isSettingWallpaper: Boolean,
    onSetWallpaper: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(cardHeight)) {

            Box(modifier = Modifier.fillMaxSize().background(Color(0xFFE8E8E8)))

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
                    Text(
                        "Setting...",
                        color = Color.Black,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                } else {
                    Text(
                        "🖼️  Set Wallpaper",
                        color = Color.Black,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}