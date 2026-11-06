package com.squarenova.emaanwallpapers.subscription.cashfree

import com.cashfree.pg.core.api.utils.CFErrorResponse
import com.cashfree.pg.core.api.utils.CFSubscriptionResponse
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Bridges Cashfree Activity callbacks into Compose collectors.
 * Registered once from [com.squarenova.emaanwallpapers.MainActivity].
 */
object SubscriptionCheckoutBridge {

    sealed class Event {
        data class Verify(val subscriptionId: String?) : Event()
        data class Failure(val message: String) : Event()
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    fun onSubscriptionVerify(response: CFSubscriptionResponse) {
        _events.tryEmit(Event.Verify(response.subscriptionId))
    }

    fun onSubscriptionFailure(error: CFErrorResponse) {
        val message = error.message?.takeIf { it.isNotBlank() }
            ?: "Payment was cancelled or failed. Please try again."
        _events.tryEmit(Event.Failure(message))
    }
}
