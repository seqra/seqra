package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.PipelineFixtures
import org.opentaint.dataflow.bidi.fixtures.QueuedProtocolSteps
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.CallerRef
import kotlin.test.*

/** Independent semantic leaf expectations; neither an old Facts history nor a raw merged root is the oracle. */
class N2GroundingCofactorTest {
    private fun guard(f: StoreFixtures, name: String): GroundingSupport {
        val literal = f.pattern(AccessPathBase.Argument(1), mark = MarkSlot.Concrete(f.mark(name)))
        val accepted = f.manager.factsOf(literal.fact, literal.exclusion, Layer.DEMAND)
        return GroundingSupport.require(listOf(GroundingRequirement(f.manager.zero, accepted, literal)))
    }

    private fun sub(f: StoreFixtures, tail: Tail, support: GroundingSupport = GroundingSupport.TRUE): Subscription =
        Subscription(f.key("callee"), CallerRef(f.key("caller"), f.manager.zero, Layer.NORMAL, f.inst(1)),
            Layer.DEMAND, f.fact(tail = tail, layer = Layer.DEMAND), grounding = support)

    private fun leaves(f: StoreFixtures, parts: List<Subscription>): Map<Tail, GroundingSupport> =
        parts.associate { part -> f.ops.leaves(part.added).single().fact.tail to part.grounding }

    @Test fun `old exact unconditional input does not authorize the new guarded any input`() {
        val f = StoreFixtures()
        val directory = CalleeSubscriptions(f.ops)
        val a = guard(f, "A")
        val first = assertNotNull(directory.admit(sub(f, Tail.EXACT)))
        val broad = assertNotNull(directory.admit(sub(f, Tail.ANY_TAINT, a)))
        assertEquals(first.handle, broad.handle)
        val row = assertNotNull(first.fresh.inputRef)
        assertEquals(mapOf(Tail.EXACT to GroundingSupport.TRUE, Tail.ANY_TAINT to a),
            leaves(f, directory.borrowCofactors(row)))
        assertEquals(mapOf(Tail.ANY_TAINT to a), leaves(f, broad.replayParts))
        val current = assertIs<TaintTree>(directory.borrow(row))
        assertTrue(!current.root.leaf.exact.isEmpty, "The exact N2 accumulator keeps both memberships")
        assertTrue(!current.root.leaf.any.isEmpty)
        val premise = f.manager.initial(f.pattern())
        val pub = Publication(premise, f.fact(AccessPathBase.Return, layer = Layer.DEMAND))
        assertEquals(2, directory.candidates(pub, 0, ApMode(true, Direction.FORWARD, 1)).count())
        assertTrue(directory.borrowCofactors(row.copy(owner = f.key("foreign"))).isEmpty())
        directory.clear()
        assertTrue(directory.borrowCofactors(row).isEmpty())
    }

    @Test fun `support growth on one duplicate input keeps its handle and merges alternative guards`() {
        val f = StoreFixtures()
        val directory = CalleeSubscriptions(f.ops)
        val a = guard(f, "A")
        val b = guard(f, "B")
        val first = assertNotNull(directory.admit(sub(f, Tail.ANY_TAINT, a)))
        val changed = assertNotNull(directory.admit(sub(f, Tail.ANY_TAINT, b)))
        assertTrue(changed.supportOnly)
        assertEquals(first.handle, changed.handle)
        assertEquals(a.or(b), changed.replayParts.single().grounding)
        assertTrue(changed.fresh.supportRevision > first.fresh.supportRevision)
        assertNull(directory.admit(sub(f, Tail.ANY_TAINT, b)))
        assertEquals(a.or(b), directory.borrowCofactors(assertNotNull(first.fresh.inputRef)).single().grounding)
        directory.clear()
    }

    @Test fun `late exact unconditional input retains its own support beside a guarded must input`() {
        val f = StoreFixtures()
        val directory = CalleeSubscriptions(f.ops)
        val a = guard(f, "A")
        val first = assertNotNull(directory.admit(sub(f, Tail.ANY_TAINT, a)))
        val exact = assertNotNull(directory.admit(sub(f, Tail.EXACT)))
        assertFalse(exact.supportOnly, "The exact N2 accumulator admits a distinct exact membership")
        assertEquals(first.handle, exact.handle)
        assertEquals(mapOf(Tail.EXACT to GroundingSupport.TRUE), leaves(f, exact.replayParts))
        assertEquals(mapOf(Tail.ANY_TAINT to a, Tail.EXACT to GroundingSupport.TRUE),
            leaves(f, directory.borrowCofactors(assertNotNull(first.fresh.inputRef))))
        directory.clear()
    }

    @Test fun `unguarded inputs retain one packed current relation`() {
        val f = StoreFixtures()
        val directory = CalleeSubscriptions(f.ops)
        val first = assertNotNull(directory.admit(sub(f, Tail.EXACT)))
        val u = sub(f, Tail.EXACT).copy(added = f.fact(mark = MarkSlot.Concrete(f.mark("U")), layer = Layer.DEMAND))
        assertNotNull(directory.admit(u))
        val current = directory.borrowCofactors(assertNotNull(first.fresh.inputRef)).single()
        assertEquals(GroundingSupport.TRUE, current.grounding)
        assertEquals(2, f.ops.leaves(current.added).count())
        directory.clear()
    }

    @Test fun `a fact-deduplicated support change replays the existing summary with its cofactor`() {
        val steps = QueuedProtocolSteps()
        PipelineFixtures(protocol = steps).use { f ->
            val a = guard(f.store, "A")
            val b = guard(f.store, "B")
            val full = f.store.mark("T")
            val atom = f.ap.marks.group(f.ap.marks.groupOf(full))
            val added = f.store.fact(mark = MarkSlot.Concrete(atom), layer = Layer.DEMAND)
            val part = f.subscription(added).copy(grounding = a)
            val initial = f.ap.initial(f.store.pattern(mark = MarkSlot.Concrete(atom)))
            f.storage().publish(listOf(Publication(initial,
                f.store.fact(AccessPathBase.Return, mark = MarkSlot.Concrete(atom), layer = Layer.DEMAND))))
            f.subscriptions.subscribe(part)
            steps.replayAll()
            assertEquals(listOf(a), f.summaries.map { it.part.grounding })
            f.summaries.clear()
            f.subscriptions.subscribe(part.copy(grounding = b))
            assertEquals(1, steps.replays.size)
            steps.replayAll()
            assertEquals(listOf(a.or(b)), f.summaries.map { it.part.grounding })
            val row = assertNotNull(f.summaries.single().part.inputRef)
            assertEquals(a.or(b), f.subscriptions.borrowN2Cofactors(row).single().grounding)
            assertEquals(part, part.copy(grounding = b), "Guard metadata is not semantic subscription identity")
        }
    }
}
