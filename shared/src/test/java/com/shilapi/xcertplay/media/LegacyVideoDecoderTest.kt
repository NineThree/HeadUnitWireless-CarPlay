package com.shilapi.xcertplay.media

import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCrypto
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import com.shilapi.xcertplay.airplay.VideoCodec
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.MediaCodecInfoBuilder
import org.robolectric.shadows.ShadowMediaCodec
import org.robolectric.shadows.ShadowMediaCodecList
import org.robolectric.util.ReflectionHelpers
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** SDK29 supplies the Android objects; only the renderer's SDK17 branch is exercised.
 * The shadow emulates a driver's rejection, not actual MTK decoding or performance. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE, shadows = [LegacyVideoDecoderTest.Driver::class])
class LegacyVideoDecoderTest {
    @Before fun setUp() {
        Driver.reset()
        val capabilities = MediaCodecInfoBuilder.CodecCapabilitiesBuilder.newBuilder()
            .setMediaFormat(MediaFormat.createVideoFormat("video/avc", 614, 360))
            .setColorFormats(intArrayOf(19)).setProfileLevels(emptyArray()).build()
        for (name in listOf(HARDWARE, SOFTWARE)) {
            ShadowMediaCodecList.addCodec(MediaCodecInfoBuilder.newBuilder()
                .setName(name).setIsEncoder(false).setCapabilities(capabilities).build())
        }
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 17)
    }

    @After fun restoreSdk() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 29)
    }

    @Test fun legacyHardwareStartsWithoutForcingAnEightMiBInputAllocation() {
        Driver.rejectOversizedInput = true
        val report = startRenderer()
        assertTrue(report.toString(), report.any { it.startsWith("decoder=$HARDWARE ") })
        val format = Driver.formats.first()
        assertFalse("Let the legacy driver choose its input allocation", format.containsKey("max-input-size"))
        assertFalse("Priority is not available on SDK17", format.containsKey("priority"))
        assertEquals(614, format.getInteger("width"))
        assertEquals(360, format.getInteger("height"))
    }

    @Test fun aRejectedDriverCannotConsumeTheSoftwareFallbacksParameterSets() {
        Driver.failHardwareStart = true
        Driver.consumeParameterSets = true
        val report = startRenderer()
        assertTrue(report.toString(), report.any { it.startsWith("decoder=$SOFTWARE ") })
        assertEquals(listOf(START_CODE + SPS, START_CODE + SPS).map { it.toList() }, Driver.spsCopies.map { it.toList() })
        assertEquals(listOf(HARDWARE), Driver.released.filter { it == HARDWARE })
        assertTrue(report.toString(), report.any { it.contains("stage=start") && it.contains(HARDWARE) && it.contains("driver rejected start") })
    }

    @Test fun anAsynchronousDecoderErrorReportsTheFailingOperation() {
        Driver.failOutput = true
        val report = startRenderer(waitForError = true)
        assertTrue(report.toString(), report.any {
            it.contains("stage=dequeue-output") && it.contains(HARDWARE) && it.contains("driver output rejected")
        })
    }

    @Test fun theLegacyInputBufferPathReportsOnlySuccessfullyQueuedFrames() {
        val frame = byteArrayOf(0, 0, 0, 2, 0x65, 10)
        val report = startRenderer(frame = frame)
        assertTrue(report.toString(), report.any { it == "first input queued bytes=6 capacity=128" })
        assertEquals(listOf((START_CODE + byteArrayOf(0x65, 10)).toList()), Driver.queuedInputs.map { it.toList() })
    }

    @Test fun anOversizedFirstKeyframeReportsBothFrameAndBufferSizes() {
        Driver.inputCapacity = 8
        val frame = byteArrayOf(0, 0, 0, 10, 0x65) + ByteArray(9)
        val report = startRenderer(frame = frame)
        assertTrue(report.toString(), report.any {
            it.startsWith("recovery:") && it.contains("bytes=14") && it.contains("capacity=8")
        })
        assertFalse(report.any { it.startsWith("first input queued") })
        assertTrue(Driver.queuedInputs.isEmpty())
    }

    private fun startRenderer(waitForError: Boolean = false, frame: ByteArray? = null): List<String> {
        val surfaceTexture = SurfaceTexture(0)
        val surface = Surface(surfaceTexture)
        val report = CopyOnWriteArrayList<String>()
        val done = CountDownLatch(1)
        val sink = AndroidMediaSink(surface, videoWidth = 614, videoHeight = 360)
        sink.setVideoDiagnosticHandler(110) { message ->
            report += message
            val finished = when {
                waitForError -> message.startsWith("decoder error")
                frame != null -> message.startsWith("first input queued") || message.startsWith("recovery:")
                else -> message.startsWith("decoder=") || message.startsWith("decoder configuration failed")
            }
            if (finished) done.countDown()
        }
        try {
            sink.onVideoCodec(110, VideoCodec.H264)
            sink.onVideoConfig(110, byteArrayOf(1, 66, 0, 30, -1, -31, 0, SPS.size.toByte()) +
                SPS + byteArrayOf(1, 0, PPS.size.toByte()) + PPS)
            if (frame != null) sink.onVideoFrame(110, frame)
            assertTrue("Renderer did not finish startup: $report", done.await(5, TimeUnit.SECONDS))
            return report.toList()
        } finally {
            @Suppress("UNCHECKED_CAST")
            val decoders = ReflectionHelpers.getField<Map<Int, Any>>(sink, "videoDecoders")
            val worker = decoders[110]?.let { ReflectionHelpers.getField<Thread>(it, "thread") }
            sink.close()
            worker?.join(2_000)
            assertFalse("Renderer worker failed to close", worker?.isAlive == true)
            surface.release()
            surfaceTexture.release()
        }
    }

    @Implements(MediaCodec::class)
    class Driver : ShadowMediaCodec() {
        private var name = "unknown"
        private var input: ByteBuffer? = null

        @Implementation override fun __constructor__(name: String, nameIsType: Boolean, encoder: Boolean) {
            this.name = name
            super.__constructor__(name, nameIsType, encoder)
        }

        @Implementation fun configure(format: MediaFormat, surface: Surface?, crypto: MediaCrypto?, flags: Int) {
            formats += format
            if (rejectOversizedInput && format.containsKey("max-input-size") &&
                format.getInteger("max-input-size") > 1_048_576) throw IllegalStateException("driver input allocation rejected")
            val sps = format.getByteBuffer("csd-0")!!
            val copy = ByteArray(sps.remaining())
            (if (consumeParameterSets) sps else sps.duplicate()).get(copy)
            spsCopies += copy
        }

        @Implementation fun start() {
            if (name == HARDWARE && failHardwareStart) throw IllegalStateException("driver rejected start")
        }
        @Implementation fun stop() = Unit
        @Implementation fun release() { released += name }
        @Implementation fun getInputBuffers(): Array<ByteBuffer> = arrayOf(
            input ?: ByteBuffer.allocate(inputCapacity).also { input = it },
        )
        @Implementation fun dequeueInputBuffer(timeout: Long): Int = 0
        @Implementation fun queueInputBuffer(index: Int, offset: Int, size: Int, time: Long, flags: Int) {
            val bytes = ByteArray(size)
            input!!.duplicate().apply { position(offset); limit(offset + size) }.get(bytes)
            queuedInputs += bytes
        }
        @Implementation fun dequeueOutputBuffer(info: MediaCodec.BufferInfo, timeout: Long): Int {
            if (failOutput) throw IllegalStateException("driver output rejected")
            return MediaCodec.INFO_TRY_AGAIN_LATER
        }

        companion object {
            var rejectOversizedInput = false
            var failHardwareStart = false
            var consumeParameterSets = false
            var failOutput = false
            var inputCapacity = 128
            val formats = CopyOnWriteArrayList<MediaFormat>()
            val spsCopies = CopyOnWriteArrayList<ByteArray>()
            val released = CopyOnWriteArrayList<String>()
            val queuedInputs = CopyOnWriteArrayList<ByteArray>()
            fun reset() {
                rejectOversizedInput = false; failHardwareStart = false
                consumeParameterSets = false; failOutput = false
                inputCapacity = 128
                formats.clear(); spsCopies.clear(); released.clear()
                queuedInputs.clear()
            }
        }
    }

    private companion object {
        const val HARDWARE = "OMX.mtk.video.decoder.avc"
        const val SOFTWARE = "OMX.google.h264.decoder"
        val START_CODE = byteArrayOf(0, 0, 0, 1)
        val SPS = byteArrayOf(0x67, 66, 0, 30, 1)
        val PPS = byteArrayOf(0x68, 1)
    }
}
