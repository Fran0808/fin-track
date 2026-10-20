package com.fintrack.capture.emitter

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class EmitterActivity : Activity() {
    private val runId = "manual-${System.currentTimeMillis()}"
    private val eventTime = System.currentTimeMillis() - 10_000
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        column.addView(TextView(this).apply { text = "Synthetic notifications only. No network access. Enable notification access for FinTrack Capture Test separately." })
        val status = TextView(this)
        NotificationFixtures.scenarios.forEach { scenario ->
            column.addView(Button(this).apply {
                text = scenario.replaceFirstChar { it.uppercase() }
                setOnClickListener {
                    try { NotificationFixtures.publish(this@EmitterActivity, scenario, runId, eventTime); status.text = "Fixture requested: $scenario" }
                    catch (_: Exception) { status.text = "Could not publish. Check notification permission." }
                }
            })
        }
        column.addView(status)
        setContentView(ScrollView(this).apply { addView(column) })
    }
}
