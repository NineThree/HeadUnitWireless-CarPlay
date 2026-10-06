package com.shilapi.xcertplay

import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class GolfStartupTest {
    private val app get() = RuntimeEnvironment.getApplication()
    @Test fun bootDoesNothingUntilTheOwnerEnablesIt() {
        GolfBootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun enablingBootOpensOnlyTheOrdinaryLauncher() {
        GolfFeatureSettings.save(app, GolfFeatureSettings.Options(bootLaunch = true))
        GolfBootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        val intent = shadowOf(app).nextStartedActivity
        assertEquals(GolfWirelessActivity::class.java.name, intent.component!!.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertFalse(GolfFeatureSettings.load(app).autoConnect)
        assertFalse(GolfFeatureSettings.load(app).microphone)
    }
    @Test fun unrelatedBroadcastsCannotLaunchTheApplication() {
        GolfFeatureSettings.save(app, GolfFeatureSettings.Options(bootLaunch = true))
        GolfBootReceiver().onReceive(app, Intent(Intent.ACTION_TIME_CHANGED))
        assertNull(shadowOf(app).nextStartedActivity)
    }
    @Test fun automaticWaitStopsAtNinetySecondsInsteadOfRetryingForever() {
        val gate = GolfAutoConnectionGate()
        gate.begin(0)
        assertEquals(GolfAutoConnectionGate.Decision.WAIT, gate.evaluate(89_999, false))
        assertEquals(GolfAutoConnectionGate.Decision.TIMEOUT, gate.evaluate(90_000, true))
        assertEquals(GolfAutoConnectionGate.Decision.STOP, gate.evaluate(91_000, true))
    }
    @Test fun readyStartupCanStartOnlyOneConnection() {
        val gate = GolfAutoConnectionGate()
        gate.begin(100)
        assertEquals(GolfAutoConnectionGate.Decision.WAIT, gate.evaluate(200, false))
        assertEquals(GolfAutoConnectionGate.Decision.CONNECT, gate.evaluate(300, true))
        assertEquals(GolfAutoConnectionGate.Decision.STOP, gate.evaluate(400, true))
    }
    @Test fun manualStopCannotBeUndoneByResumingTheSameScreen() {
        val gate = GolfAutoConnectionGate()
        gate.begin(100); gate.cancel(); gate.begin(200)
        assertEquals(GolfAutoConnectionGate.Decision.STOP, gate.evaluate(300, true))
        gate.reset(); gate.begin(400)
        assertEquals(GolfAutoConnectionGate.Decision.CONNECT, gate.evaluate(500, true))
    }
}
