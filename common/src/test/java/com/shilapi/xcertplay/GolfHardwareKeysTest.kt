package com.shilapi.xcertplay

import android.content.Intent
import android.content.ComponentName
import android.media.AudioManager
import android.os.Build
import android.os.Looper
import android.view.KeyEvent
import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.orchestration.CarPlayController
import com.shilapi.xcertplay.orchestration.CarPlayRuntimeConfig
import com.shilapi.xcertplay.orchestration.MfiTarget
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowAudioManager
import org.robolectric.util.ReflectionHelpers

/** Real window/broadcast -> controller queue -> encrypted RTSP HID reports; no iPhone is simulated. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], shadows = [GolfHardwareKeysTest.RegistrationAudioManager::class])
class GolfHardwareKeysTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val fixtures = mutableListOf<Fixture>()
    private lateinit var originalBackend: Lazy<*>
    private lateinit var originalBackendValue: Any

    @Before fun selectAnIsolatedLegacyProcessBackend() {
        // Robolectric can reuse a sandbox that first initialized this singleton for SDK29.
        // A physical API17 process never changes SDK; give this test its own real legacy backend.
        originalBackend = ReflectionHelpers.getField(CarPlayMediaKeys, "backend\$delegate")
        originalBackendValue = originalBackend.value!!
        val type = Class.forName("com.shilapi.xcertplay.CarPlayMediaKeys\$LegacyBackend")
        val constructor = type.getDeclaredConstructor().apply { isAccessible = true }
        // Keep the final delegate intact; only replace its mutable cached value for this test.
        ReflectionHelpers.setField(originalBackend, "_value", constructor.newInstance())
    }

    @After fun cleanup() {
        fixtures.forEach { CarPlayMediaKeys.detach(it.controller); it.controller.close(); it.session.close() }
        if (::originalBackendValue.isInitialized) {
            ReflectionHelpers.setField(originalBackend, "_value", originalBackendValue)
        }
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 29)
    }

    @Test fun focusedWindowForwardsNextAndPreviousExactlyOncePerPress() {
        val f = fixture()
        for (code in listOf(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS)) {
            f.host.dispatchKeyEvent(key(code, KeyEvent.ACTION_DOWN))
            f.host.dispatchKeyEvent(key(code, KeyEvent.ACTION_DOWN, repeat = 1))
            f.host.dispatchKeyEvent(key(code, KeyEvent.ACTION_UP))
        }
        assertEquals(listOf(4, 0, 5, 0), f.reports(AirPlayHid.MEDIA_HID_UID))
    }

    @Test fun ownerLearnedOemKeysUseTheSameEncryptedMediaAndTelephonyRoutes() {
        val f = fixture()
        val next = KeyEvent(1000, 1000, KeyEvent.ACTION_DOWN, 239, 0, 0, 4, 563)
        val call = KeyEvent(3000, 3000, KeyEvent.ACTION_DOWN, 236, 0, 0, 4, 560)
        GolfKeyBindings.save(context, GolfKeyBindings.Action.NEXT, next)
        GolfKeyBindings.save(context, GolfKeyBindings.Action.PHONE, call)
        val token = CarPlayMediaKeys.beginKeyTest(context) {}
        CarPlayMediaKeys.endKeyTest(token) // Completing learning refreshes the active route.
        f.host.dispatchKeyEvent(next)
        receiver(next)
        f.host.dispatchKeyEvent(KeyEvent(1000, 1100, KeyEvent.ACTION_UP, 239, 0, 0, 4, 563))
        f.host.dispatchKeyEvent(call)
        f.host.dispatchKeyEvent(KeyEvent(3000, 3100, KeyEvent.ACTION_UP, 236, 0, 0, 4, 560))
        assertEquals(listOf(4, 0), f.reports(AirPlayHid.MEDIA_HID_UID))
        assertEquals(listOf(1, 0), f.reports(AirPlayHid.TELEPHONY_HID_UID))
    }

    @Test fun phoneShortPressAnswersAndLongPressDropsThroughTelephonyNotSiri() {
        val f = fixture()
        f.host.dispatchKeyEvent(key(KeyEvent.KEYCODE_CALL, KeyEvent.ACTION_DOWN))
        assertTrue(f.commands().isEmpty()) // Do not answer first when the owner intends to long-press.
        f.host.dispatchKeyEvent(key(KeyEvent.KEYCODE_CALL, KeyEvent.ACTION_UP, held = 100))
        f.host.dispatchKeyEvent(key(KeyEvent.KEYCODE_CALL, KeyEvent.ACTION_DOWN, base = 3000))
        f.host.dispatchKeyEvent(key(KeyEvent.KEYCODE_CALL, KeyEvent.ACTION_DOWN, held = 900, repeat = 1, base = 3000))
        f.host.dispatchKeyEvent(key(KeyEvent.KEYCODE_CALL, KeyEvent.ACTION_UP, held = 1000, base = 3000))
        assertEquals(listOf(1, 0, 3, 0), f.reports(AirPlayHid.TELEPHONY_HID_UID))
        assertFalse(f.commands().any { it["type"] == "requestSiri" })
    }

    @Test fun dedicatedEndCallKeyDropsOnceAndCancelledPhonePressDoesNothing() {
        val f = fixture()
        f.host.dispatchKeyEvent(key(KeyEvent.KEYCODE_ENDCALL, KeyEvent.ACTION_DOWN))
        f.host.dispatchKeyEvent(key(KeyEvent.KEYCODE_ENDCALL, KeyEvent.ACTION_DOWN, repeat = 1))
        f.host.dispatchKeyEvent(key(KeyEvent.KEYCODE_ENDCALL, KeyEvent.ACTION_UP))
        f.host.dispatchKeyEvent(key(KeyEvent.KEYCODE_CALL, KeyEvent.ACTION_DOWN, base = 3000))
        f.host.dispatchKeyEvent(key(KeyEvent.KEYCODE_CALL, KeyEvent.ACTION_UP, held = 100,
            base = 3000, flags = KeyEvent.FLAG_CANCELED))
        assertEquals(listOf(3, 0), f.reports(AirPlayHid.TELEPHONY_HID_UID))
    }

    @Test fun windowAndMediaBroadcastCopiesOfTheSameEventDoNotSkipTwoTracks() {
        val f = fixture()
        val down = key(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_DOWN)
        f.host.dispatchKeyEvent(down)
        receiver(down)
        receiver(key(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_UP))
        assertEquals(listOf(4, 0), f.reports(AirPlayHid.MEDIA_HID_UID))
    }

    @Test fun legacyReceiverIsReadyBeforeTheFirstMusicStream() {
        fixture()
        val backend = ReflectionHelpers.getField<Any>(CarPlayMediaKeys, "backend\$delegate")
            .let { (it as Lazy<*>).value!! }
        assertNotNull("Register on attach, including before music starts",
            ReflectionHelpers.getField<Any?>(backend, "receiver"))
    }

    @Test fun detachedMediaReceiverLeavesKeysToTheSystem() {
        val f = fixture()
        CarPlayMediaKeys.detach(f.controller)
        assertFalse(CarPlayMediaKeys.dispatch(key(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_DOWN)))
        assertTrue(f.commands().isEmpty())
    }

    @Test fun releasingAnOldControllerDoesNotDetachTheReplacement() {
        val old = fixture()
        val current = fixture()
        CarPlayMediaKeys.detach(old.controller)
        receiver(key(KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.ACTION_DOWN))
        assertEquals(listOf(5, 0), current.reports(AirPlayHid.MEDIA_HID_UID))
        assertTrue(old.commands().isEmpty())
    }

    @Test fun standardBroadcastWorksAndDoesNotRepeatForHeldKeys() {
        val f = fixture()
        receiver(key(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_DOWN))
        receiver(key(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_DOWN, repeat = 1))
        receiver(key(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_UP))
        assertEquals(listOf(4, 0), f.reports(AirPlayHid.MEDIA_HID_UID))
    }

    @Test fun volumeAndInstrumentPageKeysAreNotRemappedToCarPlay() {
        val f = fixture()
        listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT, 353).forEach { f.host.dispatchKeyEvent(key(it, KeyEvent.ACTION_DOWN)) }
        assertTrue(f.commands().isEmpty())
    }

    @Test fun voiceReleaseStillRequestsSiri() {
        val f = fixture()
        f.host.dispatchKeyEvent(key(KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.ACTION_DOWN))
        assertTrue(f.commands().isEmpty())
        f.host.dispatchKeyEvent(key(KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.ACTION_UP))
        assertEquals(listOf("requestSiri", "requestSiri"), f.commands().map { it["type"] })
    }

    @Test fun delayedBroadcastAfterWindowReleaseStillDoesNotSkipTwice() {
        val f = fixture()
        val down = key(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_DOWN)
        val up = key(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_UP, held = 100)
        f.host.dispatchKeyEvent(down)
        f.host.dispatchKeyEvent(up)
        receiver(down)
        receiver(up)
        assertEquals(listOf(4, 0), f.reports(AirPlayHid.MEDIA_HID_UID))
    }

    @Test fun anOldWindowCannotSendToTheReplacementPhone() {
        val old = fixture()
        val current = fixture()
        old.host.dispatchKeyEvent(key(KeyEvent.KEYCODE_CALL, KeyEvent.ACTION_DOWN))
        old.host.dispatchKeyEvent(key(KeyEvent.KEYCODE_CALL, KeyEvent.ACTION_UP, held = 100))
        assertTrue(current.commands().isEmpty())
    }

    @Test fun cancelledVoiceReleaseDoesNotOpenSiri() {
        val f = fixture()
        f.host.dispatchKeyEvent(key(KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.ACTION_DOWN))
        f.host.dispatchKeyEvent(key(KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.ACTION_UP, flags = KeyEvent.FLAG_CANCELED))
        assertTrue(f.commands().isEmpty())
    }

    @Test fun orphanPhoneReleaseCannotAnswerACall() {
        val f = fixture()
        f.host.dispatchKeyEvent(key(KeyEvent.KEYCODE_CALL, KeyEvent.ACTION_UP, held = 100))
        assertTrue(f.commands().isEmpty())
    }

    @Test fun androidEarlyLongPressFlagDoesNotTurnAShortPhonePressIntoHangup() {
        val f = fixture()
        f.host.dispatchKeyEvent(key(KeyEvent.KEYCODE_CALL, KeyEvent.ACTION_DOWN))
        f.host.dispatchKeyEvent(key(KeyEvent.KEYCODE_CALL, KeyEvent.ACTION_DOWN, held = 500,
            repeat = 1, flags = KeyEvent.FLAG_LONG_PRESS))
        f.host.dispatchKeyEvent(key(KeyEvent.KEYCODE_CALL, KeyEvent.ACTION_UP, held = 600))
        assertEquals(listOf(1, 0), f.reports(AirPlayHid.TELEPHONY_HID_UID))
    }

    @Test fun diagnosticDialogCapturesBackgroundBroadcastsWithoutSendingCommands() {
        val f = fixture()
        val home = Robolectric.buildActivity(GolfWirelessActivity::class.java).create()
        try {
            ReflectionHelpers.callInstanceMethod<Unit>(home.get(), "chooseHardwareKeys")
            receiver(key(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_DOWN))
            receiver(key(KeyEvent.KEYCODE_CALL, KeyEvent.ACTION_DOWN))
            receiver(key(KeyEvent.KEYCODE_CALL, KeyEvent.ACTION_UP, held = 100))
            assertTrue("The diagnostic must not send commands from the broadcast path", f.commands().isEmpty())
            ShadowAlertDialog.getLatestAlertDialog().dismiss()
            shadowOf(Looper.getMainLooper()).idle()
            receiver(key(KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.ACTION_DOWN, base = 3000))
            assertEquals(listOf(5, 0), f.reports(AirPlayHid.MEDIA_HID_UID))
        } finally { home.destroy() }
    }

    @Test fun iphonePlaybackReclaimsTheLegacyMediaReceiverEvenWithFocusHeld() {
        val f = fixture()
        val original = RegistrationAudioManager.registrations
        CarPlayMediaKeys.onIphonePlaying(true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(original + 1, RegistrationAudioManager.registrations)
        f.controller.playbackListener?.invoke(true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(original + 2, RegistrationAudioManager.registrations)
    }

    @Test fun physicalMediaSessionEventUsesTheSameDedupAndDiagnosticRoute() {
        val f = fixture()
        val callback = CarPlayMediaCallback(
            hardwareEvent = { CarPlayMediaKeys.dispatch(it, "media-session") },
        ) { index, _ -> f.controller.sendMediaButton(index) }
        val event = key(KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.ACTION_DOWN)
        f.host.dispatchKeyEvent(event)
        assertTrue(callback.onMediaButtonEvent(Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT, event)))
        assertEquals(listOf(4, 0), f.reports(AirPlayHid.MEDIA_HID_UID))
    }

    @Implements(AudioManager::class)
    class RegistrationAudioManager : ShadowAudioManager() {
        @Implementation protected fun registerMediaButtonEventReceiver(component: ComponentName) {
            assertEquals(CarPlayMediaButtonReceiver::class.java.name, component.className)
            registrations += 1
        }
        companion object { var registrations = 0 }
    }

    private fun receiver(event: KeyEvent) = CarPlayMediaButtonReceiver().onReceive(context,
        Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT, event))

    private fun key(code: Int, action: Int, held: Long = 0, repeat: Int = 0, base: Long = 1000,
        flags: Int = 0) = KeyEvent(base, base + held, action, code, repeat, 0, 4, 42, flags)

    private fun fixture(): Fixture {
        val host = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        // Robolectric supplies SDK29 framework objects, while the app selects its API17 backend.
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 17)
        val config = AirPlayConfig("Synthetic Golf", "00:11:22:33:44:55", "00:11:22:33:44:55", "1",
            AirPlayDisplayConfig(1024, 600, fps = 30))
        val identity = AirPlayIdentity.generate()
        val pairings = PairingStore()
        val listener = object : AirPlaySessionListener {}
        val media = object : AirPlayMediaHandler {}
        val controller = CarPlayController(context, CarPlayRuntimeConfig(mfiTarget = MfiTarget.LOCAL,
            identification = Iap2IdentificationConfig("Golf", "Synthetic", "Golf", "SYNTHETIC", "1", "1", 0)),
            config, identity, pairings, listener, media, {})
        val wire = ByteArrayOutputStream()
        val loopback = InetAddress.getByName("127.0.0.1")
        val socket = object : Socket() {
            override fun getLocalAddress() = loopback
            override fun getInetAddress() = loopback
            override fun getOutputStream() = wire
        }
        val secret = ByteArray(32) { 8 }
        val session = AirPlaySession(socket, config, identity, pairings, null, listener, media)
        ReflectionHelpers.setField(session, "eventSocket", socket)
        ReflectionHelpers.setField(session, "eventCipher", ControlCipher(secret, secret))
        ReflectionHelpers.setField(controller, "activeSession", session)
        ReflectionHelpers.setField(host, "controller", controller)
        CarPlayMediaKeys.attach(context, controller)
        return Fixture(host, controller, session, wire, secret).also { fixtures += it }
    }

    private class Fixture(val host: CarPlayHostActivity, val controller: CarPlayController,
        val session: AirPlaySession, val wire: ByteArrayOutputStream, val secret: ByteArray) {
        fun commands(): List<Map<*, *>> {
            ReflectionHelpers.getField<ExecutorService>(controller, "touchExecutor").submit {}.get(2, TimeUnit.SECONDS)
            val decrypted = ControlCipher(secret, secret).decrypt(wire.toByteArray())
            assertEquals(0, decrypted.rest.size)
            val parsed = RtspMessage.parseMessages(decrypted.data)
            assertEquals(0, parsed.rest.size)
            return parsed.messages.map { BplistCodec.decode(it.body) as Map<*, *> }
        }
        fun reports(uid: Int) = commands().filter { it["uuid"] == uid.toString(16) }
            .map { (it["hidReport"] as ByteArray)[0].toInt() }
    }
}
