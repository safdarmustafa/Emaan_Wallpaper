package com.squarenova.emaanwallpapers.i18n

/**
 * Pure rules for the cream cover used while language strings recompose.
 * Never use a black cover — black flashes feel like a crash.
 */
internal fun shouldShowLanguageSwitchCover(
    isFirstComposition: Boolean,
    previousTag: String?,
    newTag: String,
): Boolean {
    if (isFirstComposition) return false
    if (previousTag == null) return false
    return shouldApplyLanguage(previousTag, newTag)
}

/** Cover stays fully opaque for this many ms while Compose swaps strings. */
internal const val LANGUAGE_COVER_HOLD_MS = 48L

/** Cream cover fades out over this many ms after hold. */
internal const val LANGUAGE_COVER_FADE_MS = 160
