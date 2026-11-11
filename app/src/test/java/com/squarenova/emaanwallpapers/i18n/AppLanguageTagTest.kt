package com.squarenova.emaanwallpapers.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguageTagTest {

    @Test
    fun hindiTagStaysHindi() {
        assertEquals(AppLanguage.HINDI, normalizeLanguageTag("hi"))
        assertEquals(AppLanguage.HINDI, normalizeLanguageTag("HI"))
    }

    @Test
    fun englishAndUnknownTagsStayEnglish() {
        assertEquals(AppLanguage.ENGLISH, normalizeLanguageTag("en"))
        assertEquals(AppLanguage.ENGLISH, normalizeLanguageTag("EN"))
        assertEquals(AppLanguage.ENGLISH, normalizeLanguageTag("fr"))
        assertEquals(AppLanguage.ENGLISH, normalizeLanguageTag("hi-IN"))
        assertEquals(AppLanguage.ENGLISH, normalizeLanguageTag(null))
        assertEquals(AppLanguage.ENGLISH, normalizeLanguageTag(""))
    }

    @Test
    fun sameLanguageIsNotAppliedAgain() {
        assertFalse(shouldApplyLanguage(current = "en", requested = "en"))
        assertFalse(shouldApplyLanguage(current = "hi", requested = "HI"))
        assertFalse(shouldApplyLanguage(current = "en", requested = "fr"))
    }

    @Test
    fun differentLanguageIsAppliedOnce() {
        assertTrue(shouldApplyLanguage(current = "en", requested = "hi"))
        assertTrue(shouldApplyLanguage(current = "hi", requested = "en"))
    }

    @Test
    fun applyGateMatchesOverlayGate() {
        // Overlay must only appear when apply would actually change language.
        assertEquals(
            shouldApplyLanguage("en", "hi"),
            shouldShowLanguageSwitchCover(false, "en", "hi"),
        )
        assertEquals(
            shouldApplyLanguage("en", "en"),
            shouldShowLanguageSwitchCover(false, "en", "en"),
        )
        assertEquals(
            shouldApplyLanguage("hi", "en"),
            shouldShowLanguageSwitchCover(false, "hi", "en"),
        )
    }
}
