package com.squarenova.emaanwallpapers.ui.subscription

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squarenova.emaanwallpapers.subscription.cashfree.CashfreeSubscriptionCheckout
import com.squarenova.emaanwallpapers.subscription.cashfree.InstalledUpiApp

/** Bottom sheet listing installed UPI apps for the Cashfree UPI Intent mandate. */
@Composable
fun UpiAppPickerOverlay(
    onAppSelected: (packageName: String) -> Unit,
    onDismiss: () -> Unit,
    onNoUpiAppsAvailable: () -> Unit,
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledUpiApp>?>(null) }
    var selectedPackage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        var active = true
        CashfreeSubscriptionCheckout.fetchInstalledUpiApps(context) { installed ->
            if (!active) return@fetchInstalledUpiApps
            if (installed.isEmpty()) onNoUpiAppsAvailable() else apps = installed
        }
        onDispose { active = false }
    }

    val ctaShape = RoundedCornerShape(14.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            // Swallow taps on the scrim so the paywall behind stays inert.
            .pointerInput(Unit) { detectTapGestures { } }
            .navigationBarsPadding()
            .statusBarsPadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            color = PremiumSubscriptionColors.Surface,
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Text(
                    text = "UPI ऐप चुनें",
                    color = PremiumSubscriptionColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "भुगतान पूरा करने के लिए अपना UPI ऐप चुनें",
                    color = PremiumSubscriptionColors.TextSecondary,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.height(16.dp))

                when (val list = apps) {
                    null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = PremiumSubscriptionColors.Gold,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            list.forEachIndexed { index, app ->
                                UpiAppRow(
                                    app = app,
                                    enabled = selectedPackage == null,
                                    onClick = {
                                        if (selectedPackage != null) return@UpiAppRow
                                        selectedPackage = app.packageName
                                        onAppSelected(app.packageName)
                                    },
                                )
                                if (index != list.lastIndex) {
                                    HorizontalDivider(
                                        color = PremiumSubscriptionColors.BorderSubtle,
                                        thickness = 1.dp,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(ctaShape)
                        .border(1.5.dp, PremiumSubscriptionColors.Gold.copy(alpha = 0.35f), ctaShape)
                        .clickable(enabled = selectedPackage == null, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "रद्द करें",
                        color = PremiumSubscriptionColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun UpiAppRow(
    app: InstalledUpiApp,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val iconBitmap = remember(app.iconBase64) {
        runCatching {
            if (app.iconBase64.isBlank()) return@runCatching null
            val bytes = Base64.decode(app.iconBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PremiumSubscriptionColors.SurfaceMuted),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = app.displayName.take(1).uppercase(),
                    color = PremiumSubscriptionColors.Forest,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = app.displayName,
            color = PremiumSubscriptionColors.TextPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
