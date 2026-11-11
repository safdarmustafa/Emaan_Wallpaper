package com.squarenova.emaanwallpapers.navigation

/**
 * Bottom-nav inset policy.
 *
 * NavHost must keep a stable size when leaving home/reels/ringtone for profile.
 * Changing NavHost bottom padding by route stretches the outgoing screen for a frame.
 * Inset belongs on the bottom-nav screens themselves instead.
 */
val BottomNavRoutes = setOf("home", "reels", "ringtone")

const val BOTTOM_NAV_CONTENT_INSET_DP = 90

fun showsBottomBar(route: String?): Boolean =
    route != null && route.substringBefore("/").substringBefore("?") in BottomNavRoutes

/** Always 0 — never resize NavHost when the route changes. */
fun navHostBottomPaddingDp(route: String?): Int = 0

fun contentBottomPaddingDp(route: String?): Int =
    if (showsBottomBar(route)) BOTTOM_NAV_CONTENT_INSET_DP else 0
