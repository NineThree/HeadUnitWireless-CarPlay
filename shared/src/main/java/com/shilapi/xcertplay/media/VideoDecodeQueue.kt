package com.shilapi.xcertplay.media

import android.view.Surface
import com.shilapi.xcertplay.airplay.VideoCodec
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

internal sealed interface VideoJob {
    data class Config(val codec: VideoCodec, val codecData: ByteArray) : VideoJob
    data class Frame(val nalus: ByteArray, val receivedNs: Long = System.nanoTime()) : VideoJob
    data class SurfaceChanged(val surface: Surface?) : VideoJob
    data object Resync : VideoJob
}

/** Do not resume dependent pictures after losing a reference frame. */
internal class VideoReferenceChain {
    var needsKeyFrame = true
        private set
    fun reset() { needsKeyFrame = true }
    fun accepts(bytes: ByteArray, codec: VideoCodec): Boolean =
        !needsKeyFrame || MediaCodecSupport.isRandomAccess(bytes, codec)
    fun onQueued() { needsKeyFrame = false }
}

/** Limit latency and memory without ever dropping a reference frame silently. */
internal class VideoDecodeQueue(
    // Wi-Fi burst catch-up is separately bounded; limits here protect memory and the reference chain.
    private val maxFrames: Int = 60,
    private val maxBytes: Int = 8 * 1024 * 1024,
) {
    private val lock = Object()
    private val jobs = ArrayDeque<VideoJob>()
    private var frameCount = 0
    private var frameBytes = 0L
    private var waiters = 0

    fun offer(job: VideoJob) = synchronized(lock) {
        if (job is VideoJob.Frame) {
            if (frameCount >= maxFrames || frameBytes + job.nalus.size > maxBytes) {
                discardFramesLocked()
                jobs.addLast(VideoJob.Resync)
            }
            // A single oversized frame is also a lost reference chain.
            if (job.nalus.size > maxBytes) { if (waiters > 0) lock.notifyAll(); return@synchronized }
            frameCount++
            frameBytes += job.nalus.size
        }
        jobs.addLast(job)
        if (waiters > 0) lock.notifyAll()
    }

    fun discardFrames() = synchronized(lock) { discardFramesLocked() }

    private fun discardFramesLocked() {
        val iterator = jobs.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item is VideoJob.Frame || item is VideoJob.Resync) iterator.remove()
        }
        frameCount = 0
        frameBytes = 0
    }

    fun poll(timeoutMillis: Long): VideoJob? = synchronized(lock) {
        val timeoutNs = if (jobs.isEmpty()) TimeUnit.MILLISECONDS.toNanos(timeoutMillis) else 0
        val start = if (jobs.isEmpty()) System.nanoTime() else 0
        while (jobs.isEmpty()) {
            val remaining = timeoutNs - (System.nanoTime() - start)
            if (remaining <= 0) return@synchronized null
            waiters++
            try { lock.wait(remaining / 1_000_000, (remaining % 1_000_000).toInt()) }
            finally { waiters-- }
        }
        jobs.removeFirst().also {
            if (it is VideoJob.Frame) { frameCount--; frameBytes -= it.nalus.size }
        }
    }
}

/** Drain output while waiting for input: full output buffers can otherwise starve input forever. */
internal object VideoInputPump {
    fun acquire(
        running: () -> Boolean,
        drain: () -> Unit,
        dequeue: () -> Int,
        nanoTime: () -> Long = System::nanoTime,
        timeoutNs: Long = TimeUnit.MILLISECONDS.toNanos(500),
    ): Int {
        val start = nanoTime()
        while (running()) {
            drain()
            val index = dequeue()
            if (index >= 0) return index
            if (nanoTime() - start >= timeoutNs) break
        }
        return -1
    }
}
