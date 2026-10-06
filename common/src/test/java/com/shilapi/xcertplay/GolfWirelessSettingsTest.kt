package com.shilapi.xcertplay

import com.shilapi.xcertplay.orchestration.MfiTarget
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class GolfWirelessSettingsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Test fun profileDisablesExtraFeaturesWithoutChangingTheOwnersHotspot() {
        AirPlayPersistence.saveManualHotspotSsid(context, " Golf hotspot ")
        AirPlayPersistence.saveManualHotspotPassphrase(context, " owner password ")
        AirPlayPersistence.saveWirelessEnabled(context, false)
        AirPlayPersistence.saveHevcEnabled(context, true)
        AirPlayPersistence.saveDisplayScaleTenths(context, 10)
        AirPlayPersistence.saveAutoStartOnBoot(context, true)
        GolfWirelessProfile.apply(context)
        assertTrue(AirPlayPersistence.loadWirelessEnabled(context))
        assertFalse(AirPlayPersistence.loadHevcEnabled(context))
        assertFalse(AirPlayPersistence.loadAutoStartOnBoot(context))
        assertFalse(AirPlayPersistence.loadLocationReportingEnabled(context))
        assertEquals(WirelessHotspotMode.MANUAL, AirPlayPersistence.loadWirelessHotspotMode(context))
        assertEquals(MfiTarget.LOCAL, AirPlayPersistence.loadMfiTarget(context))
        assertEquals(8, AirPlayPersistence.loadDisplayScaleTenths(context))
        assertEquals(" Golf hotspot ", AirPlayPersistence.loadManualHotspotSsid(context))
        assertEquals(" owner password ", AirPlayPersistence.loadManualHotspotPassphrase(context))
    }
    @Test fun smoothQualitySurvivesApplyingTheConnectionProfileAgain() {
        context.getSharedPreferences("golf_wireless_display", 0).edit()
            .putString("quality", "smooth").apply()
        GolfWirelessProfile.apply(context)
        assertEquals(6, AirPlayPersistence.loadDisplayScaleTenths(context))
        GolfWirelessProfile.apply(context)
        assertEquals(6, AirPlayPersistence.loadDisplayScaleTenths(context))
    }
    @Test fun anUnknownQualityChoiceUsesTheApprovedBalancedDefault() {
        context.getSharedPreferences("golf_wireless_display", 0).edit()
            .putString("quality", "unknown").apply()
        GolfWirelessProfile.apply(context)
        assertEquals(8, AirPlayPersistence.loadDisplayScaleTenths(context))
    }
    @Test fun displayChoicesPersistAndTheConnectionProfileDoesNotResetAvoidance() {
        assertEquals(GolfDisplaySettings.Quality.BALANCED, GolfDisplaySettings.quality(context))
        assertFalse(GolfDisplaySettings.avoidTopBar(context))
        GolfDisplaySettings.save(context, GolfDisplaySettings.Quality.SMOOTH, true)
        GolfWirelessProfile.apply(context)
        assertEquals(GolfDisplaySettings.Quality.SMOOTH, GolfDisplaySettings.quality(context))
        assertTrue(GolfDisplaySettings.avoidTopBar(context))
        assertEquals(6, AirPlayPersistence.loadDisplayScaleTenths(context))
        GolfDisplaySettings.save(context, GolfDisplaySettings.Quality.BALANCED, false)
        GolfWirelessProfile.apply(context)
        assertEquals(8, AirPlayPersistence.loadDisplayScaleTenths(context))
        assertFalse(GolfDisplaySettings.avoidTopBar(context))
    }
    @Test fun resetRemovesPhonePairingsAndPreservesAccessoryAndConnectionSettings() {
        val identity = AirPlayPersistence.loadIdentity(context)
        AirPlayPersistence.savePairing(context, "synthetic-controller", ByteArray(32) { 7 })
        AirPlayPersistence.saveManualHotspotSsid(context, "Golf")
        DiPlayPreferences.savePhone(context, "00:11:22:33:44:55", "Synthetic phone")
        AirPlayPersistence.clearCarPlayPairings(context)
        assertNull(AirPlayPersistence.loadPairings(context) { _, _ -> }.get("synthetic-controller"))
        assertEquals(identity.pairingId, AirPlayPersistence.loadIdentity(context).pairingId)
        assertEquals("Golf", AirPlayPersistence.loadManualHotspotSsid(context))
        assertEquals("00:11:22:33:44:55", DiPlayPreferences.phoneAddress(context))
    }
    @Test fun enabledOptionalStartupChoicesSurviveApplyingTheConnectionProfile() {
        context.getSharedPreferences("golf_wireless_features", 0).edit()
            .putBoolean("boot_launch", true).putBoolean("auto_connect", true).apply()
        GolfWirelessProfile.apply(context)
        assertTrue(AirPlayPersistence.loadAutoStartOnBoot(context))
        assertTrue(DiPlayPreferences.autoConnect(context))
        GolfWirelessProfile.apply(context)
        assertTrue(AirPlayPersistence.loadAutoStartOnBoot(context))
        assertTrue(DiPlayPreferences.autoConnect(context))
    }
}
