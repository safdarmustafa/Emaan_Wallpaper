package com.squarenova.emaanwallpapers.ui.subscription

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.squarenova.emaanwallpapers.R

/**
 * Warm cream + forest palette for the subscription experience.
 */
object PremiumSubscriptionColors {
    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFF0F5F2)
    val BorderSubtle = Color(0xFF1A5F48).copy(alpha = 0.12f)
    val TextPrimary = Color(0xFF14352C)
    val TextSecondary = Color(0xFF4A6B60)
    val TextMuted = Color(0xFF6B857C)
    val Forest = Color(0xFF1A5F48)
    val ForestDeep = Color(0xFF0F3D2E)
    val Gold = Color(0xFFC4A35A)
    val GoldSoft = Color(0xFFE8D5A3)
    val Cream = Color(0xFFFBF6EB)
    val CreamDeep = Color(0xFFF3E8D4)
    val SkyMist = Color(0xFFE8F0EA)
}

const val PAYWALL_PREVIEW_VIDEO_URL =
    "https://pub-5fe1ab41be6f4abf8e8fda19730ecfd9.r2.dev/con/imman.mp4"

@Composable
fun PremiumScreenBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PremiumSubscriptionColors.Cream),
    ) {
        // Full-bleed illustrated arch / lanterns / palms behind the video card.
        Image(
            painter = painterResource(id = R.drawable.paywall_islamic_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
        )
        // Soft cream veil over the lower half so pricing/CTA stay readable.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.42f to Color.Transparent,
                            0.62f to PremiumSubscriptionColors.Cream.copy(alpha = 0.55f),
                            0.82f to PremiumSubscriptionColors.Cream.copy(alpha = 0.92f),
                            1.00f to PremiumSubscriptionColors.Cream,
                        ),
                    ),
                ),
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PaywallPreviewVideo(
    videoUrl: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isResumed = rememberUpdatedState(true)

    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 1f
            playWhenReady = true
            prepare()
        }
    }

    LaunchedEffect(Unit) {
        exoPlayer.playWhenReady = true
        exoPlayer.play()
    }

    DisposableEffect(videoUrl) {
        onDispose { exoPlayer.release() }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> {
                    exoPlayer.playWhenReady = false
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (isResumed.value) {
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

    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .border(1.5.dp, PremiumSubscriptionColors.GoldSoft.copy(alpha = 0.65f), shape)
            .background(Color(0xFF1A2E26)),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    // ZOOM fills the card edge-to-edge (no green letterbox bars).
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun PaywallHeadlineBanner(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                color = Color.White.copy(alpha = 0.72f),
                shape = RoundedCornerShape(50),
            )
            .border(
                width = 1.dp,
                color = PremiumSubscriptionColors.Gold.copy(alpha = 0.35f),
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MosqueDomeGlyph(modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = PremiumSubscriptionColors.Forest,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.width(8.dp))
        MosqueDomeGlyph(modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MosqueDomeGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val dome = Path().apply {
            moveTo(w * 0.18f, h * 0.62f)
            quadraticTo(w * 0.5f, h * 0.05f, w * 0.82f, h * 0.62f)
            lineTo(w * 0.82f, h * 0.92f)
            lineTo(w * 0.18f, h * 0.92f)
            close()
        }
        drawPath(dome, color = PremiumSubscriptionColors.Forest)
        drawCircle(
            color = PremiumSubscriptionColors.Gold,
            radius = w * 0.08f,
            center = Offset(w * 0.5f, h * 0.18f),
        )
    }
}

@Composable
fun PaywallFeatureArchCard(
    title: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.height(118.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 2.2.dp.toPx()
            val path = Path().apply {
                val archH = size.height * 0.28f
                val side = 10.dp.toPx()
                moveTo(side, archH)
                cubicTo(
                    side,
                    4.dp.toPx(),
                    size.width - side,
                    4.dp.toPx(),
                    size.width - side,
                    archH,
                )
                lineTo(size.width - side, size.height - side)
                quadraticTo(
                    size.width - side,
                    size.height - 2.dp.toPx(),
                    size.width - side * 1.6f,
                    size.height - 2.dp.toPx(),
                )
                lineTo(side * 1.6f, size.height - 2.dp.toPx())
                quadraticTo(
                    side,
                    size.height - 2.dp.toPx(),
                    side,
                    size.height - side,
                )
                close()
            }
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.55f),
            )
            drawPath(
                path = path,
                color = PremiumSubscriptionColors.Gold.copy(alpha = 0.85f),
                style = Stroke(width = stroke),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PremiumSubscriptionColors.Forest.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                color = PremiumSubscriptionColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
                maxLines = 2,
            )
        }
    }
}

@Composable
fun PremiumGradientCtaButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    val canTap = enabled && !loading
    Button(
        onClick = onClick,
        enabled = canTap,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PremiumSubscriptionColors.Forest,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF2A4038),
            disabledContentColor = Color.White.copy(alpha = 0.72f),
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
    ) {
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(com.squarenova.emaanwallpapers.R.string.premium_wait),
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        } else {
            Text(
                text = text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun TrialActivatedSuccessOverlay(
    visible: Boolean,
    onContinue: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(320)),
        exit = fadeOut(animationSpec = tween(200)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xE6F6F3EC)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .background(PremiumSubscriptionColors.Surface, RoundedCornerShape(20.dp))
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(com.squarenova.emaanwallpapers.R.string.premium_started),
                    color = PremiumSubscriptionColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(com.squarenova.emaanwallpapers.R.string.premium_started_body),
                    color = PremiumSubscriptionColors.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                PremiumGradientCtaButton(
                    text = stringResource(com.squarenova.emaanwallpapers.R.string.premium_continue),
                    onClick = onContinue,
                    enabled = true,
                    loading = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
