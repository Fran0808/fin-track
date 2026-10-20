package com.financemanager.listener

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.financemanager.listener.data.AppDatabase
import com.financemanager.listener.data.PairingPreferences
import com.financemanager.listener.parser.NotificationParserDispatcher
import com.financemanager.listener.service.CaptureStatus
import com.financemanager.listener.service.ListenerHealthCoordinator
import com.financemanager.listener.service.YapeNotificationListenerService
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual OS notification delivery; never injects a fake package into the production listener. */
@RunWith(AndroidJUnit4::class)
class NotificationDeliveryTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val db get() = AppDatabase.getDatabase(context)
    private val runId = "fixture-${UUID.randomUUID().toString().take(12)}"
    private val eventTime = System.currentTimeMillis() - 10_000
    private var startedAt = 0L
    private var prepared = false

    @Before fun prepareIsolatedListener() {
        assertEquals("com.financemanager.listener.capturetest", context.packageName)
        assertNull("The isolated build must not be paired", PairingPreferences.session(context))
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.INTERNET))
        assertTrue(NotificationParserDispatcher.defaultInstance.isSupportedPackage(EMITTER))
        assertFalse(NotificationParserDispatcher.defaultInstance.isSupportedPackage("com.bcp.innovacxion.yapeapp"))
        shell("pm grant $EMITTER android.permission.POST_NOTIFICATIONS")
        val component = ComponentName(context, YapeNotificationListenerService::class.java).flattenToString()
        shell("cmd notification allow_listener $component")
        instrumentation.runOnMainSync { ListenerHealthCoordinator.foreground(context) }
        eventually("listener connection", 90_000) { CaptureStatus.state.value.connected }
        startedAt = SystemClock.elapsedRealtime()
        prepared = true
    }

    @After fun cleanOwnFixtures() {
        if (prepared && context.packageName == "com.financemanager.listener.capturetest") {
            emit("clear")
            val metrics = Bundle().apply {
                putString("capture_fixture", runId)
                putLong("elapsed_ms", SystemClock.elapsedRealtime() - startedAt)
                putInt("stored_movements", count("local_transactions"))
                putInt("review_observations", count("capture_inbox", "status = 'REVIEW' AND kind = 'POSTED'"))
            }
            instrumentation.sendStatus(0, metrics)
        }
    }

    @Test fun postedReceiptReachesRoomWithoutNetworkOrPairing() {
        emit("basic")
        eventually("one durable movement") { count("local_transactions") == 1 }
        assertAmounts("12.30")
        assertEquals(0, count("local_transactions", "isSynced = 1"))
        assertEquals(1, count("capture_inbox", "status = 'SAVED' AND kind = 'POSTED'"))
    }

    @Test fun replayOfAnActiveReceiptIsIdempotent() {
        emit("basic")
        eventually("initial movement") { count("local_transactions") == 1 }
        instrumentation.runOnMainSync { YapeNotificationListenerService.reviewActive() }
        eventually("duplicate observation from active replay") { count("capture_inbox", "status = 'DUPLICATE' AND kind = 'POSTED'") > 0 }
        assertEquals(1, count("local_transactions"))
    }

    @Test fun presentationUpdateIsDuplicateAndNewAmbiguousEventIsRetained() {
        emit("basic")
        eventually("initial movement") { count("local_transactions") == 1 }
        emit("update")
        eventually("presentation revision") { count("capture_inbox", "status = 'DUPLICATE' AND kind = 'POSTED'") > 0 }
        emit("basic", eventTime + 1000)
        eventually("ambiguous reused source") { count("capture_inbox", "reason = 'REUSED_ACTIVE_SOURCE'") > 0 }
        assertEquals(1, count("local_transactions"))
    }

    @Test fun groupedChildrenAreStoredButSummaryIsNotAPayment() {
        emit("group")
        eventually("two grouped payments") { count("local_transactions") == 2 }
        eventually("ignored group summary") { count("capture_inbox", "reason = 'GROUP_SUMMARY'") > 0 }
        assertAmounts("11.00", "22.00")
    }

    @Test fun expandedTextAndInboxLinesUseActualAndroidExtras() {
        emit("expanded")
        emit("lines")
        eventually("expanded and line receipts") { count("local_transactions") == 2 }
        assertAmounts("17.40", "25.50")
    }

    @Test fun ambiguousAndOversizedContentCannotCreateFinancialRows() {
        emit("ambiguous")
        emit("oversized")
        eventually("both review outcomes") { count("capture_inbox", "status = 'REVIEW' AND kind = 'POSTED'") == 2 }
        assertEquals(0, count("local_transactions"))
    }

    @Test fun nonFinancialNotificationIsIgnored() {
        emit("promotion")
        eventually("nonfinancial outcome") { count("capture_inbox", "reason = 'NON_FINANCIAL'") > 0 }
        assertEquals(0, count("local_transactions"))
    }

    @Test fun shellOriginCannotEnterTheCaptureInbox() {
        val tag = "shell-$runId"
        // UiAutomation tokenizes arguments without shell quote expansion.
        shell("cmd notification post -t Fixture $tag SyntheticReceipt")
        eventually("shell notification published by Android") {
            shell("cmd notification list").lineSequence().any { it.contains("com.android.shell") && it.contains(tag) }
        }
        // A subsequent authorized delivery acts as a processing barrier for this run.
        emit("basic")
        eventually("authorized receipt after shell post") { count("local_transactions") == 1 }
        db.query("SELECT COUNT(*) FROM capture_inbox WHERE sourceKey LIKE ?", arrayOf("%$tag%")).use {
            assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
        }
        assertAmounts("12.30")
    }

    private fun emit(scenario: String, time: Long = eventTime) {
        val latch = CountDownLatch(1)
        var result = Activity.RESULT_CANCELED
        val command = Intent("com.fintrack.capture.emitter.EMIT")
            .setComponent(ComponentName(EMITTER, "$EMITTER.EmitterReceiver"))
            .putExtra("scenario", scenario).putExtra("run_id", runId).putExtra("event_time", time)
        context.sendOrderedBroadcast(command, null, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { result = resultCode; latch.countDown() }
        }, Handler(Looper.getMainLooper()), Activity.RESULT_CANCELED, null, null)
        assertTrue("Emitter did not acknowledge the command", latch.await(10, TimeUnit.SECONDS))
        assertEquals("Synthetic emitter command failed", Activity.RESULT_OK, result)
    }

    private fun count(table: String, condition: String = "1 = 1"): Int =
        db.query("SELECT COUNT(*) FROM $table WHERE sourceKey LIKE ? AND ($condition)", arrayOf("%$runId%")).use {
            assertTrue(it.moveToFirst()); it.getInt(0)
        }

    private fun assertAmounts(vararg expected: String) {
        val amounts = mutableListOf<String>()
        db.query("SELECT amount FROM local_transactions WHERE sourceKey LIKE ? ORDER BY amount", arrayOf("%$runId%")).use {
            while (it.moveToNext()) amounts.add(it.getString(0))
        }
        assertEquals(expected.sorted(), amounts.sorted())
    }

    private fun eventually(description: String, timeout: Long = 15_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        fail("Timed out waiting for $description")
    }

    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
        java.io.FileInputStream(it.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
    }

    companion object { private const val EMITTER = "com.fintrack.capture.emitter" }
}
