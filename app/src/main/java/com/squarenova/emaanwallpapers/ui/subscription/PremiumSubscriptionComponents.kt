package com.squarenova.emaanwallpapers.ui.subscription

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Netflix / Spotify–style dark premium palette for subscription flows. */
object PremiumSubscriptionColors {
    val Background = Color(0xFF0A0A0B)
    val Surface = Color(0xFF141416)
    val SurfaceElevated = Color(0xFF1C1C1F)
    val BorderSubtle = Color.White.copy(alpha = 0.08f)
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFF8E8E93)
    val Gold = Color(0xFFD4AF37)
    val GoldLight = Color(0xFFE8C547)
    val GoldDeep = Color(0xFFB8860B)
}

@Composable
fun PremiumScreenBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D0D0F),
                        PremiumSubscriptionColors.Background,
                        Color(0xFF121214)
                    )
                )
            )
    )
}

@Composable
fun PremiumGradientCtaButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    loading: Boolean = false
) {
    val brush = Brush.horizontalGradient(
        colors = listOf(
            PremiumSubscriptionColors.GoldDeep,
            PremiumSubscriptionColors.Gold,
            PremiumSubscriptionColors.GoldLight
        )
    )
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color(0xFF0A0A0B),
            disabledContainerColor = Color(0xFF3A3A3C),
            disabledContentColor = Color(0xFF636366)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 6.dp,
            pressedElevation = 2.dp,
            disabledElevation = 0.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (enabled && !loading) brush else Brush.horizontalGradient(listOf(Color(0xFF3A3A3C), Color(0xFF48484A)))),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color(0xFF0A0A0B),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Processing…",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Color(0xFF0A0A0B)
                    )
                }
            } else {
                Text(
                    text = text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (enabled) Color(0xFF0A0A0B) else Color(0xFFAEAEB2)
                )
            }
        }
    }
}

@Composable
fun PremiumFeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(PremiumSubscriptionColors.Gold.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PremiumSubscriptionColors.Gold,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = PremiumSubscriptionColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = PremiumSubscriptionColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = PremiumSubscriptionColors.Gold.copy(alpha = 0.85f),
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Full-screen interstitial after ₹5 success: explains mandate step (not a second “payment”).
 */
@Composable
fun SubscriptionSetupFullScreenOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(280)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xE6000000)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                shape = RoundedCornerShape(24.dp),
                color = PremiumSubscriptionColors.SurfaceElevated,
                tonalElevation = 0.dp,
                shadowElevation = 16.dp,
                border = BorderStroke(1.dp, PremiumSubscriptionColors.Gold.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = PremiumSubscriptionColors.Gold,
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "Setting up your subscription…",
                        color = PremiumSubscriptionColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Next step: Approve automatic payments of ₹99/month",
                        color = PremiumSubscriptionColors.TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "No charges today",
                        color = PremiumSubscriptionColors.Gold.copy(alpha = 0.95f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = PremiumSubscriptionColors.Gold,
                        trackColor = Color.White.copy(alpha = 0.08f)
                    )
                }
            }
        }
    }
}

@Composable
fun TrialActivatedSuccessOverlay(
    visible: Boolean,
    onContinue: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(320)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF2000000)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(28.dp),
                color = PremiumSubscriptionColors.SurfaceElevated,
                shadowElevation = 20.dp,
                border = BorderStroke(1.dp, PremiumSubscriptionColors.Gold.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🎉 Trial Activated",
                        color = PremiumSubscriptionColors.TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "You have premium access for 3 days",
                        color = PremiumSubscriptionColors.TextSecondary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(28.dp))
                    PremiumGradientCtaButton(
                        text = "Continue",
                        onClick = onContinue,
                        enabled = true,
                        loading = false
                    )
                }
            }
        }
    }
}

@Composable
fun PremiumPricingHighlightCard(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = PremiumSubscriptionColors.SurfaceElevated,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    PremiumSubscriptionColors.Gold.copy(alpha = 0.5f),
                    PremiumSubscriptionColors.Gold.copy(alpha = 0.15f),
                    PremiumSubscriptionColors.Gold.copy(alpha = 0.4f)
                )
            )
        )
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "₹5 today",
                        color = PremiumSubscriptionColors.TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "₹99/month after 3 days",
                        color = PremiumSubscriptionColors.TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PremiumSubscriptionColors.Gold.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, PremiumSubscriptionColors.Gold.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "BEST VALUE",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = PremiumSubscriptionColors.Gold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = PremiumSubscriptionColors.BorderSubtle)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = PremiumSubscriptionColors.Gold,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Cancel anytime",
                    color = PremiumSubscriptionColors.TextSecondary,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = PremiumSubscriptionColors.Gold,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Secure payments via Razorpay",
                    color = PremiumSubscriptionColors.TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}
