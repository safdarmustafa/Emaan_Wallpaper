package com.squarenova.emaanwallpapers.ui.ringtone.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.squarenova.emaanwallpapers.theme.BrandGreen

@Composable
fun PlayerSlider(
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    val target = if (duration > 0) currentPosition.toFloat() else 0f

    // Smoothly interpolate playback progress so the thumb glides instead of stepping.
    val animatedValue by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 260),
        label = "sliderProgress"
    )

    Slider(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp),
        value = if (isDragging) dragValue else animatedValue,
        onValueChange = {
            isDragging = true
            dragValue = it
        },
        onValueChangeFinished = {
            onSeek(dragValue.toLong())
            isDragging = false
        },
        valueRange = 0f..maxOf(duration.toFloat(), 1f),
        colors = SliderDefaults.colors(
            thumbColor = BrandGreen,
            activeTrackColor = BrandGreen,
            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
        )
    )
}
