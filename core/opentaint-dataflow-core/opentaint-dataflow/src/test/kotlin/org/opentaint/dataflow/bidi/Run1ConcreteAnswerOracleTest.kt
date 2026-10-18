package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import kotlin.test.*

/** Independent scalar relations: Base answers use requests; static answers use actual marks. */
class Run1ConcreteAnswerOracleTest {
    private val f = StoreFixtures()
    private val ap = f.manager
    private val b = AccessPathBase.Argument(0)
    private val c = ap.accessors.index(ClassStaticAccessor("C"))
    private val d = ap.accessors.index(ClassStaticAccessor("D"))
    private val a = f.field("a")
    private val z = f.field("z")
    private val t = f.mark("T")
    private val u = f.mark("U")

    private val staticProfile by lazy {
        Run1StaticProfiles.build(ap,emptyMap(),mapOf(f.commonMethod to Run1StaticLocalProfile.EMPTY),
            declaredFieldUniverse=setOf(Run1Slot.StaticField(c,a),Run1Slot.StaticField(c,z),Run1Slot.StaticField(d,a)))[f.commonMethod]
    }

    private fun answers(input: Facts, slot: Run1Slot, marks: RequestMarkSet,
                        counts: RequestBatchStatistics.Counts? = null): Set<InitialAp> = buildSet {
        if (slot is Run1Slot.StaticField) {
            if (input is TaintTree && input.base == STATIC) forEachActualStaticInitial(ap,input,staticProfile,counts) {
                if (it.pathData.toList() == slot.pathData.toList()) add(it)
            }
        } else if (input.base != STATIC)
            forEachConcreteAnswer(ap, input, listOf(RequestedRow(slot, RequestRowView(marks, RequestMarkSet.EMPTY))), counts) { add(it) }
    }

    @Test
    fun `all accepted supplier tails and layers match the scalar slot answer relation ignoring E`() {
        val slots = listOf(Run1Slot.Base(b), Run1Slot.StaticField(c, a), Run1Slot.StaticField(c, z))
        val suppliers = listOf(b to emptyList(), b to listOf(a), STATIC to emptyList(), STATIC to listOf(c),
            STATIC to listOf(c, a), STATIC to listOf(c, z), STATIC to listOf(d, a))
        for (slot in slots) for ((base, path) in suppliers) for (layer in Layer.entries) {
            for (tail in listOf(Tail.EXACT, if (layer == Layer.NORMAL) Tail.ANY_TAINT else Tail.ANY)) {
                if (base == STATIC && path.isEmpty() && tail == Tail.EXACT) continue // forbidden supplier
                val e = if (tail == Tail.ANY_TAINT) ExclusionSet.of(a) else ExclusionSet.Empty
                val input = f.fact(base, path, tail, MarkSlot.Concrete(t), e, layer)
                val requested = RequestMarkSet.fromIds(intArrayOf(t.id, u.id))
                val target = slot.pathData.toList()
                val same = path == target
                val supplies = base == slot.base && (slot is Run1Slot.Base ||
                    path.take(target.size) == target ||
                    (tail != Tail.EXACT && target.take(path.size) == path))
                val expected = if (!supplies) emptySet() else setOf(ap.initial(slot.base, ap.path(target),
                    if (same && tail == Tail.EXACT) Tail.EXACT else Tail.STAR,
                    ExclusionSet.Empty, MarkSlot.Concrete(t)))
                assertEquals(expected, answers(input, slot, requested), "$slot <- $base/$path/$tail/$layer")
                if (slot is Run1Slot.Base) assertTrue(answers(input, slot, RequestMarkSet.of(u)).isEmpty())
                else assertEquals(expected,answers(input,slot,RequestMarkSet.of(u)),"Static answers ignore mark-request membership")
            }
        }
    }

    @Test
    fun `one concrete leaf probes actual marks rather than a wide request set`() {
        val input = f.fact(mark = MarkSlot.Concrete(TaintMark(250)))
        val counts = RequestBatchStatistics.Counts()
        val result = answers(input, Run1Slot.Base(b), RequestMarkSet.fromIds(IntArray(500) { it + 1 }), counts)
        assertEquals(1, result.size)
        assertEquals(1L, counts.memberScans)
    }

    @Test
    fun `mixed request rows answer only the matching nonstatic base with an actual mark`() {
        val other = Run1Slot.Base(AccessPathBase.Argument(1))
        fun row(slot: Run1Slot, mark: TaintMark) =
            RequestedRow(slot, RequestRowView(RequestMarkSet.of(mark), RequestMarkSet.EMPTY))
        val rows = listOf(row(Run1Slot.StaticField(c, a), u), row(Run1Slot.Base(b), t), row(other, u))
        val marks = ap.intern(MarkSet(intArrayOf(t.id, u.id).sortedArray()))
        val root = ap.taintAlg.leafNode(ap.taintLeaves(marks, MarkSet.EMPTY))
        val input = ap.taintTree(b, Layer.NORMAL, root)
        val expected = setOf(ap.initial(b, null, Tail.EXACT, ExclusionSet.Empty, MarkSlot.Concrete(t)))
        for (batch in listOf(rows, rows.reversed())) {
            val actual = buildSet { forEachConcreteAnswer(ap, input, batch) { add(it) } }
            assertEquals(expected, actual)
        }
        val staticOnly = buildSet { forEachConcreteAnswer(ap, input, rows.take(1)) { add(it) } }
        assertTrue(staticOnly.isEmpty(), "Static interest cannot request a nonstatic concrete initial")
    }

    @Test
    fun `packed exact and nonexact branches retain both initial alternatives without ANY initials`() {
        val exact = ap.taintAlg.leafNode(ap.taintLeaves(ap.markSetOf(t), MarkSet.EMPTY))
        val any = ap.taintAlg.leafNode(ap.taintLeaves(MarkSet.EMPTY, ap.markSetOf(t)))
        val ordinary = ap.taintTree(b, Layer.NORMAL, ap.taintAlg.mergeAdd(exact,
            ap.taintAlg.prepend(ap, ap.path(a, null), any)))
        val result = answers(ordinary, Run1Slot.Base(b), RequestMarkSet.of(t))
        assertEquals(setOf(Tail.EXACT, Tail.STAR), result.mapTo(linkedSetOf()) { it.tail })
        val static = ap.taintTree(STATIC, Layer.NORMAL, ap.taintAlg.prepend(ap, ap.path(listOf(c, a)),
            ap.taintAlg.mergeAdd(exact, any)))
        assertEquals(setOf(Tail.EXACT, Tail.STAR), answers(static, Run1Slot.StaticField(c, a),
            RequestMarkSet.of(t)).mapTo(linkedSetOf()) { it.tail })
    }

    @Test
    fun `FLOW slot geometry ignores field and mark exclusions and has no concrete answers`() {
        val input = f.fact(STATIC, listOf(c), Tail.STAR, MarkSlot.Star(ap.markSetOf(t)),
            ExclusionSet.of(a), Layer.NORMAL)
        val slot = Run1Slot.StaticField(c, a)
        assertTrue(slotMatches(slot, input))
        assertFalse(slotMatches(Run1Slot.StaticField(d, a), input))
        assertTrue(answers(input, slot, RequestMarkSet.of(t)).isEmpty())
    }
}
