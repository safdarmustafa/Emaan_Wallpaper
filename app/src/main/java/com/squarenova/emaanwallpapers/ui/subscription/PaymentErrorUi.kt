package com.squarenova.emaanwallpapers.ui.subscription

import android.util.Log
import androidx.compose.ui.res.stringResource
import com.squarenova.emaanwallpapers.BuildConfig
import com.squarenova.emaanwallpapers.R
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

    fun titleRes(type: PaymentErrorType): Int = when (type) {
        PaymentErrorType.PAYMENT_CANCELLED -> R.string.pay_cancelled_title
        PaymentErrorType.MANDATE_NOT_APPROVED -> R.string.pay_mandate_title
        PaymentErrorType.PAYMENT_FAILED -> R.string.pay_failed_title
        PaymentErrorType.NETWORK_ERROR -> R.string.pay_network_title
        PaymentErrorType.UNKNOWN_ERROR -> R.string.pay_unknown_title
    }

    fun messageRes(type: PaymentErrorType): Int = when (type) {
        PaymentErrorType.PAYMENT_CANCELLED -> R.string.pay_cancelled_body
        PaymentErrorType.MANDATE_NOT_APPROVED -> R.string.pay_mandate_body
        PaymentErrorType.PAYMENT_FAILED -> R.string.pay_failed_body
        PaymentErrorType.NETWORK_ERROR -> R.string.pay_network_body
        PaymentErrorType.UNKNOWN_ERROR -> R.string.pay_unknown_body
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
        title = { Text(stringResource(PaymentErrors.titleRes(current.type))) },
        text = {
            Text(
                text = stringResource(PaymentErrors.messageRes(current.type)),
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
                Text(stringResource(R.string.action_retry))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
