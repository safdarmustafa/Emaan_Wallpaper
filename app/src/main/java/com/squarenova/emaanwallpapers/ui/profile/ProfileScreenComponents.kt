package com.squarenova.emaanwallpapers.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.squarenova.emaanwallpapers.data.SubscriptionEntitlement
import com.squarenova.emaanwallpapers.data.SupabaseTimestampParser
import com.squarenova.emaanwallpapers.theme.AppDivider
import com.squarenova.emaanwallpapers.theme.AppTextPrimary
import com.squarenova.emaanwallpapers.theme.AppTextSecondary
import com.squarenova.emaanwallpapers.theme.AppTextTertiary
import com.squarenova.emaanwallpapers.theme.BackgroundCream
import com.squarenova.emaanwallpapers.theme.BrandGreen
import com.squarenova.emaanwallpapers.theme.ProfileCardSurface
import com.squarenova.emaanwallpapers.theme.ProfileDanger
import com.squarenova.emaanwallpapers.theme.ProfileDangerSurface
import com.squarenova.emaanwallpapers.theme.ProfileEmerald
import com.squarenova.emaanwallpapers.theme.ProfileGold
import com.squarenova.emaanwallpapers.theme.ProfileGoldLight
import com.squarenova.emaanwallpapers.theme.ProfileStatusEnding
import com.squarenova.emaanwallpapers.theme.ProfileStatusExpired
import com.squarenova.emaanwallpapers.theme.ProfileStatusPremium
import com.squarenova.emaanwallpapers.theme.ProfileStatusTrial
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val ProfileCardShape = RoundedCornerShape(20.dp)
internal val ProfileSectionSpacing = 24.dp
internal val ProfileHorizontalPadding = 20.dp

internal enum class ProfilePlanVisual(
    val label: String,
    val accent: Color,
    val container: Color,
) {
    Trial("Trial Active", ProfileStatusTrial, ProfileStatusTrial.copy(alpha = 0.12f)),
    Premium("Premium Active", ProfileStatusPremium, ProfileStatusPremium.copy(alpha = 0.12f)),
    EndingSoon("Ending Soon", ProfileStatusEnding, ProfileStatusEnding.copy(alpha = 0.12f)),
    Expired("Expired", ProfileStatusExpired, ProfileStatusExpired.copy(alpha = 0.12f)),
    Free("Free Plan", AppTextSecondary, BackgroundCream),
}

internal fun resolveProfilePlanVisual(
    subscriptionStatus: String?,
    trialEndIso: String?,
): ProfilePlanVisual {
    val status = subscriptionStatus?.trim()?.lowercase().orEmpty()
    val trialActive = SupabaseTimestampParser.isInFuture(trialEndIso)
    return when {
        status == "active" -> ProfilePlanVisual.Premium
        status == "trial" && trialActive -> ProfilePlanVisual.Trial
        status == "cancel_requested" && trialActive -> ProfilePlanVisual.EndingSoon
        status == "expired" || (status == "trial" && !trialActive) -> ProfilePlanVisual.Expired
        SubscriptionEntitlement.hasPremiumAccess(status, trialEndIso) -> ProfilePlanVisual.Premium
        else -> ProfilePlanVisual.Free
    }
}

internal fun formatProfileDate(iso: String?): String? {
    val ms = SupabaseTimestampParser.parseToEpochMillis(iso) ?: return null
    return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ms))
}

@Composable
fun ProfileTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = ProfileEmerald,
            )
        }
        Text(
            text = "Profile",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = AppTextPrimary,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.width(48.dp))
    }
}

@Composable
fun ProfileHeaderSection(
    fullName: String,
    phoneNumber: String,
    avatarUrl: String?,
    avatarInitial: String,
    isLoading: Boolean,
    isUploadingAvatar: Boolean,
    subscriptionStatus: String?,
    trialEndIso: String?,
    onEditProfile: () -> Unit,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan = resolveProfilePlanVisual(subscriptionStatus, trialEndIso)
    val hasPremium = SubscriptionEntitlement.hasPremiumAccess(subscriptionStatus, trialEndIso)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ProfileHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .border(2.dp, ProfileEmerald.copy(alpha = 0.35f), CircleShape),
                color = BackgroundCream,
                onClick = onAvatarClick,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when {
                        isUploadingAvatar -> CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = BrandGreen,
                            strokeWidth = 3.dp,
                        )
                        !avatarUrl.isNullOrBlank() -> AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                        )
                        isLoading -> CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = BrandGreen,
                            strokeWidth = 2.dp,
                        )
                        else -> Text(
                            text = avatarInitial,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = ProfileEmerald,
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.BottomEnd),
                shape = CircleShape,
                color = ProfileEmerald,
                shadowElevation = 4.dp,
                onClick = onAvatarClick,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Change photo",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (isLoading) "Loading…" else fullName.ifBlank { "Guest" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = AppTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hasPremium) {
                Spacer(modifier = Modifier.width(8.dp))
                ProfileStatusChip(
                    text = when (plan) {
                        ProfilePlanVisual.Trial -> "TRIAL"
                        ProfilePlanVisual.EndingSoon -> "ENDING"
                        else -> "PREMIUM"
                    },
                    accent = plan.accent,
                    container = plan.container,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onEditProfile, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit profile",
                    tint = AppTextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = phoneNumber.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            color = AppTextSecondary,
        )
    }
}

@Composable
private fun ProfileStatusChip(
    text: String,
    accent: Color,
    container: Color,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = container,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
    }
}

private val ProfileStatusSetup = Color(0xFF2563EB)

/** Display-only labels and chip colors for [ProfileSubscriptionCard]. */
private data class SubscriptionCardDisplay(
    val statusLabel: String,
    val statusAccent: Color,
    val statusContainer: Color,
)

private fun resolveSubscriptionCardDisplay(
    subscriptionStatus: String?,
    trialEndIso: String?,
    hasPremium: Boolean,
): SubscriptionCardDisplay {
    val status = subscriptionStatus?.trim()?.lowercase().orEmpty()
    val trialActive = SupabaseTimestampParser.isInFuture(trialEndIso)
    return when {
        status == "trial" && trialActive -> SubscriptionCardDisplay(
            statusLabel = "Free Trial Active",
            statusAccent = ProfileStatusTrial,
            statusContainer = ProfileStatusTrial.copy(alpha = 0.12f),
        )
        status == "active" -> SubscriptionCardDisplay(
            statusLabel = "Premium Active",
            statusAccent = ProfileStatusPremium,
            statusContainer = ProfileStatusPremium.copy(alpha = 0.12f),
        )
        (status == "cancel_requested" || status == "cancelled") && hasPremium && trialActive ->
            SubscriptionCardDisplay(
                statusLabel = "Ending Soon",
                statusAccent = ProfileStatusEnding,
                statusContainer = ProfileStatusEnding.copy(alpha = 0.12f),
            )
        status == "authenticated" || status == "created" -> SubscriptionCardDisplay(
            statusLabel = "Setting Up AutoPay",
            statusAccent = ProfileStatusSetup,
            statusContainer = ProfileStatusSetup.copy(alpha = 0.12f),
        )
        status == "pending" -> SubscriptionCardDisplay(
            statusLabel = "Waiting for Confirmation",
            statusAccent = ProfileStatusSetup,
            statusContainer = ProfileStatusSetup.copy(alpha = 0.12f),
        )
        status == "expired" || (status == "trial" && !trialActive) -> SubscriptionCardDisplay(
            statusLabel = "Expired",
            statusAccent = ProfileStatusExpired,
            statusContainer = ProfileStatusExpired.copy(alpha = 0.12f),
        )
        hasPremium -> SubscriptionCardDisplay(
            statusLabel = "Premium Active",
            statusAccent = ProfileStatusPremium,
            statusContainer = ProfileStatusPremium.copy(alpha = 0.12f),
        )
        else -> SubscriptionCardDisplay(
            statusLabel = "Setting Up AutoPay",
            statusAccent = ProfileStatusSetup,
            statusContainer = ProfileStatusSetup.copy(alpha = 0.12f),
        )
    }
}

@Composable
fun ProfileSubscriptionCard(
    subscriptionStatus: String?,
    trialEndIso: String?,
    onManageSubscription: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = subscriptionStatus?.trim()?.lowercase().orEmpty()
    val hasPremium = SubscriptionEntitlement.hasPremiumAccess(subscriptionStatus, trialEndIso)
    val showCard = when {
        hasPremium -> true
        status == "cancelled" -> false
        status.isNotEmpty() -> true
        else -> false
    }

    if (!showCard) return

    val trialEndFormatted = formatProfileDate(trialEndIso)
    val trialActive = SupabaseTimestampParser.isInFuture(trialEndIso)
    val display = resolveSubscriptionCardDisplay(subscriptionStatus, trialEndIso, hasPremium)
    val isTrialActive = status == "trial" && trialActive
    val isActivePremium = status == "active"
    val isEndingSoonWithAccess =
        (status == "cancel_requested" || status == "cancelled") && hasPremium && trialActive
    val isExpired = status == "expired" || (status == "trial" && !trialActive)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ProfileCardShape,
        colors = CardDefaults.cardColors(containerColor = ProfileCardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = display.statusContainer,
                    modifier = Modifier.size(50.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = display.statusAccent,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Premium Membership",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppTextSecondary,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ProfileStatusChip(
                        text = display.statusLabel,
                        accent = display.statusAccent,
                        container = display.statusContainer,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = AppDivider)
            Spacer(modifier = Modifier.height(18.dp))

            when {
                isTrialActive -> {
                    ProfileSubscriptionDetailRow(
                        label = "Trial Ends",
                        value = trialEndFormatted ?: "—",
                        icon = Icons.Default.CalendarToday,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ProfileSubscriptionDetailRow(
                        label = "Next Renewal",
                        value = trialEndFormatted ?: "—",
                        icon = Icons.Default.CalendarToday,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ProfileSubscriptionDetailRow(
                        label = "Auto Renewal",
                        value = "On",
                        icon = Icons.Default.Autorenew,
                        valueColor = ProfileStatusPremium,
                    )
                }

                isEndingSoonWithAccess -> {
                    ProfileSubscriptionDetailRow(
                        label = "Access Until",
                        value = trialEndFormatted ?: "—",
                        icon = Icons.Default.CalendarToday,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ProfileSubscriptionDetailRow(
                        label = "Auto Renewal",
                        value = "Off",
                        icon = Icons.Default.Cancel,
                        valueColor = ProfileStatusExpired,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    ProfileSubscriptionInfoNote(
                        text = buildString {
                            append("You won't be charged again. Premium access continues until ")
                            append(trialEndFormatted ?: "your trial ends")
                            append('.')
                        },
                    )
                }

                isActivePremium -> {
                    ProfileSubscriptionDetailRow(
                        label = "Next Renewal",
                        value = trialEndFormatted ?: "Monthly · ₹249",
                        icon = Icons.Default.CalendarToday,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ProfileSubscriptionDetailRow(
                        label = "Plan",
                        value = "₹249 / month",
                        icon = Icons.Default.WorkspacePremium,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ProfileSubscriptionDetailRow(
                        label = "Auto Renewal",
                        value = "On",
                        icon = Icons.Default.Autorenew,
                        valueColor = ProfileStatusPremium,
                    )
                }

                isExpired -> {
                    ProfileSubscriptionInfoNote(
                        text = "Premium access has ended.",
                        icon = Icons.Default.Info,
                        accent = ProfileStatusExpired,
                    )
                }

                else -> {
                    if (!trialEndFormatted.isNullOrBlank() && hasPremium) {
                        ProfileSubscriptionDetailRow(
                            label = "Access Until",
                            value = trialEndFormatted,
                            icon = Icons.Default.CalendarToday,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    ProfileSubscriptionInfoNote(
                        text = when (status) {
                            "authenticated", "created" ->
                                "Complete AutoPay setup to activate your premium membership."
                            "pending" ->
                                "We're confirming your subscription. This usually takes a moment."
                            else -> "Manage your subscription details below."
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onManageSubscription,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ProfileEmerald,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = "Manage Subscription",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ProfileSubscriptionInfoNote(
    text: String,
    icon: ImageVector = Icons.Default.Info,
    accent: Color = ProfileStatusEnding,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = AppTextSecondary,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun ProfileSubscriptionDetailRow(
    label: String,
    value: String,
    icon: ImageVector? = null,
    valueColor: Color = AppTextPrimary,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppTextTertiary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = AppTextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            textAlign = TextAlign.End,
        )
    }
}

data class ProfileQuickAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
fun ProfileQuickActionsSection(
    actions: List<ProfileQuickAction>,
    modifier: Modifier = Modifier,
) {
    ProfileSectionTitle(title = "Quick Actions", modifier = modifier)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = ProfileHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(actions) { action ->
            Card(
                onClick = action.onClick,
                shape = ProfileCardShape,
                colors = CardDefaults.cardColors(containerColor = ProfileCardSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ProfileEmerald.copy(alpha = 0.1f),
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.label,
                                tint = ProfileEmerald,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Text(
                        text = action.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTextPrimary,
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier.padding(
            horizontal = ProfileHorizontalPadding,
            vertical = 8.dp,
        ),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = AppTextSecondary,
        letterSpacing = 0.5.sp,
    )
}

data class ProfileAccountItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val opensExternal: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun ProfileLegalSupportSection(
    items: List<ProfileAccountItem>,
    modifier: Modifier = Modifier,
) {
    ProfileSectionTitle(title = "Legal & Support", modifier = modifier)
    ProfileAccountSection(items = items, showSectionTitle = false)
}

@Composable
fun ProfileAccountSection(
    items: List<ProfileAccountItem>,
    modifier: Modifier = Modifier,
    sectionTitle: String = "Account",
    showSectionTitle: Boolean = true,
) {
    if (showSectionTitle) {
        ProfileSectionTitle(title = sectionTitle, modifier = modifier)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ProfileHorizontalPadding),
        shape = ProfileCardShape,
        colors = CardDefaults.cardColors(containerColor = ProfileCardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            items.forEachIndexed { index, item ->
                ProfileAccountListRow(item = item)
                if (index < items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        color = AppDivider,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileAccountListRow(item: ProfileAccountItem) {
    Surface(
        onClick = item.onClick,
        enabled = item.enabled,
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BackgroundCream,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = ProfileEmerald,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (item.enabled) AppTextPrimary else AppTextTertiary,
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = if (item.opensExternal) {
                    Icons.AutoMirrored.Filled.OpenInNew
                } else {
                    Icons.Default.ChevronRight
                },
                contentDescription = if (item.opensExternal) "Opens in browser" else null,
                tint = AppTextTertiary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun ProfilePremiumUpsellCard(
    visible: Boolean,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ProfileHorizontalPadding),
        shape = ProfileCardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF3D2E0A),
                            Color(0xFF5C4512),
                            Color(0xFF7A5C18),
                        )
                    )
                )
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = ProfileGoldLight,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Emaan Premium",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Text(
                    text = "Unlimited HD wallpapers, live reels, and new designs every day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.88f),
                    lineHeight = 20.sp,
                )
                val benefits = listOf(
                    "Ad-free experience",
                    "Exclusive Islamic collections",
                    "Priority new releases",
                )
                benefits.forEach { benefit ->
                    Text(
                        text = "• $benefit",
                        style = MaterialTheme.typography.bodySmall,
                        color = ProfileGoldLight,
                    )
                }
                Button(
                    onClick = onUpgrade,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ProfileGold,
                        contentColor = Color(0xFF1A1408),
                    ),
                ) {
                    Text(
                        text = "Upgrade to Premium",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileDangerZoneSection(
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ProfileSectionTitle(title = "Danger Zone", modifier = modifier)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ProfileHorizontalPadding),
        shape = ProfileCardShape,
        colors = CardDefaults.cardColors(containerColor = ProfileDangerSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ProfileDanger.copy(alpha = 0.15f)),
    ) {
        Column {
            ProfileDangerRow(
                title = "Logout",
                subtitle = "Sign out of this device",
                icon = Icons.AutoMirrored.Filled.Logout,
                onClick = onLogout,
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 72.dp),
                color = ProfileDanger.copy(alpha = 0.12f),
            )
            ProfileDangerRow(
                title = "Delete Account",
                subtitle = "Permanently remove your data",
                icon = Icons.Default.Delete,
                onClick = onDeleteAccount,
            )
        }
    }
}

@Composable
private fun ProfileDangerRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ProfileDanger.copy(alpha = 0.1f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = ProfileDanger,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = ProfileDanger,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTextSecondary,
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = ProfileDanger.copy(alpha = 0.5f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun ProfileFooter(versionLabel: String) {
    Text(
        text = versionLabel,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelSmall,
        color = AppTextTertiary,
    )
}

@Composable
fun ProfileMessageSnackbar(
    message: String,
    isSuccess: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(16.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (isSuccess) BrandGreen else ProfileDanger,
        shadowElevation = 6.dp,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
