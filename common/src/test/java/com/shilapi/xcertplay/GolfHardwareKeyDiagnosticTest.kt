package com.shilapi.xcertplay

import android.view.KeyEvent
import java.io.File
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
class GolfHardwareKeyDiagnosticTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun keyTestDialogRecordsOemCodesInTheExportedReportWithoutSendingCommands() {
        val lifecycle = Robolectric.buildActivity(GolfWirelessActivity::class.java).create()
        try {
            val home = lifecycle.get()
            ReflectionHelpers.callInstanceMethod<Unit>(home, "chooseHardwareKeys")
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(dialog.isShowing)
            assertTrue(dialog.dispatchKeyEvent(KeyEvent(1000, 1000, KeyEvent.ACTION_DOWN, 353, 0, 0, 4, 42)))
            val report = ReflectionHelpers.callInstanceMethod<String>(home, "buildReport")
            assertTrue(report.contains("HardwareKey source=key-test code=353 scan=42"))
            assertFalse(report.contains("queued=true"))
            dialog.dismiss()
        } finally { lifecycle.destroy() }
    }

    @Test fun keyLogRotationCannotOverwriteConnectionHistory() {
        val folder = File(context.filesDir, "logs").apply { mkdirs() }
        val connectionHistory = File(folder, "previous.log").apply { writeText("Retained video/connection history\n") }
        File(folder, GolfHardwareKeyLog.FILE_NAME).writeText("HardwareKey " + "x".repeat(SessionLogFile.MAX_BYTES.toInt()))
        GolfHardwareKeyLog.record(context, "HardwareKey source=key-test code=87 scan=42")
        assertEquals("Retained video/connection history\n", connectionHistory.readText())
        assertTrue(File(folder, GolfHardwareKeyLog.PREVIOUS_FILE_NAME).isFile)
        assertTrue(File(folder, GolfHardwareKeyLog.FILE_NAME).length() < 1000)
    }
}
