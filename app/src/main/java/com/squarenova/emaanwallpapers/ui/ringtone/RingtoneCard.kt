package com.squarenova.emaanwallpapers.ui.ringtone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squarenova.emaanwallpapers.data.model.Ringtone
import com.squarenova.emaanwallpapers.theme.BrandGreen

@Composable
fun RingtoneCard(
    ringtone: Ringtone,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit = {},
    onSetRingtoneClick: () -> Unit = {}
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .background(
                            Color(0xFFE8F5E9),
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎵",
                        fontSize = 30.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = ringtone.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = ringtone.category,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }

                IconButton(
                    onClick = onPlayClick
                ) {

                    Icon(
                        imageVector = if (isPlaying)
                            Icons.Default.Pause
                        else
                            Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = BrandGreen
                    )

                }

            }

            Spacer(modifier = Modifier.height(18.dp))

            Row {

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onDownloadClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEDEDED),
                        contentColor = Color.Black
                    )
                ) {

                    Icon(Icons.Default.Download, null)

                    Spacer(modifier = Modifier.width(8.dp))

                    Text("Download")
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onSetRingtoneClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandGreen
                    )
                ) {

                    Icon(Icons.Default.PhoneAndroid, null)

                    Spacer(modifier = Modifier.width(8.dp))

                    Text("Set Ringtone")

                }

            }

        }

    }
}