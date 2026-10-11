package org.opentaint.dataflow.bidi.engine

import java.util.PriorityQueue

/** Runner-owned queue: actor state is read once when each immutable priority key is made. */
class EventQueue {
    private class Entry(val event: RunEvent, val rank: Int, val steps: Long, val sequence: Long)
    private val heap = PriorityQueue(compareBy<Entry>({ it.rank }, { it.steps }, { it.sequence }))
    private var sequence = 0L

    fun add(event: RunEvent) {
        val (rank, steps) = when (event) {
            is RunEvent.Work -> (if (event.analyzer.hasZeroWork) 0 else 2) to event.analyzer.steps
            else -> 1 to 0L
        }
        heap.add(Entry(event, rank, steps, sequence++))
    }

    fun poll(): RunEvent? = heap.poll()?.event
    fun isEmpty(): Boolean = heap.isEmpty()

    /** The manager calls this only after joining the owning runner. */
    fun clear() = heap.clear()
}
