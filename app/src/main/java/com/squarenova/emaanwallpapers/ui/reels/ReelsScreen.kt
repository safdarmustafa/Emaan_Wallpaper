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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.squarenova.emaanwallpapers.analytics.AnalyticsManager
import com.squarenova.emaanwallpapers.ui.components.smoothClickable
import com.squarenova.emaanwallpapers.BuildConfig
import com.squarenova.emaanwallpapers.network.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
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
fun ReelsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var reels by remember { mutableStateOf<List<ReelRow>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var isSharingToWhatsApp by remember { mutableStateOf(false) }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2500)
            toastMessage = null
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            reels = SupabaseClient.client
                .postgrest["reels"]
                .select(columns = Columns.ALL) {
                    order("created_at", Order.DESCENDING)
                }
                .decodeList()

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
                val pagerState = rememberPagerState(pageCount = { reels.size })

                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    ReelItem(
                        reel = reels[page],
                        isVisible = pagerState.currentPage == page,
                        onDownload = {
                            AnalyticsManager.trackEvent("Reels - Download Tapped", mapOf("reel_id" to reels[page].id))
                            downloadVideo(context, reels[page].url, reels[page].title)
                            toastMessage = "Downloading... check notifications 📥"
                        },
                        onShare = {
                            AnalyticsManager.trackEvent("Reels - Share Tapped", mapOf("reel_id" to reels[page].id))
                            shareVideo(context, reels[page].url, reels[page].title)
                        },
                        onWhatsApp = {
                            if (isSharingToWhatsApp) return@ReelItem
                            AnalyticsManager.trackEvent("Reels - WhatsApp Tapped", mapOf("reel_id" to reels[page].id))
                            isSharingToWhatsApp = true
                            scope.launch {
                                val success = shareVideoToWhatsApp(
                                    context, reels[page].url, reels[page].title
                                )
                                isSharingToWhatsApp = false
                                toastMessage = if (success) "Opening WhatsApp... 📱" else "Sharing link instead"
                            }
                        }
                    )
                }
            }
        }

        // WhatsApp sharing overlay
        if (isSharingToWhatsApp) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color(0xFFD4AF37),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Preparing video for WhatsApp...",
                        color = Color.White,
                        fontSize = 14.sp
                    )
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
    val currentIsVisible by rememberUpdatedState(isVisible)
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }

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

        // ✅ PlayerView — prepare() called inside factory after surface is ready
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    // ✅ Attach player THEN prepare — this fixes black screen
                    player = exoPlayer
                    exoPlayer.prepare()
                }
            },
            update = { view ->
                view.player = exoPlayer
            },
            modifier = Modifier
                .fillMaxSize()
                .smoothClickable {
                        AnalyticsManager.trackEvent("Reels - Video Tapped", mapOf("action" to (if (isPlaying) "pause" else "play")))
                        isPlaying = !isPlaying
                        if (isPlaying) exoPlayer.play() else exoPlayer.pause()
                    }
        )

        // Top gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
        )

        // Bottom gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )

        // Top bar — category + mute
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!reel.category.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFD4AF37),
                                    Color(0xFFB8860B)
                                )
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        reel.category,
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            // Mute toggle
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .smoothClickable {
                        AnalyticsManager.trackEvent("Reels - Mute Toggled", mapOf("muted" to (!isMuted).toString()))
                        isMuted = !isMuted
                        exoPlayer.volume = if (isMuted) 0f else 1f
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(if (isMuted) "🔇" else "🔊", fontSize = 16.sp)
            }
        }

        // Pause indicator
        if (!isPlaying) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Text("⏸", fontSize = 32.sp)
            }
        }

        // Right side action buttons
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            ActionButton(
                emoji = "💬",
                label = "WhatsApp",
                onClick = onWhatsApp,
                accentColor = Color(0xFF25D366)
            )
            ActionButton(emoji = "↗️", label = "Share", onClick = onShare)
            ActionButton(
                emoji = "⬇️",
                label = "Download",
                onClick = onDownload,
                accentColor = Color(0xFFD4AF37)
            )
        }

        // Bottom info
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 90.dp, bottom = 28.dp)
                .navigationBarsPadding()
        ) {
            if (!reel.title.isNullOrEmpty()) {
                Text(
                    reel.title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            Text(
                "Swipe up for more ↑",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun ActionButton(
    emoji: String,
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
            Text(emoji, fontSize = 24.sp)
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