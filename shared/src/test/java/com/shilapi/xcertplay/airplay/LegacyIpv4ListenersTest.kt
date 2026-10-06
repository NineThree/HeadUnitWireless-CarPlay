package com.shilapi.xcertplay.airplay

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyIpv4ListenersTest {
    private val loopback = InetAddress.getByName("127.0.0.1")

    @Test fun screenListenerAcceptsAnIpv4Connection() {
        val stream = ScreenStream(ByteArray(32), bindAddress = loopback)
        try {
            val port = stream.listen(object : ScreenStream.Listener {})
            Socket(loopback, port).close()
            assertTrue(port > 0)
        } finally { stream.close() }
    }

    @Test fun audioAndTimingListenersBindWithoutAnIpv6Wildcard() {
        val stream = AudioStream(ByteArray(32), bindAddress = loopback)
        val timing = NtpClock(bindAddress = loopback)
        try {
            val ports = stream.listen(object : AudioStream.Listener {})
            val port = timing.listen()
            assertTrue(ports.first > 0 && ports.second > 0 && port > 0)
            val probe = DatagramSocket()
            try { probe.send(DatagramPacket(ByteArray(4), 4, loopback, port)) }
            finally { probe.close() }
        } finally { stream.close(); timing.close() }
    }

    @Test fun legacySocketCleanupClosesEachSocketDirectly() {
        val server = ServerSocket(0, 1, loopback)
        val client = Socket(loopback, server.localPort)
        val accepted = server.accept()
        val udp = DatagramSocket()
        safeClose(accepted); safeClose(client); safeClose(server); safeClose(udp)
        assertTrue(client.isClosed && accepted.isClosed && server.isClosed && udp.isClosed)
    }
}
