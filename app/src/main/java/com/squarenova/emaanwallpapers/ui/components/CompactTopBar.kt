package com.squarenova.emaanwallpapers.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.squarenova.emaanwallpapers.theme.AppTextPrimary
import com.squarenova.emaanwallpapers.theme.AppTextSecondary
import com.squarenova.emaanwallpapers.theme.HomeTopBarSurface
import com.squarenova.emaanwallpapers.data.SubscriptionEntitlement
import com.squarenova.emaanwallpapers.ui.subscription.PremiumBadge

@Composable
fun CompactTopBar(
    greeting: String,
    title: String,
    isLoadingTitle: Boolean,
    avatarUrl: String?,
    avatarInitial: String,
    isSubscribed: Boolean,
    subscriptionStatus: String?,
    trialEndIso: String? = null,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val showBadge = SubscriptionEntitlement.hasPremiumAccess(
        subscriptionStatus = subscriptionStatus,
        trialEndIso = trialEndIso,
        isSubscribedLegacy = isSubscribed,
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = HomeTopBarSurface,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        shape = RectangleShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTextSecondary,
                        fontSize = 10.sp,
                        letterSpacing = 0.3.sp
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isLoadingTitle) "" else title,
                            style = MaterialTheme.typography.titleMedium,
                            color = AppTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (!isLoadingTitle && showBadge) {
                            Spacer(modifier = Modifier.padding(start = 8.dp))
                            PremiumBadge(
                                isSubscribed = isSubscribed,
                                subscriptionStatus = subscriptionStatus,
                                compact = true
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onProfileClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F3F4)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(avatarUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Profile",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Text(
                                text = avatarInitial,
                                color = AppTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
