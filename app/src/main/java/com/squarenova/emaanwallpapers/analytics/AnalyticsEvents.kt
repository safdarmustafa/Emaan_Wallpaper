package com.squarenova.emaanwallpapers.analytics

/**
 * Canonical event names for analytics. Values must match existing production events exactly.
 */
object AnalyticsEvents {

    const val APP_LAUNCHED = "App Launched"
    const val META_TEST_EVENT = "meta_test_event"
    const val SCREEN_VIEWED = "screen_viewed"

    const val LOGIN_CONTINUE_TAPPED = "Login - Continue Tapped"
    const val PROFILE_SETUP_SAVE_CONTINUE_TAPPED = "Profile Setup - Save & Continue Tapped"
    const val PROFILE_SETUP_SKIP_TAPPED = "Profile Setup - Skip Tapped"
    const val SIGN_UP = "sign_up"

    const val PAYWALL_SHOWN = "paywall_shown"
    const val HOME_ACCESS_BLOCKED = "home_access_blocked"
    const val REELS_ACCESS_BLOCKED = "reels_access_blocked"

    const val MANDATE_INITIATED = "mandate_initiated"
    const val MANDATE_SUCCESS = "mandate_success"
    const val MANDATE_FAILED = "mandate_failed"
    const val MANDATE_RETRY_CLICKED = "mandate_retry_clicked"
    const val MANDATE_PENDING_RESUME_CLICKED = "mandate_pending_resume_clicked"
    const val SUBSCRIPTION_CONFIRMED = "subscription_confirmed"
    const val SUBSCRIPTION_CANCEL_REQUESTED = "subscription_cancel_requested"
    const val SUBSCRIPTION_ACTIVATED = "subscription_activated"
    const val SUBSCRIPTION_CANCELLED = "subscription_cancelled"
    const val TRIAL_STARTED = "trial_started"
    const val TRIAL_EXPIRED = "trial_expired"
    const val SUBSCRIPTION_CHARGE_FAILED = "subscription_charge_failed"

    /** Mixpanel equivalent of a standard purchase / revenue event. Meta uses logPurchase(). */
    const val PURCHASE = "Purchase"

    const val PROFILE_BACK_TAPPED = "Profile - Back Tapped"
    const val PROFILE_EDIT_PROFILE_TAPPED = "Profile - Edit Profile Tapped"
    const val PROFILE_AVATAR_TAPPED = "Profile - Avatar Tapped"
    const val PROFILE_MANAGE_SUBSCRIPTION_TAPPED = "Profile - Manage Subscription Tapped"
    const val PROFILE_ABOUT_APP_TAPPED = "Profile - About App Tapped"
    const val PROFILE_SHARE_APP_TAPPED = "Profile - Share App Tapped"
    const val PROFILE_PRIVACY_POLICY_TAPPED = "Profile - Privacy Policy Tapped"
    const val PROFILE_TERMS_TAPPED = "Profile - Terms Tapped"
    const val PROFILE_SUBSCRIPTION_DISCLOSURE_TAPPED = "Profile - Subscription Disclosure Tapped"
    const val PROFILE_CONTACT_US_TAPPED = "Profile - Contact Us Tapped"
    const val PROFILE_UPGRADE_PREMIUM_TAPPED = "Profile - Upgrade Premium Tapped"
    const val PROFILE_LOGOUT_TAPPED = "Profile - Logout Tapped"
    const val PROFILE_DELETE_ACCOUNT_TAPPED = "Profile - Delete Account Tapped"
    const val PROFILE_EDIT_DIALOG_CANCEL_TAPPED = "Profile - Edit Dialog Cancel Tapped"
    const val PROFILE_ABOUT_DIALOG_DISMISSED = "Profile - About Dialog Dismissed"

    const val SUBSCRIPTION_DISCLOSURE_LINK_TAPPED = "Subscription - Disclosure Link Tapped"

    const val REELS_DOWNLOAD_TAPPED = "Reels - Download Tapped"
    const val REELS_SHARE_TAPPED = "Reels - Share Tapped"
    const val REELS_WHATSAPP_TAPPED = "Reels - WhatsApp Tapped"
    const val REELS_VIDEO_TAPPED = "Reels - Video Tapped"
    const val REELS_MUTE_TOGGLED = "Reels - Mute Toggled"

    /** Preserves the existing dynamic event shape: `"Profile - {feature} Tapped"`. */
    fun profileFeatureTapped(feature: String): String = "Profile - $feature Tapped"
}
