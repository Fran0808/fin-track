package com.financemanager.listener.worker

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object SyncCoordinator {
    private val mutex = Mutex()

    suspend fun <T> serialized(block: suspend () -> T): T = mutex.withLock { block() }
}
