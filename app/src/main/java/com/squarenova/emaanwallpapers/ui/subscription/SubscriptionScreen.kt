package com.squarenova.emaanwallpapers.ui.subscription

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.min
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.squarenova.emaanwallpapers.R
import com.squarenova.emaanwallpapers.analytics.AnalyticsEvents
import com.squarenova.emaanwallpapers.analytics.AnalyticsManager
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.data.LocalSession
import com.squarenova.emaanwallpapers.subscription.EntitlementRepository
import com.squarenova.emaanwallpapers.subscription.SubscriptionPricing
import com.squarenova.emaanwallpapers.subscription.cashfree.CashfreeSubscriptionCheckout
import com.squarenova.emaanwallpapers.subscription.cashfree.CashfreeSubscriptionRepository
import com.squarenova.emaanwallpapers.subscription.cashfree.InstalledUpiApp
import com.squarenova.emaanwallpapers.subscription.cashfree.SubscriptionCheckoutBridge
import com.squarenova.emaanwallpapers.subscription.cashfree.UpiLastUsedStore
import com.squarenova.emaanwallpapers.subscription.cashfree.canStartTrialPayment
import com.squarenova.emaanwallpapers.subscription.cashfree.preferDefaultUpiPackage
import com.squarenova.emaanwallpapers.ui.legal.LegalUrlOpener
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

private const val TAG = "SUBSCRIPTION"

/** After the UPI app returns, the webhook may lag — poll subscription-status a few times. */
private const val VERIFY_POLL_ATTEMPTS = 3
private const val VERIFY_POLL_INTERVAL_MS = 3_000L

/** Gives the Cashfree callback a chance to arrive before ON_RESUME falls back to polling. */
private const val RESUME_VERIFY_DELAY_MS = 1_500L

/** Same video frame shape on every phone (width / height). */
private const val PAYWALL_VIDEO_ASPECT_RATIO = 1.05f

private data class PendingCashfreeSession(
    val subscriptionId: String,
    val sessionId: String,
)

@Composable
fun SubscriptionScreen(navController: NavController) {

    val context = LocalContext.current
    val activity = LocalActivity.current
    val dataStoreManager = DataStoreManager(context)
    val scope = rememberCoroutineScope()

    var phone by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var paymentErrorDialog by remember { mutableStateOf<PaymentErrorDialogState?>(null) }

    /** Cashfree subscription + session from create-subscription, used to launch the UPI intent. */
    var pendingSession by remember { mutableStateOf<PendingCashfreeSession?>(null) }

    /** null = still loading installed UPI apps; empty = none installed. */
    var installedUpiApps by remember { mutableStateOf<List<InstalledUpiApp>?>(null) }
    var selectedUpiPackage by remember { mutableStateOf<String?>(null) }

    /** UPI app was launched; waiting for the Cashfree callback or the user returning to the app. */
    var awaitingUpiResult by remember { mutableStateOf(false) }

    /** Post-authorization success; user taps Continue → Home. */
    var showTrialSuccessScreen by remember { mutableStateOf(false) }

    /** subscription-status is being polled after the UPI flow returned. */
    var confirming by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current

    fun goHome() {
        navController.navigate("home") {
            popUpTo("subscription") { inclusive = true }
        }
    }

    LaunchedEffect(Unit) {
        phone = dataStoreManager.phoneNumber.firstOrNull()?.trim().orEmpty()
        if (phone.isBlank()) return@LaunchedEffect
        AnalyticsManager.identify(phone)
        val result = EntitlementRepository.refreshDetailed(phone)
        when {
            result.accountMissing -> {
                LocalSession.clear(context)
                LocalSession.goToLogin(navController)
            }
            result.hasPremium -> {
                Log.i(TAG, "Valid Cashfree entitlement on entry — routing to home")
                goHome()
            }
        }
    }

    // Load UPI apps as soon as the paywall opens — no post-CTA cancel sheet.
    DisposableEffect(Unit) {
        var active = true
        CashfreeSubscriptionCheckout.fetchInstalledUpiApps(context) { apps ->
            if (!active) return@fetchInstalledUpiApps
            installedUpiApps = apps
            selectedUpiPackage = preferDefaultUpiPackage(
                installedPackageNames = apps.map { it.packageName },
                lastUsedPackageName = UpiLastUsedStore.read(context),
            )
        }
        onDispose { active = false }
    }

    fun showPaymentError(
        raw: String? = null,
        throwable: Throwable? = null,
        isMandateStep: Boolean = false,
        onRetry: () -> Unit,
    ) {
        PaymentErrors.logFailure(TAG, raw, throwable)
        paymentErrorDialog = PaymentErrorDialogState(
            type = PaymentErrors.classify(raw, throwable, isMandateStep = isMandateStep),
            onRetry = onRetry,
        )
        isLoading = false
    }

    fun navigateBackToLogin() {
        scope.launch {
            showTrialSuccessScreen = false
            EntitlementRepository.clearForLogout()
            dataStoreManager.logout()
            navController.navigate("login") {
                popUpTo("subscription") { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    /** Toolbar / system back: leave paywall, never wipe the session. */
    fun leavePaywall() {
        showTrialSuccessScreen = false
        if (!navController.popBackStack()) {
            activity?.moveTaskToBack(true)
        }
    }

    BackHandler { leavePaywall() }

    /** Premium is unlocked ONLY when subscription-status says isPremium (webhook is the writer). */
    fun verifyAndUnlock() {
        if (confirming) return
        val phoneForApi = phone.trim()
        if (phoneForApi.isBlank()) {
            showPaymentError(raw = "session_expired", onRetry = { navigateBackToLogin() })
            return
        }
        awaitingUpiResult = false
        confirming = true
        scope.launch {
            var hasPremium = false
            for (attempt in 0 until VERIFY_POLL_ATTEMPTS) {
                if (attempt > 0) delay(VERIFY_POLL_INTERVAL_MS)
                hasPremium = EntitlementRepository.refreshDetailed(phoneForApi).hasPremium
                if (hasPremium) break
            }
            confirming = false
            if (hasPremium) {
                pendingSession = null
                paymentErrorDialog = null
                // Meta Purchase (₹3 AUTH) is webhook-only to avoid double-counting.
                AnalyticsManager.trackOnce(
                    key = "trial_paid",
                    eventName = AnalyticsEvents.TRIAL_PAID,
                    props = mapOf(
                        "amount" to SubscriptionPricing.AUTHORIZATION_AMOUNT,
                        "currency" to SubscriptionPricing.CURRENCY,
                        "phone" to phoneForApi,
                    ),
                )
                AnalyticsManager.flush()
                showTrialSuccessScreen = true
            } else {
                // Verify also fires for pending/cancelled UPI flows; stay on the paywall quietly.
                Log.i(TAG, "subscription-status not premium after polling")
            }
        }
    }

    // Local mutual recursion: launch retries create; create launches UPI.
    lateinit var startSubscription: () -> Unit

    fun launchUpiApp(packageName: String) {
        val session = pendingSession
        val hostActivity = activity
        if (session == null || hostActivity == null) {
            showPaymentError(raw = "checkout_unavailable", onRetry = { startSubscription() })
            return
        }
        CashfreeSubscriptionCheckout.launchUpiIntent(
            activity = hostActivity,
            subscriptionId = session.subscriptionId,
            subscriptionSessionId = session.sessionId,
            upiAppPackageName = packageName,
        ).fold(
            onSuccess = {
                isLoading = false
                UpiLastUsedStore.write(context, packageName)
                awaitingUpiResult = true
            },
            onFailure = { e ->
                Log.e(TAG, "launchUpiIntent failed", e)
                showPaymentError(
                    raw = e.message,
                    throwable = e,
                    isMandateStep = true,
                    onRetry = { startSubscription() },
                )
            },
        )
    }

    startSubscription = start@{
        val selected = selectedUpiPackage?.trim().orEmpty()
        val apps = installedUpiApps
        if (!canStartTrialPayment(
                selectedPackageName = selected,
                appsLoading = apps == null,
                installedCount = apps?.size ?: 0,
                isLoading = isLoading,
                confirming = confirming,
            )
        ) {
            if (apps != null && apps.isEmpty()) {
                showPaymentError(
                    raw = context.getString(R.string.sub_no_upi),
                    onRetry = { startSubscription() },
                )
            }
            return@start
        }
        val phoneForApi = phone.trim()
        if (phoneForApi.isBlank()) {
            showPaymentError(raw = "session_expired", onRetry = { navigateBackToLogin() })
            return@start
        }
        isLoading = true
        scope.launch {
            CashfreeSubscriptionRepository.createSubscription(phoneForApi).fold(
                onSuccess = { created ->
                    if (created.alreadyExists && created.status.equals("active", ignoreCase = true)) {
                        isLoading = false
                        verifyAndUnlock()
                        return@launch
                    }
                    val subscriptionId = created.subscriptionId
                    val sessionId = created.subscriptionSessionId
                    if (subscriptionId.isNullOrBlank() || sessionId.isNullOrBlank()) {
                        showPaymentError(
                            raw = context.getString(R.string.sub_start_failed),
                            onRetry = { startSubscription() },
                        )
                        return@launch
                    }
                    pendingSession = PendingCashfreeSession(subscriptionId, sessionId)
                    // Direct launch — no Cancel / overlay step.
                    launchUpiApp(selected)
                },
                onFailure = { e ->
                    Log.e(TAG, "create-subscription failed", e)
                    showPaymentError(raw = e.message, throwable = e, onRetry = { startSubscription() })
                },
            )
        }
    }

    LaunchedEffect(Unit) {
        SubscriptionCheckoutBridge.events.collect { event ->
            when (event) {
                is SubscriptionCheckoutBridge.Event.Verify -> {
                    Log.i(TAG, "Cashfree verify callback subId=${event.subscriptionId}")
                    verifyAndUnlock()
                }
                is SubscriptionCheckoutBridge.Event.Failure -> {
                    Log.w(TAG, "Cashfree failure callback: ${event.message}")
                    awaitingUpiResult = false
                    showPaymentError(
                        raw = event.message,
                        isMandateStep = true,
                        onRetry = { startSubscription() },
                    )
                    // The mandate may still have gone through — re-check once.
                    val phoneForApi = phone.trim()
                    if (phoneForApi.isNotBlank() && EntitlementRepository.refreshDetailed(phoneForApi).hasPremium) {
                        paymentErrorDialog = null
                        pendingSession = null
                        // Meta Purchase (₹3 AUTH) is webhook-only to avoid double-counting.
                        AnalyticsManager.trackOnce(
                            key = "trial_paid",
                            eventName = AnalyticsEvents.TRIAL_PAID,
                            props = mapOf(
                                "amount" to SubscriptionPricing.AUTHORIZATION_AMOUNT,
                                "currency" to SubscriptionPricing.CURRENCY,
                                "phone" to phoneForApi,
                            ),
                        )
                        AnalyticsManager.flush()
                        showTrialSuccessScreen = true
                    }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingUpiResult) {
                scope.launch {
                    delay(RESUME_VERIFY_DELAY_MS)
                    if (awaitingUpiResult) verifyAndUnlock()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val ctaEnabled = !showTrialSuccessScreen &&
        canStartTrialPayment(
            selectedPackageName = selectedUpiPackage,
            appsLoading = installedUpiApps == null,
            installedCount = installedUpiApps?.size ?: 0,
            isLoading = isLoading,
            confirming = confirming,
        )

    Box(modifier = Modifier.fillMaxSize()) {
        PremiumScreenBackground()

        // Top block flexes (video). Bottom stack is tight:
        // features → legal links → UPI + Start Free Trial (no gap).
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                ) {
                    IconButton(
                        onClick = { leavePaywall() },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = PremiumSubscriptionColors.TextPrimary,
                            containerColor = Color.White.copy(alpha = 0.55f),
                        ),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.sub_back),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val idealHeight = maxWidth / PAYWALL_VIDEO_ASPECT_RATIO
                    val videoHeight = min(idealHeight, maxHeight)
                    PaywallPreviewVideo(
                        videoUrl = PAYWALL_PREVIEW_VIDEO_URL,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(videoHeight),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                PaywallHeadlineBanner(text = stringResource(R.string.sub_headline))

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "₹${SubscriptionPricing.AUTHORIZATION_AMOUNT}",
                    color = PremiumSubscriptionColors.Forest,
                    fontSize = 68.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp,
                    maxLines = 1,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = stringResource(
                        R.string.sub_start_today,
                        SubscriptionPricing.AUTHORIZATION_AMOUNT,
                    ),
                    color = PremiumSubscriptionColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(6.dp))

                Image(
                    painter = painterResource(id = R.drawable.paywall_feature_cards),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp)
                        .padding(horizontal = 2.dp),
                    contentScale = ContentScale.Fit,
                )

                Spacer(modifier = Modifier.height(6.dp))

                PaywallLegalLinksRow()

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(20.dp), clip = false)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PaywallUpiDropdown(
                        apps = installedUpiApps,
                        selectedPackageName = selectedUpiPackage,
                        onSelect = { selectedUpiPackage = it },
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    PremiumGradientCtaButton(
                        text = stringResource(R.string.sub_cta),
                        onClick = { startSubscription() },
                        enabled = ctaEnabled,
                        loading = isLoading,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        TrialActivatedSuccessOverlay(
            visible = showTrialSuccessScreen,
            onContinue = {
                showTrialSuccessScreen = false
                goHome()
            },
        )

        SubscriptionConfirmingOverlay(
            visible = confirming,
            soft = false,
            onContinueInBackground = { confirming = false },
        )

        PaymentErrorAlertDialog(
            state = paymentErrorDialog,
            onDismiss = { paymentErrorDialog = null },
        )
    }
}

@Composable
private fun PaywallLegalLinksRow() {
    val context = LocalContext.current
    val linkColor = PremiumSubscriptionColors.TextMuted
    val links = listOf(
        stringResource(R.string.sub_terms) to { LegalUrlOpener.openTermsAndConditions(context) },
        stringResource(R.string.sub_privacy) to { LegalUrlOpener.openPrivacyPolicy(context) },
        stringResource(R.string.sub_refund) to { LegalUrlOpener.openSubscriptionDisclosure(context) },
        stringResource(R.string.sub_use_of_ai) to { LegalUrlOpener.openPrivacyPolicy(context) },
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        links.forEachIndexed { index, (label, onClick) ->
            if (index > 0) {
                Text(
                    text = stringResource(R.string.sub_legal_dot),
                    color = linkColor,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            Text(
                text = label,
                color = linkColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onClick),
            )
        }
    }
}

/**
 * Blocking, reassuring overlay shown while subscription-status is polled after the UPI flow, so the
 * user is never shown a premature failure during confirmation.
 */
@Composable
private fun SubscriptionConfirmingOverlay(
    visible: Boolean,
    soft: Boolean,
    onContinueInBackground: () -> Unit,
) {
    if (!visible) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6F6F3EC)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp),
        ) {
            CircularProgressIndicator(color = PremiumSubscriptionColors.Gold)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(
                    if (soft) R.string.sub_confirming_short else R.string.sub_confirming,
                ),
                color = PremiumSubscriptionColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(if (soft) R.string.sub_slow else R.string.sub_keep_open),
                color = PremiumSubscriptionColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
            if (soft) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onContinueInBackground) {
                    Text(
                        text = stringResource(R.string.sub_browse),
                        color = PremiumSubscriptionColors.Gold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

