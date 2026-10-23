package com.fintrack.capture.emitter

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/** Host orchestration restarts instrumentation after a real device reboot. */
@RunWith(AndroidJUnit4::class)
class CaptureRebootTest {
    @Test fun rebootPhase() {
        val arguments = InstrumentationRegistry.getArguments()
        val run = requireNotNull(arguments.getString("capture_run"))
        require(run.matches(Regex("lifecycle-[a-z0-9-]{1,30}")))
        val driver = CaptureLifecycleTest()
        when (arguments.getString("reboot_phase")) {
            "prepare" -> driver.prepareRebootCapture()
            "verify" -> driver.verifyRebootCapture()
            "cleanup" -> driver.cleanupRebootCapture()
            else -> error("Explicit host reboot phase required")
        }
    }
}
