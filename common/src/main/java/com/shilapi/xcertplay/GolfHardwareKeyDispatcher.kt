package com.shilapi.xcertplay

import android.view.KeyEvent
import com.shilapi.xcertplay.airplay.CarPlayMediaButton

/** One route for the focused window and Android's media-button broadcast. No polling or timers. */
internal class GolfHardwareKeyDispatcher(
    private val media: (Int) -> Boolean,
    private val telephony: (Int) -> Boolean,
    private val siri: () -> Boolean,
    private val report: (String) -> Unit,
    private val logicalKey: (KeyEvent) -> Int? = { null },
) {
    private data class EventId(val device: Int, val code: Int, val scan: Int, val action: Int,
        val downTime: Long, val time: Long, val repeat: Int)
    private val recentEvents = linkedMapOf<EventId, String>()
    private var phoneDown: EventId? = null

    fun reset() { recentEvents.clear(); phoneDown = null }

    fun dispatch(event: KeyEvent, source: String): Boolean {
        val code = logicalKey(event) ?: event.keyCode
        val mediaIndex = CarPlayMediaButton.forKeyCode(code)
        val voice = CarPlayMediaButton.opensSiri(code)
        val phone = code == KeyEvent.KEYCODE_CALL || code == KeyEvent.KEYCODE_ENDCALL
        if (mediaIndex == null && !voice && !phone) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
                (event.keyCode == KeyEvent.KEYCODE_UNKNOWN || event.keyCode >= 256 || !event.isPrintingKey)) {
                record(event, source, "unmapped", null)
            }
            return false
        }
        val id = EventId(event.deviceId, event.keyCode, event.scanCode, event.action,
            event.downTime, event.eventTime, event.repeatCount)
        if (recentEvents[id]?.let { it != source } == true) return true
        recentEvents[id] = source
        if (recentEvents.size > 16) recentEvents.remove(recentEvents.keys.first())

        when {
            mediaIndex != null -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0)
                    record(event, source, "media:$mediaIndex", media(mediaIndex))
            }
            voice -> {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled)
                    record(event, source, "siri", siri())
            }
            code == KeyEvent.KEYCODE_ENDCALL -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0)
                    record(event, source, "phone:drop", telephony(PHONE_DROP))
            }
            event.action == KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    phoneDown = id
                    record(event, source, "phone:waiting-for-release", null)
                }
            }
            event.action == KeyEvent.ACTION_UP -> {
                val down = phoneDown
                phoneDown = null
                val matched = down != null && down.device == id.device && down.code == id.code &&
                    down.downTime == id.downTime
                if (!matched || event.isCanceled) {
                    record(event, source, "phone:cancelled-or-unpaired-release", null)
                } else {
                    // Android may flag a long repeat at500ms; use our actual800ms threshold.
                    val longPress = event.eventTime - down!!.time >= LONG_PRESS_MS
                    val index = if (longPress) PHONE_DROP else PHONE_HOOK
                    record(event, source, if (longPress) "phone:drop" else "phone:hook", telephony(index))
                }
            }
        }
        return true
    }

    private fun record(event: KeyEvent, source: String, route: String, queued: Boolean?) {
        report("HardwareKey source=$source code=${event.keyCode} scan=${event.scanCode} " +
            "action=${event.action} repeat=${event.repeatCount} route=$route queued=${queued ?: "none"}")
    }

    companion object {
        // Ordinals in AirPlayHid's advertised telephony array, not raw USB usage numbers.
        const val PHONE_HOOK = 1
        const val PHONE_DROP = 3
        const val LONG_PRESS_MS = 800L
    }
}
