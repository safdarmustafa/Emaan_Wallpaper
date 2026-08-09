package com.squarenova.emaanwallpapers.ui.ringtone

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squarenova.emaanwallpapers.data.model.Ringtone
import com.squarenova.emaanwallpapers.theme.BrandGreen
import com.squarenova.emaanwallpapers.ui.ringtone.components.PlayerSlider

private val RowSurface = Color(0xFF141414)
private val RowSurfaceActive = Color(0xFF182018)
private val RowBorder = Color(0xFF262626)
private val RowBorderActive = BrandGreen.copy(alpha = 0.55f)
private val TextPrimaryDark = Color(0xFFF2F3F2)
private val TextSecondaryDark = Color(0xFF9AA39C)
private val SetButtonFill = Color(0xFF1A1A1A)

@Composable
fun RingtoneCard(
    ringtone: Ringtone,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    isSelected: Boolean = false,
    @Suppress("UNUSED_PARAMETER") gradientIndex: Int = 0,
    isDownloaded: Boolean = false,
    onSeek: (Long) -> Unit,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit = {},
    onSetRingtoneClick: () -> Unit = {},
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) RowSurfaceActive else RowSurface,
        border = BorderStroke(1.dp, if (isSelected) RowBorderActive else RowBorder),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactPlayButton(
                    isPlaying = isPlaying,
                    onClick = onPlayClick,
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ringtone.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${ringtone.category} • ${formatDuration((duration / 1000).toInt())}",
                        fontSize = 12.sp,
                        color = TextSecondaryDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onDownloadClick,
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        imageVector = if (isDownloaded) Icons.Default.Check else Icons.Default.Download,
                        contentDescription = if (isDownloaded) "Saved" else "Download",
                        tint = BrandGreen,
                        modifier = Modifier.size(20.dp),
                    )
                }

                CompactSetButton(onClick = onSetRingtoneClick)
            }

            if (isSelected) {
                Spacer(modifier = Modifier.height(8.dp))
                PlayerSlider(
                    currentPosition = currentPosition,
                    duration = duration,
                    onSeek = onSeek,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatDuration((currentPosition / 1000).toInt()),
                        fontSize = 11.sp,
                        color = TextSecondaryDark,
                    )
                    Text(
                        text = formatDuration((duration / 1000).toInt()),
                        fontSize = 11.sp,
                        color = TextSecondaryDark,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(
                if (isPlaying) {
                    Modifier.background(BrandGreen)
                } else {
                    Modifier
                        .border(1.5.dp, BrandGreen.copy(alpha = 0.75f), CircleShape)
                        .background(Color.Transparent)
                },
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = if (isPlaying) Color.White else BrandGreen,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun CompactSetButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = SetButtonFill,
        border = BorderStroke(1.dp, BrandGreen.copy(alpha = 0.65f)),
    ) {
        Text(
            text = "Set",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = TextPrimaryDark,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val minutes = safe / 60
    val seconds = safe % 60
    return "%02d:%02d".format(minutes, seconds)
}
