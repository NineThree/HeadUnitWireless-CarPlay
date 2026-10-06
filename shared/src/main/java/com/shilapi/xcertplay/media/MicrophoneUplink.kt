package com.shilapi.xcertplay.media

import android.media.AudioFormat as AndroidAudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.MicrophoneConfig
import com.shilapi.xcertplay.airplay.MicrophoneCounters
import com.shilapi.xcertplay.airplay.MicrophonePacketizer
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures one PCM microphone stream and sends it back to the phone as sealed CarPlay RTP.
 *
 * The recorder runs only while the matching audio stream is active, so callers start this after
 * the first downlink audio packet and close it on stream teardown.
 */
@android.annotation.SuppressLint("MissingPermission")
internal class MicrophoneUplink(
    private val config: MicrophoneConfig,
    private val onDiagnostic: (String) -> Unit = {},
    private val createSocket: () -> DatagramSocket = { DatagramSocket(null) },
) : Closeable {
    private val running = AtomicBoolean(false)
    private var closed = false
    private val firstPacketLogged = AtomicBoolean(false)
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var opusEncoder: OpusEncoder? = null
    private var thread: Thread? = null

    @Synchronized
    fun start(): Boolean {
        if (closed) return false
        if (!running.compareAndSet(false, true)) return true

        val channelMask = if (config.channels >= 2) {
            AndroidAudioFormat.CHANNEL_IN_STEREO
        } else {
            AndroidAudioFormat.CHANNEL_IN_MONO
        }
        val minBuffer = runCatching { AudioRecord.getMinBufferSize(
            config.sampleRate,
            channelMask,
            AndroidAudioFormat.ENCODING_PCM_16BIT,
        ) }.getOrDefault(-1)
        if (minBuffer <= 0) {
            Log.w(TAG, "microphone unavailable rate=${config.sampleRate} channels=${config.channels}")
            report("microphone unavailable stage=buffer-size rate=${config.sampleRate} channels=${config.channels}")
            running.set(false)
            return false
        }

        val source = when (config.audioType) {
            "telephony" -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
            "speechrecognition" -> MediaRecorder.AudioSource.VOICE_RECOGNITION
            else -> MediaRecorder.AudioSource.MIC
        }
        val nextEncoder = if (config.codec == AudioCodecKind.OPUS) {
            OpusEncoder(config.bitrate ?: 48_000).takeIf { it.available }
        } else {
            null
        }
        if (config.codec == AudioCodecKind.OPUS && nextEncoder == null) {
            Log.w(TAG, "microphone Opus encoder is unavailable")
            running.set(false)
            report("microphone unavailable stage=opus-encoder")
            return false
        }
        val bufferSize = maxOf(minBuffer * 2, config.frameBytes * 4)
        var selectedSource = source
        val sources = if (source == MediaRecorder.AudioSource.MIC) listOf(source) else listOf(source, MediaRecorder.AudioSource.MIC)
        val nextRecorder = sources.firstNotNullOfOrNull { candidate ->
            val recorder = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioRecord.Builder()
                        .setAudioSource(candidate)
                        .setAudioFormat(
                            AndroidAudioFormat.Builder()
                                .setEncoding(AndroidAudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(config.sampleRate)
                                .setChannelMask(channelMask)
                                .build(),
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioRecord(
                        candidate,
                        config.sampleRate,
                        channelMask,
                        AndroidAudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                    )
                }
            } catch (_: Exception) { null }
            val recording = runCatching {
                if (recorder?.state != AudioRecord.STATE_INITIALIZED) false
                else {
                    recorder.startRecording()
                    // API17 may return normally after a native start failure.
                    recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING
                }
            }.getOrDefault(false)
            if (recording) {
                selectedSource = candidate
                recorder
            } else {
                runCatching { recorder?.release() }
                null
            }
        }
        if (nextRecorder == null) {
            nextEncoder?.close()
            running.set(false)
            report("microphone unavailable stage=recorder sources=${sources.joinToString(",")} rate=${config.sampleRate}")
            return false
        }

        var initializingSocket: DatagramSocket? = null
        val nextSocket = try {
            createSocket().also { initializingSocket = it }.apply {
                reuseAddress = true
                val wildcard = if (config.host is Inet4Address) "0.0.0.0" else "::"
                bind(InetSocketAddress(config.localAddress ?: InetAddress.getByName(wildcard), 0))
            }
        } catch (error: Exception) {
            Log.e(TAG, "microphone socket creation failed", error)
            initializingSocket?.close()
            runCatching { nextRecorder.release() }
            nextEncoder?.close()
            running.set(false)
            report("microphone unavailable stage=socket error=${error.javaClass.simpleName}")
            return false
        }

        recorder = nextRecorder
        socket = nextSocket
        opusEncoder = nextEncoder
        return try {
            thread = Thread({ capture(nextRecorder, nextSocket) }, "carplay-mic").apply {
                isDaemon = true
                start()
            }
            Log.i(
                TAG,
                "microphone uplink started type=${config.audioType} " +
                    "rate=${config.sampleRate} channels=${config.channels} " +
                    "frameMs=${config.frameMillis} port=${config.port}",
            )
            report("microphone started type=${config.audioType} codec=${config.codec} rate=${config.sampleRate} " +
                "channels=${config.channels} source=$selectedSource family=${if (config.host is Inet4Address) "IPv4" else "IPv6"}")
            true
        } catch (error: Exception) {
            Log.e(TAG, "microphone recording failed", error)
            release()
            report("microphone unavailable stage=start-recording error=${error.javaClass.simpleName}")
            false
        }
    }

    private fun capture(recorder: AudioRecord, socket: DatagramSocket) {
        val frame = ByteArray(config.frameBytes)
        val readBuffer = ByteArray(maxOf(frame.size, MIN_READ_BYTES))
        val counters = MicrophoneCounters()
        var filled = 0
        var bytesRead = 0L
        var sampledPeak = 0
        var nextReportNanos = System.nanoTime() + 2_000_000_000L
        try {
            while (running.get()) {
                val count = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    recorder.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
                } else {
                    recorder.read(readBuffer, 0, readBuffer.size)
                }
                if (count < 0) {
                    if (running.get()) Log.e(TAG, "microphone read failed code=$count")
                    if (running.get()) report("microphone unavailable stage=read code=$count")
                    return
                }
                if (count == 0) {
                    Thread.sleep(5)
                    continue
                }
                bytesRead += count
                val stride = maxOf(2, (count / 32) and 1.inv())
                var sample = 0
                while (sample + 1 < count) {
                    val value = ((readBuffer[sample].toInt() and 255) or (readBuffer[sample + 1].toInt() shl 8)).toShort().toInt()
                    sampledPeak = maxOf(sampledPeak, kotlin.math.abs(value))
                    sample += stride
                }
                if (System.nanoTime() >= nextReportNanos) {
                    report("microphone stats bytesRead=$bytesRead sampledPeak=$sampledPeak")
                    sampledPeak = 0
                    nextReportNanos = System.nanoTime() + 2_000_000_000L
                }
                var offset = 0
                while (offset < count && running.get()) {
                    val copied = minOf(frame.size - filled, count - offset)
                    readBuffer.copyInto(frame, filled, offset, offset + copied)
                    filled += copied
                    offset += copied
                    if (filled == frame.size) {
                        sendFrame(socket, counters, frame)
                        filled = 0
                    }
                }
            }
        } catch (error: Exception) {
            if (running.get()) Log.e(TAG, "microphone capture failed", error)
            if (running.get()) report("microphone unavailable stage=capture error=${error.javaClass.simpleName}")
        } finally {
            running.set(false)
            release()
            report("microphone stopped bytesRead=$bytesRead")
        }
    }

    private fun sendFrame(socket: DatagramSocket, counters: MicrophoneCounters, frame: ByteArray) {
        val bodies = if (config.codec == AudioCodecKind.OPUS) {
            opusEncoder?.encode(frame).orEmpty()
        } else {
            listOf(MicrophonePacketizer.toWirePcm(frame))
        }
        bodies.forEach { body ->
            sendPacket(
                socket = socket,
                counters = counters,
                body = body,
                samples = config.samplesPerPacket,
            )
        }
    }

    private fun sendPacket(
        socket: DatagramSocket,
        counters: MicrophoneCounters,
        body: ByteArray,
        samples: Int,
    ) {
        val packet = MicrophonePacketizer.sealPacket(
            key = config.key,
            payloadType = config.payloadType,
            counters = counters,
            body = body,
            samples = samples,
        )
        try {
            socket.send(DatagramPacket(packet, packet.size, config.host, config.port))
            if (firstPacketLogged.compareAndSet(false, true)) {
                Log.i(
                    TAG,
                    "microphone first packet bytes=${packet.size} body=${body.size} " +
                        "port=${config.port}",
                )
            }
            if (counters.nonce == 1L) report("microphone first packet bytes=${packet.size}")
        } catch (error: Exception) {
            if (running.get()) throw error
        }
    }

    override fun close() {
        val worker = synchronized(this) {
            closed = true
            running.set(false)
            runCatching { recorder?.stop() }
            runCatching { socket?.close() }
            thread
        }
        // capture() releases under this object's monitor; do not hold it while joining.
        if (worker != null && worker !== Thread.currentThread()) {
            try {
                worker.join(CLOSE_JOIN_MILLIS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            if (worker.isAlive) worker.interrupt()
        }
        release()
    }

    private fun report(message: String) { runCatching { onDiagnostic(message) } }

    @Synchronized
    private fun release() {
        running.set(false)
        val currentRecorder = recorder
        recorder = null
        try {
            currentRecorder?.release()
        } catch (_: Exception) {
            // Best effort.
        }
        val currentSocket = socket
        socket = null
        try {
            currentSocket?.close()
        } catch (_: Exception) {
            // Best effort.
        }
        val currentEncoder = opusEncoder
        opusEncoder = null
        currentEncoder?.close()
    }

    private companion object {
        const val TAG = "xcertplay-usb"
        const val MIN_READ_BYTES = 2_048
        const val CLOSE_JOIN_MILLIS = 500L
    }
}
