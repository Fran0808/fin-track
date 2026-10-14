package com.financemanager.listener.service

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CaptureState(
    val connected: Boolean = false,
    val lastConnection: Long? = null,
    val lastNotification: Long? = null,
    val lastSaved: Long? = null,
    val lastError: String? = null,
    val syncMessage: String = "El envío se realizará cuando haya conexión.",
    val history: List<DiagnosticEvent> = emptyList(),
    val syncPhase: SyncPhase = SyncPhase.IDLE
)

enum class SyncPhase { IDLE, QUEUED, SENDING, COMPLETE, AUTH_REQUIRED, RETRY, REJECTED }

enum class CaptureEvent(val label: String) {
    RECEIVED("Notificación de Yape recibida"),
    RECOGNIZED("Movimiento reconocido"),
    UNRECOGNIZED("No se pudo identificar un movimiento"),
    GROUP_SUMMARY("Resumen de notificaciones omitido"),
    SAVED("Movimiento guardado en el teléfono"),
    DUPLICATE("Notificación ya registrada o recuperada"),
    SENT("La web confirmó un movimiento"),
    ERROR("No se pudo procesar una notificación")
}

data class DiagnosticEvent(val time: Long, val event: CaptureEvent)

fun recordCapture(state: CaptureState, event: CaptureEvent, time: Long): CaptureState = state.copy(
    lastNotification = if (event == CaptureEvent.RECEIVED) time else state.lastNotification,
    lastSaved = if (event == CaptureEvent.SAVED) time else state.lastSaved,
    lastError = if (event == CaptureEvent.SAVED || event == CaptureEvent.DUPLICATE) null else state.lastError,
    history = (listOf(DiagnosticEvent(time, event)) + state.history).take(30)
)

object CaptureStatus {
    private val mutable = MutableStateFlow(CaptureState())
    val state = mutable.asStateFlow()
    private var preferences: SharedPreferences? = null

    @Synchronized
    fun initialize(context: Context) {
        if (preferences != null) return
        val prefs = context.getSharedPreferences("fintrack_capture_diagnostics", Context.MODE_PRIVATE)
        preferences = prefs
        val history = runCatching {
            val array = JSONArray(prefs.getString("history", "[]"))
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                DiagnosticEvent(item.getLong("time"), CaptureEvent.valueOf(item.getString("event")))
            }.take(30)
        }.getOrDefault(emptyList())
        mutable.value = CaptureState(
            lastConnection = prefs.getLong("connection", 0).takeIf { it > 0 },
            lastNotification = prefs.getLong("notification", 0).takeIf { it > 0 },
            lastSaved = prefs.getLong("saved", 0).takeIf { it > 0 }, history = history
        )
    }

    @Synchronized
    private fun persist() {
        val value = mutable.value
        val history = JSONArray()
        value.history.forEach { history.put(JSONObject().put("time", it.time).put("event", it.event.name)) }
        preferences?.edit()?.putLong("connection", value.lastConnection ?: 0)
            ?.putLong("notification", value.lastNotification ?: 0)?.putLong("saved", value.lastSaved ?: 0)
            ?.putString("history", history.toString())?.apply()
    }

    @Synchronized
    fun connected(value: Boolean) {
        mutable.update { it.copy(connected = value, lastConnection = if (value) System.currentTimeMillis() else it.lastConnection) }
        persist()
    }
    @Synchronized
    fun event(event: CaptureEvent) {
        mutable.update { recordCapture(it, event, System.currentTimeMillis()) }
        persist()
    }
    fun received() = event(CaptureEvent.RECEIVED)
    fun saved() = event(CaptureEvent.SAVED)
    fun error(message: String) {
        mutable.update { it.copy(lastError = message) }
        event(CaptureEvent.ERROR)
    }
    fun sync(message: String, phase: SyncPhase = SyncPhase.IDLE) = mutable.update { it.copy(syncMessage = message, syncPhase = phase) }
}
