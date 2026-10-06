package com.shilapi.xcertplay

import android.os.Build
import com.shilapi.xcertplay.airplay.AirPlayConfig
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class UniversalDisplayTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @After fun restoreSdk() { ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 29) }

    @Test fun nativeSizeUsesEachHeadUnitsActualCanvasAtThirtyFrames() {
        GolfDisplaySettings.save(context, GolfDisplaySettings.Quality.NATIVE, false)
        val host = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 17)
        ReflectionHelpers.setField(host, "airPlayIdentity", AirPlayPersistence.loadIdentity(context))
        ReflectionHelpers.callInstanceMethod<Unit>(host, "loadPersistedSettings")
        val type = CarPlayHostActivity::class.java.declaredClasses.single { it.simpleName == "DisplaySize" }
        val constructor = type.getDeclaredConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
        val method = CarPlayHostActivity::class.java.getDeclaredMethod("createAirPlayConfig", type)
            .apply { isAccessible = true }
        for ((width, height) in listOf(800 to 480, 1024 to 600, 1280 to 720)) {
            val config = method.invoke(host, constructor.newInstance(width, height)) as AirPlayConfig
            assertEquals(width, config.main.widthPixels)
            assertEquals(height, config.main.heightPixels)
            assertEquals(30, config.main.fps)
            assertFalse(config.hevc)
        }
    }

    @Test fun genericQualityLabelsDoNotPinOtherScreensToTheGolfCanvas() {
        assertTrue(GolfDisplaySettings.labelFor(GolfDisplaySettings.Quality.NATIVE,800,480).contains("800×480"))
        assertTrue(GolfDisplaySettings.labelFor(GolfDisplaySettings.Quality.BALANCED,1280,720).contains("1024×576"))
        assertTrue(GolfDisplaySettings.labelFor(GolfDisplaySettings.Quality.SMOOTH,1024,600).contains("614×360"))
    }
}
