package com.squarenova.emaanwallpapers.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomNavPolicyTest {

    @Test
    fun bottomBarVisibleOnlyOnMainTabs() {
        assertTrue(showsBottomBar("home"))
        assertTrue(showsBottomBar("reels"))
        assertTrue(showsBottomBar("ringtone"))
        assertFalse(showsBottomBar("profile"))
        assertFalse(showsBottomBar("login"))
        assertFalse(showsBottomBar("subscription"))
        assertFalse(showsBottomBar(null))
        assertFalse(showsBottomBar("otp/9717621755"))
    }

    @Test
    fun navHostBottomPaddingNeverChangesByRoute() {
        // Regression: toggling NavHost padding home→profile stretched the home layout.
        val routes = listOf(
            null,
            "splash",
            "home",
            "reels",
            "ringtone",
            "profile",
            "login",
            "subscription",
            "otp/123",
        )
        routes.forEach { route ->
            assertEquals(
                "NavHost padding must stay 0 for route=$route",
                0,
                navHostBottomPaddingDp(route),
            )
        }
    }

    @Test
    fun contentInsetOnlyOnBottomNavScreens() {
        assertEquals(BOTTOM_NAV_CONTENT_INSET_DP, contentBottomPaddingDp("home"))
        assertEquals(BOTTOM_NAV_CONTENT_INSET_DP, contentBottomPaddingDp("reels"))
        assertEquals(BOTTOM_NAV_CONTENT_INSET_DP, contentBottomPaddingDp("ringtone"))
        assertEquals(0, contentBottomPaddingDp("profile"))
        assertEquals(0, contentBottomPaddingDp("login"))
        assertEquals(0, contentBottomPaddingDp(null))
    }

    @Test
    fun homeToProfileDoesNotResizeNavHost() {
        val onHome = navHostBottomPaddingDp("home")
        val onProfile = navHostBottomPaddingDp("profile")
        assertEquals(onHome, onProfile)
        assertEquals(0, onHome)
    }

    @Test
    fun insetConstantIsPositiveAndStable() {
        assertTrue(BOTTOM_NAV_CONTENT_INSET_DP >= 80)
        assertTrue(BOTTOM_NAV_CONTENT_INSET_DP <= 120)
    }
}
