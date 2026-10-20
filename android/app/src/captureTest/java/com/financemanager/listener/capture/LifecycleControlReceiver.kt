package com.financemanager.listener.capture

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import com.financemanager.listener.data.AppDatabase
import com.financemanager.listener.data.PairingPreferences
import com.financemanager.listener.service.CaptureStatus
import com.financemanager.listener.service.ListenerHealthCoordinator
import com.financemanager.listener.service.YapeNotificationListenerService
import kotlinx.coroutines.*
import org.json.JSONArray

/** Signature protected, fixed commands, synthetic tag scope; absent from normal APKs. */
class LifecycleControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.financemanager.listener.capturetest.CONTROL") return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                check(context.packageName == "com.financemanager.listener.capturetest")
                check(context.checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_DENIED)
                check(PairingPreferences.session(context) == null)
                val run = requireNotNull(intent.getStringExtra("run_id"))
                require(run.matches(Regex("lifecycle-[a-z0-9-]{1,50}")))
                when (intent.getStringExtra("command")) {
                    "arm" -> CaptureExecutionProbe.arm(run, requireNotNull(intent.getStringExtra("stage")))
                    "release" -> { CaptureExecutionProbe.release(); CaptureRuntime.get(context).resume() }
                    "reset" -> CaptureExecutionProbe.reset()
                    "snapshot" -> Unit
                    "foreground" -> withContext(Dispatchers.Main) { ListenerHealthCoordinator.foreground(context) }
                    "review" -> withContext(Dispatchers.Main) { YapeNotificationListenerService.reviewActive() }
                    "unbind" -> withContext(Dispatchers.Main) { CaptureExecutionProbe.unbind() }
                    "block_rebind" -> CaptureExecutionProbe.blockRebind()
                    else -> error("Unknown synthetic lifecycle command")
                }
                val db = AppDatabase.getDatabase(context)
                val result = CaptureExecutionProbe.snapshot().put("pid", Process.myPid())
                    .put("connected", CaptureStatus.state.value.connected)
                    .put("permission", YapeNotificationListenerService.hasPermission(context))
                    .put("last_error", CaptureStatus.state.value.lastError ?: "")
                val key = arrayOf<Any>("%$run%")
                for ((name, sql) in mapOf(
                    "posted" to "SELECT COUNT(*) FROM capture_inbox WHERE kind = 'POSTED' AND sourceKey LIKE ?",
                    "pending" to "SELECT COUNT(*) FROM capture_inbox WHERE kind = 'POSTED' AND status = 'PENDING' AND sourceKey LIKE ?",
                    "recovering" to "SELECT COUNT(*) FROM capture_inbox WHERE kind = 'POSTED' AND recovering = 1 AND sourceKey LIKE ?",
                    "movements" to "SELECT COUNT(*) FROM local_transactions WHERE sourceKey LIKE ?",
                    "synced" to "SELECT COUNT(*) FROM local_transactions WHERE isSynced = 1 AND sourceKey LIKE ?"
                )) db.query(sql, key).use { check(it.moveToFirst()); result.put(name, it.getInt(0)) }
                val hashes = JSONArray()
                db.query("SELECT transactionHash FROM local_transactions WHERE sourceKey LIKE ? ORDER BY transactionHash", key).use {
                    while (it.moveToNext()) hashes.put(it.getString(0))
                }
                result.put("hashes", hashes)
                pending.resultCode = Activity.RESULT_OK
                pending.resultData = result.toString()
            } catch (_: Exception) {
                pending.resultCode = Activity.RESULT_CANCELED
                pending.resultData = "SYNTHETIC_CONTROL_FAILED"
            } finally { pending.finish() }
        }
    }
}
