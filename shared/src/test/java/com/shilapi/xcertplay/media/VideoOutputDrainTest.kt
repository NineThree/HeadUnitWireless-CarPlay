package com.shilapi.xcertplay.media

import org.junit.Assert.*
import org.junit.Test

class VideoOutputDrainTest {
    private data class Output(val index: Int, val pts: Long, val flags: Int = 0, val size: Int = index + 10)
    private class Fixture(val outputs: List<Output>) {
        var cursor = 0
        var current = Output(-1,0)
        val policy = VideoCatchUp()
        val released = mutableListOf<Pair<Int, Boolean>>()
        val sizes = mutableListOf<Int>()
        var failDequeueAt = -1
        var failReleaseAt = -1
        var running = true
        fun drain() = VideoOutputDrain.drain(
            running = { running },
            dequeue = {
                if (cursor == failDequeueAt) throw IllegalStateException("synthetic dequeue failure")
                if (cursor == outputs.size) -1 else outputs[cursor++].also { current = it }.index
            }, timestamp = { current.pts }, flags = { current.flags }, size = { current.size }, formatChanged = {},
            release = { index, pts, _, size, newer ->
                released.add(index to policy.shouldRender(pts,2_000_000_000,newer)); sizes.add(size)
                if (index == failReleaseAt) throw IllegalStateException("synthetic release failure")
            },
        )
    }
    @Test fun staleBurstSkipsEarlierOutputAndAlwaysShowsItsFinalFrameWithOriginalMetadata() {
        val f = Fixture(emptyList())
        val pts = listOf(1_000_000_000L,1_033_333_333L,1_066_666_666L).map(f.policy::onQueued)
        val actual = Fixture(pts.mapIndexed { i, p -> Output(i,p) })
        listOf(1_000_000_000L,1_033_333_333L,1_066_666_666L).forEach(actual.policy::onQueued)
        actual.drain()
        assertEquals(listOf(0 to false,1 to false,2 to true),actual.released)
        assertEquals(listOf(10,11,12),actual.sizes)
    }
    @Test fun delayedSingleFinalOutputAndUnknownOrReorderedTimestampsRemainVisible() {
        val single = Fixture(listOf(Output(0,1_000_000)))
        single.policy.onQueued(1_000_000_000); single.drain()
        assertEquals(listOf(0 to true),single.released)
        val reordered = Fixture(listOf(Output(0,1_033_333),Output(1,1_000_000),Output(2,0),Output(3,987)))
        reordered.policy.onQueued(1_000_000_000); reordered.policy.onQueued(1_033_333_333)
        reordered.drain()
        assertEquals(listOf(0 to true,1 to true,2 to true,3 to true),reordered.released)
    }
    @Test fun dequeuingErrorReturnsHeldOutputAndReleasingErrorAlsoReturnsNewOutputExactlyOnce() {
        val dequeueError = Fixture(listOf(Output(0,0)))
        dequeueError.failDequeueAt = 1
        assertThrows(IllegalStateException::class.java) { dequeueError.drain() }
        assertEquals(listOf(0 to true),dequeueError.released)
        val releaseError = Fixture(listOf(Output(0,0),Output(1,0)))
        releaseError.failReleaseAt = 0
        assertThrows(IllegalStateException::class.java) { releaseError.drain() }
        assertEquals(listOf(0 to true,1 to true),releaseError.released)
    }
    @Test fun endOfStreamStopsDequeueAndReturnsTheHeldFinalOutput() {
        val f = Fixture(listOf(Output(0,0,flags=4),Output(1,0)))
        f.drain()
        assertEquals(1,f.cursor)
        assertEquals(listOf(0 to true),f.released)
    }
}
