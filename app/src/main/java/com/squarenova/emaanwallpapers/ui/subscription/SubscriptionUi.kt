package com.squarenova.emaanwallpapers.ui.subscription

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
