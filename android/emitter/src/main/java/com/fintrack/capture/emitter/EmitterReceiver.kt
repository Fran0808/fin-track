package com.fintrack.capture.emitter

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class EmitterReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.fintrack.capture.emitter.EMIT") return
        try {
            NotificationFixtures.publish(context, requireNotNull(intent.getStringExtra("scenario")),
                requireNotNull(intent.getStringExtra("run_id")), intent.getLongExtra("event_time", 0))
            resultCode = Activity.RESULT_OK
        } catch (_: Exception) {
            resultCode = Activity.RESULT_CANCELED
            resultData = "SYNTHETIC_EMISSION_FAILED"
        }
    }
}
