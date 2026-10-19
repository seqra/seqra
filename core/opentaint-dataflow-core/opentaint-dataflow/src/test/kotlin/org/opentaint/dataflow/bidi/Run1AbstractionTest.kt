package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import kotlin.test.*

/** Selected grouped input admission. Historical F/B/static-start laws live in engine.historical. */
class Run1AbstractionTest {
    private class Harness {
        val f = StoreFixtures()
        val ap = f.manager
        val answers = arrayListOf<InitialAp>()
        val requests = arrayListOf<Pair<Run1Slot, RequestMarkSet>>()
        val storage = RequestStorage()
        val emitter = Run1Abstraction(f.ops, answers::add, { storage.borrowMatchingRows(it, {}) },
            { slot, marks -> requests += slot to marks.minus(storage.borrow(slot)) })
        val slot = Run1Slot.Base(AccessPathBase.Argument(0))
        val ref get() = CallerRef(f.key("caller"), slot.initial(ap), Layer.NORMAL, f.inst(1))
        val zeroRef get() = ref.copy(premise = ap.zero)
        fun group(name: String): TaintMark = ap.marks.runtimeAtom(f.mark(name), MarkMode.GROUP)
        fun staticPath(): List<Int> = listOf(ap.accessors.index(ClassStaticAccessor("C")), f.field("f"))
    }

    @Test fun `static concrete suppliers never start a method entry or ordinary request row`() {
        val h = Harness()
        for (tail in listOf(Tail.EXACT, Tail.ANY)) {
            val input = h.f.fact(STATIC, h.staticPath(), tail, MarkSlot.Concrete(h.group("T")), layer = Layer.DEMAND)
            h.emitter.emit(h.zeroRef, input)
        }
        assertTrue(h.answers.isEmpty())
        assertTrue(h.requests.isEmpty())
        assertTrue(h.storage.isEmpty)
    }

    @Test fun `static FLOW suppliers are point-only even when their original is nonstatic`() {
        val h = Harness()
        h.emitter.emit(h.ref, h.f.fact(STATIC, h.staticPath(), Tail.STAR, MarkSlot.STAR, layer = Layer.DEMAND))
        assertTrue(h.answers.isEmpty())
        assertTrue(h.requests.isEmpty())
    }

    @Test fun `ordinary FLOW keeps one canonical original independent of mark exclusion`() {
        val h = Harness()
        val g = h.group("T")
        h.emitter.emit(h.ref, h.f.fact(h.slot.base, tail = Tail.STAR,
            mark = MarkSlot.Star(MarkExclusions.of(h.ap.marks, g))))
        assertEquals(listOf(h.slot.initial(h.ap)), h.answers)
        assertTrue(h.requests.isEmpty())
    }

    @Test fun `ordinary concrete answers require an actual supplied group and an admitted request`() {
        val h = Harness()
        val g = h.group("T")
        val absent = h.group("absent")
        val input = h.f.fact(h.slot.base, mark = MarkSlot.Concrete(g), layer = Layer.DEMAND)
        h.emitter.emit(h.zeroRef, input)
        assertEquals(listOf(h.slot.initial(h.ap)), h.answers)
        h.storage.addAll(h.slot, RequestMarkSet.of(absent))
        h.emitter.emit(h.zeroRef, input)
        assertTrue(h.answers.none { it.mark == MarkSlot.Concrete(absent) })
        h.storage.addAll(h.slot, RequestMarkSet.of(g))
        h.emitter.emit(h.zeroRef, input)
        val expected = h.ap.initial(h.slot.base, null, Tail.EXACT, ExclusionSet.Empty, MarkSlot.Concrete(g))
        assertEquals(1, h.answers.count { it == expected })
    }

    @Test fun `nonexact ordinary answers use STAR premises while preserving the actual group`() {
        val h = Harness()
        val g = h.group("T")
        h.storage.addAll(h.slot, RequestMarkSet.of(g))
        h.emitter.emit(h.zeroRef, h.f.fact(h.slot.base, tail = Tail.ANY,
            mark = MarkSlot.Concrete(g), layer = Layer.DEMAND))
        assertEquals(setOf(h.slot.initial(h.ap), h.ap.initial(h.slot.base, null, Tail.STAR,
            ExclusionSet.Empty, MarkSlot.Concrete(g))), h.answers.toSet())
        assertTrue(h.answers.none { it.tail == Tail.ANY || it.tail == Tail.ANY_TAINT })
    }

    @Test fun `only an abstract ordinary original can raise a mark interest`() {
        val h = Harness()
        val g = h.group("T")
        h.emitter.requireMark(h.slot.initial(h.ap), g)
        h.emitter.requireMark(h.ap.zero, g)
        h.emitter.requireMark(h.ap.initial(h.slot.base, null, Tail.EXACT, ExclusionSet.Empty, MarkSlot.Concrete(g)), g)
        assertEquals(listOf<Pair<Run1Slot, RequestMarkSet>>(h.slot to RequestMarkSet.of(g)), h.requests)
    }

    @Test fun `kind validation precedes static exclusion from input admission`() {
        val h = Harness()
        val flow = h.f.fact(STATIC, h.staticPath(), Tail.STAR, MarkSlot.STAR, layer = Layer.DEMAND)
        assertFailsWith<IllegalStateException> { h.emitter.emit(h.zeroRef, flow) }
        assertTrue(h.answers.isEmpty())
    }

    @Test fun `release retains no deferred answers and rejects later work`() {
        val h = Harness()
        assertFalse(h.emitter.hasPending)
        assertEquals(0L, h.emitter.pendingAnswerShapeCount)
        assertEquals(0L, h.emitter.pendingAnswerMarkCount)
        h.emitter.release()
        assertFailsWith<IllegalStateException> { h.emitter.emit(h.ref,
            h.f.fact(h.slot.base, tail = Tail.STAR, mark = MarkSlot.STAR)) }
    }
}
