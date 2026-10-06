package com.shilapi.xcertplay

import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.network.CarPlayVpnService
import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class WirelessServiceOwnershipTest {
    @Test fun lateCleanupFromAnOldControllerCannotDetachItsReplacement() {
        val lifecycle = Robolectric.buildService(CarPlayVpnService::class.java).create()
        val service = lifecycle.get()
        val old = Any()
        val replacement = Any()
        val identity = AirPlayIdentity.generate()
        val config = AirPlayConfig("test", "00:11:22:33:44:55", "00:11:22:33:44:55", "test",
            AirPlayDisplayConfig(614, 360), port = 0)
        fun attach(owner: Any) = service.attachWireless(owner, InetAddress.getByName("127.0.0.1"),
            config, identity, PairingStore(), null, object : AirPlaySessionListener {}, object : AirPlayMediaHandler {})
        try {
            assertEquals(CarPlayVpnService.AttachResult.Started, attach(old))
            assertEquals(CarPlayVpnService.AttachResult.Started, attach(replacement))
            service.detachWireless(old)
            assertTrue(service.isAttached())
            service.detachWireless(replacement)
            assertFalse(service.isAttached())
        } finally { lifecycle.destroy() }
    }
}
