package com.squarenova.emaanwallpapers.i18n

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Builds a locale-wrapped context without mutating the Activity Resources.
 * Mutating via [android.content.res.Resources.updateConfiguration] can flash black.
 *
 * Do NOT install this as [androidx.compose.ui.platform.LocalContext] — a non-Activity
 * context breaks [androidx.activity.compose.rememberLauncherForActivityResult].
 * Use only for [androidx.compose.ui.platform.LocalConfiguration] / resources reads.
 */
fun localizedContextForTag(base: Context, tag: String): Context {
    val localeTag = normalizeLanguageTag(tag)
    val locale = Locale.forLanguageTag(localeTag)
    Locale.setDefault(locale)
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return base.createConfigurationContext(config)
}

/** True when [candidate] must not replace the Activity LocalContext. */
internal fun mustKeepActivityLocalContext(candidateIsConfigurationContext: Boolean): Boolean =
    candidateIsConfigurationContext
