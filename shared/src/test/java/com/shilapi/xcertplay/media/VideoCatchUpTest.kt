package com.shilapi.xcertplay.media

import org.junit.Assert.*
import org.junit.Test

class VideoCatchUpTest {
    @Test fun briefRadioBurstsDecodeReferenceFramesWithoutRecreatingCodec() {
        assertFalse(VideoCatchUp.needsResync(249_000_000))
        assertFalse(VideoCatchUp.needsResync(300_000_000))
        assertFalse(VideoCatchUp.needsResync(700_000_000))
        assertTrue(VideoCatchUp.needsResync(751_000_000))
    }
    @Test fun alreadyDecodedOldOutputsCanBeSkippedWhileFreshAndUnknownTimestampsRender() {
        val policy = VideoCatchUp()
        val now = 2_000_000_000L
        val stale = policy.onQueued(1_600_000_000)
        val fresh = policy.onQueued(1_900_000_000)
        assertFalse(policy.shouldRender(stale, now, fresh))
        assertTrue(policy.shouldRender(fresh, now))
        assertTrue(policy.shouldRender(0, now))
        assertTrue(policy.shouldRender(now / 1000 + 1, now))
        assertTrue(policy.shouldRender(123, now)) // A timestamp the driver changed is unknown.
    }
    @Test fun timestampMatchingSupportsReorderedOutputAndCodecRecreation() {
        val policy = VideoCatchUp()
        val stale = policy.onQueued(1_000_000_000)
        val fresh = policy.onQueued(1_700_000_000)
        assertTrue(policy.shouldRender(fresh, 1_800_000_000))
        assertTrue(policy.shouldRender(stale, 1_800_000_000)) // Final/reordered output must still be visible.
        val oldCodec = policy.onQueued(1_900_000_000)
        policy.clear()
        assertTrue(policy.shouldRender(oldCodec, 3_000_000_000))
    }
    @Test fun finiteTimestampHistoryAndFutureClockNeverCauseUnknownOutputBlackout() {
        val policy = VideoCatchUp()
        val evicted = policy.onQueued(1_000_000_000)
        repeat(256) { policy.onQueued(1_000_000_000L + it) }
        assertTrue(policy.shouldRender(evicted, 2_000_000_000))
        val future = policy.onQueued(3_000_000_000)
        assertTrue(policy.shouldRender(future, 2_000_000_000))
    }
    @Test fun consistentlyLateOutputsAndTheStaticFinalFrameStillRender() {
        val policy = VideoCatchUp()
        repeat(10) {
            val arrival = 1_000_000_000L + it * 33_333_333L
            val timestamp = policy.onQueued(arrival)
            assertTrue(policy.shouldRender(timestamp, arrival + 400_000_000))
        }
    }
}
