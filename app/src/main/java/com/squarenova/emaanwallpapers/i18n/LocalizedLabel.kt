package com.squarenova.emaanwallpapers.i18n

/**
 * Picks the label for the active language.
 * Hindi falls back to English when [hindi] is blank, so a category without a
 * Hindi name never shows an empty chip.
 */
fun localizedLabel(english: String, hindi: String?, language: String?): String {
    if (language.equals("hi", ignoreCase = true)) {
        val translated = hindi?.trim().orEmpty()
        if (translated.isNotEmpty()) return translated
    }
    return english
}
