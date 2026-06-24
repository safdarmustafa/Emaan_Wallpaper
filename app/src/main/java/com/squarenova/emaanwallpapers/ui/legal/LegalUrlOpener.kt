package com.squarenova.emaanwallpapers.ui.legal

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.toArgb
import com.squarenova.emaanwallpapers.theme.ProfileEmerald

private const val TAG = "LegalUrlOpener"

object LegalUrlOpener {

    fun openPrivacyPolicy(context: Context) {
        openUrl(context, LegalUrls.PRIVACY_POLICY, "Privacy Policy")
    }

    fun openTermsAndConditions(context: Context) {
        openUrl(context, LegalUrls.TERMS_AND_CONDITIONS, "Terms & Conditions")
    }

    /** Subscription billing, auto-renewal, and refund terms live on the hosted Terms page. */
    fun openSubscriptionDisclosure(context: Context) {
        openTermsAndConditions(context)
    }

    fun openWebsite(context: Context) {
        openUrl(context, LegalUrls.WEBSITE, "SquareNova Tech")
    }

    fun openEmailSupport(context: Context, subject: String = "Emaan Wallpapers Support") {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${LegalUrls.SUPPORT_EMAIL}")
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Contact support"))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No email client available", e)
        }
    }

    fun openUrl(context: Context, url: String, title: String? = null) {
        val uri = Uri.parse(url)
        try {
            val colorScheme = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(ProfileEmerald.toArgb())
                .build()
            val customTabsIntent = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colorScheme)
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(context, uri)
        } catch (e: Exception) {
            Log.w(TAG, "Custom Tab failed, falling back to browser: $url", e)
            openInBrowser(context, uri, title)
        }
    }

    private fun openInBrowser(context: Context, uri: Uri, title: String?) {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            if (!title.isNullOrBlank()) {
                putExtra(Intent.EXTRA_TITLE, title)
            }
        }
        try {
            context.startActivity(Intent.createChooser(intent, title ?: "Open link"))
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No browser available for $uri", e)
        }
    }
}
