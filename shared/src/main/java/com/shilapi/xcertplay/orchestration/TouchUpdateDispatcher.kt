package com.shilapi.xcertplay.orchestration

import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/** Share one queued task for consecutive moves while preserving down, up and cancel ordering. */
internal class TouchUpdateDispatcher(private val executor: Executor) {
    private class Move(var send: () -> Unit)

    private var pendingMove: Move? = null

    @Synchronized
    fun submit(coalesce: Boolean, send: () -> Unit): Boolean {
        val previous = pendingMove
        if (coalesce && previous != null) {
            previous.send = send
            return true
        }
        val move = if (coalesce) Move(send) else null
        pendingMove = move
        return try {
            executor.execute {
                if (move == null) send()
                else {
                    val latest = synchronized(this) {
                        if (pendingMove === move) pendingMove = null
                        move.send
                    }
                    latest()
                }
            }
            true
        } catch (_: RejectedExecutionException) {
            pendingMove = null
            false
        }
    }

    @Synchronized
    fun clear() { pendingMove = null }
}
