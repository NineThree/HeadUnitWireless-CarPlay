package com.shilapi.xcertplay

import android.view.InputDevice
import android.view.MotionEvent
import com.shilapi.xcertplay.airplay.AirPlayHid
import com.shilapi.xcertplay.media.CarPlayTouchMapper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [22], manifest = Config.NONE)
class CarPlayTouchMapperTest {
    @Test fun liftingTheFirstFingerKeepsTheSecondFingerInTheSameCarPlaySlot() {
        val mapper = CarPlayTouchMapper()
        val first = Finger(4, -12f, 0f)
        val second = Finger(7, 400f, 150f)
        val down = mapper.map(MotionEvent.ACTION_DOWN, first).single()
        assertEquals(0, down.id)
        assertEquals(0.0, down.x, 0.0)
        val secondDown = MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        assertEquals(listOf(0, 1), mapper.map(secondDown, first, second).map { it.id })

        val lifted = mapper.map(MotionEvent.ACTION_POINTER_UP, first, second)
        assertFalse(lifted[0].down)
        assertTrue(lifted[1].down)
        val movingContacts = mapper.map(MotionEvent.ACTION_MOVE, second)
        assertFalse(movingContacts[0].down)
        val move = movingContacts[1]
        assertTrue(move.down)
        assertEquals(1, move.id)
        assertEquals(0.5, move.x, 0.0)
        assertEquals(0.25, move.y, 0.0)
        val report = AirPlayHid.touchReport(movingContacts.map { it.copy(x = it.x * 800, y = it.y * 600) })
        assertEquals(0, report[1].toInt())
        assertEquals(1, report[7].toInt())
        assertEquals(400, (report[8].toInt() and 255) + (report[9].toInt() shl 8))

        assertTrue(mapper.map(MotionEvent.ACTION_CANCEL, second).none { it.down })
        assertEquals(0, mapper.map(MotionEvent.ACTION_DOWN, second).single().id)
    }

    private data class Finger(val id: Int, val x: Float, val y: Float)

    private fun CarPlayTouchMapper.map(action: Int, vararg fingers: Finger) =
        MotionEvent.obtain(
            1L, 2L, action, fingers.size,
            fingers.map { finger -> MotionEvent.PointerProperties().apply {
                id = finger.id
                toolType = MotionEvent.TOOL_TYPE_FINGER
            } }.toTypedArray(),
            fingers.map { finger -> MotionEvent.PointerCoords().apply {
                x = finger.x
                y = finger.y
                pressure = 1f
                size = 1f
            } }.toTypedArray(),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
        ).let { event ->
            try {
                contacts(event, 800, 600)
            } finally {
                event.recycle()
            }
        }
}
