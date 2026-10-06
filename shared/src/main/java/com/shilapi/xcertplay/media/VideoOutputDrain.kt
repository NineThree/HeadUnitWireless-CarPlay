package com.shilapi.xcertplay.media

import android.media.MediaCodec

/** Hold at most one output within a drain; a final frame is never lost to age filtering. */
internal object VideoOutputDrain {
    inline fun drain(
        running: () -> Boolean,
        dequeue: () -> Int,
        timestamp: () -> Long,
        flags: () -> Int,
        size: () -> Int,
        formatChanged: () -> Unit,
        release: (index: Int, timestampUs: Long, flags: Int, size: Int, newerTimestampUs: Long) -> Unit,
    ) {
        var pending = -1
        var pendingTimestamp = 0L
        var pendingFlags = 0
        var pendingSize = 0
        try {
            while (running()) {
                val index = dequeue()
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> formatChanged()
                    index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    index < 0 -> break
                    else -> {
                        val previous = pending
                        val previousTimestamp = pendingTimestamp
                        val previousFlags = pendingFlags
                        val previousSize = pendingSize
                        // Own the newly dequeued buffer before releasing the previous one: if
                        // that release throws, finally still returns the new buffer exactly once.
                        pending = index
                        pendingTimestamp = timestamp()
                        pendingFlags = flags()
                        pendingSize = size()
                        if (previous >= 0) release(previous, previousTimestamp, previousFlags, previousSize, pendingTimestamp)
                        if (pendingFlags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        } finally {
            if (pending >= 0) release(pending, pendingTimestamp, pendingFlags, pendingSize, 0)
        }
    }
}
