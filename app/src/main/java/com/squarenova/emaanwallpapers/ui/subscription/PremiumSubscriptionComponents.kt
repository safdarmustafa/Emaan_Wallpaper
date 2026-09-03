package com.squarenova.emaanwallpapers.ui.subscription

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Warm cream + forest palette for the subscription experience.
 */
object PremiumSubscriptionColors {
    val Background = Color(0xFFF6F3EC)
    val BackgroundSoft = Color(0xFFEEF4F0)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceElevated = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFF0F5F2)
    val BorderSubtle = Color(0xFF1A5F48).copy(alpha = 0.12f)
    val TextPrimary = Color(0xFF14352C)
    val TextSecondary = Color(0xFF4A6B60)
    val TextMuted = Color(0xFF6B857C)
    val Forest = Color(0xFF1A5F48)
    val ForestDeep = Color(0xFF134636)
    val ForestCard = Color(0xFFFFFFFF)
    val Sage = Color(0xFFA8C5B5)
    val Gold = Color(0xFFB8943A)
    val GoldLight = Color(0xFFD6C08A)
    val GoldDeep = Color(0xFF9A7D3A)
    val GoldBright = Color(0xFFB8943A)
    val CtaGreen = Color(0xFF1A5F48)
    val CtaGreenLight = Color(0xFF1F6B52)
}

@Composable
fun PremiumScreenBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF8F5EE),
                        Color(0xFFF3F6F2),
                        Color(0xFFEEF3EF),
                    ),
                ),
            ),
    )
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
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
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
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "कृपया प्रतीक्षा करें…",
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = Color.White,
                )
            }
        } else {
            Text(
                text = text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Color.White,
            )
        }
    }
}

/**
 * Full-screen interstitial before the mandate step.
 */
@Composable
fun SubscriptionSetupFullScreenOverlay(
    visible: Boolean,
    ctaText: String = "आगे बढ़ें",
    ctaEnabled: Boolean = true,
    onContinue: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(280)),
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
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "आपका subscription सेट हो रहा है",
                    color = PremiumSubscriptionColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    lineHeight = 28.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "आगे बढ़ने पर सिर्फ ₹5 लगेगा।",
                    color = PremiumSubscriptionColors.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                PremiumGradientCtaButton(
                    text = ctaText,
                    onClick = onContinue,
                    enabled = ctaEnabled,
                    loading = false,
                )
            }
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
                    text = "Premium शुरू हो गया",
                    color = PremiumSubscriptionColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "अब आप Premium content का आनंद ले सकते हैं।",
                    color = PremiumSubscriptionColors.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                PremiumGradientCtaButton(
                    text = "Premium जारी रखें",
                    onClick = onContinue,
                    enabled = true,
                    loading = false,
                )
            }
        }
    }
}
