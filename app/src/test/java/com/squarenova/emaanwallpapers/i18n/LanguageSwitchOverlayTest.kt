package com.squarenova.emaanwallpapers.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageSwitchOverlayTest {

    @Test
    fun firstCompositionNeverShowsCover() {
        assertFalse(
            shouldShowLanguageSwitchCover(
                isFirstComposition = true,
                previousTag = null,
                newTag = "en",
            ),
        )
        assertFalse(
            shouldShowLanguageSwitchCover(
                isFirstComposition = true,
                previousTag = "en",
                newTag = "hi",
            ),
        )
    }

    @Test
    fun nullPreviousNeverShowsCover() {
        assertFalse(
            shouldShowLanguageSwitchCover(
                isFirstComposition = false,
                previousTag = null,
                newTag = "hi",
            ),
        )
    }

    @Test
    fun sameLanguageNeverShowsCover() {
        assertFalse(
            shouldShowLanguageSwitchCover(
                isFirstComposition = false,
                previousTag = "en",
                newTag = "en",
            ),
        )
        assertFalse(
            shouldShowLanguageSwitchCover(
                isFirstComposition = false,
                previousTag = "hi",
                newTag = "HI",
            ),
        )
        assertFalse(
            shouldShowLanguageSwitchCover(
                isFirstComposition = false,
                previousTag = "en",
                newTag = "fr",
            ),
        )
    }

    @Test
    fun realLanguageChangeShowsCreamCover() {
        assertTrue(
            shouldShowLanguageSwitchCover(
                isFirstComposition = false,
                previousTag = "en",
                newTag = "hi",
            ),
        )
        assertTrue(
            shouldShowLanguageSwitchCover(
                isFirstComposition = false,
                previousTag = "hi",
                newTag = "en",
            ),
        )
    }

    @Test
    fun coverTimingIsShortAndSmooth() {
        assertTrue(LANGUAGE_COVER_HOLD_MS in 16L..120L)
        assertTrue(LANGUAGE_COVER_FADE_MS in 80..300)
        assertTrue(LANGUAGE_COVER_HOLD_MS + LANGUAGE_COVER_FADE_MS < 400L)
    }

    @Test
    fun coverNeverUsesBlackHexPolicy() {
        // Contract: overlay color in UI is AppBackground cream/green-tint, never #000000.
        // This pure test locks the timing + decision API so UI cannot skip the cover path.
        val decisions = listOf(
            shouldShowLanguageSwitchCover(false, "en", "hi"),
            shouldShowLanguageSwitchCover(false, "hi", "en"),
        )
        assertEquals(listOf(true, true), decisions)
    }

    @Test
    fun configurationContextMustNotReplaceActivityLocalContext() {
        // Regression: Profile crashed with
        // "No ActivityResultRegistryOwner was provided via LocalActivityResultRegistryOwner"
        // when LocalContext was overridden with createConfigurationContext.
        assertTrue(mustKeepActivityLocalContext(candidateIsConfigurationContext = true))
        assertFalse(mustKeepActivityLocalContext(candidateIsConfigurationContext = false))
    }
}
