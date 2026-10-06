package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.orchestration.ManualHotspotBand
import com.shilapi.xcertplay.orchestration.MfiTarget
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode

/** Shared conservative API17+ wireless profile; video size comes from the actual view. */
internal object GolfWirelessProfile {
    const val displayScaleTenths = 8
    const val frameRate = 30
    const val microphoneEnabled = false
    const val hevcEnabled = false

    fun appliesTo(api: Int) = api >= 17

    fun apply(context: Context) {
        AirPlayPersistence.saveWirelessEnabled(context, true)
        AirPlayPersistence.saveWirelessHotspotMode(context, WirelessHotspotMode.MANUAL)
        AirPlayPersistence.saveManualHotspotBand(context, ManualHotspotBand.AUTO)
        AirPlayPersistence.saveManualHotspotChannel(context, 0)
        AirPlayPersistence.saveMfiTarget(context, MfiTarget.LOCAL)
        AirPlayPersistence.saveDisplayScaleTenths(context, GolfDisplaySettings.quality(context).scaleTenths)
        AirPlayPersistence.saveHevcEnabled(context, hevcEnabled)
        AirPlayPersistence.saveHevcSoftwareDecoderEnabled(context, false)
        AirPlayPersistence.saveFps(context, frameRate)
        AirPlayPersistence.saveLocationReportingEnabled(context, false)
        AirPlayPersistence.saveAdvancedAudioChannelMapping(context, false)
        val optional = GolfFeatureSettings.load(context)
        AirPlayPersistence.saveAutoStartOnBoot(context, optional.bootLaunch)
        AirPlayPersistence.saveDebugLogsEnabled(context, false)
        AirPlayPersistence.saveOemLabel(context, HeadUnitEdition.oemLabel(context))
        DiPlayPreferences.saveAutoConnect(context, optional.autoConnect)
    }
}
