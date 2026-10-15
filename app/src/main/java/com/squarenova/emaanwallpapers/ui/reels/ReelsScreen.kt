package com.squarenova.emaanwallpapers.ui.reels

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.FileProvider
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.rememberUpdatedState
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.data.LocalSession
import com.squarenova.emaanwallpapers.subscription.EntitlementRepository
import com.squarenova.emaanwallpapers.ui.components.smoothClickable
import com.squarenova.emaanwallpapers.BuildConfig
import com.squarenova.emaanwallpapers.R
import com.squarenova.emaanwallpapers.network.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream
import java.net.URL

@Serializable
data class ReelRow(
    val id: Long,
    val title: String? = null,
    val url: String,
    val thumbnail_url: String? = null,
    val category: String? = null
)

fun downloadVideo(context: Context, url: String, title: String?) {
    try {
        val ext = if (url.contains(".MOV", ignoreCase = true)) ".mov" else ".mp4"
        val fileName = (title ?: "islamic_reel_${System.currentTimeMillis()}") + ext
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(fileName)
            setDescription("Downloading Islamic Reel...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
    } catch (e: Exception) {
        Log.e("DOWNLOAD_ERROR", e.message ?: "Unknown")
    }
}

fun shareVideo(context: Context, url: String, title: String?) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${title ?: "Islamic Reel"}\n\n$url")
        putExtra(Intent.EXTRA_SUBJECT, title ?: "Islamic Reel")
    }
    context.startActivity(Intent.createChooser(intent, "Share via"))
}

/**
 * Downloads video and shares it as a file to WhatsApp (status/chats).
 * Shares the actual video, not a link.
 */
suspend fun shareVideoToWhatsApp(context: Context, url: String, title: String?): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val ext = if (url.contains(".MOV", ignoreCase = true)) ".mov" else ".mp4"
            val fileName = "reel_${System.currentTimeMillis()}$ext"
            val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(shareDir, fileName)

            URL(url).openStream().use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                // Must match AndroidManifest.xml provider authority: `${applicationId}.file_provider`
                "${BuildConfig.APPLICATION_ID}.file_provider",
                file
            )

            withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "video/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                        // Grant WhatsApp permission to read the temp shared file
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    setPackage("com.whatsapp")
                }
                context.startActivity(intent)
            }
            true
        } catch (e: Exception) {
            Log.e("WHATSAPP_SHARE", e.message ?: "Unknown error", e)
            withContext(Dispatchers.Main) {
                shareVideo(context, url, title)
            }
            false
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun ReelsScreen(navController: NavController) {
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager(context) }
    val scope = rememberCoroutineScope()

    var reels by remember { mutableStateOf<List<ReelRow>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    // Category filter
    val categories = listOf(
        "ramadan", "dua", "quran", "prayer",
        "makkah", "madinah", "mosque",
        "islamic_night", "peaceful", "architecture"
    )
    val categoryLabels = mapOf(
        "ramadan" to "Ramadan",
        "dua" to "Dua",
        "quran" to "Quran",
        "prayer" to "Prayer",
        "makkah" to "Makkah",
        "madinah" to "Madinah",
        "mosque" to "Mosque",
        "islamic_night" to "Islamic Night",
        "peaceful" to "Peaceful",
        "architecture" to "Architecture"
    )
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var shuffleTrigger by remember { mutableIntStateOf(0) }

    val filteredReels = remember(selectedCategory, reels, shuffleTrigger) {
        if (selectedCategory == null) reels
        else reels.filter { it.category == selectedCategory }.shuffled()
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2500)
            toastMessage = null
        }
    }

    LaunchedEffect(Unit) {
        val phone = dataStoreManager.phoneNumber.firstOrNull()
        if (phone.isNullOrBlank()) {
            navController.navigate("subscription") {
                popUpTo("reels") { inclusive = true }
            }
            return@LaunchedEffect
        }
        val refresh = EntitlementRepository.refreshDetailed(phone)
        if (refresh.accountMissing) {
            LocalSession.clear(context)
            LocalSession.goToLogin(navController)
            return@LaunchedEffect
        }
        if (!refresh.hasPremium) {
            navController.navigate("subscription") {
                popUpTo("reels") { inclusive = true }
            }
            return@LaunchedEffect
        }

        isLoading = true
        try {
            reels = SupabaseClient.client
                .postgrest["reels"]
                .select(columns = Columns.ALL) {
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<ReelRow>()
                .shuffled()

            Log.d("REELS_DEBUG", "Fetched reels count: ${reels.size}")

        } catch (e: Exception) {
            Log.e("REELS_ERROR", e.message ?: "Unknown")
        } finally {
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            isLoading -> {
                val infiniteTransition = rememberInfiniteTransition(label = "loading")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🎬", fontSize = 56.sp)
                    Spacer(modifier = Modifier.height(20.dp))
                    CircularProgressIndicator(
                        color = Color(0xFFD4AF37),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Loading reels...",
                        color = Color.White.copy(alpha = alpha),
                        fontSize = 14.sp
                    )
                }
            }

            reels.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🎬", fontSize = 72.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "No reels yet",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Coming soon insha'Allah",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 15.sp
                    )
                }
            }

            else -> {
                val pagerState = rememberPagerState(pageCount = { filteredReels.size })

                // Reset to page 0 when category changes
                LaunchedEffect(selectedCategory, shuffleTrigger) {
                    if (filteredReels.isNotEmpty()) pagerState.scrollToPage(0)
                }

                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    ReelItem(
                        reel = filteredReels[page],
                        isVisible = pagerState.currentPage == page,
                        onDownload = {
                            downloadVideo(context, filteredReels[page].url, filteredReels[page].title)
                            toastMessage = "Downloading... check notifications 📥"
                        },
                        onShare = {
                            shareVideo(context, filteredReels[page].url, filteredReels[page].title)
                        },
                        onWhatsApp = {
                            scope.launch {
                                shareVideoToWhatsApp(
                                    context, filteredReels[page].url, filteredReels[page].title
                                )
                            }
                        }
                    )
                }

                // Category chips overlay — top of screen
                LazyRow(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    // "All" chip
                    item {
                        val isSelected = selectedCategory == null
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(
                                    if (isSelected) Color(0xFFD4AF37)
                                    else Color.Black.copy(alpha = 0.55f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(50.dp)
                                )
                                .smoothClickable {
                                    selectedCategory = null
                                    shuffleTrigger++
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "All",
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    items(categories) { cat ->
                        val isSelected = selectedCategory == cat
                        val hasReels = reels.any { it.category == cat }
                        if (!hasReels) return@items
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(
                                    if (isSelected) Color(0xFFD4AF37)
                                    else Color.Black.copy(alpha = 0.55f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(50.dp)
                                )
                                .smoothClickable {
                                    selectedCategory = if (selectedCategory == cat) null else cat
                                    shuffleTrigger++
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                categoryLabels[cat] ?: cat,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Empty state when category has no reels
                if (filteredReels.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🎬", fontSize = 56.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No reels in this category",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Toast
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color(0xFF064E3B))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    toastMessage ?: "",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

enum class DownloadState { IDLE, DOWNLOADING, DONE }

@OptIn(UnstableApi::class)
@Composable
fun ReelItem(
    reel: ReelRow,
    isVisible: Boolean,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onWhatsApp: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val currentIsVisible by rememberUpdatedState(isVisible)
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var downloadState by remember { mutableStateOf(DownloadState.IDLE) }
    var whatsAppState by remember { mutableStateOf(DownloadState.IDLE) }

    // ✅ Create player WITHOUT calling prepare() here
    val exoPlayer = remember(reel.url) {
        ExoPlayer.Builder(context)
            .build()
            .also { player ->
                player.setMediaItem(MediaItem.fromUri(reel.url))
                player.repeatMode = Player.REPEAT_MODE_ONE
                player.volume = 1f
                // ✅ prepare() called AFTER surface is attached (in AndroidView factory)
            }
    }

    // ✅ Play/pause based on page visibility
    LaunchedEffect(isVisible) {
        if (isVisible) {
            exoPlayer.seekTo(0)
            exoPlayer.playWhenReady = true
        } else {
            exoPlayer.playWhenReady = false
        }
    }

    DisposableEffect(reel.url) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // ✅ Stop playback when app goes background or screen is paused.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> {
                    exoPlayer.playWhenReady = false
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    // If user returns to this screen and this reel is visible, resume playback.
                    if (currentIsVisible) {
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // ✅ Full screen video — no black gaps
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player = exoPlayer
                    exoPlayer.prepare()
                }
            },
            update = { view -> view.player = exoPlayer },
            modifier = Modifier
                .fillMaxSize()
                .smoothClickable {
                    isPlaying = !isPlaying
                    if (isPlaying) exoPlayer.play() else exoPlayer.pause()
                }
        )

        // Top gradient scrim — behind chips
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                    )
                )
        )

        // Bottom gradient scrim — behind buttons + title
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                    )
                )
        )

        // Mute button — top right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 16.dp, top = 56.dp) // below chips row
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .smoothClickable {
                    isMuted = !isMuted
                    exoPlayer.volume = if (isMuted) 0f else 1f
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Pause indicator — center
        if (!isPlaying) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Paused",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Title — above bottom buttons
        if (!reel.title.isNullOrEmpty()) {
            Text(
                reel.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, bottom = 74.dp, end = 16.dp)
            )
        }

        // Bottom action buttons row — like reference app
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // WhatsApp
            Button(
                onClick = {
                    if (whatsAppState == DownloadState.IDLE) {
                        whatsAppState = DownloadState.DOWNLOADING
                        scope.launch {
                            onWhatsApp()
                            whatsAppState = DownloadState.DONE
                            delay(2500)
                            whatsAppState = DownloadState.IDLE
                        }
                    }
                },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (whatsAppState) {
                        DownloadState.DONE -> Color(0xFF128C7E)
                        else -> Color(0xFF25D366)
                    }
                ),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                when (whatsAppState) {
                    DownloadState.DOWNLOADING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Opening...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    DownloadState.DONE -> {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Shared",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Sharing...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    DownloadState.IDLE -> {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_whatsapp),
                            contentDescription = "WhatsApp",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("WhatsApp", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Share
            Button(
                onClick = onShare,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "Share",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Share", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            // Download
            Button(
                onClick = {
                    if (downloadState == DownloadState.IDLE) {
                        downloadState = DownloadState.DOWNLOADING
                        onDownload()
                        scope.launch {
                            delay(1200)
                            downloadState = DownloadState.DONE
                            delay(2500)
                            downloadState = DownloadState.IDLE
                        }
                    }
                },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (downloadState) {
                        DownloadState.DONE -> Color(0xFF065F46)
                        else -> Color.White.copy(alpha = 0.15f)
                    }
                ),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                when (downloadState) {
                    DownloadState.DOWNLOADING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Saving...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    DownloadState.DONE -> {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Downloaded",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Saved!", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    DownloadState.IDLE -> {
                        Icon(
                            imageVector = Icons.Filled.FileDownload,
                            contentDescription = "Download",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Download", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

sealed class ActionIcon {
    data class Vector(val imageVector: ImageVector) : ActionIcon()
    data class Drawable(@androidx.annotation.DrawableRes val resId: Int) : ActionIcon()
}

@Composable
fun ActionButton(
    icon: ActionIcon,
    label: String,
    onClick: () -> Unit,
    accentColor: Color? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.smoothClickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    accentColor?.copy(alpha = 0.3f)
                        ?: Color.Black.copy(alpha = 0.55f)
                ),
            contentAlignment = Alignment.Center
        ) {
            when (icon) {
                is ActionIcon.Vector -> Icon(
                    imageVector = icon.imageVector,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
                is ActionIcon.Drawable -> Icon(
                    painter = androidx.compose.ui.res.painterResource(id = icon.resId),
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}