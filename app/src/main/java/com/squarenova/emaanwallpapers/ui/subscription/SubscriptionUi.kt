package com.squarenova.emaanwallpapers.ui.subscription

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import kotlinx.coroutines.delay
import java.time.format.DateTimeParseException

private val GoldStart = Color(0xFFD4AF37)
private val GoldEnd = Color(0xFFFFD700)
private val TrialOrange = Color(0xFFFF9800)
private val TrialYellow = Color(0xFFFFC107)
private val EndingBg = Color(0xFF9E9E9E)
private val EndingAccent = Color(0xFFFF6F00)

@Composable
fun PremiumBadge(
    isSubscribed: Boolean,
    subscriptionStatus: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val hPad = if (compact) 8.dp else 10.dp
    val vPad = if (compact) 3.dp else 4.dp
    val fontSize = if (compact) 10.sp else 12.sp
    val shape = if (compact) RoundedCornerShape(12.dp) else RoundedCornerShape(20.dp)
    val s = subscriptionStatus?.lowercase()
    when {
        s == "trial" -> {
            Surface(
                shape = shape,
                color = TrialOrange.copy(alpha = 0.2f),
                modifier = modifier,
                border = BorderStroke(1.dp, TrialYellow.copy(alpha = 0.8f))
            ) {
                Text(
                    "TRIAL",
                    modifier = Modifier.padding(horizontal = hPad, vertical = vPad),
                    color = TrialOrange,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        s == "cancelled" -> {}
        s == "cancel_requested" -> {
            Surface(
                shape = shape,
                color = EndingBg.copy(alpha = 0.35f),
                modifier = modifier,
                border = BorderStroke(1.dp, EndingAccent.copy(alpha = 0.6f))
            ) {
                Text(
                    "ENDING SOON",
                    modifier = Modifier.padding(horizontal = hPad, vertical = vPad),
                    color = Color(0xFF424242),
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        isSubscribed -> {
            Box(
                modifier = modifier
                    .background(
                        brush = Brush.horizontalGradient(listOf(GoldStart, GoldEnd)),
                        shape = shape
                    )
                    .padding(horizontal = hPad, vertical = vPad)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("👑", fontSize = if (compact) 10.sp else 11.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "PREMIUM",
                        color = Color(0xFF1A1A1A),
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        else -> {}
    }
}

private fun parseTrialEndMillis(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return try {
        Instant.parse(iso).toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}

@Composable
fun TrialCountdown(
    trialEndIso: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(trialEndIso) {
        tick = 0
        while (true) {
            delay(60_000L)
            tick++
        }
    }

    val endMs = remember(trialEndIso) { parseTrialEndMillis(trialEndIso) }
    val now = System.currentTimeMillis()
    val text: String
    val color: Color

    if (endMs == null) {
        text = "Trial"
        color = Color(0xFF2E7D32)
    } else {
        val remaining = endMs - now
        if (remaining <= 0L) {
            text = "Trial expired"
            color = Color(0xFFC62828)
        } else {
            val dayMs = 86_400_000L
            val hourMs = 3_600_000L
            val minMs = 60_000L
            val d = remaining / dayMs
            val h = (remaining % dayMs) / hourMs
            val m = (remaining % hourMs) / minMs
            text = "Trial ends in ${d}d ${h}h ${m}m"
            color = if (remaining <= 12 * hourMs) Color(0xFFC62828) else Color(0xFF2E7D32)
        }
    }

    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = if (compact) 11.sp else 13.sp,
        fontWeight = FontWeight.Medium
    )
}

/**
 * One subtle line inside the home header (teal gradient) — no extra band, no wallpaper space lost.
 */
@Composable
fun HomeHeaderSubscriptionCaption(subscriptionStatus: String?) {
    val s = subscriptionStatus?.lowercase()
    val label = when (s) {
        "trial" -> "Premium trial · full access"
        "cancel_requested" -> "Plan not renewing · access until period ends"
        else -> return
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = label,
        color = Color.White.copy(alpha = 0.75f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun SubscriptionBanner(
    @Suppress("UNUSED_PARAMETER") isSubscribed: Boolean,
    subscriptionStatus: String?,
    @Suppress("UNUSED_PARAMETER") modifier: Modifier = Modifier
) {
    HomeHeaderSubscriptionCaption(subscriptionStatus)
}
