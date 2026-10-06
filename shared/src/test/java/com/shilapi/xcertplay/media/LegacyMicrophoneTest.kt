package com.shilapi.xcertplay.media

import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import com.shilapi.xcertplay.airplay.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers

/** Real uplink worker and UDP, simulated recorder. This is not a physical microphone test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE, shadows = [LegacyMicrophoneTest.Recorder::class])
class LegacyMicrophoneTest {
    private val loopback = InetAddress.getByName("127.0.0.1")
    @Before fun prepare() {
        Recorder.sources.clear(); Recorder.rejectVoice = false; Recorder.rejectAll = false; Recorder.rejectVoiceStart = false
        Recorder.released = 0
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 17)
    }
    @After fun restoreSdk() { ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 29) }

    @Test fun pcmMicrophoneUsesIpv4AndSendsASealedPacketThenReleasesRecording() {
        DatagramSocket(0, loopback).use { receiver ->
            receiver.soTimeout = 2000
            val uplink = MicrophoneUplink(configuration(receiver.localPort))
            try {
                assertTrue(uplink.start())
                val socket = ReflectionHelpers.getField<DatagramSocket>(uplink, "socket")
                assertTrue("An IPv4 phone must not require an IPv6 wildcard; actual=${socket.localAddress}", socket.localAddress is Inet4Address)
                val packet = DatagramPacket(ByteArray(2048), 2048)
                receiver.receive(packet)
                assertEquals(676, packet.length)
            } finally { uplink.close() }
            assertTrue(Recorder.released > 0)
            assertFalse(ReflectionHelpers.getField<Thread?>(uplink, "thread")?.isAlive == true)
        }
    }

    @Test fun rejectedSpeechSourceFallsBackOnceToTheOrdinaryMicrophone() {
        Recorder.rejectVoice = true
        DatagramSocket(0, loopback).use { receiver ->
            val uplink = MicrophoneUplink(configuration(receiver.localPort))
            try {
                assertTrue("Unsupported voice source should fall back to MIC", uplink.start())
                assertEquals(listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC), Recorder.sources.toList())
            } finally { uplink.close() }
        }
    }

    @Test fun unavailableRecorderReportsTheFailureWithoutThrowingFromTheMediaSink() {
        Recorder.rejectAll = true
        val report = CopyOnWriteArrayList<String>()
        val sink = AndroidMediaSink(onAudioDiagnostic = { report += it })
        try {
            sink.onMicrophoneStarted(100, configuration(12345))
            assertTrue(report.toString(), report.any { it.contains("microphone unavailable") })
        } finally { sink.close() }
    }

    @Test fun unavailableLocalInterfaceClosesTheSocketAndRecorderWithoutThrowing() {
        val socket = DatagramSocket(null)
        val report = CopyOnWriteArrayList<String>()
        val uplink = MicrophoneUplink(
            configuration(12345).copy(localAddress = InetAddress.getByName("203.0.113.1")),
            onDiagnostic = { report += it },
            createSocket = { socket },
        )
        try {
            assertFalse(uplink.start())
            assertTrue("A failed bind must close the partially initialized socket", socket.isClosed)
            assertTrue(Recorder.released > 0)
            assertTrue(report.any { it.contains("stage=socket") })
        } finally { uplink.close(); socket.close() }
    }

    @Test fun initializedButNonRecordingVoiceSourceFallsBackToMic() {
        Recorder.rejectVoiceStart = true
        val uplink = MicrophoneUplink(configuration(12345))
        try {
            assertTrue(uplink.start())
            assertEquals(listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC), Recorder.sources.toList())
        } finally { uplink.close() }
    }

    @Test fun closedUplinkCannotStartAnOrphanedRecording() {
        val uplink = MicrophoneUplink(configuration(12345))
        uplink.close()
        try { assertFalse(uplink.start()); assertTrue(Recorder.sources.isEmpty()) }
        finally { uplink.close() }
    }

    @Test fun closedSinkRejectsLateMicrophoneStart() {
        val sink = AndroidMediaSink()
        sink.close()
        try { sink.onMicrophoneStarted(100, configuration(12345)); assertTrue(Recorder.sources.isEmpty()) }
        finally { sink.close() }
    }

    @Test fun legacyVoiceCapabilityCanExcludeUnavailableOpusFromBothDirections() {
        val config = AirPlayConfig("test", "00:11:22:33:44:55", "00:11:22:33:44:55", "test",
            AirPlayDisplayConfig(820,480), microphone = true, opusAudioEnabled = false)
        val entries = AirPlayInfoPlist.build(config)["audioFormats"] as List<*>
        for (entry in entries.filterIsInstance<Map<String, Any?>>()) {
            for (name in listOf("audioInputFormats", "audioOutputFormats")) {
                val bits = (entry[name] as? Number)?.toInt() ?: continue
                assertEquals(0, bits and 0x70000000)
            }
        }
    }

    private fun configuration(port: Int) = MicrophoneConfig("speechrecognition", 16000, 1, 100, 20,
        loopback, port, ByteArray(32) { 7 }, localAddress = loopback)

    @Implements(AudioRecord::class)
    class Recorder {
        private var source = 0
        private var recordingState = AudioRecord.RECORDSTATE_STOPPED
        @Implementation fun __constructor__(source: Int, rate: Int, channels: Int, encoding: Int, bufferBytes: Int) {
            this.source = source; sources += source
        }
        @Implementation fun getState(): Int = if (rejectAll || rejectVoice && source != MediaRecorder.AudioSource.MIC)
            AudioRecord.STATE_UNINITIALIZED else AudioRecord.STATE_INITIALIZED
        @Implementation fun startRecording() {
            recordingState = if (rejectVoiceStart && source != MediaRecorder.AudioSource.MIC)
                AudioRecord.RECORDSTATE_STOPPED else AudioRecord.RECORDSTATE_RECORDING
        }
        @Implementation fun getRecordingState() = recordingState
        @Implementation fun read(bytes: ByteArray, offset: Int, size: Int): Int {
            Thread.sleep(5)
            val count = minOf(640, size)
            for (i in 0 until count) bytes[offset+i] = (i and 0xff).toByte()
            return count
        }
        @Implementation fun stop() { recordingState = AudioRecord.RECORDSTATE_STOPPED }
        @Implementation fun release() { released++ }
        companion object {
            val sources = CopyOnWriteArrayList<Int>()
            @Volatile var rejectVoice = false
            @Volatile var rejectAll = false
            @Volatile var rejectVoiceStart = false
            @Volatile var released = 0
            @JvmStatic @Implementation fun getMinBufferSize(rate: Int, channels: Int, encoding: Int) = 1024
        }
    }
}
