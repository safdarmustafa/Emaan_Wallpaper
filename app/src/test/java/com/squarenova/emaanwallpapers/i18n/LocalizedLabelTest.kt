package com.squarenova.emaanwallpapers.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalizedLabelTest {

    @Test
    fun englishLanguageUsesEnglishLabel() {
        assertEquals("Nasheed", localizedLabel("Nasheed", "नशीद", "en"))
    }

    @Test
    fun hindiLanguageUsesHindiLabel() {
        assertEquals("नशीद", localizedLabel("Nasheed", "नशीद", "hi"))
    }

    @Test
    fun hindiLanguageIgnoresCase() {
        assertEquals("काबा", localizedLabel("Kaaba", "काबा", "HI"))
    }

    @Test
    fun hindiFallsBackToEnglishWhenTranslationMissing() {
        assertEquals("Madni_Channel", localizedLabel("Madni_Channel", null, "hi"))
        assertEquals("Madni_Channel", localizedLabel("Madni_Channel", "  ", "hi"))
    }

    @Test
    fun unknownLanguageUsesEnglish() {
        assertEquals("Quran", localizedLabel("Quran", "कुरान", "fr"))
        assertEquals("Quran", localizedLabel("Quran", "कुरान", null))
    }
}
