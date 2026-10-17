package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.*
import kotlin.test.*

/** Independent finite geometry expectations, before any transport implementation. */
class Run1StaticEntryProjectionTest {
    private class Fixture {
        val f = StoreFixtures()
        val ap = f.manager
        val c = ap.accessors.index(ClassStaticAccessor("C"))
        val d = ap.accessors.index(ClassStaticAccessor("D"))
        val cf = Run1Slot.StaticField(c, f.field("f"))
        val cg = Run1Slot.StaticField(c, f.field("g"))
        val dh = Run1Slot.StaticField(d, f.field("h"))
        val domain = linkedSetOf(cf, cg, dh)
        val t = f.mark("T")
        val u = f.mark("U")
        val symbolic = ArrayList<FlowTree>()
        val answers = ArrayList<InitialAp>()
        val collected = ArrayList<Pair<Run1Slot, RequestMarkSelection>>()

        fun emitter(marked: Boolean = false, packed: Boolean = true): Run1Abstraction {
            val profile = Run1StaticProfiles.build(ap, emptyMap(), mapOf(f.commonMethod to
                Run1StaticLocalProfile(abstractions = listOf(cf.pathData.toList()), marks =
                    if (!marked) emptyList() else listOf(StaticMarkInterest(
                        f.pattern(STATIC, cg.pathData.toList(), Tail.EXACT, MarkSlot.Concrete(t)),
                        StaticMarkRole.SINK, RequestMarkSet.of(t))))), declaredFieldUniverse = domain)[f.commonMethod]
            val storage = RequestStorage(profile.precomputedRequests)
            return Run1Abstraction(f.ops, profile, answers::add, { storage.borrowMatchingRows(it, {}) },
                { slot, marks -> collected += slot to marks },
                staticEntries = if (packed) ({ tree -> symbolic.add(tree); Unit }) else null)
        }

        fun ref(input: Facts): CallerRef = CallerRef(f.key("origin"),
            if (input is FlowTree) cf.initial(ap) else ap.zero, Layer.NORMAL, f.inst(1))

        fun paths(tree: FlowTree): Set<List<Int>> = f.ops.leaves(tree).map { it.fact.path }.toSet()

        fun checkCanonical(tree: FlowTree) {
            assertEquals(STATIC, tree.base)
            assertEquals(Layer.DEMAND, tree.layer)
            assertEquals(Tail.STAR, tree.tail)
            assertEquals(ExclusionSet.Empty, tree.exclusion)
            assertTrue(tree.markExclusion.isEmpty)
            assertTrue(f.ops.leaves(tree).all { it.fact.path.size == 2 && it.fact.mark == MarkSlot.STAR })
        }
    }

    @Test fun `nonreader actual field creates one symbolic binding without a scalar start`() {
        val f = Fixture()
        val emitter = f.emitter()
        val supplied = f.f.fact(STATIC, f.cg.pathData.toList(), Tail.EXACT, MarkSlot.Concrete(f.t))
        emitter.emit(f.ref(supplied), supplied)
        assertEquals(1, f.symbolic.size)
        assertEquals(setOf(f.cg.pathData.toList()), f.paths(f.symbolic.single()))
        f.checkCanonical(f.symbolic.single())
        assertTrue(f.answers.isEmpty(), "Unrequested concrete marks are not fabricated")
        assertTrue(f.collected.isEmpty(), "Binding a field is not a field request")
    }

    @Test fun `root class and mixed field supply project exact domain geometry once per input`() {
        val f = Fixture()
        val emitter = f.emitter()
        val cases = listOf(
            f.f.fact(STATIC, emptyList(), Tail.ANY, MarkSlot.Concrete(f.t)) to
                setOf(f.cf.pathData.toList(), f.cg.pathData.toList(), f.dh.pathData.toList()),
            f.f.fact(STATIC, listOf(f.c), Tail.STAR, MarkSlot.Star(f.ap.markSetOf(f.t))) to
                setOf(f.cf.pathData.toList(), f.cg.pathData.toList()),
            f.f.fact(STATIC, f.dh.pathData.toList(), Tail.STAR, MarkSlot.STAR) to
                setOf(f.dh.pathData.toList()))
        for ((input, expected) in cases) {
            val before = f.symbolic.size
            emitter.emit(f.ref(input), input)
            assertEquals(before + 1, f.symbolic.size)
            assertEquals(expected, f.paths(f.symbolic.last()))
            f.checkCanonical(f.symbolic.last())
        }
        assertTrue(f.answers.isEmpty())
    }

    @Test fun `abstract supply is independent while concrete answers need actual requested T`() {
        val f = Fixture()
        val emitter = f.emitter(marked = true)
        for (mark in listOf(f.u, f.t)) {
            val supplied = f.f.fact(STATIC, listOf(f.c), Tail.ANY, MarkSlot.Concrete(mark))
            emitter.emit(f.ref(supplied), supplied)
        }
        assertEquals(2, f.symbolic.size)
        assertTrue(f.symbolic.all { f.paths(it) == setOf(f.cf.pathData.toList(), f.cg.pathData.toList()) })
        val answer = f.answers.single()
        assertEquals(f.cg.pathData.toList(), answer.pathData.toList())
        assertEquals(MarkSlot.Concrete(f.t), answer.mark)
        assertEquals(Tail.STAR, answer.tail)
        assertTrue(f.collected.isEmpty(), "The frozen mark base causes no startup notification wave")
    }

    @Test fun `exact class point does not supply fields and retains its separate concrete path`() {
        val f = Fixture()
        val emitter = f.emitter()
        val supplied = f.f.fact(STATIC, listOf(f.c), Tail.EXACT, MarkSlot.Concrete(f.u))
        emitter.emit(f.ref(supplied), supplied)
        assertTrue(f.symbolic.isEmpty())
        val point = f.answers.single()
        assertEquals(listOf(f.c), point.pathData.toList())
        assertEquals(MarkSlot.Concrete(f.u), point.mark)
        assertEquals(Tail.EXACT, point.tail)
        assertTrue(f.collected.isEmpty())
    }

    @Test fun `scalar oracle fallback is exhaustive and never admits a root or ANY initial`() {
        val f = Fixture()
        val emitter = f.emitter(packed = false)
        val supplied = f.f.fact(STATIC, emptyList(), Tail.ANY, MarkSlot.Concrete(f.u))
        emitter.emit(f.ref(supplied), supplied)
        assertEquals(f.domain.map { it.initial(f.ap) }.toSet(), f.answers.toSet())
        assertTrue(f.answers.all { it.tail == Tail.STAR && it.pathData.size == 2 && it.mark == MarkSlot.STAR })
        f.answers.forEach { validateRun1Initial(it, 1) }
        assertTrue(f.symbolic.isEmpty())
        assertTrue(f.collected.isEmpty())
        emitter.release()
        assertFailsWith<IllegalStateException> { emitter.emit(f.ref(supplied), supplied) }
    }
}
