package com.squarenova.emaanwallpapers.ui.subscription

import android.util.Log
import com.squarenova.emaanwallpapers.BuildConfig
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class PaymentErrorType {
    PAYMENT_CANCELLED,
    MANDATE_NOT_APPROVED,
    PAYMENT_FAILED,
    NETWORK_ERROR,
    UNKNOWN_ERROR,
}

data class PaymentErrorDialogState(
    val type: PaymentErrorType,
    val onRetry: () -> Unit,
)

object PaymentErrors {
    private const val TAG = "PaymentErrors"

    fun classify(
        raw: String? = null,
        throwable: Throwable? = null,
        errorCode: Int? = null,
        isMandateStep: Boolean = false,
    ): PaymentErrorType {
        val msg = buildString {
            raw?.trim()?.let { append(it.lowercase()) }
            throwable?.let {
                if (isNotEmpty()) append(' ')
                append(it.message.orEmpty().lowercase())
                append(' ')
                append(it::class.simpleName.orEmpty().lowercase())
            }
        }

        // Razorpay Checkout: 0 = cancelled by user, 4 = checkout closed by user
        if (errorCode == 0 || errorCode == 4) {
            return PaymentErrorType.PAYMENT_CANCELLED
        }
        if (errorCode == 2) {
            return PaymentErrorType.NETWORK_ERROR
        }

        if (msg.contains("cancel") || msg.contains("cancelled") || msg.contains("canceled")) {
            return PaymentErrorType.PAYMENT_CANCELLED
        }

        throwable?.let {
            if (it is IOException || it is UnknownHostException || it is SocketTimeoutException) {
                return PaymentErrorType.NETWORK_ERROR
            }
        }

        if (isNetworkMessage(msg)) {
            return PaymentErrorType.NETWORK_ERROR
        }

        if (isMandateStep || isMandateMessage(msg)) {
            return PaymentErrorType.MANDATE_NOT_APPROVED
        }

        if (isPaymentFailureMessage(msg)) {
            return PaymentErrorType.PAYMENT_FAILED
        }

        return PaymentErrorType.UNKNOWN_ERROR
    }

    fun title(type: PaymentErrorType): String = when (type) {
        PaymentErrorType.PAYMENT_CANCELLED -> "Payment cancelled"
        PaymentErrorType.MANDATE_NOT_APPROVED -> "AutoPay not approved"
        PaymentErrorType.PAYMENT_FAILED -> "Payment failed"
        PaymentErrorType.NETWORK_ERROR -> "Connection problem"
        PaymentErrorType.UNKNOWN_ERROR -> "Something went wrong"
    }

    fun message(type: PaymentErrorType): String = when (type) {
        PaymentErrorType.PAYMENT_CANCELLED ->
            "You closed the payment screen. No money was charged. Tap Retry when you're ready to continue."
        PaymentErrorType.MANDATE_NOT_APPROVED ->
            "AutoPay was not approved in your UPI app. Approve the mandate to finish setting up your subscription."
        PaymentErrorType.PAYMENT_FAILED ->
            "We couldn't complete your payment. Check your UPI app or try another payment method."
        PaymentErrorType.NETWORK_ERROR ->
            "Please check your internet connection and try again."
        PaymentErrorType.UNKNOWN_ERROR ->
            "We ran into an unexpected issue. Please try again in a moment."
    }

    fun logFailure(
        tag: String,
        raw: String? = null,
        throwable: Throwable? = null,
        errorCode: Int? = null,
    ) {
        if (BuildConfig.DEBUG) {
            Log.e(tag, "Payment failure (user dialog hidden) code=$errorCode raw=$raw", throwable)
        }
    }

    private fun isNetworkMessage(msg: String): Boolean =
        listOf(
            "network",
            "timeout",
            "timed out",
            "unable to resolve",
            "failed to connect",
            "connection",
            "unreachable",
            "no address associated",
            "ssl",
            "handshake",
        ).any { it in msg }

    private fun isMandateMessage(msg: String): Boolean =
        listOf(
            "mandate",
            "autopay",
            "not approved",
            "approval",
            "verification timed out",
            "verification failed",
            "authenticated",
        ).any { it in msg }

    private fun isPaymentFailureMessage(msg: String): Boolean =
        listOf(
            "payment",
            "declined",
            "refund",
            "refunded",
            "order",
            "verify",
            "invalid",
            "replay",
            "already verified",
            "failed",
        ).any { it in msg }
}

@Composable
fun PaymentErrorAlertDialog(
    state: PaymentErrorDialogState?,
    onDismiss: () -> Unit,
) {
    val current = state ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(PaymentErrors.title(current.type)) },
        text = {
            Text(
                text = PaymentErrors.message(current.type),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    current.onRetry()
                },
            ) {
                Text("Retry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
