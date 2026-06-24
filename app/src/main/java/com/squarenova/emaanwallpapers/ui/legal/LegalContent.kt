package com.squarenova.emaanwallpapers.ui.legal

object LegalContent {
    const val supportEmail = "support@emaanwallpapers.com"
    const val privacyPolicyUrl = "https://emaanwallpapers.com/privacy"
    const val termsUrl = "https://emaanwallpapers.com/terms"

    const val contactIntro =
        "For subscription help, account deletion, billing disputes, or general questions, " +
            "contact our support team. We typically respond within 2 business days."

    val privacyPolicy = listOf(
        LegalSection(
            "Overview",
            "Emaan Wallpapers (\"we\", \"our\", \"us\") respects your privacy. This policy explains what " +
                "data we collect, why we collect it, and how you can control it.",
        ),
        LegalSection(
            "Data We Collect",
            "• Phone number (for login and account identification)\n" +
                "• Profile name and optional profile photo\n" +
                "• Subscription and payment status (processed by Razorpay; we do not store card/UPI PIN data)\n" +
                "• App usage analytics (Firebase Analytics, Mixpanel) — device events, screens viewed\n" +
                "• Wallpaper download and browsing activity within the app",
        ),
        LegalSection(
            "How We Use Data",
            "We use your data to authenticate you, deliver premium content, process subscriptions, " +
                "improve the app, prevent fraud, and comply with legal obligations.",
        ),
        LegalSection(
            "Third-Party Services",
            "We use Supabase (database), Razorpay (payments), Fast2SMS (OTP delivery), Firebase, " +
                "Mixpanel, and Meta SDK. Each provider has its own privacy policy.",
        ),
        LegalSection(
            "Data Retention & Deletion",
            "You may request account deletion from Profile → Delete Account. We delete your profile " +
                "data within 30 days unless retention is required by law or payment dispute resolution.",
        ),
        LegalSection(
            "Your Rights",
            "You may request access, correction, or deletion of your personal data by emailing " +
                "$supportEmail. Indian users may exercise rights under applicable data protection laws.",
        ),
        LegalSection(
            "Contact",
            "Privacy questions: $supportEmail",
        ),
    )

    val termsAndConditions = listOf(
        LegalSection(
            "Acceptance",
            "By using Emaan Wallpapers you agree to these Terms. If you do not agree, do not use the app.",
        ),
        LegalSection(
            "Service Description",
            "Emaan Wallpapers provides Islamic-themed wallpapers and reels. Premium features require " +
                "an active subscription or valid trial.",
        ),
        LegalSection(
            "Subscriptions & Billing",
            "• ₹5 one-time trial entry fee (non-refundable once consumed)\n" +
                "• Auto-renewing monthly subscription at ₹99/month after trial via Razorpay AutoPay\n" +
                "• You authorize recurring charges until you cancel\n" +
                "• Cancel anytime from Profile → Manage Subscription",
        ),
        LegalSection(
            "Auto-Renewal",
            "Your subscription automatically renews each billing period unless cancelled at least 24 hours " +
                "before renewal. Manage or cancel through the app or your UPI mandate settings.",
        ),
        LegalSection(
            "Refunds",
            "Refunds are handled per Google Play and Razorpay policies. Trial entry fees are generally " +
                "non-refundable. Contact support for billing disputes.",
        ),
        LegalSection(
            "Content License",
            "Wallpapers are licensed for personal device use only. Redistribution, resale, or commercial " +
                "use without permission is prohibited.",
        ),
        LegalSection(
            "Limitation of Liability",
            "The app is provided \"as is\". We are not liable for indirect damages arising from use of the service.",
        ),
    )

    val subscriptionDisclosure = listOf(
        LegalSection(
            "Premium Plan",
            "Emaan Premium: ₹99/month (INR), billed automatically after a 3-day trial period.",
        ),
        LegalSection(
            "Trial",
            "A ₹5 trial entry fee is required to start the trial. During trial you get full premium access. " +
                "You must approve UPI AutoPay mandate to continue after trial.",
        ),
        LegalSection(
            "Auto-Renewal Disclosure",
            "Payment will be charged to your UPI/bank account through Razorpay at confirmation of purchase. " +
                "Subscription automatically renews unless cancelled at least 24 hours before the end of the " +
                "current period. You can manage and cancel subscriptions in Profile → Manage Subscription.",
        ),
        LegalSection(
            "Cancellation",
            "Cancelling during trial prevents the ₹99 monthly charge. After trial, cancellation stops future " +
                "renewals; access continues until the end of the paid period.",
        ),
        LegalSection(
            "Restore Purchases",
            "Sign in with the same phone number to restore subscription status. Entitlement syncs from our " +
                "servers on login.",
        ),
    )

    val deleteAccount = listOf(
        LegalSection(
            "Request Deletion",
            "To delete your account and associated personal data:\n\n" +
                "1. Open Profile → Delete Account\n" +
                "2. Confirm deletion in the dialog\n" +
                "3. Or email $supportEmail from your registered phone number",
        ),
        LegalSection(
            "What Gets Deleted",
            "Profile name, phone number, avatar, subscription metadata, and app preferences. " +
                "Anonymized analytics may be retained.",
        ),
        LegalSection(
            "Billing Records",
            "Payment records held by Razorpay may be retained for tax and legal compliance even after " +
                "account deletion.",
        ),
        LegalSection(
            "Timeline",
            "Deletion is processed within 30 days. You will receive email confirmation when complete.",
        ),
    )
}
