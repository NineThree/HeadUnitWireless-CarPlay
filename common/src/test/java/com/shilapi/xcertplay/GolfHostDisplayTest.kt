package com.shilapi.xcertplay

import android.os.Build
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/** Android objects use SDK29; only the app's legacy fullscreen branch is selected. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class GolfHostDisplayTest {
    @After fun restoreSdk() { ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 29) }

    @Test fun api17RequestsFullscreenWithoutConsumingTheFirstTouchToHideNavigation() {
        val host = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 17)
        ReflectionHelpers.callInstanceMethod<Unit>(host, "applyFullscreenMode")
        assertTrue(host.window.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN != 0)
        assertEquals(0, host.window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        assertEquals(0, host.window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
    }

    @Test fun fullScreenVideoDoesNotRequestTransparentComposition() {
        val host = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        val root = ReflectionHelpers.callInstanceMethod<FrameLayout>(host, "buildContentView")
        assertTrue((root.getChildAt(0) as TextureView).isOpaque)
    }

    @Test fun showingBarsAgainClearsOnlyTheFullscreenFlagsOnApi17() {
        val host = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 17)
        host.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        host.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE
        ReflectionHelpers.callInstanceMethod<Unit>(host, "applyFullscreenMode")
        ReflectionHelpers.setField(host, "hideTopBar", false)
        ReflectionHelpers.setField(host, "hideBottomBar", false)
        ReflectionHelpers.callInstanceMethod<Unit>(host, "applyFullscreenMode")
        assertEquals(0, host.window.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN)
        assertTrue(host.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
        assertEquals(View.SYSTEM_UI_FLAG_LOW_PROFILE, host.window.decorView.systemUiVisibility)
    }

    @Test fun api19CanHideNavigationWithImmersiveTouchSupport() {
        val host = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 19)
        ReflectionHelpers.callInstanceMethod<Unit>(host, "applyFullscreenMode")
        assertTrue(host.window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION != 0)
        assertTrue(host.window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY != 0)
    }

    @Test fun modernAndroidRetainsItsInsetsFullscreenPath() {
        val host = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        ReflectionHelpers.callInstanceMethod<Unit>(host, "applyFullscreenMode")
        assertTrue(host.window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0)
        assertTrue(host.window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION != 0)
    }

    @Test fun topBarAvoidanceGivesVideoAndTouchTheSameUnobstructedArea() {
        RuntimeEnvironment.getApplication().getSharedPreferences("golf_wireless_display", 0)
            .edit().putBoolean("avoid_top_bar", true).apply()
        val host = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        val root = ReflectionHelpers.callInstanceMethod<FrameLayout>(host, "buildContentView")
        val expectedTop = (64 * host.resources.displayMetrics.density).toInt()
        root.measure(View.MeasureSpec.makeMeasureSpec(1024, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1024, 600)
        val video = root.getChildAt(0)
        val touch = root.getChildAt(1)
        assertEquals(expectedTop, video.top)
        assertEquals(video.top, touch.top)
        assertEquals(video.bottom, touch.bottom)
        assertEquals(600 - expectedTop, video.height)
    }
}
