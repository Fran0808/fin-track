package com.financemanager.listener.capture

import android.service.notification.NotificationListenerService
import com.financemanager.listener.data.CaptureObservation

/** Normal builds have no fault controls, persistent gates or lifecycle test receiver. */
internal object CaptureExecutionProbe {
    fun allowRebind() = true
    @Suppress("UNUSED_PARAMETER")
    suspend fun checkpoint(stage: String, observation: CaptureObservation) = Unit
    @Suppress("UNUSED_PARAMETER")
    fun listenerConnected(service: NotificationListenerService) = Unit
    @Suppress("UNUSED_PARAMETER")
    fun lifecycle(event: String) = Unit
}
