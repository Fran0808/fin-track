package com.financemanager.listener.capture

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Bounded precommit ingress. Rejection is observable; durability starts at persist completion. */
class DurableCaptureQueue<T>(scope: CoroutineScope, capacity: Int = 256,
    persist: suspend (T) -> Unit, onCommitted: () -> Unit, onFailure: () -> Unit) {
    private val channel = Channel<T>(capacity)
    init {
        scope.launch {
            for (item in channel) {
                try { persist(item); onCommitted() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { onFailure() }
            }
        }
    }
    fun offer(item: T): Boolean = channel.trySend(item).isSuccess
}
