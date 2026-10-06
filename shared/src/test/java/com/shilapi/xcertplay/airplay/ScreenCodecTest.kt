package com.shilapi.xcertplay.airplay

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ScreenCodecTest {
    @Test fun ownedFrameCanConvertInPlaceButMalformedInputCannotBePartiallyModified() {
        val frame = byteArrayOf(0, 0, 0, 2, 0x65, 1, 0, 0, 0, 1, 0x41)
        assertSame(frame, ScreenCodec.lengthPrefixedToAnnexB(frame))
        assertArrayEquals(byteArrayOf(0,0,0,1,0x65,1,0,0,0,1,0x41), frame)
        val malformed = byteArrayOf(0,0,0,2,0x65,1,0,0,0,4,0x41)
        val original = malformed.copyOf()
        assertSame(malformed, ScreenCodec.lengthPrefixedToAnnexB(malformed))
        assertArrayEquals(original, malformed)
    }
    @Test fun oversizedUnsignedLengthCannotOverflowTheBoundsCheck() {
        val malformed = byteArrayOf(0,0,0,2,0x65,1,0x7f,0xff.toByte(),0xff.toByte(),0xff.toByte())
        val original = malformed.copyOf()
        assertSame(malformed, ScreenCodec.lengthPrefixedToAnnexB(malformed))
        assertArrayEquals(original, malformed)
    }
    @Test
    fun validLengthPrefixesBecomeAnnexBInPlace() {
        val first = byteArrayOf(0x40, 0x01)
        val second = byteArrayOf(0x42, 0x01, 0x02)
        val payload =
            byteArrayOf(0, 0, 0, first.size.toByte()) + first +
                byteArrayOf(0, 0, 0, second.size.toByte()) + second

        val converted = ScreenCodec.lengthPrefixedToAnnexB(payload)

        assertSame(payload, converted)
        assertArrayEquals(
            byteArrayOf(0, 0, 0, 1) + first +
                byteArrayOf(0, 0, 0, 1) + second,
            converted,
        )
    }

    @Test
    fun malformedLengthsLeavePayloadUntouched() {
        val payload = byteArrayOf(0, 0, 0, 5, 0x40, 0x01)
        val original = payload.copyOf()

        assertSame(payload, ScreenCodec.lengthPrefixedToAnnexB(payload))
        assertArrayEquals(original, payload)
    }
}
