package com.shilapi.xcertplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Opens the ordinary launcher only after the owner opts in. Does not start a hotspot or recording. */
class GolfBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED || !GolfFeatureSettings.load(context).bootLaunch) return
        try {
            context.startActivity(Intent(context, GolfWirelessActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        } catch (error: RuntimeException) {
            Log.w("GolfStartup", "Boot launcher unavailable: ${error.javaClass.simpleName}")
        }
    }
}
