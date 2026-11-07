package com.squarenova.emaanwallpapers.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Per-app language. Stored by AppCompat, not in [com.squarenova.emaanwallpapers.data.DataStoreManager],
 * so logout does not reset the user's choice.
 *
 * An empty app locale list means "follow the phone". Hindi phones start in Hindi;
 * every other phone starts in English, because only en and hi are in locales_config.
 */
object AppLanguage {
    const val ENGLISH = "en"
    const val HINDI = "hi"

    private var tagState by mutableStateOf(readAppliedTag())

    fun apply(tag: String) {
        val locale = normalizeLanguageTag(tag)
        if (!shouldApplyLanguage(tagState, locale)) return
        tagState = locale
        Locale.setDefault(Locale.forLanguageTag(locale))
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(locale))
    }

    /** Language the UI should show right now. Reading this inside Compose follows language changes. */
    fun currentTag(): String = tagState

    private fun readAppliedTag(): String {
        val override = AppCompatDelegate.getApplicationLocales()
        val language = if (override.isEmpty) {
            Locale.getDefault().language
        } else {
            override[0]?.language
        }
        return normalizeLanguageTag(language)
    }
}

/** Only Hindi is a real second language. Everything else stays English. */
internal fun normalizeLanguageTag(tag: String?): String {
    return if (tag.equals(AppLanguage.HINDI, ignoreCase = true)) AppLanguage.HINDI else AppLanguage.ENGLISH
}

/** A second tap on the language already showing must not apply again. */
internal fun shouldApplyLanguage(current: String, requested: String): Boolean {
    return normalizeLanguageTag(current) != normalizeLanguageTag(requested)
}
