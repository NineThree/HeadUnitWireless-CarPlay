package com.shilapi.xcertplay.media

import android.view.MotionEvent
import com.shilapi.xcertplay.airplay.AirPlayContact

/** Converts Android MotionEvents into normalized CarPlay touch contacts. */
class CarPlayTouchMapper {
    private val pointerForSlot = IntArray(2) { -1 }

    fun reset() = pointerForSlot.fill(-1)

    fun contacts(event: MotionEvent, viewWidth: Int, viewHeight: Int): List<AirPlayContact> {
        val width = viewWidth.coerceAtLeast(1)
        val height = viewHeight.coerceAtLeast(1)
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) reset()
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            val slot = pointerForSlot.indexOf(-1)
            if (slot >= 0) pointerForSlot[slot] = event.getPointerId(event.actionIndex)
        }

        val contacts = ArrayList<AirPlayContact>(pointerForSlot.size)
        for (slot in pointerForSlot.indices) {
            val pointer = pointerForSlot[slot]
            if (pointer < 0) continue
            val index = event.findPointerIndex(pointer)
            if (index < 0) continue
            val up = action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP ||
                (action == MotionEvent.ACTION_POINTER_UP && index == event.actionIndex)
            // The upstream HID serializer maps the list position to the report slot.
            if (slot == 1 && contacts.isEmpty()) contacts.add(AirPlayContact(0, 0.0, 0.0, down = false))
            contacts.add(
                AirPlayContact(
                    id = slot,
                    x = (event.getX(index).toDouble() / width).coerceIn(0.0, 1.0),
                    y = (event.getY(index).toDouble() / height).coerceIn(0.0, 1.0),
                    down = !up,
                ),
            )
            if (up) pointerForSlot[slot] = -1
        }
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) reset()
        return contacts
    }
}
