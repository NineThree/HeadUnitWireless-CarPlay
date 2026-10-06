package com.shilapi.xcertplay.network

import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.SystemClock

/** One bounded recovery of an already enabled legacy AP; never turns on an owner-disabled AP. */
internal class LegacyHotspotRecovery(
    private val api: Int,
    private val enabled: Boolean,
    private val expectedSsid: String,
    private val port: Port,
    private val nowMillis: () -> Long = SystemClock::elapsedRealtime,
    private val waitMillis: (Long) -> Unit = Thread::sleep,
    private val onDiagnostic: (String) -> Unit = {},
    private val isInterfaceReady: () -> Boolean = { false },
    private val budget: Budget = processBudget,
) {
    interface Port {
        fun state(): CarHotspotStatus.State
        fun configuredSsid(): String?
        fun setEnabled(enabled: Boolean): Boolean
    }
    private var eligibleSince: Long? = nowMillis()
    private var attempted = false

    fun recoverIfNeeded(interfaceReady: Boolean, deadlineMillis: Long, cancelled: () -> Boolean): Boolean {
        if (interfaceReady) { budget.reset(expectedSsid); eligibleSince = null; return false }
        if (attempted || !enabled || api !in 17..22 || cancelled()) return false
        val state = runCatching { port.state() }.getOrDefault(CarHotspotStatus.State.UNKNOWN)
        if (state != CarHotspotStatus.State.ENABLED && state != CarHotspotStatus.State.FAILED) {
            eligibleSince = null; return false
        }
        val since = eligibleSince ?: nowMillis().also { eligibleSince = it }
        if (nowMillis() - since < 5000 || deadlineMillis - nowMillis() < 10_000) return false
        if (runCatching { port.configuredSsid() }.getOrNull() != expectedSsid) return false
        // SSID reflection can block while the owner changes AP state; recheck before mutation.
        if (cancelled() || isInterfaceReady()) return false
        val current = runCatching { port.state() }.getOrDefault(CarHotspotStatus.State.UNKNOWN)
        if (current != CarHotspotStatus.State.ENABLED && current != CarHotspotStatus.State.FAILED) {
            eligibleSince = null; return false
        }
        if (cancelled() || deadlineMillis - nowMillis() < 10_000) return false
        attempted = true
        if (!budget.claim(expectedSsid)) {
            onDiagnostic("HotspotRecovery retry-budget-used; waiting for interface or manual action")
            return false
        }
        onDiagnostic("HotspotRecovery missing-interface apState=$state attempt=1")
        if (!runCatching { port.setEnabled(false) }.getOrDefault(false)) {
            onDiagnostic("HotspotRecovery disable-rejected; use system hotspot settings")
            return false
        }
        var disabled = false
        var wasCancelled = false
        var restored: Boolean
        try {
            val stopDeadline = minOf(deadlineMillis, nowMillis() + 5000)
            while (nowMillis() < stopDeadline) {
                if (cancelled()) { wasCancelled = true; break }
                if (port.state() == CarHotspotStatus.State.DISABLED) { disabled = true; break }
                waitMillis(100)
            }
            if (!disabled && !wasCancelled) onDiagnostic("HotspotRecovery disable-timeout")
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            wasCancelled = true
        } catch (error: Exception) {
            onDiagnostic("HotspotRecovery wait-failed type=${error.javaClass.simpleName}")
        } finally {
            // Restoration is required even when the connection is cancelled after our disable.
            restored = runCatching { port.setEnabled(true) }.getOrDefault(false)
            onDiagnostic(if (restored) "HotspotRecovery enable-requested; awaiting AP interface"
                else "HotspotRecovery enable-rejected; turn hotspot on in system settings")
        }
        return disabled && !wasCancelled && restored
    }

    class AndroidPort(private val wifi: WifiManager) : Port {
        override fun state() = CarHotspotStatus.state(wifi)
        override fun configuredSsid(): String? {
            val config = WifiManager::class.java.getMethod("getWifiApConfiguration").invoke(wifi) as? WifiConfiguration
                ?: return null
            val ssid = config.SSID ?: return null
            return if (ssid.length >= 2 && ssid.first() == '"' && ssid.last() == '"') ssid.substring(1, ssid.length - 1) else ssid
        }
        override fun setEnabled(enabled: Boolean): Boolean = WifiManager::class.java
            .getMethod("setWifiApEnabled", WifiConfiguration::class.java, Boolean::class.javaPrimitiveType)
            .invoke(wifi, null, enabled) as? Boolean == true // null preserves the system's saved configuration.
    }
    class Budget {
        private val used = mutableSetOf<String>()
        @Synchronized fun claim(ssid: String): Boolean = used.add(ssid)
        @Synchronized fun reset(ssid: String) { used.remove(ssid) }
    }
    companion object {
        private val processBudget = Budget()
        fun resetBudget(ssid: String) = processBudget.reset(ssid)
    }
}
