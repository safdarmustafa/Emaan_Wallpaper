package com.squarenova.emaanwallpapers.subscription

/**
 * Subscription System V2 — the single, explicit confirmation state machine.
 *
 * Boolean flags (`isLoading`, `mandatePaymentSuccessReceived`, …) are replaced by this one
 * observable state. Only the [SubscriptionOrchestrator] mutates it; every UI surface observes it.
 *
 * Legal transitions (checkout launch itself is owned by the screen; everything after Razorpay's
 * payment-success callback is owned by the orchestrator):
 *
 *   Idle
 *     └─ onMandatePaymentSuccess ─► Confirming(VERIFY)
 *   Confirming(VERIFY)      ─► Confirming(ACTIVATE)     (Razorpay authenticated/active)
 *   Confirming(ACTIVATE)    ─► Confirming(SYNC)         (activate-trial ok)
 *   Confirming(ACTIVATE)    ─► Confirming(VERIFY)       (403 not-eligible: status lag, retry)
 *   Confirming(SYNC)        ─► Premium                  (entitlement granted)
 *   Confirming(any)         ─► SoftTimeout              (budget elapsed, still non-terminal)
 *   Confirming(any)         ─► TerminalFailure          (Razorpay terminal / negative)
 *   SoftTimeout             ─► Confirming(...)          (auto-resumed by recovery)
 *   SoftTimeout             ─► Premium                  (recovery / webhook caught up)
 *   Premium / TerminalFailure ─► Idle                   (reset)
 */
sealed interface SubscriptionState {

    /** No confirmation in flight. */
    data object Idle : SubscriptionState

    /** Durable confirmation is running. [phase] is the granular sub-step for observability/UI. */
    data class Confirming(
        val phase: ConfirmationPhase,
        val subscriptionId: String,
        val attempt: Int,
    ) : SubscriptionState

    /** Entitlement confirmed — user is premium (trial or active). Terminal-good. */
    data class Premium(val subscriptionStatus: String?) : SubscriptionState

    /**
     * The active foreground confirmation budget elapsed while Razorpay was still non-terminal
     * AND the mandate was already authenticated/active (entitlement is merely propagating).
     * This is NOT a failure: the durable ticket is retained and recovery keeps confirming.
     */
    data class SoftTimeout(val subscriptionId: String) : SubscriptionState

    /**
     * A subscription exists, but the AutoPay mandate was never completed — Razorpay status is still
     * pre-activation (created). The user exited/cancelled the mandate checkout. This is NOT a failure
     * and NOT an endless "confirming": the durable ticket is retained (survives restart) and the UI
     * offers a single "Complete AutoPay Setup" action that resumes THIS subscription's mandate
     * checkout (never charging twice).
     */
    data class MandatePending(val subscriptionId: String) : SubscriptionState

    /** Razorpay reported a terminal/negative state (cancelled/halted/completed/expired) or the
     *  payment itself failed. This is the only genuine failure. */
    data class TerminalFailure(val reason: String) : SubscriptionState
}

enum class ConfirmationPhase {
    /** Confirming the mandate reached authenticated/active at Razorpay. */
    VERIFYING,

    /** Transitioning the subscription to trial/active server-side (activate-trial). */
    ACTIVATING,

    /** Reading back the entitlement source of truth. */
    SYNCING_ENTITLEMENT,
}
