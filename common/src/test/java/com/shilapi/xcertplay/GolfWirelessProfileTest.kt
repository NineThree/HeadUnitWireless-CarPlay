package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class GolfWirelessProfileTest {
    @Test fun measuredHeadUnitUsesTheManualWirelessProfile() {
        assertTrue(GolfWirelessProfile.appliesTo(17))
        assertTrue(GolfWirelessProfile.appliesTo(19))
        assertFalse(GolfWirelessProfile.appliesTo(16))
        assertEquals(8, GolfWirelessProfile.displayScaleTenths)
        assertEquals(30, GolfWirelessProfile.frameRate)
        assertFalse(GolfWirelessProfile.microphoneEnabled)
        assertFalse(GolfWirelessProfile.hevcEnabled)
    }
}
