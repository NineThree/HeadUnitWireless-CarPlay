package com.shilapi.xcertplay

import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import org.junit.Assert.*
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
class GolfFeatureDialogTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Test fun cancelKeepsFeatureDefaultsAndRecoveryEnabled() {
        val lifecycle = Robolectric.buildActivity(GolfWirelessActivity::class.java).create()
        try {
            ReflectionHelpers.callInstanceMethod<Unit>(lifecycle.get(), "chooseFeatures")
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            val checks = views(dialog.window!!.decorView).filterIsInstance<CheckBox>()
            assertEquals(4, checks.size); assertTrue(checks.take(3).none { it.isChecked })
            assertTrue(checks[3].isChecked)
            checks.forEach { it.isChecked = !it.isChecked }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(GolfFeatureSettings.Options(), GolfFeatureSettings.load(context))
        } finally { lifecycle.destroy() }
    }
    @Test fun hotspotRecoveryCanBeDisabledWithoutChangingOtherOptions() {
        GolfFeatureSettings.save(context, GolfFeatureSettings.Options(true, true, true, true))
        val lifecycle = Robolectric.buildActivity(GolfWirelessActivity::class.java).create()
        try {
            ReflectionHelpers.callInstanceMethod<Unit>(lifecycle.get(), "chooseFeatures")
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            val checks = views(dialog.window!!.decorView).filterIsInstance<CheckBox>()
            checks[3].isChecked = false
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(GolfFeatureSettings.Options(true, true, true, false), GolfFeatureSettings.load(context))
        } finally { lifecycle.destroy() }
    }
    @Test fun bootAndMicrophoneCanBeSavedWithoutEnablingAutomaticConnectionOrChangingQuality() {
        GolfDisplaySettings.save(context, GolfDisplaySettings.Quality.SMOOTH, true)
        val lifecycle = Robolectric.buildActivity(GolfWirelessActivity::class.java).create()
        try {
            ReflectionHelpers.callInstanceMethod<Unit>(lifecycle.get(), "chooseFeatures")
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            val checks = views(dialog.window!!.decorView).filterIsInstance<CheckBox>()
            checks[0].isChecked = true; checks[2].isChecked = true
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(GolfFeatureSettings.Options(true,false,true), GolfFeatureSettings.load(context))
            assertTrue(AirPlayPersistence.loadAutoStartOnBoot(context))
            assertFalse(DiPlayPreferences.autoConnect(context))
            assertEquals(GolfDisplaySettings.Quality.SMOOTH, GolfDisplaySettings.quality(context))
            assertTrue(GolfDisplaySettings.avoidTopBar(context))
        } finally { lifecycle.destroy() }
    }
    private fun views(root: View): List<View> = listOf(root) +
        if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
}
