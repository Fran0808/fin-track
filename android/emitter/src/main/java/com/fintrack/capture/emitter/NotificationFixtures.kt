package com.fintrack.capture.emitter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** Fixed synthetic fixtures; no arbitrary financial content or network input is accepted. */
object NotificationFixtures {
    private const val CHANNEL = "synthetic-receipts"
    val scenarios = listOf("basic", "update", "expanded", "lines", "group", "ambiguous", "promotion", "oversized", "clear")

    fun publish(context: Context, scenario: String, runId: String, eventTime: Long) {
        require(scenario in scenarios)
        require(runId.matches(Regex("[A-Za-z0-9_-]{1,40}")))
        require(eventTime > 0 && eventTime <= System.currentTimeMillis())
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Synthetic capture fixtures", NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(null, null)
            enableVibration(false)
        })
        if (scenario == "clear") {
            listOf(1, 2, 3, 4, 5, 6, 10, 11, 12).forEach { manager.cancel(runId, it) }
            return
        }
        fun builder(text: String) = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Confirmación de Pago")
            .setContentText(text).setWhen(eventTime).setOnlyAlertOnce(true)
        fun post(id: Int, notification: Notification.Builder) = manager.notify(runId, id, notification.build())
        when (scenario) {
            "basic" -> post(1, builder("Persona Demo te envió S/ 12.30"))
            "update" -> post(1, builder("Persona Demo te envió S/ 12.30 a tu Yape"))
            "expanded" -> post(2, builder("Persona Demo te envió S/ 25")
                .setStyle(Notification.BigTextStyle().bigText("Persona Demo te envió S/ 25.50")))
            "lines" -> post(3, builder("").setStyle(Notification.InboxStyle().addLine("Persona Demo te envió S/ 17.40")))
            "ambiguous" -> post(4, builder("Persona Demo te envió S/ 12.30 y S/ 8.00"))
            "promotion" -> post(5, builder("Descubre novedades y promociones").setContentTitle("Novedades"))
            // Android clips individual CharSequences before delivery; exceed the listener's
            // line-count boundary instead of relying on a single oversized text field.
            "oversized" -> post(6, builder("").setStyle(Notification.InboxStyle().apply {
                repeat(21) { addLine("Persona Demo te envió S/ 12.30") }
            }))
            "group" -> {
                val group = "synthetic-group-$runId"
                post(11, builder("Persona Uno te envió S/ 11.00").setGroup(group))
                post(12, builder("Persona Dos te envió S/ 22.00").setGroup(group))
                post(10, builder("Resumen de pruebas").setGroup(group).setGroupSummary(true))
            }
        }
    }
}
