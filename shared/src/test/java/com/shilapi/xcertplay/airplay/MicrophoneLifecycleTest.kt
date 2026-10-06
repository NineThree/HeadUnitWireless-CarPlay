package com.shilapi.xcertplay.airplay

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** Exercises the real encrypted UDP receive callback against session/stream teardown. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class MicrophoneLifecycleTest {
    private val loopback = InetAddress.getByName("127.0.0.1")
    private val secret = ByteArray(32) { 11 }
    private val setup = mapOf<String, Any?>("streamConnectionID" to 123L, "audioType" to "speechRecognition",
        "audioFormat" to 0x10L, "dataPort" to 12345, "framesPerPacket" to 320)

    @Test fun closingSessionStopsItsMicrophoneImmediately() {
        val started = CountDownLatch(1)
        val active = AtomicBoolean(false)
        val sink = object : MediaSink {
            override fun onMicrophoneStarted(type: Int, config: MicrophoneConfig) {
                assertEquals(loopback, config.localAddress)
                active.set(true); started.countDown()
            }
            override fun onMicrophoneStopped(type: Int) { active.set(false) }
        }
        val engine = CarPlayMediaEngine(sink, microphoneEnabled = true)
        val session = session(engine)
        try {
            val response = engine.onAudio(session, 100, setup)!!
            sendFirstPacket((response["dataPort"] as Number).toInt())
            assertTrue(started.await(2, TimeUnit.SECONDS))
            assertTrue(active.get())
            session.close()
            assertFalse("Session loss must stop microphone without waiting for reconnect", active.get())
        } finally { session.close() }
    }

    @Test fun teardownCannotBeFollowedByALateFirstPacketMicrophoneStart() {
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val tornDown = CountDownLatch(1)
        val events = CopyOnWriteArrayList<String>()
        val active = AtomicBoolean(false)
        val sink = object : MediaSink {
            override fun onAudioStarted(type: Int, format: AudioFormat, firstSample: Int) {
                entered.countDown()
                val deadline = System.nanoTime() + 3_000_000_000L
                while (resume.count > 0 && System.nanoTime() < deadline) {
                    try { resume.await(50, TimeUnit.MILLISECONDS) }
                    catch (_: InterruptedException) { /* Simulate initialization that cannot be interrupted. */ }
                }
            }
            override fun onMicrophoneStarted(type: Int, config: MicrophoneConfig) {
                active.set(true); events += "start"
            }
            override fun onMicrophoneStopped(type: Int) { active.set(false); events += "stop" }
        }
        val engine = CarPlayMediaEngine(sink, microphoneEnabled = true)
        val session = session(engine)
        var teardown: Thread? = null
        try {
            val response = engine.onAudio(session, 100, setup)!!
            sendFirstPacket((response["dataPort"] as Number).toInt())
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            teardown = Thread { engine.onTeardown(session, 100); tornDown.countDown() }.apply { start() }
            // Old implementation completes teardown while onStarted is paused, then starts mic late.
            tornDown.await(250, TimeUnit.MILLISECONDS)
            resume.countDown()
            teardown.join(2000)
            assertFalse(teardown.isAlive)
            assertTrue(tornDown.await(2, TimeUnit.SECONDS))
            // Allow the receive callback to finish; it must never leave mic active after stop.
            val deadline = System.nanoTime() + 1_000_000_000L
            while (events.size < 2 && System.nanoTime() < deadline) Thread.sleep(5)
            assertFalse("Late callback left microphone running: $events", active.get())
            assertEquals("stop", events.last())
        } finally { resume.countDown(); teardown?.join(2000); session.close() }
    }

    @Test fun oldSessionCloseDoesNotStopTheReplacementPhonesMicrophone() {
        val started = CountDownLatch(1)
        val active = AtomicBoolean(false)
        val sink = object : MediaSink {
            override fun onMicrophoneStarted(type: Int, config: MicrophoneConfig) { active.set(true); started.countDown() }
            override fun onMicrophoneStopped(type: Int) { active.set(false) }
        }
        val engine = CarPlayMediaEngine(sink, microphoneEnabled = true)
        val old = session(engine)
        val next = session(engine)
        try {
            engine.onAudio(old, 100, setup)
            val response = engine.onAudio(next, 100, setup)!!
            sendFirstPacket((response["dataPort"] as Number).toInt())
            assertTrue(started.await(2, TimeUnit.SECONDS))
            old.close()
            assertTrue("Old session teardown must not affect the replacement stream", active.get())
            next.close()
            assertFalse(active.get())
        } finally { old.close(); next.close() }
    }

    @Test fun closedSessionCannotCreateANewVoiceStream() {
        val engine = CarPlayMediaEngine(object : MediaSink {}, microphoneEnabled = true)
        val session = session(engine)
        session.close()
        try { assertNull(engine.onAudio(session, 100, setup)) }
        finally { engine.onSessionClosed(session) }
    }

    private fun sendFirstPacket(port: Int) {
        val key = AirPlayCrypto.hkdfSha512(secret, "DataStream-Salt123".toByteArray(Charsets.US_ASCII),
            "DataStream-Output-Encryption-Key".toByteArray(Charsets.US_ASCII), 32)
        val wire = MicrophonePacketizer.sealPacket(key, 100, MicrophoneCounters(), ByteArray(640), 320)
        DatagramSocket().use { it.send(DatagramPacket(wire, wire.size, loopback, port)) }
    }

    private fun session(media: AirPlayMediaHandler): AirPlaySession = AirPlaySession(
        socket = object : Socket() {
            override fun getLocalAddress(): InetAddress = loopback
            override fun getInetAddress(): InetAddress = loopback
            override fun getRemoteSocketAddress(): SocketAddress = InetSocketAddress(loopback, 1234)
        },
        config = AirPlayConfig("test", "02:00:00:00:00:02", "02:00:00:00:00:01", "1.0", AirPlayDisplayConfig(820, 480)),
        identity = AirPlayIdentity.generate(), pairings = PairingStore(), mfi = null,
        listener = object : AirPlaySessionListener {}, media = media,
    ).also { ReflectionHelpers.setField(it.pairVerify, "sharedSecret", secret) }
}
