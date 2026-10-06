package com.shilapi.xcertplay

import android.app.AlertDialog
import android.os.Looper
import android.os.Build
import android.widget.RadioGroup
import android.view.View
import android.view.ViewGroup
import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import com.shilapi.xcertplay.airplay.AirPlayConfig
import com.shilapi.xcertplay.airplay.CarPlayDisplayScale
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class GolfNativeDisplayTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @After fun restoreSdk() { ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 29) }

    @Test fun nativePreferenceSurvivesReconnectAndKeepsThirtyFrames() {
        context.getSharedPreferences("golf_wireless_display", 0).edit().putString("quality", "native").apply()
        AirPlayPersistence.saveFps(context, 60) // An old preference must not create a 60fps option.
        repeat(2) { GolfWirelessProfile.apply(context) }
        val display = CarPlayDisplayScale.apply(AirPlayDisplayConfig(1024, 600,
            fps = AirPlayPersistence.loadFps(context)), AirPlayPersistence.loadDisplayScaleTenths(context))
        assertEquals(1024, display.widthPixels)
        assertEquals(600, display.heightPixels)
        assertEquals(30, display.fps)
        assertEquals("native", GolfDisplaySettings.quality(context).id)
    }

    @Test fun nativeChoiceCanBeSavedWithoutResettingHotspotOrMicrophone() {
        AirPlayPersistence.saveManualHotspotSsid(context, "Synthetic hotspot")
        GolfFeatureSettings.save(context, GolfFeatureSettings.Options(true, true, true))
        val lifecycle = Robolectric.buildActivity(GolfWirelessActivity::class.java).create()
        try {
            ReflectionHelpers.callInstanceMethod<Unit>(lifecycle.get(), "chooseDisplay")
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            val group = allViews(dialog.window!!.decorView).filterIsInstance<RadioGroup>().single()
            assertEquals(3, group.childCount)
            group.check(3)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("native", GolfDisplaySettings.quality(context).id)
            assertEquals(8, AirPlayPersistence.loadDisplayScaleTenths(context)) // Save applies to the next connection.
            GolfWirelessProfile.apply(context)
            assertEquals(10, AirPlayPersistence.loadDisplayScaleTenths(context))
            assertEquals("Synthetic hotspot", AirPlayPersistence.loadManualHotspotSsid(context))
            assertTrue(GolfFeatureSettings.load(context).microphone)
        } finally { lifecycle.destroy() }
    }

    @Test fun actualHostNegotiatesNativeCanvasAndTheAvailableTopAvoidanceAreaAtThirtyFps() {
        context.getSharedPreferences("golf_wireless_display", 0).edit().putString("quality", "native").apply()
        AirPlayPersistence.saveFps(context, 60)
        val host = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 17)
        ReflectionHelpers.setField(host, "airPlayIdentity", AirPlayPersistence.loadIdentity(context))
        ReflectionHelpers.callInstanceMethod<Unit>(host, "loadPersistedSettings")
        val sizeType = CarPlayHostActivity::class.java.declaredClasses.single { it.simpleName == "DisplaySize" }
        val constructor = sizeType.getDeclaredConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
        val method = CarPlayHostActivity::class.java.getDeclaredMethod("createAirPlayConfig", sizeType)
            .apply { isAccessible = true }
        for (height in listOf(600, 536)) {
            val config = method.invoke(host, constructor.newInstance(1024, height)) as AirPlayConfig
            assertEquals(1024, config.main.widthPixels)
            assertEquals(height, config.main.heightPixels)
            assertEquals(30, config.main.fps)
            assertFalse(config.hevc)
        }
    }

    private fun allViews(root: View): List<View> = listOf(root) +
        if (root is ViewGroup) (0 until root.childCount).flatMap { allViews(root.getChildAt(it)) } else emptyList()
}
