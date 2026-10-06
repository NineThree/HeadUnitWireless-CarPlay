package com.shilapi.xcertplay

import android.view.InputDevice
import java.io.File

/** Export-time metadata only; no key polling, layout modification or privileged device access. */
internal object InputDiagnosticSnapshot {
    fun report(): String = buildString {
        val ids = runCatching { InputDevice.getDeviceIds().take(16) }.getOrDefault(emptyList())
        append("InputDevicesReported=${ids.size} limit=16\n")
        for (id in ids) {
            val device = runCatching { InputDevice.getDevice(id) }.getOrNull() ?: continue
            val line = "InputDevice id=$id sources=${device.sources} keyboardType=${device.keyboardType}"
            DiagnosticRedactor.redact(line)?.let { append(it).append('\n') }
        }
        for (directory in listOf("/system/usr/keylayout", "/vendor/usr/keylayout")) {
            val names = runCatching { File(directory).list()?.asSequence()?.filter { it.endsWith(".kl") }
                ?.take(16)?.map { it.replace(Regex("[\\r\\n\\t]"), " ").take(96) }?.toList() }
                .getOrNull()
            DiagnosticRedactor.redact("KeyLayoutDirectory=$directory files=${names?.joinToString(",") ?: "unavailable"}")
                ?.let { append(it).append('\n') }
        }
    }
}
