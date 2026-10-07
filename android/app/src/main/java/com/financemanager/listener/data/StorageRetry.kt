package com.financemanager.listener.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

object StorageRetry {
    suspend fun <T> run(isTransient: (Exception) -> Boolean, pause: suspend (Long) -> Unit = { delay(it) }, block: () -> T): T {
        for (attempt in 0..2) {
            try { return block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (attempt == 2 || !isTransient(e)) throw e
                pause(250L * (attempt + 1))
            }
        }
        error("Unreachable storage retry state")
    }
}
