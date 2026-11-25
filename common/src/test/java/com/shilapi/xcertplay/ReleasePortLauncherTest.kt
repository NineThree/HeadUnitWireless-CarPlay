package com.shilapi.xcertplay

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [22])
class ReleasePortLauncherTest {
    @Test fun launcherAcceptsInputWhileAuthenticationWaitsAndFailsClosedWithoutIdentity() {
        val lifecycle = Robolectric.buildActivity(GolfWirelessActivity::class.java)
        val activity = lifecycle.get()
        val worker = ReflectionHelpers.getField<ExecutorService>(activity, "connectionWorker")
        val release = CountDownLatch(1)
        val ready = CountDownLatch(1)
        worker.execute { ready.countDown(); release.await() }
        assertTrue(ready.await(2, TimeUnit.SECONDS))
        lifecycle.setup()
        try {
            val viewport = descendants(activity.window.decorView).filterIsInstance<ScrollView>().first()
            assertFalse(viewport.isSmoothScrollingEnabled)
            assertTrue(descendants(viewport).filterIsInstance<TextView>().any { it.text.toString().contains("正在检查") })
            val (ssid, password) = descendants(viewport).filterIsInstance<EditText>().toList()
            ssid.setText("Local test hotspot")
            password.setText("fixture-password")
            descendants(viewport).filterIsInstance<Button>().first { it.text == "保存热点" }.performClick()
            assertEquals("Local test hotspot", AirPlayPersistence.loadManualHotspotSsid(activity))

            descendants(viewport).filterIsInstance<Button>().first { it.text == "开始无线连接" }.performClick()
            assertNull(shadowOf(activity).nextStartedActivity)
            val completed = CountDownLatch(1)
            worker.execute(completed::countDown)
            release.countDown()
            assertTrue(completed.await(3, TimeUnit.SECONDS))
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(descendants(viewport).filterIsInstance<TextView>().any { it.text.toString().contains("初始化失败") })
            assertNull(shadowOf(activity).nextStartedActivity)
        } finally {
            release.countDown()
            lifecycle.pause().stop().destroy()
        }
    }

    private fun descendants(root: View): Sequence<View> = sequence {
        yield(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) yieldAll(descendants(root.getChildAt(index)))
        }
    }
}
