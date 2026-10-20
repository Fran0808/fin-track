package com.financemanager.listener.capture

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import com.financemanager.listener.FinTrackApplication
import com.financemanager.listener.data.CaptureObservation
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference

/** Compiled only in captureTest. Gates hold synthetic captures outside Room transactions. */
internal object CaptureExecutionProbe {
    private val prefs get() = FinTrackApplication.instance.getSharedPreferences("synthetic_lifecycle", Context.MODE_PRIVATE)
    private var listener = WeakReference<NotificationListenerService>(null)
    private val events = mutableListOf<JSONObject>()
    @Volatile private var rebindBlocked = false

    fun allowRebind() = !rebindBlocked
    fun blockRebind() { rebindBlocked = true }

    suspend fun checkpoint(stage: String, observation: CaptureObservation) {
        if (observation.kind != "POSTED") return
        val run = prefs.getString("run", null) ?: return
        if (!observation.sourceKey.contains(run)) return
        val gate = prefs.getString("gate", null) ?: return
        if (stage != gate && !(stage == "BEFORE_PROCESS" && gate == "AFTER_COMMIT")) return
        if (stage == gate) {
            check(prefs.edit().putString("reached", stage).putInt("checkpoint_pid", Process.myPid()).commit())
        }
        while (prefs.getString("run", null) == run && prefs.getString("gate", null) == gate) {
            check(System.currentTimeMillis() < prefs.getLong("expires", 0)) { "Synthetic checkpoint expired" }
            delay(20)
        }
    }

    fun arm(run: String, stage: String) {
        require(stage in setOf("BEFORE_COMMIT", "AFTER_COMMIT"))
        check(prefs.edit().clear().putString("run", run).putString("gate", stage)
            .putLong("expires", System.currentTimeMillis() + 120_000).commit())
    }

    fun release() {
        rebindBlocked = false
        check(prefs.edit().remove("gate").remove("run").commit())
    }
    fun reset() { release(); synchronized(events) { events.clear() } }
    fun listenerConnected(service: NotificationListenerService) {
        listener = WeakReference(service)
        lifecycle("CONNECTED")
    }
    fun unbind() { checkNotNull(listener.get()).requestUnbind() }
    fun lifecycle(event: String) = synchronized(events) {
        require(event in setOf("CONNECTED", "DISCONNECTED", "DESTROYED", "ACTIVE_QUERY", "REBIND", "REBIND_EXHAUSTED"))
        if (events.size == 200) events.removeAt(0)
        events.add(JSONObject().put("type", event).put("elapsed", SystemClock.elapsedRealtime()))
        Unit
    }
    fun snapshot(): JSONObject = synchronized(events) {
        JSONObject().put("events", JSONArray(events.toList())).put("reached", prefs.getString("reached", ""))
            .put("checkpoint_pid", prefs.getInt("checkpoint_pid", 0))
    }
}
