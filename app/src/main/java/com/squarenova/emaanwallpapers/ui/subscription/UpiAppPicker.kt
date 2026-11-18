package com.squarenova.emaanwallpapers.ui.subscription

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squarenova.emaanwallpapers.R
import com.squarenova.emaanwallpapers.subscription.cashfree.InstalledUpiApp

/**
 * Inline UPI chips above the Start Today CTA.
 * One app is always selected when [apps] is non-empty — no Cancel sheet.
 */
@Composable
fun InlineUpiAppSelector(
    apps: List<InstalledUpiApp>?,
    selectedPackageName: String?,
    onSelect: (packageName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            apps == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = PremiumSubscriptionColors.Gold,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            apps.isEmpty() -> {
                Text(
                    text = stringResource(R.string.sub_no_upi),
                    color = PremiumSubscriptionColors.TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
            else -> {
                Text(
                    text = stringResource(R.string.sub_pay_with),
                    color = PremiumSubscriptionColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    textAlign = TextAlign.Start,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    apps.forEach { app ->
                        UpiAppChip(
                            app = app,
                            selected = app.packageName == selectedPackageName,
                            onClick = { onSelect(app.packageName) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact UPI picker for the paywall footer — same height/radius as the CTA.
 */
@Composable
fun PaywallUpiDropdown(
    apps: List<InstalledUpiApp>?,
    selectedPackageName: String?,
    onSelect: (packageName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = apps?.firstOrNull { it.packageName == selectedPackageName } ?: apps?.firstOrNull()
    val shape = RoundedCornerShape(PaywallFooterActionRadius)

    Box(modifier = modifier) {
        when {
            apps == null -> {
                Box(
                    modifier = Modifier
                        .height(PaywallFooterActionHeight)
                        .width(76.dp)
                        .clip(shape)
                        .background(PremiumSubscriptionColors.SurfaceMuted)
                        .border(1.dp, PremiumSubscriptionColors.BorderSubtle, shape),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = PremiumSubscriptionColors.Gold,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            apps.isEmpty() -> {
                Text(
                    text = stringResource(R.string.sub_no_upi),
                    color = PremiumSubscriptionColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    modifier = Modifier.width(148.dp),
                )
            }
            selected != null -> {
                Row(
                    modifier = Modifier
                        .height(PaywallFooterActionHeight)
                        .width(76.dp)
                        .clip(shape)
                        .background(PremiumSubscriptionColors.SurfaceMuted)
                        .border(1.dp, PremiumSubscriptionColors.BorderSubtle, shape)
                        .clickable { expanded = true }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    UpiAppIcon(app = selected, size = 28.dp)
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = selected.displayName,
                        tint = PremiumSubscriptionColors.TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    apps.forEach { app ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    UpiAppIcon(app = app, size = 24.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = app.displayName,
                                        fontSize = 14.sp,
                                        fontWeight = if (app.packageName == selectedPackageName) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Medium
                                        },
                                    )
                                }
                            },
                            onClick = {
                                onSelect(app.packageName)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpiAppIcon(
    app: InstalledUpiApp,
    size: androidx.compose.ui.unit.Dp,
) {
    val iconBitmap = remember(app.iconBase64) {
        runCatching {
            if (app.iconBase64.isBlank()) return@runCatching null
            val bytes = Base64.decode(app.iconBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp)),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(PremiumSubscriptionColors.SurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = app.displayName.take(1).uppercase(),
                color = PremiumSubscriptionColors.Forest,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun UpiAppChip(
    app: InstalledUpiApp,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val borderColor = if (selected) {
        PremiumSubscriptionColors.Forest
    } else {
        PremiumSubscriptionColors.BorderSubtle
    }
    val bg = if (selected) {
        PremiumSubscriptionColors.Forest.copy(alpha = 0.10f)
    } else {
        PremiumSubscriptionColors.Surface
    }

    Row(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .border(1.5.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UpiAppIcon(app = app, size = 28.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = app.displayName,
            color = PremiumSubscriptionColors.TextPrimary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
