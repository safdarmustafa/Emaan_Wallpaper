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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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

/**
 * Calm premium palette for subscription — cream canvas, deep forest green, soft sage, subtle gold.
 */
object PremiumSubscriptionColors {
    val Background = Color(0xFFF7F5F0)
    val BackgroundSoft = Color(0xFFEEF4F1)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceElevated = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFF0F5F2)
    val BorderSubtle = Color(0xFF0D5C4B).copy(alpha = 0.12f)
    val TextPrimary = Color(0xFF0D5C4B)
    val TextSecondary = Color(0xFF4A6B60)
    val TextMuted = Color(0xFF6B857C)
    val Forest = Color(0xFF0D5C4B)
    val ForestDeep = Color(0xFF094437)
    val Sage = Color(0xFFA8C5B5)
    val Gold = Color(0xFFC9A227)
    val GoldLight = Color(0xFFE8D48B)
    val GoldDeep = Color(0xFFA8841C)
}

@Composable
fun PremiumScreenBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PremiumSubscriptionColors.BackgroundSoft,
                        PremiumSubscriptionColors.Background,
                        Color(0xFFF3F0E9),
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
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = Color.White.copy(alpha = 0.7f),
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    if (enabled && !loading) {
                        Brush.horizontalGradient(
                            listOf(
                                PremiumSubscriptionColors.ForestDeep,
                                PremiumSubscriptionColors.Forest,
                            ),
                        )
                    } else {
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF9BB5AB),
                                Color(0xFF8AA89C),
                            ),
                        )
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "कृपया प्रतीक्षा करें…",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Color.White,
                    )
                }
            } else {
                Text(
                    text = text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
fun PremiumFeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(PremiumSubscriptionColors.Forest.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PremiumSubscriptionColors.Forest,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = PremiumSubscriptionColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = PremiumSubscriptionColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(PremiumSubscriptionColors.Forest.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = PremiumSubscriptionColors.Forest,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * Full-screen interstitial before the mandate step: explains AutoPay approval (not a second “payment”).
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
                .background(Color(0xCC0D5C4B)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                shape = RoundedCornerShape(24.dp),
                color = PremiumSubscriptionColors.Surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, PremiumSubscriptionColors.BorderSubtle),
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "आपका subscription सेट हो रहा है…",
                        color = PremiumSubscriptionColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 28.sp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Free Trial और AutoPay सेटअप एक साथ होगा।",
                        color = PremiumSubscriptionColors.TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "अभी सिर्फ ₹5 लगेगा — यह refundable है",
                        color = PremiumSubscriptionColors.Forest,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "इसके साथ AutoPay ₹249/माह भी confirm हो जाएगा",
                        color = PremiumSubscriptionColors.TextMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(22.dp))
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
                .background(Color(0xD90D5C4B)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(28.dp),
                color = PremiumSubscriptionColors.Surface,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, PremiumSubscriptionColors.Gold.copy(alpha = 0.35f)),
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Trial शुरू हो गया",
                        color = PremiumSubscriptionColors.TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "आपके पास 1 दिन के लिए Premium access है",
                        color = PremiumSubscriptionColors.TextSecondary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(28.dp))
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
}

@Composable
fun PremiumPricingHighlightCard(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = PremiumSubscriptionColors.Surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, PremiumSubscriptionColors.BorderSubtle),
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "पहले 1 दिन का Free Trial",
                        color = PremiumSubscriptionColors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 30.sp,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "अभी ₹5 (refundable) · Trial के बाद ₹249/माह",
                        color = PremiumSubscriptionColors.TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 22.sp,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PremiumSubscriptionColors.Gold.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, PremiumSubscriptionColors.Gold.copy(alpha = 0.35f)),
                ) {
                    Text(
                        text = "ट्रायल",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = PremiumSubscriptionColors.GoldDeep,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = PremiumSubscriptionColors.BorderSubtle)
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = PremiumSubscriptionColors.Forest,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "₹5 trial fee refundable है",
                    color = PremiumSubscriptionColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = PremiumSubscriptionColors.Forest,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Payment Razorpay द्वारा सुरक्षित रूप से process होता है",
                    color = PremiumSubscriptionColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}
