package com.squarenova.emaanwallpapers.ui.reels

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.squarenova.emaanwallpapers.network.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

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

@OptIn(UnstableApi::class)
@Composable
fun ReelsScreen() {
    val context = LocalContext.current

    var reels by remember { mutableStateOf<List<ReelRow>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

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
                .select(columns = Columns.ALL)
                .decodeList()
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
                CircularProgressIndicator(
                    color = Color(0xFFD4AF37),
                    modifier = Modifier.align(Alignment.Center),
                    strokeWidth = 3.dp
                )
            }

            reels.isEmpty() -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🎬", fontSize = 64.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No reels yet", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Coming soon insha'Allah", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
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
                            downloadVideo(context, reels[page].url, reels[page].title)
                            toastMessage = "Downloading... check notifications 📥"
                        },
                        onShare = { shareVideo(context, reels[page].url, reels[page].title) },
                        onWhatsApp = {
                            try {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    `package` = "com.whatsapp"
                                    putExtra(Intent.EXTRA_TEXT, "${reels[page].title ?: "Islamic Reel"}\n\n${reels[page].url}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                shareVideo(context, reels[page].url, reels[page].title)
                            }
                        }
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
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(toastMessage ?: "", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
                .clickable {
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
                        .background(Color(0xFFD4AF37).copy(alpha = 0.9f))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(reel.category, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            // Mute toggle
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable {
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
                .padding(end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ActionButton(emoji = "💬", label = "WhatsApp", onClick = onWhatsApp)
            ActionButton(emoji = "↗️", label = "Share", onClick = onShare)
            ActionButton(emoji = "⬇️", label = "Download", onClick = onDownload)
        }

        // Bottom info
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 80.dp, bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            if (!reel.title.isNullOrEmpty()) {
                Text(reel.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text("Swipe up for more ↑", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
    }
}

@Composable
fun ActionButton(emoji: String, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 22.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}