package com.shilapi.xcertplay.media

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyAvcDecoderSelectionTest {
    @Test fun excludesTheFailedWirelessDisplayComponentAndPrefersTheNormalVendor() {
        assertEquals(listOf("OMX.mtk.video.decoder.avc", "OMX.google.h264.decoder"),
            legacyAvcDecoderNames(listOf("OMX.google.h264.decoder", "OMX.mtkwfd.video.decoder.avc",
                "OMX.mtk.video.decoder.avc")))
    }
    @Test fun aSoftwareOnlyDeviceStillHasACandidate() {
        assertEquals(listOf("OMX.google.h264.decoder"), legacyAvcDecoderNames(listOf("OMX.google.h264.decoder")))
        assertEquals(emptyList<String>(), legacyAvcDecoderNames(listOf("OMX.mtkwfd.video.decoder.avc")))
    }
    @Test fun mediaTekDecoderWinsAndSpecializedVariantsAreExcluded() {
        val names = listOf("OMX.other.avc", "OMX.MTK.VIDEO.DECODER.AVC.secure", "OMX.MTK.avc.tunnel",
            "OMX.google.h264.decoder", "OMX.MTK.VIDEO.DECODER.AVC")
        assertEquals(listOf("OMX.MTK.VIDEO.DECODER.AVC", "OMX.other.avc", "OMX.google.h264.decoder"),
            legacyAvcDecoderNames(names))
    }
}
