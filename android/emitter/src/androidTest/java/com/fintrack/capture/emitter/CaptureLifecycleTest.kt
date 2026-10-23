package com.fintrack.capture.emitter

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The harness lives in the emitter process, so killing FinTrack does not kill the test. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CaptureLifecycleTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val runId = InstrumentationRegistry.getArguments().getString("capture_run")
        ?: "lifecycle-${UUID.randomUUID().toString().take(20)}"
    private val eventTime = System.currentTimeMillis() - 5_000
    private var prepared = false

    @Before fun prepareDisposableTarget() {
        assertEquals("com.fintrack.capture.emitter", context.packageName)
        assertTrue("Lifecycle tests require an emulator", Build.HARDWARE in setOf("ranchu", "goldfish"))
        @Suppress("DEPRECATION")
        val target = context.packageManager.getPackageInfo(TARGET, PackageManager.GET_PERMISSIONS)
        assertFalse(target.requestedPermissions.orEmpty().contains(Manifest.permission.INTERNET))
        shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        control("reset")
        prepared = true
        shell("cmd notification allow_listener $COMPONENT")
        control("foreground")
        await("initial listener connection", 90_000) { control().getBoolean("connected") }
    }

    @After fun releaseOnlyOwnFixtures() {
        if (prepared) {
            if (targetIsStopped()) reopenTarget()
            control("component_restore")
            NotificationFixtures.publish(context, "clear", runId, eventTime)
            control("release")
            shell("cmd notification allow_listener $COMPONENT")
        }
    }

    @Test fun a_committedCaptureSurvivesKillAfterItsNotificationDisappears() {
        control("arm", "AFTER_COMMIT")
        publish()
        val committed = awaitState("capture committed before processing") {
            it.optString("reached") == "AFTER_COMMIT" && it.getInt("pending") == 1
        }
        assertEquals(0, committed.getInt("movements"))
        cancelAndVerifyGone()
        val oldPid = committed.getInt("pid")
        killTarget(oldPid)
        reopenTarget()
        val resumed = control("release")
        assertNotEquals("A new process must read the committed inbox", oldPid, resumed.getInt("pid"))
        val saved = awaitState("movement recovered without its source notification") {
            it.getInt("movements") == 1 && it.getInt("pending") == 0
        }
        val hash = saved.getJSONArray("hashes").getString(0)
        assertEquals(0, saved.getInt("synced"))
        killTarget(saved.getInt("pid"))
        reopenTarget()
        control("foreground")
        val replayed = awaitState("connection after second process death") { it.getBoolean("connected") }
        assertEquals(1, replayed.getInt("movements"))
        assertEquals(hash, replayed.getJSONArray("hashes").getString(0))
    }

    @Test fun b_precommitCaptureWithRemovedNotificationIsNotClaimedDurable() {
        control("arm", "BEFORE_COMMIT")
        publish()
        val before = awaitState("snapshot waiting before its first write") { it.optString("reached") == "BEFORE_COMMIT" }
        assertEquals(0, before.getInt("posted"))
        cancelAndVerifyGone()
        killTarget(before.getInt("pid"))
        reopenTarget()
        control("release")
        control("foreground")
        await("connection after precommit death") { control().getBoolean("connected") }
        // A separately tagged real delivery proves the replacement process is working.
        val barrier = "lifecycle-${UUID.randomUUID().toString().take(20)}"
        try {
            NotificationFixtures.publish(context, "basic", barrier, eventTime)
            await("replacement process processing barrier") { control(run = barrier).getInt("movements") == 1 }
            val lost = control()
            assertEquals(0, lost.getInt("posted"))
            assertEquals(0, lost.getInt("movements"))
        } finally { NotificationFixtures.publish(context, "clear", barrier, eventTime) }
    }

    @Test fun c_precommitDeathCanRecoverANotificationStillActiveInAndroid() {
        control("arm", "BEFORE_COMMIT")
        publish()
        val before = awaitState("precommit checkpoint with visible source") { it.optString("reached") == "BEFORE_COMMIT" }
        assertEquals(0, before.getInt("posted"))
        assertTrue(manager().activeNotifications.any { it.tag == runId })
        killTarget(before.getInt("pid"))
        reopenTarget()
        control("release")
        control("foreground")
        val recovered = awaitState("recovery from Android active notifications") {
            it.getInt("movements") == 1 && it.getInt("recovering") > 0
        }
        val hashes = recovered.getJSONArray("hashes").toString()
        repeat(3) { control("foreground") }
        assertEquals(hashes, control().getJSONArray("hashes").toString())
    }

    @Test fun d_revokedAccessPreventsRebindAndActiveQueriesUntilRegranted() {
        shell("cmd notification disallow_listener $COMPONENT")
        val revoked = awaitState("permission revocation and listener disconnect") {
            !it.getBoolean("permission") && !it.getBoolean("connected")
        }
        val attempts = eventTimes(revoked, "REBIND").size
        val queries = eventTimes(revoked, "ACTIVE_QUERY").size
        repeat(8) { control("foreground"); control("review") }
        publish()
        val blocked = control()
        assertEquals(attempts, eventTimes(blocked, "REBIND").size)
        assertEquals(queries, eventTimes(blocked, "ACTIVE_QUERY").size)
        assertEquals(0, blocked.getInt("posted"))
        shell("cmd notification allow_listener $COMPONENT")
        control("foreground")
        val recovered = awaitState("active receipt after notification access returns", 90_000) {
            it.getBoolean("connected") && it.getInt("movements") == 1 && eventTimes(it, "ACTIVE_QUERY").size > queries
        }
        val hashes = recovered.getJSONArray("hashes").toString()
        // Android may redeliver an existing notice as a live callback after access returns.
        // Verify the financial result first, then an explicit active replay of that same notice.
        control("review")
        await("active replay after access returns") { control().getInt("recovering") > 0 }
        repeat(3) { control("foreground") }
        assertEquals(hashes, control().getJSONArray("hashes").toString())
    }

    @Test fun e_realUnbindReconnectsAndRepeatedRequestsRespectTheCooldown() {
        val baseline = eventTimes(control(), "REBIND").size
        control("unbind")
        val first = awaitState("automatic rebind after actual system unbind", 85_000) {
            eventTimes(it, "REBIND").size > baseline && it.getBoolean("connected")
        }
        val firstTimes = eventTimes(first, "REBIND")
        control("unbind")
        val disconnected = awaitState("second actual disconnect") { !it.getBoolean("connected") }
        val queries = eventTimes(disconnected, "ACTIVE_QUERY").size
        repeat(8) { control("foreground"); control("review") }
        val waiting = control()
        assertFalse(waiting.getBoolean("connected"))
        assertEquals(firstTimes.size, eventTimes(waiting, "REBIND").size)
        assertEquals(queries, eventTimes(waiting, "ACTIVE_QUERY").size)
        val second = awaitState("rebind after the real 60-second cooldown", 85_000) {
            it.getBoolean("connected") && eventTimes(it, "REBIND").size > firstTimes.size
        }
        val times = eventTimes(second, "REBIND")
        assertEquals(firstTimes.size + 1, times.size)
        assertTrue("Rebind requests must be at least 60 seconds apart", times.last() - firstTimes.last() >= 60_000)
        instrumentation.sendStatus(0, Bundle().apply {
            putLong("rebind_interval_ms", times.last() - firstTimes.last())
            putInt("rebind_requests_after_unbind", times.size - baseline)
        })
        publish()
        await("capture after the restored connection") { control().getInt("movements") == 1 }
    }

    @Test fun f_exhaustedCycleStopsAfterThreeAttemptsAndAllowsExplicitRetry() {
        control("block_rebind")
        val baseline = eventTimes(control(), "REBIND").size
        control("unbind")
        awaitState("listener disconnected for failed reconnect cycle") { !it.getBoolean("connected") }
        repeat(8) { control("foreground") }
        val exhausted = awaitState("three-attempt cycle exhausted with real timeouts", 250_000) {
            eventTimes(it, "REBIND_EXHAUSTED").isNotEmpty()
        }
        val times = eventTimes(exhausted, "REBIND").drop(baseline)
        assertEquals(3, times.size)
        times.zipWithNext().forEach { (first, second) -> assertTrue(second - first >= 60_000) }
        assertFalse(exhausted.getBoolean("connected"))
        assertTrue(exhausted.getString("last_error").contains("Android no ha conectado"))
        // Observe longer than a full retry interval, without generating a new foreground trigger.
        val quietUntil = SystemClock.elapsedRealtime() + 65_000
        while (SystemClock.elapsedRealtime() < quietUntil) {
            val state = control()
            assertEquals(baseline + 3, eventTimes(state, "REBIND").size)
            assertEquals(1, eventTimes(state, "REBIND_EXHAUSTED").size)
            SystemClock.sleep(500)
        }
        control("release")
        control("foreground")
        awaitState("explicit retry after exhausted cycle", 90_000) { it.getBoolean("connected") }
        publish()
        await("capture after exhausted-cycle recovery") { control().getInt("movements") == 1 }
    }

    @Test fun g_disabledComponentPreventsRequestsAndRecoversWhenRestored() {
        val changed = control("component_disable")
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, changed.getInt("component_state"))
        try {
            // Android may keep an already bound instance alive after a component change.
            // A real unbind starts recovery while PackageManager still reports disabled.
            if (changed.getBoolean("connected")) control("unbind")
            val disabled = awaitState("disabled listener disconnected") { !it.getBoolean("connected") }
            assertTrue("Keep notification access granted to exercise the component guard", disabled.getBoolean("permission"))
            val attempts = eventTimes(disabled, "REBIND").size
            val queries = eventTimes(disabled, "ACTIVE_QUERY").size
            repeat(8) { control("foreground"); control("review") }
            publish()
            val quietUntil = SystemClock.elapsedRealtime() + 65_000
            while (SystemClock.elapsedRealtime() < quietUntil) {
                val state = control()
                assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, state.getInt("component_state"))
                assertFalse(state.getBoolean("connected"))
                assertEquals(attempts, eventTimes(state, "REBIND").size)
                assertEquals(queries, eventTimes(state, "ACTIVE_QUERY").size)
                assertEquals(0, state.getInt("posted"))
                SystemClock.sleep(500)
            }
        } finally { control("component_restore") }
        shell("cmd notification allow_listener $COMPONENT")
        control("foreground")
        val recovered = awaitState("active capture after component restoration", 90_000) {
            it.getBoolean("connected") && it.getInt("movements") == 1
        }
        val hashes = recovered.getJSONArray("hashes").toString()
        control("review")
        await("idempotent replay after component restoration") { control().getInt("recovering") > 0 }
        assertEquals(hashes, control().getJSONArray("hashes").toString())
    }

    @Test fun h_listenerDestructionDoesNotCancelAnAdmittedPrecommitCapture() {
        control("arm", "BEFORE_COMMIT")
        publish()
        val admitted = awaitState("capture admitted before its first write") {
            it.optString("reached") == "BEFORE_COMMIT"
        }
        assertEquals(0, admitted.getInt("posted"))
        val destroyed = eventTimes(admitted, "DESTROYED").size
        control("unbind")
        val stopped = awaitState("actual listener destruction during precommit capture") {
            !it.getBoolean("connected") && eventTimes(it, "DESTROYED").size > destroyed
        }
        assertEquals("Listener destruction must not replace the application process",
            admitted.getInt("pid"), stopped.getInt("pid"))
        assertEquals(0, stopped.getInt("posted"))
        cancelAndVerifyGone()
        control("release")
        val saved = awaitState("application-owned ingress commits after listener destruction") {
            it.getInt("movements") == 1 && it.getInt("pending") == 0
        }
        assertEquals(admitted.getInt("pid"), saved.getInt("pid"))
        assertEquals(1, saved.getInt("posted"))
        assertEquals(0, saved.getInt("synced"))
        val hashes = saved.getJSONArray("hashes").toString()
        control("foreground")
        awaitState("listener restored after in-flight capture", 90_000) { it.getBoolean("connected") }
        control("review")
        assertEquals(hashes, control().getJSONArray("hashes").toString())
    }

    @Test fun i_committedCaptureSurvivesForceStopAndExplicitReopening() {
        control("arm", "AFTER_COMMIT")
        publish()
        val committed = awaitState("capture committed before force-stop") {
            it.optString("reached") == "AFTER_COMMIT" && it.getInt("pending") == 1
        }
        assertEquals(0, committed.getInt("movements"))
        cancelAndVerifyGone()
        var observedStopped = forceStopTarget(committed.getInt("pid"))
        var restartedWithoutLaunch = false
        // Do not send control broadcasts during observation: Android may restart a bound listener.
        val quietUntil = SystemClock.elapsedRealtime() + 3_000
        while (SystemClock.elapsedRealtime() < quietUntil) {
            observedStopped = targetIsStopped() || observedStopped
            restartedWithoutLaunch = shell("pidof $TARGET").isNotBlank() || restartedWithoutLaunch
            SystemClock.sleep(100)
        }
        instrumentation.sendStatus(0, Bundle().apply {
            putBoolean("force_stop_observed_stopped", observedStopped)
            putBoolean("force_stop_restart_without_launch", restartedWithoutLaunch)
            putLong("force_stop_observation_ms", 3_000)
        })
        reopenTarget()
        assertFalse("Explicit launch must clear the stopped state", targetIsStopped())
        val resumed = control("release")
        assertNotEquals(committed.getInt("pid"), resumed.getInt("pid"))
        val saved = awaitState("committed inbox processed after force-stop and reopening") {
            it.getInt("movements") == 1 && it.getInt("pending") == 0
        }
        assertEquals(1, saved.getInt("posted"))
        assertEquals(0, saved.getInt("synced"))
        val hashes = saved.getJSONArray("hashes").toString()
        forceStopTarget(saved.getInt("pid"))
        reopenTarget()
        control("foreground")
        val replayed = awaitState("listener connected after repeated force-stop", 90_000) {
            it.getBoolean("connected")
        }
        assertEquals(1, replayed.getInt("movements"))
        assertEquals(0, replayed.getInt("pending"))
        assertEquals(hashes, replayed.getJSONArray("hashes").toString())
    }

    private fun targetIsStopped(): Boolean = Regex("(?m)^\\s*User 0:.*\\bstopped=true\\b")
        .containsMatchIn(shell("dumpsys package $TARGET"))

    private fun forceStopTarget(oldPid: Int): Boolean {
        assertTrue("Could not force-stop the isolated app", shell("am force-stop --user 0 $TARGET").isBlank())
        val observedStopped = targetIsStopped()
        await("force-stopped process terminated") {
            !shell("pidof $TARGET").trim().split(' ').contains(oldPid.toString())
        }
        return observedStopped
    }

    private fun publish() = NotificationFixtures.publish(context, "basic", runId, eventTime)
    internal fun prepareRebootCapture() {
        prepareDisposableTarget()
        control("arm_reboot")
        publish()
        val committed = awaitState("reboot fixture committed before processing") {
            it.optString("reached") == "AFTER_COMMIT" && it.getInt("pending") == 1
        }
        assertEquals(0, committed.getInt("movements"))
        cancelAndVerifyGone()
    }

    internal fun verifyRebootCapture() {
        prepared = true
        try {
            reopenTarget()
            val saved = awaitState("movement processed after reboot and explicit launch", 90_000) {
                it.getInt("movements") == 1 && it.getInt("pending") == 0
            }
            val hashes = saved.getJSONArray("hashes").toString()
            repeat(3) { control("foreground") }
            val final = control()
            assertEquals(1, final.getInt("posted"))
            assertEquals(0, final.getInt("synced"))
            assertEquals(hashes, final.getJSONArray("hashes").toString())
        } finally { releaseOnlyOwnFixtures() }
    }

    internal fun cleanupRebootCapture() {
        prepared = true
        releaseOnlyOwnFixtures()
    }
    private fun manager() = context.getSystemService(NotificationManager::class.java)
    private fun cancelAndVerifyGone() {
        NotificationFixtures.publish(context, "clear", runId, eventTime)
        await("source notification removed before process death") { manager().activeNotifications.none { it.tag == runId } }
    }

    private fun killTarget(pid: Int) {
        assertTrue(pid > 0)
        assertTrue(shell("pidof $TARGET").trim().split(' ').contains(pid.toString()))
        // run-as limits kill to the isolated app UID. This is SIGKILL, not force-stop.
        val output = shell("run-as $TARGET kill -9 $pid")
        assertTrue("Could not terminate the isolated process", output.isBlank())
        await("old target PID terminated") { !shell("pidof $TARGET").trim().split(' ').contains(pid.toString()) }
    }

    private fun reopenTarget() {
        // Exercise real application startup/onResume; a cold receiver may be deferred by Android.
        val output = shell("am start -W -n $TARGET/com.financemanager.listener.MainActivity")
        assertFalse("Could not reopen the isolated app", output.contains("Error:"))
    }

    private fun control(command: String = "snapshot", stage: String? = null, run: String = runId): JSONObject {
        val latch = CountDownLatch(1)
        var code = Activity.RESULT_CANCELED
        var data: String? = null
        val intent = Intent("$TARGET.CONTROL").setComponent(ComponentName(TARGET,
            "com.financemanager.listener.capture.LifecycleControlReceiver"))
            .putExtra("command", command).putExtra("run_id", run).putExtra("stage", stage)
        context.sendOrderedBroadcast(intent, null, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                code = resultCode; data = resultData; latch.countDown()
            }
        }, Handler(Looper.getMainLooper()), Activity.RESULT_CANCELED, null, null)
        assertTrue("Isolated control timed out: $command", latch.await(30, TimeUnit.SECONDS))
        assertEquals("Isolated control failed: $command ($data)", Activity.RESULT_OK, code)
        return JSONObject(requireNotNull(data))
    }

    private fun eventTimes(state: JSONObject, type: String): List<Long> {
        val events = state.getJSONArray("events")
        return (0 until events.length()).map { events.getJSONObject(it) }
            .filter { it.getString("type") == type }.map { it.getLong("elapsed") }
    }
    private fun awaitState(description: String, timeout: Long = 20_000, condition: (JSONObject) -> Boolean): JSONObject {
        var last = JSONObject()
        try { await(description, timeout) { last = control(); condition(last) } }
        catch (failure: AssertionError) {
            throw AssertionError("$description: connected=${last.optBoolean("connected")}, " +
                "permission=${last.optBoolean("permission")}, movements=${last.optInt("movements")}, " +
                "recovering=${last.optInt("recovering")}, events=${last.optJSONArray("events")}", failure)
        }
        return last
    }
    private fun await(description: String, timeout: Long = 20_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        fail("Timed out waiting for $description")
    }
    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
        java.io.FileInputStream(it.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
    }
    companion object {
        private const val TARGET = "com.financemanager.listener.capturetest"
        private const val COMPONENT = "$TARGET/com.financemanager.listener.service.YapeNotificationListenerService"
    }
}
