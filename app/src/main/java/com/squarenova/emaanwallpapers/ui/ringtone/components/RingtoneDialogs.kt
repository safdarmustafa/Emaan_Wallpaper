package com.squarenova.emaanwallpapers.ui.ringtone.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.squarenova.emaanwallpapers.theme.BrandGreen
import com.squarenova.emaanwallpapers.theme.BrandGreenDark

/**
 * Animated circular check mark drawn with a stroke path animation and a soft pulse ring.
 */
@Composable
private fun AnimatedSuccessCheck(
    accent: Color = BrandGreen
) {
    val progress = remember { Animatable(0f) }
    val ringScale = remember { Animatable(0.6f) }

    LaunchedEffect(Unit) {
        ringScale.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(650, delayMillis = 120, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = Modifier.size(96.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .scale(ringScale.value)
                .background(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.18f), accent.copy(alpha = 0.02f))
                    ),
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(ringScale.value)
                .background(accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(38.dp)) {
                val w = size.width
                val h = size.height
                val p1 = Offset(w * 0.18f, h * 0.52f)
                val p2 = Offset(w * 0.42f, h * 0.74f)
                val p3 = Offset(w * 0.82f, h * 0.28f)

                val seg1 = 0.42f
                val t = progress.value

                if (t > 0f) {
                    val f1 = (t / seg1).coerceIn(0f, 1f)
                    drawLine(
                        color = Color.White,
                        start = p1,
                        end = Offset(
                            p1.x + (p2.x - p1.x) * f1,
                            p1.y + (p2.y - p1.y) * f1
                        ),
                        strokeWidth = 7f,
                        cap = StrokeCap.Round
                    )
                }
                if (t > seg1) {
                    val f2 = ((t - seg1) / (1f - seg1)).coerceIn(0f, 1f)
                    drawLine(
                        color = Color.White,
                        start = p2,
                        end = Offset(
                            p2.x + (p3.x - p2.x) * f2,
                            p2.y + (p3.y - p2.y) * f2
                        ),
                        strokeWidth = 7f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun BasePremiumDialog(
    onDismiss: () -> Unit,
    dismissable: Boolean = true,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = { if (dismissable) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = dismissable,
            dismissOnClickOutside = dismissable
        )
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            tonalElevation = 6.dp,
            shadowElevation = 24.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                content()
            }
        }
    }
}

@Composable
fun RingtoneSuccessDialog(
    title: String,
    onDone: () -> Unit
) {
    BasePremiumDialog(onDismiss = onDone) {
        AnimatedSuccessCheck()
        androidx.compose.foundation.layout.Spacer(Modifier.size(18.dp))
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = BrandGreenDark,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Text(
            text = "Successfully set as your default ringtone.",
            fontSize = 14.sp,
            color = Color(0xFF5F6B62),
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
        ) {
            Text("Done", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
fun DownloadSuccessDialog(
    onDone: () -> Unit
) {
    BasePremiumDialog(onDismiss = onDone) {
        AnimatedSuccessCheck()
        androidx.compose.foundation.layout.Spacer(Modifier.size(18.dp))
        Text(
            text = "Downloaded Successfully",
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = BrandGreenDark,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Text(
            text = "Your ringtone is saved and ready to use.",
            fontSize = 14.sp,
            color = Color(0xFF5F6B62),
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
        ) {
            Text("Done", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
fun RingtoneFailureDialog(
    message: String = "Couldn't set automatically.",
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    BasePremiumDialog(onDismiss = onDismiss) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(Color(0xFFFFEBEE), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("!", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD84343))
        }
        androidx.compose.foundation.layout.Spacer(Modifier.size(18.dp))
        Text(
            text = message,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2A2A2A),
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Text(
            text = "Open ringtone settings?",
            fontSize = 14.sp,
            color = Color(0xFF5F6B62),
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp))
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandGreen)
        ) {
            Text("Open Settings", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
        }
        androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Cancel", modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
fun RingtoneProcessingDialog(
    message: String
) {
    BasePremiumDialog(onDismiss = {}, dismissable = false) {
        CircularProgressIndicator(
            color = BrandGreen,
            strokeWidth = 3.dp,
            modifier = Modifier.size(48.dp)
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(20.dp))
        Text(
            text = message,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF2A2A2A),
            textAlign = TextAlign.Center
        )
    }
}
