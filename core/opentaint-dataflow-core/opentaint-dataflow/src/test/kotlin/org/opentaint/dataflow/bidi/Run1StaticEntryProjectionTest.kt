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

        fun emitter(marked: Boolean = false, packed: Boolean = true, classPoint: Boolean = false): Run1Abstraction {
            val profile = Run1StaticProfiles.build(ap, emptyMap(), mapOf(f.commonMethod to
                Run1StaticLocalProfile(abstractions = listOf(cf.pathData.toList()), marks =
                    if (!marked) emptyList() else listOf(StaticMarkInterest(
                        f.pattern(STATIC, cg.pathData.toList(), Tail.EXACT, MarkSlot.Concrete(t)),
                        StaticMarkRole.SINK, RequestMarkSet.of(t))),
                    classPoints = if (classPoint) setOf(c) else emptySet())), declaredFieldUniverse = domain)[f.commonMethod]
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
        val actual = f.answers.single()
        assertEquals(f.cg.pathData.toList(), actual.pathData.toList())
        assertEquals(MarkSlot.Concrete(f.t), actual.mark)
        assertEquals(Tail.EXACT, actual.tail)
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
        assertEquals(f.domain.map { it.pathData.toList() }.toSet(),
            f.answers.map { it.pathData.toList() }.toSet())
        assertTrue(f.answers.all { it.mark == MarkSlot.Concrete(f.t) && it.tail == Tail.STAR })
    }

    @Test fun `all actual static marks are admitted without a local or inherited request`() {
        val f = Fixture()
        val emitter = f.emitter(marked = true)
        for (mark in listOf(f.u, f.t)) {
            val supplied = f.f.fact(STATIC, listOf(f.c), Tail.ANY, MarkSlot.Concrete(mark))
            emitter.emit(f.ref(supplied), supplied)
        }
        assertEquals(2, f.symbolic.size)
        assertTrue(f.symbolic.all { f.paths(it) == setOf(f.cf.pathData.toList(), f.cg.pathData.toList()) })
        val expected = setOf(f.cf, f.cg).flatMap { slot -> listOf(f.t, f.u).map { mark ->
            f.ap.initial(STATIC, f.ap.path(slot.pathData), Tail.STAR, ExclusionSet.Empty, MarkSlot.Concrete(mark))
        } }.toSet()
        assertEquals(expected, f.answers.toSet())
        assertTrue(f.collected.isEmpty(), "Static actual admission produces no ordinary static request")
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
        val expected = f.domain.flatMap { slot -> listOf(slot.initial(f.ap),
            f.ap.initial(STATIC, f.ap.path(slot.pathData), Tail.STAR, ExclusionSet.Empty, MarkSlot.Concrete(f.u))) }.toSet()
        assertEquals(expected, f.answers.toSet())
        assertTrue(f.answers.all { it.tail == Tail.STAR && it.pathData.size == 2 })
        f.answers.forEach { validateRun1Initial(it, 1) }
        assertTrue(f.symbolic.isEmpty())
        assertTrue(f.collected.isEmpty())
        emitter.release()
        assertFailsWith<IllegalStateException> { emitter.emit(f.ref(supplied), supplied) }
    }

    @Test fun `static admission never borrows requests and no interest fabricates an absent mark`() {
        val f = Fixture()
        val profile = Run1StaticProfiles.build(f.ap, emptyMap(), mapOf(f.f.commonMethod to
            Run1StaticLocalProfile(marks = listOf(StaticMarkInterest(
                f.f.pattern(STATIC, f.cf.pathData.toList(), mark = MarkSlot.Concrete(f.t)),
                StaticMarkRole.SINK, RequestMarkSet.of(f.t))))), declaredFieldUniverse = f.domain)[f.f.commonMethod]
        val emitted = ArrayList<InitialAp>()
        val emitter = Run1Abstraction(f.f.ops, profile, emitted::add,
            { error("Static concrete admission must not read R/B") }, { _, _ -> })
        val supplied = f.f.fact(STATIC, f.cf.pathData.toList(), mark = MarkSlot.Concrete(f.u))
        emitter.emit(f.ref(supplied), supplied)
        val concrete = emitted.filter { it.mark is MarkSlot.Concrete }
        assertEquals(listOf(MarkSlot.Concrete(f.u)), concrete.map { it.mark })
        assertTrue(profile.precomputedRequests.isEmpty)
        val flow = f.f.fact(STATIC, f.cf.pathData.toList(), Tail.STAR,
            MarkSlot.Star(f.ap.markSetOf(f.t)))
        emitter.emit(f.ref(flow), flow)
        assertEquals(concrete, emitted.filter { it.mark is MarkSlot.Concrete })
    }

    @Test fun `same actual mark at exact and any field leaves retains both legal initial kinds`() {
        val f = Fixture(); val emitter = f.emitter()
        val marks = f.ap.markSetOf(f.t)
        val leaf = f.ap.taintAlg.leafNode(f.ap.taintLeaves(marks, marks))
        val root = f.ap.taintAlg.prepend(f.ap, f.ap.path(f.cf.pathData), leaf)
        val supplied = f.ap.taintTree(STATIC, Layer.DEMAND, root)
        emitter.emit(f.ref(supplied), supplied)
        assertEquals(setOf(Tail.EXACT, Tail.STAR), f.answers.map { it.tail }.toSet())
        assertTrue(f.answers.all { it.mark == MarkSlot.Concrete(f.t) && it.pathData.contentEquals(f.cf.pathData) })
        for (initial in f.answers) {
            validateRun1Initial(initial, 1)
            val start = normalizeRun1StaticFlow(initial, f.f.ops.startFact(initial))
            assertEquals(Layer.DEMAND, start.layer)
            assertEquals(if (initial.tail == Tail.EXACT) Tail.EXACT else Tail.ANY,
                f.f.ops.leaves(start).single().fact.tail)
        }
    }

    @Test fun `broad class actual marks use class inventory and retain unrelated supplied U`() {
        val f = Fixture(); val emitter = f.emitter(marked = true, classPoint = true)
        val supplied = f.f.fact(STATIC, listOf(f.c), Tail.ANY, MarkSlot.Concrete(f.u))
        emitter.emit(f.ref(supplied), supplied)
        val point = f.answers.single { it.pathData.size == 1 }
        assertEquals(listOf(f.c), point.pathData.toList())
        assertEquals(MarkSlot.Concrete(f.u), point.mark)
        assertEquals(Tail.STAR, point.tail)
        assertEquals(setOf(listOf(f.c), f.cf.pathData.toList(), f.cg.pathData.toList()),
            f.answers.map { it.pathData.toList() }.toSet())
    }

    @Test fun `actual mark zero on a static field is not the ZERO base initial`() {
        val f = Fixture(); val emitter = f.emitter()
        val supplied = f.f.fact(STATIC, f.cf.pathData.toList(), mark = MarkSlot.Concrete(TaintMark.ZERO))
        emitter.emit(f.ref(supplied), supplied)
        assertEquals(1, f.answers.size)
        assertFalse(f.answers.single().isZero)
        assertEquals(MarkSlot.Concrete(TaintMark.ZERO), f.answers.single().mark)
        validateRun1Initial(f.answers.single(), 1)
    }
}
