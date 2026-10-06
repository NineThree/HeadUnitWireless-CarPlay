package com.shilapi.xcertplay

import android.app.AlertDialog
import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class GolfKeyLearningDialogTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun signal(code: Int = 239, scan: Int = 563) = KeyEvent(1000,1100,KeyEvent.ACTION_DOWN,code,0,0,4,scan)
    private fun show(activity: GolfWirelessActivity): AlertDialog {
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "learnKey",
            ReflectionHelpers.ClassParameter.from(GolfKeyBindings.Action::class.java, GolfKeyBindings.Action.NEXT))
        return ShadowAlertDialog.getLatestAlertDialog()
    }
    @Test fun candidateIsNotSavedUntilOwnerClicksSave() {
        val lifecycle = Robolectric.buildActivity(GolfWirelessActivity::class.java).create()
        try {
            val dialog = show(lifecycle.get())
            assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            assertTrue(CarPlayMediaKeys.dispatch(signal(), "learning-test"))
            assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            assertTrue(GolfKeyBindings.load(context).isEmpty())
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertEquals(KeyEvent.KEYCODE_MEDIA_NEXT, GolfKeyBindings.logicalKey(context, signal()))
            assertFalse(dialog.isShowing)
        } finally { lifecycle.destroy() }
    }
    @Test fun protectedKeyAfterCandidateDisablesSaveAndCancelKeepsNoBinding() {
        val lifecycle = Robolectric.buildActivity(GolfWirelessActivity::class.java).create()
        try {
            val dialog = show(lifecycle.get())
            CarPlayMediaKeys.dispatch(signal(), "learning-test")
            CarPlayMediaKeys.dispatch(signal(KeyEvent.KEYCODE_VOLUME_UP,115), "learning-test")
            assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            assertTrue(GolfKeyBindings.load(context).isEmpty())
        } finally { lifecycle.destroy() }
    }
}
