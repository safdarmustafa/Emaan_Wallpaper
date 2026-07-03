package com.squarenova.emaanwallpapers.subscription

import kotlinx.serialization.Serializable

/**
 * Durable record of an in-flight post-payment confirmation.
 *
 * Created the moment Razorpay returns a MANDATE payment success (or reconstructed during recovery
 * when a mandate checkout was in flight across process death). It is persisted BEFORE any network
 * work starts, so confirmation always survives:
 *   restart · process death · phone reboot · background/resume · configuration change.
 *
 * There is at most ONE active ticket at a time (single-flight). [ticketId] is the idempotency key.
 */
@Serializable
data class ConfirmationTicket(
    val ticketId: String,
    val phone: String,
    val subscriptionId: String,
    val paymentId: String,
    val createdAtMs: Long,
    val attempt: Int = 0,
    val phase: String = ConfirmationPhase.VERIFYING.name,
    /** True once Razorpay reported authenticated/active — lets recovery skip re-verification. */
    val mandateVerified: Boolean = false,
)
