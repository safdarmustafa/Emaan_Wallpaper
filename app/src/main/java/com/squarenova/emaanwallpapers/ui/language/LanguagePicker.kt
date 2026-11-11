package com.squarenova.emaanwallpapers.ui.language

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.squarenova.emaanwallpapers.R
import com.squarenova.emaanwallpapers.i18n.AppLanguage
import com.squarenova.emaanwallpapers.i18n.LANGUAGE_COVER_FADE_MS
import com.squarenova.emaanwallpapers.i18n.LANGUAGE_COVER_HOLD_MS
import com.squarenova.emaanwallpapers.i18n.localizedContextForTag
import com.squarenova.emaanwallpapers.i18n.shouldShowLanguageSwitchCover
import com.squarenova.emaanwallpapers.theme.AppBackground
import com.squarenova.emaanwallpapers.theme.AppTextPrimary
import com.squarenova.emaanwallpapers.theme.AppTextSecondary
import com.squarenova.emaanwallpapers.theme.BrandGreen
import com.squarenova.emaanwallpapers.theme.ProfileEmerald
import com.squarenova.emaanwallpapers.theme.ProfileEmeraldLight
import com.squarenova.emaanwallpapers.theme.ProfileGold
import com.squarenova.emaanwallpapers.theme.ProfileGoldLight
import kotlinx.coroutines.delay

@Composable
fun LanguageChangeFade(content: @Composable () -> Unit) {
    val tag = AppLanguage.currentTag()
    // Keep Activity as LocalContext — overriding it with createConfigurationContext
    // drops LocalActivityResultRegistryOwner and crashes Profile (gallery picker).
    val baseContext = LocalContext.current
    val localizedConfig = remember(tag, baseContext) {
        localizedContextForTag(baseContext, tag).resources.configuration
    }

    var previousTag by remember { mutableStateOf<String?>(null) }
    var isFirstComposition by remember { mutableStateOf(true) }
    val coverAlpha = remember { Animatable(0f) }

    LaunchedEffect(tag) {
        val showCover = shouldShowLanguageSwitchCover(
            isFirstComposition = isFirstComposition,
            previousTag = previousTag,
            newTag = tag,
        )
        previousTag = tag
        isFirstComposition = false
        if (!showCover) return@LaunchedEffect

        // Cream cover only — never black — while strings/layout recompose.
        coverAlpha.snapTo(1f)
        delay(LANGUAGE_COVER_HOLD_MS)
        coverAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(LANGUAGE_COVER_FADE_MS),
        )
    }

    CompositionLocalProvider(
        LocalConfiguration provides localizedConfig,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground),
        ) {
            content()
            if (coverAlpha.value > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppBackground.copy(alpha = coverAlpha.value)),
                )
            }
        }
    }
}

@Composable
fun LoginLanguageButton(modifier: Modifier = Modifier) {
    val current = AppLanguage.currentTag()
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .zIndex(2f)
            .shadow(12.dp, shape, ambientColor = Color.Black.copy(alpha = 0.22f), spotColor = Color.Black.copy(alpha = 0.16f))
            .clip(shape)
            .background(Color.White.copy(alpha = 0.97f))
            .border(
                width = 1.5.dp,
                brush = Brush.horizontalGradient(
                    listOf(ProfileGoldLight, ProfileGold, ProfileGoldLight),
                ),
                shape = shape,
            )
            .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Language,
            contentDescription = null,
            tint = ProfileEmerald,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.size(6.dp))
        LoginLanguageChoice(
            label = stringResource(R.string.language_english),
            selected = current == AppLanguage.ENGLISH,
            onClick = { AppLanguage.apply(AppLanguage.ENGLISH) },
        )
        LoginLanguageChoice(
            label = stringResource(R.string.language_hindi),
            selected = current == AppLanguage.HINDI,
            onClick = { AppLanguage.apply(AppLanguage.HINDI) },
        )
    }
}

@Composable
private fun LoginLanguageChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val chipShape = RoundedCornerShape(22.dp)
    Text(
        text = label,
        color = if (selected) Color.White else ProfileEmerald,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        modifier = Modifier
            .padding(start = 2.dp)
            .clip(chipShape)
            .background(
                if (selected) {
                    Brush.horizontalGradient(listOf(ProfileEmerald, ProfileEmeraldLight))
                } else {
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val current = AppLanguage.currentTag()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.language_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppTextPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.language_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = AppTextSecondary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            LanguageRow(
                title = stringResource(R.string.language_english),
                selected = current == AppLanguage.ENGLISH,
                onClick = {
                    AppLanguage.apply(AppLanguage.ENGLISH)
                    onDismiss()
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            LanguageRow(
                title = stringResource(R.string.language_hindi),
                selected = current == AppLanguage.HINDI,
                onClick = {
                    AppLanguage.apply(AppLanguage.HINDI)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun LanguageRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) BrandGreen.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = if (selected) ProfileEmerald else AppTextPrimary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 16.sp,
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = ProfileEmerald,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
