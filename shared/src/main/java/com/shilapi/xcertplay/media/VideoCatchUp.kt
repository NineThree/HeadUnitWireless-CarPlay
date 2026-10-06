package com.shilapi.xcertplay.media

/** Worker-owned arrival correlation. Decode every reference; skip only known stale output. */
internal class VideoCatchUp {
    private val timestamps = LongArray(128)
    private val arrivals = LongArray(128)
    private var next = 0
    private var count = 0
    private var lastTimestamp = 0L

    fun onQueued(receivedNs: Long): Long {
        val timestamp = maxOf(lastTimestamp + 1, receivedNs / 1000)
        lastTimestamp = timestamp
        timestamps[next] = timestamp
        arrivals[next] = receivedNs
        next = (next + 1) % timestamps.size
        count = minOf(count + 1, timestamps.size)
        return timestamp
    }

    fun shouldRender(presentationTimeUs: Long, nowNs: Long, newerPresentationTimeUs: Long = 0): Boolean {
        if (presentationTimeUs <= 0) return true
        var newerArrival = Long.MIN_VALUE
        if (newerPresentationTimeUs > presentationTimeUs) {
            for (offset in 1..count) {
                val index = (next - offset + timestamps.size) % timestamps.size
                if (timestamps[index] == newerPresentationTimeUs) { newerArrival = arrivals[index]; break }
            }
        }
        for (offset in 1..count) {
            val index = (next - offset + timestamps.size) % timestamps.size
            if (timestamps[index] == presentationTimeUs) {
                timestamps[index] = 0
                val age = nowNs - arrivals[index]
                return age <= MAX_DISPLAY_AGE_NS || newerArrival < arrivals[index]
                // The last output always renders, even on a consistently slow or now-static stream.
            }
        }
        return true // Legacy drivers may replace PTS; never infer age from an unknown clock.
    }

    fun clear() { timestamps.fill(0); next = 0; count = 0; lastTimestamp = 0 }

    companion object {
        private const val MAX_DISPLAY_AGE_NS = 250_000_000L
        private const val MAX_CATCH_UP_NS = 750_000_000L
        fun needsResync(ageNs: Long): Boolean = ageNs > MAX_CATCH_UP_NS
    }
}
