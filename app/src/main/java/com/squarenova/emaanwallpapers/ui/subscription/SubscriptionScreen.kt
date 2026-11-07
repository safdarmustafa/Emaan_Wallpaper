package com.squarenova.emaanwallpapers.ui.subscription

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Wallpaper
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.squarenova.emaanwallpapers.R
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.squarenova.emaanwallpapers.analytics.AnalyticsManager
import com.squarenova.emaanwallpapers.data.DataStoreManager
import com.squarenova.emaanwallpapers.data.LocalSession
import com.squarenova.emaanwallpapers.subscription.EntitlementRepository
import com.squarenova.emaanwallpapers.subscription.SubscriptionPricing
import com.squarenova.emaanwallpapers.subscription.cashfree.CashfreeSubscriptionCheckout
import com.squarenova.emaanwallpapers.subscription.cashfree.CashfreeSubscriptionRepository
import com.squarenova.emaanwallpapers.subscription.cashfree.SubscriptionCheckoutBridge
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

    var showUpiPicker by remember { mutableStateOf(false) }

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
            showUpiPicker = false
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
        if (showUpiPicker) {
            showUpiPicker = false
            return
        }
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
                val subscriptionId = pendingSession?.subscriptionId ?: phoneForApi
                pendingSession = null
                paymentErrorDialog = null
                AnalyticsManager.trackPurchaseOnce(
                    key = "purchase:cashfree:$subscriptionId",
                    amount = SubscriptionPricing.MONTHLY_AMOUNT,
                    currency = SubscriptionPricing.CURRENCY,
                    props = mapOf(
                        "subscription_id" to subscriptionId,
                        "status" to EntitlementRepository.lastStatus.orEmpty(),
                        "billing_type" to "client_activation",
                        "source" to "android_cashfree",
                    ),
                )
                showTrialSuccessScreen = true
            } else {
                // Verify also fires for pending/cancelled UPI flows; stay on the paywall quietly.
                Log.i(TAG, "subscription-status not premium after polling")
            }
        }
    }

    fun startSubscription() {
        if (isLoading || confirming || showUpiPicker) return
        val phoneForApi = phone.trim()
        if (phoneForApi.isBlank()) {
            showPaymentError(raw = "session_expired", onRetry = { navigateBackToLogin() })
            return
        }
        isLoading = true
        scope.launch {
            CashfreeSubscriptionRepository.createSubscription(phoneForApi).fold(
                onSuccess = { created ->
                    isLoading = false
                    if (created.alreadyExists && created.status.equals("active", ignoreCase = true)) {
                        verifyAndUnlock()
                        return@launch
                    }
                    val subscriptionId = created.subscriptionId
                    val sessionId = created.subscriptionSessionId
                    if (subscriptionId.isNullOrBlank() || sessionId.isNullOrBlank()) {
                        showPaymentError(
                            raw = "Subscription start नहीं हो सकी। फिर कोशिश करें।",
                            onRetry = { startSubscription() },
                        )
                        return@launch
                    }
                    pendingSession = PendingCashfreeSession(subscriptionId, sessionId)
                    showUpiPicker = true
                },
                onFailure = { e ->
                    Log.e(TAG, "create-subscription failed", e)
                    showPaymentError(raw = e.message, throwable = e, onRetry = { startSubscription() })
                },
            )
        }
    }

    fun launchUpiApp(packageName: String) {
        showUpiPicker = false
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
            onSuccess = { awaitingUpiResult = true },
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

    Box(modifier = Modifier.fillMaxSize()) {
        PremiumScreenBackground()

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxHeight < 680.dp
            val tight = maxHeight < 600.dp
            val priceSize = when {
                tight -> 44.sp
                compact -> 52.sp
                else -> 60.sp
            }
            val titleSize = when {
                tight -> 24.sp
                compact -> 28.sp
                else -> 32.sp
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = if (tight) 2.dp else 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { leavePaywall() },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = PremiumSubscriptionColors.TextPrimary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.sub_back),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.sub_title),
                        color = PremiumSubscriptionColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = if (tight) Arrangement.Center else Arrangement.SpaceEvenly,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(if (tight) 48.dp else 56.dp)
                                .clip(CircleShape)
                                .background(PremiumSubscriptionColors.Gold.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NightsStay,
                                contentDescription = null,
                                tint = PremiumSubscriptionColors.Gold,
                                modifier = Modifier.size(if (tight) 22.dp else 26.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(if (tight) 10.dp else 16.dp))
                        Text(
                            text = stringResource(R.string.sub_headline),
                            color = PremiumSubscriptionColors.TextPrimary,
                            fontSize = titleSize,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.4.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.sub_pitch),
                            color = PremiumSubscriptionColors.TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.sub_only),
                            color = PremiumSubscriptionColors.TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 0.8.sp,
                        )
                        Text(
                            text = "₹${SubscriptionPricing.AUTHORIZATION_AMOUNT}",
                            color = PremiumSubscriptionColors.TextPrimary,
                            fontSize = priceSize,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-1).sp,
                        )
                        Text(
                            text = stringResource(R.string.sub_start_today),
                            color = PremiumSubscriptionColors.TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SubscriptionBenefit(
                            icon = Icons.Outlined.Wallpaper,
                            title = stringResource(R.string.sub_feature_wallpapers),
                            subtitle = stringResource(R.string.sub_feature_wallpapers_sub),
                            compact = compact,
                        )
                        SubscriptionBenefit(
                            icon = Icons.Outlined.MusicNote,
                            title = stringResource(R.string.sub_feature_ringtones),
                            subtitle = stringResource(R.string.sub_feature_ringtones_sub),
                            compact = compact,
                        )
                        SubscriptionBenefit(
                            icon = Icons.Outlined.PlayCircle,
                            title = stringResource(R.string.sub_feature_reels),
                            subtitle = stringResource(R.string.sub_feature_reels_sub),
                            compact = compact,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = if (tight) 8.dp else 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PremiumGradientCtaButton(
                        text = stringResource(R.string.sub_cta, SubscriptionPricing.AUTHORIZATION_AMOUNT),
                        onClick = { startSubscription() },
                        enabled = !showTrialSuccessScreen && !confirming && !showUpiPicker,
                        loading = isLoading,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = PremiumSubscriptionColors.TextMuted,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.sub_secure),
                            color = PremiumSubscriptionColors.TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                    TextButton(
                        onClick = { LegalUrlOpener.openSubscriptionDisclosure(context) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.sub_terms),
                            color = PremiumSubscriptionColors.TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                }
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

        if (showUpiPicker) {
            UpiAppPickerOverlay(
                onAppSelected = { packageName -> launchUpiApp(packageName) },
                onDismiss = { showUpiPicker = false },
                onNoUpiAppsAvailable = {
                    showUpiPicker = false
                    showPaymentError(
                        raw = "कोई UPI app नहीं मिला। UPI app install करके फिर कोशिश करें।",
                        onRetry = { startSubscription() },
                    )
                },
            )
        }

        PaymentErrorAlertDialog(
            state = paymentErrorDialog,
            onDismiss = { paymentErrorDialog = null },
        )
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

@Composable
private fun SubscriptionBenefit(
    icon: ImageVector,
    title: String,
    subtitle: String,
    compact: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(104.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PremiumSubscriptionColors.Gold,
            modifier = Modifier.size(if (compact) 20.dp else 22.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            color = PremiumSubscriptionColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = subtitle,
            color = PremiumSubscriptionColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
        )
    }
}
