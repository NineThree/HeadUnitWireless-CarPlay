package com.shilapi.xcertplay.network

import org.junit.Assert.*
import org.junit.Test

class LegacyHotspotRecoveryTest {
    private class FakePort : LegacyHotspotRecovery.Port {
        var currentState = CarHotspotStatus.State.ENABLED
        var ssid: String? = "Synthetic AP"
        var rejectDisable = false
        var rejectEnable = false
        var stuckDisabling = false
        var onSsidRead: () -> Unit = {}
        var afterDisable: () -> Unit = {}
        val writes = mutableListOf<Boolean>()
        override fun state() = currentState
        override fun configuredSsid(): String? { onSsidRead(); return ssid }
        override fun setEnabled(enabled: Boolean): Boolean {
            writes.add(enabled)
            if (if (enabled) rejectEnable else rejectDisable) return false
            currentState = if (enabled) CarHotspotStatus.State.ENABLED
                else if (stuckDisabling) CarHotspotStatus.State.DISABLING else CarHotspotStatus.State.DISABLED
            if (!enabled) afterDisable()
            return true
        }
    }
    private class Fixture(val port: FakePort = FakePort(), api: Int = 17, enabled: Boolean = true,
        budget: LegacyHotspotRecovery.Budget = LegacyHotspotRecovery.Budget()) {
        var now = 0L
        var cancelled = false
        val logs = mutableListOf<String>()
        val recovery = LegacyHotspotRecovery(api, enabled, "Synthetic AP", port,
            nowMillis = { now }, waitMillis = { now += it }, onDiagnostic = { logs.add(it) }, budget = budget)
        fun attempt(ready: Boolean = false) = recovery.recoverIfNeeded(ready, 60_000) { cancelled }
    }

    @Test fun interfaceThatNeverAppearsTriggersOneRestartAfterGracePeriod() {
        val f = Fixture()
        assertFalse(f.attempt())
        f.now = 5000
        assertTrue(f.attempt())
        assertEquals(listOf(false, true), f.port.writes)
        assertFalse(f.attempt())
        assertEquals(2, f.port.writes.size)
        assertEquals(CarHotspotStatus.State.ENABLED, f.port.currentState)
    }
    @Test fun healthyInterfaceAndDisabledOptionNeverChangeSystemHotspot() {
        val healthy = Fixture(); healthy.now = 6000
        assertFalse(healthy.attempt(ready = true)); assertTrue(healthy.port.writes.isEmpty())
        val disabled = Fixture(enabled = false); disabled.now = 6000
        assertFalse(disabled.attempt()); assertTrue(disabled.port.writes.isEmpty())
    }
    @Test fun unsupportedFirmwareUnknownStateAndExplicitlyOffStayUntouched() {
        for (api in listOf(16, 23, 29)) {
            val f = Fixture(api = api); f.now = 6000; assertFalse(f.attempt()); assertTrue(f.port.writes.isEmpty())
        }
        for (state in listOf(CarHotspotStatus.State.UNKNOWN, CarHotspotStatus.State.DISABLED,
            CarHotspotStatus.State.ENABLING, CarHotspotStatus.State.DISABLING)) {
            val f = Fixture(); f.now = 6000; f.port.currentState = state
            assertFalse(f.attempt()); assertTrue(f.port.writes.isEmpty())
        }
    }
    @Test fun differentOrUnreadableSystemSsidMustNotBeRestarted() {
        for (ssid in listOf("Other hotspot", null)) {
            val f = Fixture(); f.now = 6000; f.port.ssid = ssid
            assertFalse(f.attempt()); assertTrue(f.port.writes.isEmpty())
        }
    }
    @Test fun disablePermissionDenialHasNoFalseSuccessOrRepeatedAttempts() {
        val f = Fixture(); f.now = 6000; f.port.rejectDisable = true
        assertFalse(f.attempt()); assertFalse(f.attempt())
        assertEquals(listOf(false), f.port.writes)
        assertTrue(f.logs.any { it.contains("disable-rejected") })
    }
    @Test fun cancellationAfterDisableRestoresTheHotspot() {
        val f = Fixture(); f.now = 6000
        f.port.afterDisable = { f.cancelled = true }
        assertFalse(f.attempt())
        assertEquals(listOf(false, true), f.port.writes)
        assertEquals(CarHotspotStatus.State.ENABLED, f.port.currentState)
    }
    @Test fun failedStateCanRecoverButEnableDenialIsReported() {
        val f = Fixture(); f.now = 6000; f.port.currentState = CarHotspotStatus.State.FAILED
        f.port.rejectEnable = true
        assertFalse(f.attempt())
        assertTrue(f.logs.any { it.contains("enable-rejected") })
    }
    @Test fun stuckShutdownIsFiniteAndStillRestoresRequestedState() {
        val f = Fixture(); f.now = 6000; f.port.stuckDisabling = true
        assertFalse(f.attempt())
        assertTrue(f.now <= 16_000)
        assertEquals(listOf(false, true), f.port.writes)
        assertTrue(f.logs.any { it.contains("disable-timeout") })
    }
    @Test fun shortOverallDeadlineDoesNotStartARecoveryThatCannotFinish() {
        val f = Fixture(); f.now = 59_000
        assertFalse(f.attempt()); assertTrue(f.port.writes.isEmpty())
    }
    @Test fun transitionalApStatesAreNotMistakenForExplicitlyOff() {
        assertEquals(CarHotspotStatus.State.DISABLED, CarHotspotStatus.fromLegacyState(11))
        assertEquals(CarHotspotStatus.State.ENABLED, CarHotspotStatus.fromLegacyState(13))
        assertEquals(CarHotspotStatus.State.DISABLING, CarHotspotStatus.fromLegacyState(10))
        assertEquals(CarHotspotStatus.State.ENABLING, CarHotspotStatus.fromLegacyState(12))
        assertEquals(CarHotspotStatus.State.FAILED, CarHotspotStatus.fromLegacyState(14))
        assertEquals(CarHotspotStatus.State.UNKNOWN, CarHotspotStatus.fromLegacyState(999))
    }
    @Test fun ownerTurnsOffOrCancelsDuringPreflightMustNotBeReenabled() {
        val off = Fixture(); off.now = 6000
        off.port.onSsidRead = { off.port.currentState = CarHotspotStatus.State.DISABLED }
        assertFalse(off.attempt()); assertTrue(off.port.writes.isEmpty())
        val cancelled = Fixture(); cancelled.now = 6000
        cancelled.port.onSsidRead = { cancelled.cancelled = true }
        assertFalse(cancelled.attempt()); assertTrue(cancelled.port.writes.isEmpty())
    }
    @Test fun enablingTransitionGetsAFreshGracePeriodForItsNetworkInterface() {
        val f = Fixture()
        f.port.currentState = CarHotspotStatus.State.ENABLING
        assertFalse(f.attempt())
        f.now = 8000; assertFalse(f.attempt())
        f.port.currentState = CarHotspotStatus.State.ENABLED
        assertFalse(f.attempt())
        f.now = 9000; assertFalse(f.attempt(ready = true))
        assertTrue(f.port.writes.isEmpty())
    }
    @Test fun automaticRetryManagersShareOneBudgetUntilInterfaceActuallyAppears() {
        val budget = LegacyHotspotRecovery.Budget()
        val first = Fixture(budget = budget); first.now = 6000
        assertTrue(first.attempt())
        val retry = Fixture(budget = budget); retry.now = 6000
        assertFalse(retry.attempt()); assertTrue(retry.port.writes.isEmpty())
        assertFalse(retry.attempt(ready = true))
        val laterFailure = Fixture(budget = budget); laterFailure.now = 6000
        assertTrue(laterFailure.attempt())
    }
    @Test fun transitionDuringSsidReadAlsoStartsAFreshGracePeriod() {
        val f = Fixture(); f.now = 6000
        f.port.onSsidRead = { f.port.currentState = CarHotspotStatus.State.ENABLING }
        assertFalse(f.attempt())
        f.port.onSsidRead = {}; f.port.currentState = CarHotspotStatus.State.ENABLED
        f.now = 6250; assertFalse(f.attempt())
        f.now = 7000; assertFalse(f.attempt(ready = true))
        assertTrue(f.port.writes.isEmpty())
    }
    @Test fun preflightReadsCannotStartRecoveryAfterItsDeadline() {
        val f = Fixture(); f.now = 49_000
        f.port.onSsidRead = { f.now = 61_000 }
        assertFalse(f.attempt()); assertTrue(f.port.writes.isEmpty())
    }
}
