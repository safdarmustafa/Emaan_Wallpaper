package com.squarenova.emaanwallpapers.ui.ringtone.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.squarenova.emaanwallpapers.theme.BrandGreen

/**
 * A lightweight animated 4-bar equalizer shown beside the title while a ringtone is playing.
 */
@Composable
fun AudioEqualizer(
    modifier: Modifier = Modifier,
    barColor: Color = BrandGreen,
    maxBarHeight: Int = 18
) {
    val transition = rememberInfiniteTransition(label = "eq")
    val durations = listOf(520, 700, 430, 600)

    Row(
        modifier = modifier.height(maxBarHeight.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        durations.forEachIndexed { index, duration ->
            val fraction by transition.animateFloat(
                initialValue = 0.28f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = duration, delayMillis = index * 60),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar$index"
            )

            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height((maxBarHeight * fraction).dp.coerceAtLeast(2.dp))
                    .background(barColor, RoundedCornerShape(2.dp))
            )
        }
    }
}
