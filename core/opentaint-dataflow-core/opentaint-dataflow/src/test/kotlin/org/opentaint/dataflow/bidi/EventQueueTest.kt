package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.engine.EventQueue
import org.opentaint.dataflow.bidi.engine.InFlight
import org.opentaint.dataflow.bidi.engine.RunActor
import org.opentaint.dataflow.bidi.engine.RunEvent
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EventQueueTest {
    private val fixture = StoreFixtures()

    /** These actors have no AP work; their two required result counters are therefore zero. */
    private class Actor(override val key: MethodKey, var zero: Boolean = false, var completed: Long = 0) : RunActor {
        var zeroReads = 0
        var stepReads = 0
        var workCalls = 0
        var freezeCalls = 0
        override val hasZeroWork: Boolean get() { zeroReads++; return zero }
        override val steps: Long get() { stepReads++; return completed }
        override val demandLayerEdges: Long = 0
        override val recordCrossings: Long = 0
        override fun work(quantum: Int): Boolean { workCalls++; return true }
        override fun freeze() { freezeCalls++ }
    }

    @Test
    fun `empty queue reports empty and polling its last event restores that state`(): Unit {
        val queue = EventQueue()
        assertTrue(queue.isEmpty())
        assertNull(queue.poll())
        val event = RunEvent.Start(fixture.key("start"))
        queue.add(event)
        assertFalse(queue.isEmpty())
        assertSame(event, queue.poll())
        assertTrue(queue.isEmpty())
        assertNull(queue.poll())
    }

    @Test
    fun `zero work precedes ordinary events which precede other work regardless of steps`(): Unit {
        val queue = EventQueue()
        val zero = RunEvent.Work(Actor(fixture.key("zero"), zero = true, completed = 900))
        val other = RunEvent.Work(Actor(fixture.key("other"), completed = 0))
        val start = RunEvent.Start(fixture.key("start"))
        queue.add(other)
        queue.add(start)
        queue.add(zero)
        assertSame(zero, queue.poll())
        assertSame(start, queue.poll())
        assertSame(other, queue.poll())
        assertTrue(queue.isEmpty())
    }

    @Test
    fun `within each work rank lower step snapshots precede FIFO ties`(): Unit {
        for (zero in listOf(false, true)) {
            val queue = EventQueue()
            val high = RunEvent.Work(Actor(fixture.key("high-$zero"), zero, 9))
            val first = RunEvent.Work(Actor(fixture.key("first-$zero"), zero, 3))
            val second = RunEvent.Work(Actor(fixture.key("second-$zero"), zero, 3))
            queue.add(high)
            queue.add(first)
            queue.add(second)
            assertSame(first, queue.poll())
            assertSame(second, queue.poll())
            assertSame(high, queue.poll())
        }
        val queue = EventQueue()
        val first = RunEvent.Start(fixture.key("first"))
        val second = RunEvent.ZeroIn(fixture.key("second"))
        val third = RunEvent.Start(fixture.key("third"))
        queue.add(first)
        queue.add(second)
        queue.add(third)
        assertSame(first, queue.poll())
        assertSame(second, queue.poll())
        assertSame(third, queue.poll())
    }

    @Test
    fun `mutating actor state while queued cannot change captured keys or reread them`(): Unit {
        val queue = EventQueue()
        val initiallyZero = Actor(fixture.key("initially-zero"), zero = true, completed = 500)
        val initiallyOther = Actor(fixture.key("initially-other"), zero = false, completed = 1)
        val zero = RunEvent.Work(initiallyZero)
        val other = RunEvent.Work(initiallyOther)
        val start = RunEvent.Start(fixture.key("start"))
        queue.add(other)
        queue.add(zero)
        queue.add(start)
        initiallyZero.zero = false
        initiallyZero.completed = 0
        initiallyOther.zero = true
        initiallyOther.completed = 0
        assertSame(zero, queue.poll())
        assertSame(start, queue.poll())
        assertSame(other, queue.poll())
        for (actor in listOf(initiallyZero, initiallyOther)) {
            assertEquals(1, actor.zeroReads)
            assertEquals(1, actor.stepReads)
            assertEquals(0, actor.workCalls)
            assertEquals(0, actor.freezeCalls)
        }
    }

    @Test
    fun `partial work requeues with a fresh rank and keeps its original reservation`(): Unit {
        val queue = EventQueue()
        var zeros = 0
        val count = InFlight { zeros++ }
        val actor = Actor(fixture.key("work"), zero = true)
        val work = RunEvent.Work(actor)
        val start = RunEvent.Start(fixture.key("start"))
        count.beforeSend()
        queue.add(work)
        count.beforeSend()
        queue.add(start)
        assertSame(work, queue.poll())
        actor.zero = false
        actor.completed = 1000
        queue.add(work) // Still counted: no second reservation or completed handler.
        assertEquals(2L, count.pending)
        assertEquals(0L, count.handled)
        assertEquals(0, zeros)
        assertSame(start, queue.poll())
        count.afterHandler()
        assertEquals(1L, count.pending)
        assertEquals(0, zeros)
        assertSame(work, queue.poll())
        count.afterHandler()
        assertEquals(0L, count.pending)
        assertEquals(2L, count.handled)
        assertEquals(1, zeros)
        assertEquals(2, actor.zeroReads)
        assertEquals(2, actor.stepReads)
    }

    @Test
    fun `requeued work receives a new FIFO position among equal step snapshots`(): Unit {
        val queue = EventQueue()
        val actor = Actor(fixture.key("requeued"), completed = 4)
        val work = RunEvent.Work(actor)
        val older = RunEvent.Work(Actor(fixture.key("older"), completed = 4))
        queue.add(work)
        queue.add(older)
        assertSame(work, queue.poll())
        queue.add(work)
        assertSame(older, queue.poll())
        assertSame(work, queue.poll())
        assertEquals(2, actor.zeroReads)
        assertEquals(2, actor.stepReads)
    }

    @Test
    fun `clear releases all queued events without running or freezing their actors`(): Unit {
        val queue = EventQueue()
        val actor = Actor(fixture.key("released"), zero = true)
        queue.add(RunEvent.Work(actor))
        queue.add(RunEvent.Start(fixture.key("start")))
        queue.clear()
        assertTrue(queue.isEmpty())
        assertNull(queue.poll())
        assertEquals(0, actor.workCalls)
        assertEquals(0, actor.freezeCalls)
    }
}
