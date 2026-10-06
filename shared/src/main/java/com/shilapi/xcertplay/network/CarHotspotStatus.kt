package com.shilapi.xcertplay.network

import android.content.Context
import android.net.wifi.WifiManager

/** Read the system-owned hotspot state. Unknown state does not block interface detection. */
object CarHotspotStatus {
    enum class State { DISABLING, DISABLED, ENABLING, ENABLED, FAILED, UNKNOWN }
    fun resetRecoveryBudget(ssid: String) = LegacyHotspotRecovery.resetBudget(ssid)
    fun fromLegacyState(value: Int): State = when (value) {
        10 -> State.DISABLING
        11 -> State.DISABLED
        12 -> State.ENABLING
        13 -> State.ENABLED
        14 -> State.FAILED
        else -> State.UNKNOWN
    }
    fun state(context: Context): State {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return State.UNKNOWN
        return state(wifi)
    }
    internal fun state(wifi: WifiManager): State =
        runCatching {
            fromLegacyState(WifiManager::class.java.getMethod("getWifiApState").invoke(wifi) as Int)
        }.recoverCatching {
            if (WifiManager::class.java.getMethod("isWifiApEnabled").invoke(wifi) as Boolean)
                State.ENABLED else State.DISABLED
        }.getOrDefault(State.UNKNOWN)
    fun isEnabled(context: Context): Boolean? = when (state(context)) {
        State.ENABLED -> true
        State.DISABLED -> false
        else -> null
    }
}
