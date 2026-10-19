package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.PipelineFixtures
import org.opentaint.dataflow.bidi.fixtures.QueuedProtocolSteps
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.CallerRef
import kotlin.test.*

/** The ordinary caller directory keeps current exact relations and only fresh AP deltas. */
class N2AdmissionTest {
    private fun sub(f: StoreFixtures, tail: Tail): Subscription =
        Subscription(f.key("callee"), CallerRef(f.key("caller"), f.manager.zero, Layer.NORMAL, f.inst(1)),
            Layer.DEMAND, f.fact(tail = tail, layer = Layer.DEMAND))

    @Test fun `exact and must inputs keep one packed current relation in either order`() {
        for (tails in listOf(listOf(Tail.EXACT, Tail.ANY_TAINT), listOf(Tail.ANY_TAINT, Tail.EXACT))) {
            val f = StoreFixtures()
            val directory = CalleeSubscriptions(f.ops)
            val first = assertNotNull(directory.admit(sub(f, tails[0])))
            val next = assertNotNull(directory.admit(sub(f, tails[1])))
            assertTrue(next.growth)
            assertEquals(listOf(tails[1]), f.ops.leaves(next.fresh.added).map { it.fact.tail }.toList())
            assertEquals(setOf(Tail.EXACT, Tail.ANY_TAINT),
                f.ops.leaves(first.current.added).map { it.fact.tail }.toSet())
            val initial = f.manager.initial(f.pattern())
            val pub = Publication(initial, f.fact(AccessPathBase.Return, layer = Layer.DEMAND))
            assertEquals(1, directory.candidates(pub, 0, ApMode(true, Direction.FORWARD, 1)).count())
            assertNull(directory.admit(sub(f, tails[0])))
            assertNull(directory.admit(sub(f, tails[1])))
            directory.clear()
            assertTrue(directory.candidates(pub, 0, ApMode(true, Direction.FORWARD, 1)).none())
        }
    }

    @Test fun `new marks grow the current relation but duplicate marks schedule no replay`() {
        val f = StoreFixtures()
        val directory = CalleeSubscriptions(f.ops)
        val first = assertNotNull(directory.admit(sub(f, Tail.EXACT)))
        val u = sub(f, Tail.EXACT).copy(added = f.fact(mark = MarkSlot.Concrete(f.mark("U")), layer = Layer.DEMAND))
        assertNotNull(directory.admit(u))
        assertEquals(2, f.ops.leaves(first.current.added).count())
        assertNull(directory.admit(u))
        directory.clear()
    }

    @Test fun `equal AP at another actual stage retains a separate caller binding`() {
        val f = StoreFixtures()
        val directory = CalleeSubscriptions(f.ops)
        val one = sub(f, Tail.EXACT)
        assertNotNull(directory.admit(one))
        assertNotNull(directory.admit(one.copy(ref = one.ref.copy(stageOrdinal = 1))))
        val initial = f.manager.initial(f.pattern())
        val pub = Publication(initial, f.fact(AccessPathBase.Return, layer = Layer.DEMAND))
        assertEquals(setOf(0, 1), directory.candidates(pub, 0, ApMode(true, Direction.FORWARD, 1))
            .map { it.ref.stageOrdinal }.toSet())
        directory.clear()
    }

    @Test fun `a duplicate actual input does not replay an existing summary`() {
        val steps = QueuedProtocolSteps()
        PipelineFixtures(protocol = steps).use { f ->
            val full = f.store.mark("T")
            val atom = f.ap.marks.group(f.ap.marks.groupOf(full))
            val added = f.store.fact(mark = MarkSlot.Concrete(atom), layer = Layer.DEMAND)
            val part = f.subscription(added)
            val initial = f.ap.initial(f.store.pattern(mark = MarkSlot.Concrete(atom)))
            f.storage().publish(listOf(Publication(initial,
                f.store.fact(AccessPathBase.Return, mark = MarkSlot.Concrete(atom), layer = Layer.DEMAND))))
            f.subscriptions.subscribe(part)
            steps.replayAll()
            assertEquals(1, f.summaries.size)
            f.summaries.clear()
            f.subscriptions.subscribe(part)
            assertTrue(steps.replays.isEmpty())
            steps.replayAll()
            assertTrue(f.summaries.isEmpty())
        }
    }
}
