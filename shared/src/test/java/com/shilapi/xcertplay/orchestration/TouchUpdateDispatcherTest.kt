package com.shilapi.xcertplay.orchestration

import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchUpdateDispatcherTest {
    @Test fun burstKeepsLatestMoveWithoutReorderingContactTransitions() {
        val pending = ArrayDeque<Runnable>()
        val dispatcher = TouchUpdateDispatcher(Executor(pending::addLast))
        val delivered = mutableListOf<String>()
        dispatcher.submit(false) { delivered.add("down") }
        repeat(100) { id -> dispatcher.submit(true) { delivered.add("move $id") } }
        dispatcher.submit(false) { delivered.add("second down") }
        repeat(100) { id -> dispatcher.submit(true) { delivered.add("pinch $id") } }
        dispatcher.submit(false) { delivered.add("cancel") }

        assertEquals(5, pending.size)
        while (pending.isNotEmpty()) pending.removeFirst().run()
        assertEquals(listOf("down", "move 99", "second down", "pinch 99", "cancel"), delivered)
    }

    @Test fun failureToScheduleDoesNotKeepASlotForLaterMoves() {
        var rejected = true
        val dispatcher = TouchUpdateDispatcher(Executor { if (rejected) throw RejectedExecutionException() else it.run() })
        var delivered = 0
        assertFalse(dispatcher.submit(true) { delivered++ })
        rejected = false
        assertTrue(dispatcher.submit(true) { delivered++ })
        assertEquals(1, delivered)
    }
}
