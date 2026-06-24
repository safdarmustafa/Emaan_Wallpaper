package com.squarenova.emaanwallpapers.ui.subscription

import android.app.Application
import android.content.Context
import com.squarenova.emaanwallpapers.util.SecureLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * Bridges [com.razorpay.PaymentResultListener] (MainActivity) to Compose.
 * Tracks which Checkout session is in flight so ₹5 vs mandate success are handled differently.
 *
 * [prepareCheckout] also persists the kind so after UPI / memory pressure / process death
 * the success callback still knows whether this was ENTRY_FEE or MANDATE (in-memory alone is lost).
 */
object SubscriptionManager {

    private const val TAG = "SubscriptionManager"
    private const val PREF = "subscription_razorpay_checkout"
    private const val KEY_PENDING_KIND = "pending_kind_ordinal"

    private var appContext: Context? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _paymentResult = MutableSharedFlow<PaymentResult>(extraBufferCapacity = 64)
    val paymentResult = _paymentResult.asSharedFlow()

    private val pendingCheckout = AtomicReference(CheckoutKind.NONE)

    fun init(application: Application) {
        appContext = application.applicationContext
    }

    private fun persistKind(kind: CheckoutKind) {
        if (kind == CheckoutKind.NONE) return
        appContext?.getSharedPreferences(PREF, Context.MODE_PRIVATE)?.edit()
            ?.putInt(KEY_PENDING_KIND, kind.ordinal)
            ?.apply()
    }

    private fun readPersistedKind(): CheckoutKind {
        val ctx = appContext ?: return CheckoutKind.NONE
        val ord = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt(KEY_PENDING_KIND, -1)
        if (ord < 0 || ord >= CheckoutKind.entries.size) return CheckoutKind.NONE
        val k = CheckoutKind.entries[ord]
        return if (k == CheckoutKind.NONE) CheckoutKind.NONE else k
    }

    private fun clearPersistedKind() {
        appContext?.getSharedPreferences(PREF, Context.MODE_PRIVATE)?.edit()
            ?.remove(KEY_PENDING_KIND)
            ?.apply()
    }

    /**
     * Read persisted checkout kind without clearing — for UI restore on resume / rotation.
     */
    fun peekPersistedCheckoutKind(): CheckoutKind = readPersistedKind()

    /**
     * After process death, in-memory [pendingCheckout] is [CheckoutKind.NONE] but disk may still
     * hold ENTRY_FEE or MANDATE. Call on [Lifecycle.Event.ON_RESUME] before relying on phase state.
     */
    fun restorePendingCheckoutFromPersistenceIfNeeded() {
        val p = readPersistedKind()
        if (p == CheckoutKind.NONE) return
        if (pendingCheckout.compareAndSet(CheckoutKind.NONE, p)) {
            SecureLog.d(TAG, "Restored in-memory pending checkout from persistence: $p")
        }
    }

    /**
     * Call immediately before [com.razorpay.Checkout.open] for each session.
     */
    fun prepareCheckout(kind: CheckoutKind) {
        pendingCheckout.set(kind)
        persistKind(kind)
        SecureLog.d(TAG, "prepareCheckout: $kind")
    }

    /**
     * Resolves kind for a success: in-memory first, then disk (process may have been killed while user was in UPI app).
     */
    private fun resolveKindForSuccess(): CheckoutKind {
        var k = pendingCheckout.getAndSet(CheckoutKind.NONE)
        if (k != CheckoutKind.NONE) {
            clearPersistedKind()
            return k
        }
        k = readPersistedKind()
        if (k != CheckoutKind.NONE) {
            clearPersistedKind()
            SecureLog.i(TAG, "Recovered checkout kind after process/activity loss: $k")
            return k
        }
        return CheckoutKind.NONE
    }

    fun onPaymentSuccess(paymentId: String) {
        SecureLog.d(TAG, "onPaymentSuccess id=${SecureLog.redactId(paymentId)}")
        val kind = resolveKindForSuccess()
        if (kind == CheckoutKind.NONE) {
            SecureLog.w(TAG, "Payment success without prepareCheckout — ignoring")
            return
        }
        scope.launch {
            _paymentResult.emit(PaymentResult.Success(paymentId = paymentId, kind = kind))
        }
    }

    fun onPaymentError(errorCode: Int, description: String) {
        SecureLog.e(TAG, "onPaymentError code=$errorCode")
        val kind = pendingCheckout.getAndSet(CheckoutKind.NONE)
        clearPersistedKind()
        scope.launch {
            _paymentResult.emit(
                PaymentResult.Error(
                    message = description,
                    errorCode = errorCode,
                    checkoutKind = kind,
                )
            )
        }
    }
}

/** Which Razorpay Checkout session completed. */
enum class CheckoutKind {
    NONE,
    /** ₹5 entry fee (order) */
    ENTRY_FEE,
    /** Subscription / autopay mandate */
    MANDATE
}

sealed class PaymentResult {
    data class Success(val paymentId: String, val kind: CheckoutKind) : PaymentResult()
    data class Error(
        val message: String,
        val errorCode: Int = -1,
        val checkoutKind: CheckoutKind = CheckoutKind.NONE,
    ) : PaymentResult()
}
