package com.shilapi.xcertplay

import android.Manifest
import android.os.Build
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class GolfMicrophonePermissionTest {
    private val app get() = RuntimeEnvironment.getApplication()
    @After fun restoreSdk() { ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 29) }
    private fun initialize() = Robolectric.buildActivity(CarPlayHostActivity::class.java).get().also {
        ReflectionHelpers.callInstanceMethod<Unit>(it, "initializeMicrophonePreference")
    }
    @Test fun microphoneStaysOffWithoutAnExplicitOwnerChoice() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val host = initialize()
        assertFalse(ReflectionHelpers.getField(host,"microphoneAvailable"))
        assertTrue(ReflectionHelpers.getField(host,"microphonePermissionResolved"))
    }
    @Test fun api17UsesTheInstallPermissionWithoutRequestingAnUnsupportedRuntimePrompt() {
        GolfFeatureSettings.save(app,GolfFeatureSettings.Options(microphone=true))
        ReflectionHelpers.setStaticField(Build.VERSION::class.java,"SDK_INT",17)
        val host = initialize()
        assertTrue(ReflectionHelpers.getField(host,"microphoneAvailable"))
        assertTrue(ReflectionHelpers.getField(host,"microphonePermissionResolved"))
    }
    @Test fun modernAndroidWaitsForPermissionAndEnablesInputOnlyAfterGrant() {
        GolfFeatureSettings.save(app,GolfFeatureSettings.Options(microphone=true))
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        val denied = initialize()
        assertFalse(ReflectionHelpers.getField(denied,"microphoneAvailable"))
        assertFalse(ReflectionHelpers.getField(denied,"microphonePermissionResolved"))
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val granted = initialize()
        assertTrue(ReflectionHelpers.getField(granted,"microphoneAvailable"))
        assertTrue(ReflectionHelpers.getField(granted,"microphonePermissionResolved"))
    }
}
