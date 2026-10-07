package com.financemanager.listener.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CaptureState(
    val connected: Boolean = false,
    val lastConnection: Long? = null,
    val lastNotification: Long? = null,
    val lastSaved: Long? = null,
    val lastError: String? = null,
    val syncMessage: String = "Sin sincronización en esta ejecución"
)

object CaptureStatus {
    private val mutable = MutableStateFlow(CaptureState())
    val state = mutable.asStateFlow()
    fun connected(value: Boolean) = mutable.update {
        it.copy(connected = value, lastConnection = if (value) System.currentTimeMillis() else it.lastConnection)
    }
    fun received() = mutable.update { it.copy(lastNotification = System.currentTimeMillis()) }
    fun saved() = mutable.update { it.copy(lastSaved = System.currentTimeMillis()) }
    fun error(message: String) = mutable.update { it.copy(lastError = message) }
    fun sync(message: String) = mutable.update { it.copy(syncMessage = message) }
}
