package com.financemanager.listener.worker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ManualSyncDispatcher(private val scope: CoroutineScope) {
    private var attempt: Job? = null

    @Synchronized
    fun start(send: suspend () -> Unit): Boolean {
        if (attempt?.isCompleted == false) return false
        val next = scope.launch(start = CoroutineStart.LAZY) { send() }
        attempt = next
        next.start()
        return true
    }

    @Synchronized
    fun cancel() {
        attempt?.cancel()
    }
}
