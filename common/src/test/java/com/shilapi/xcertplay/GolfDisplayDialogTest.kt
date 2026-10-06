package com.shilapi.xcertplay

import android.app.AlertDialog
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.RadioGroup
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
class GolfDisplayDialogTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun cancellingDisplayEditsLeavesTheSavedSettingsIntact() {
        GolfDisplaySettings.save(context, GolfDisplaySettings.Quality.SMOOTH, false)
        val lifecycle = Robolectric.buildActivity(GolfWirelessActivity::class.java).create()
        try {
            ReflectionHelpers.callInstanceMethod<Unit>(lifecycle.get(), "chooseDisplay")
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            find<RadioGroup>(dialog.window!!.decorView)!!.check(1)
            find<CheckBox>(dialog.window!!.decorView)!!.isChecked = true
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(GolfDisplaySettings.Quality.SMOOTH, GolfDisplaySettings.quality(context))
            assertFalse(GolfDisplaySettings.avoidTopBar(context))
        } finally { lifecycle.destroy() }
    }

    @Test fun savingDisplayEditsChangesTheNextProfileWithoutChangingTheCurrentOne() {
        GolfDisplaySettings.save(context, GolfDisplaySettings.Quality.SMOOTH, false)
        val lifecycle = Robolectric.buildActivity(GolfWirelessActivity::class.java).create()
        try {
            assertEquals(6, AirPlayPersistence.loadDisplayScaleTenths(context))
            ReflectionHelpers.callInstanceMethod<Unit>(lifecycle.get(), "chooseDisplay")
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            find<RadioGroup>(dialog.window!!.decorView)!!.check(1)
            find<CheckBox>(dialog.window!!.decorView)!!.isChecked = true
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(GolfDisplaySettings.Quality.BALANCED, GolfDisplaySettings.quality(context))
            assertTrue(GolfDisplaySettings.avoidTopBar(context))
            assertEquals(6, AirPlayPersistence.loadDisplayScaleTenths(context))
            GolfWirelessProfile.apply(context)
            assertEquals(8, AirPlayPersistence.loadDisplayScaleTenths(context))
        } finally { lifecycle.destroy() }
    }

    private inline fun <reified T : View> find(root: View): T? = allViews(root).filterIsInstance<T>().firstOrNull()
    private fun allViews(root: View): List<View> = listOf(root) +
        if (root is ViewGroup) (0 until root.childCount).flatMap { allViews(root.getChildAt(it)) } else emptyList()
}
