package com.shilapi.xcertplay

import android.content.Context

internal object GolfFeatureSettings {
    data class Options(val bootLaunch: Boolean = false, val autoConnect: Boolean = false,
        val microphone: Boolean = false, val hotspotRecovery: Boolean = true)
    private fun prefs(context: Context) = context.getSharedPreferences("golf_wireless_features", Context.MODE_PRIVATE)
    fun load(context: Context): Options = prefs(context).let {
        Options(it.getBoolean("boot_launch", false), it.getBoolean("auto_connect", false),
            it.getBoolean("microphone", false), it.getBoolean("hotspot_recovery", true))
    }
    fun save(context: Context, options: Options) {
        prefs(context).edit().putBoolean("boot_launch", options.bootLaunch)
            .putBoolean("auto_connect", options.autoConnect).putBoolean("microphone", options.microphone)
            .putBoolean("hotspot_recovery", options.hotspotRecovery).apply()
    }
}
