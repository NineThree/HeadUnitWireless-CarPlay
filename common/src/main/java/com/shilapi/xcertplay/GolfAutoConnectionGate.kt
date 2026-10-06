package com.shilapi.xcertplay

/** A single bounded automatic attempt per launcher visit; manual cancellation remains authoritative. */
internal class GolfAutoConnectionGate {
    enum class Decision { WAIT, CONNECT, TIMEOUT, STOP }
    private var deadline: Long? = null
    private var consumed = false
    fun begin(now: Long) { if (!consumed && deadline == null) deadline = now + 90_000L }
    fun evaluate(now: Long, ready: Boolean): Decision {
        if (consumed) return Decision.STOP
        val until = deadline ?: return Decision.STOP
        return when {
            now >= until -> { consumed = true; Decision.TIMEOUT }
            ready -> { consumed = true; Decision.CONNECT }
            else -> Decision.WAIT
        }
    }
    fun cancel() { consumed = true }
    fun reset() { deadline = null; consumed = false }
}
