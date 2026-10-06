package com.shilapi.xcertplay

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class GolfKeyBindingsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun event(code: Int = 239, scan: Int = 563, action: Int = KeyEvent.ACTION_DOWN) =
        KeyEvent(1000, 1100, action, code, 0, 0, 4, scan)

    @Test fun observedOemCodesAreUnmappedUntilOwnerSavesThem() {
        assertNull(GolfKeyBindings.logicalKey(context, event()))
        GolfKeyBindings.save(context, GolfKeyBindings.Action.NEXT, event())
        assertEquals(KeyEvent.KEYCODE_MEDIA_NEXT, GolfKeyBindings.logicalKey(context, event()))
        assertEquals(KeyEvent.KEYCODE_MEDIA_NEXT,
            GolfKeyBindings.logicalKey(context, event(action = KeyEvent.ACTION_UP)))
        assertNull(GolfKeyBindings.logicalKey(context, event(scan = 560)))
        assertNull(GolfKeyBindings.logicalKey(context, event(code = 236)))
    }
    @Test fun bindingsPersistSeparatelyAndClearingDoesNotEraseConnectionOrMicrophone() {
        AirPlayPersistence.saveManualHotspotSsid(context, "Synthetic AP")
        GolfFeatureSettings.save(context, GolfFeatureSettings.Options(false, false, true))
        GolfKeyBindings.save(context, GolfKeyBindings.Action.NEXT, event())
        GolfKeyBindings.save(context, GolfKeyBindings.Action.PHONE, event(code = 236, scan = 560))
        assertEquals(2, GolfKeyBindings.load(context).size)
        GolfKeyBindings.clear(context)
        assertTrue(GolfKeyBindings.load(context).isEmpty())
        assertEquals("Synthetic AP", AirPlayPersistence.loadManualHotspotSsid(context))
        assertTrue(GolfFeatureSettings.load(context).microphone)
    }
    @Test fun onePhysicalSignalCannotExecuteTwoDifferentActions() {
        GolfKeyBindings.save(context, GolfKeyBindings.Action.NEXT, event())
        assertThrows(IllegalArgumentException::class.java) {
            GolfKeyBindings.save(context, GolfKeyBindings.Action.PHONE, event())
        }
        assertEquals(KeyEvent.KEYCODE_MEDIA_NEXT, GolfKeyBindings.logicalKey(context, event()))
    }
    @Test fun navigationVolumePowerAndTypedCharactersCannotBeCaptured() {
        for (code in listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_ENTER)) {
            assertFalse("code=$code", GolfKeyBindings.canLearn(event(code)))
        }
        assertTrue(GolfKeyBindings.canLearn(event()))
        assertFalse(GolfKeyBindings.canLearn(event(action = KeyEvent.ACTION_UP)))
    }
    @Test fun malformedPreferenceCannotCreateAMappingOrCrash() {
        context.getSharedPreferences("golf_wireless_keys", 0).edit()
            .putString("next", "bad:999")
            .putString("phone", "4:158")
            .putString("previous", "239:-1").apply()
        assertTrue(GolfKeyBindings.load(context).isEmpty())
    }
}
