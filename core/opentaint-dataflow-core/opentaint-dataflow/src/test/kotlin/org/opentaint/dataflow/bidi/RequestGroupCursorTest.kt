package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.ReferenceRequestAction as RequestAction
import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.*
import kotlin.test.*

class RequestGroupCursorTest {
    private val x = AccessPathBase.Argument(0)
    private fun policy(f: StoreFixtures, base: AccessPathBase = x, path: List<Int> = emptyList()) =
        f.manager.initial(base, f.manager.path(path), Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
    private fun drain(f: StoreFixtures, i: InitialAp, marks: RequestMarkSelection, g: Facts, ref: CallerRef):
        Triple<Set<InitialAp>, Set<TaintMark>, RequestGroupCursor> {
        val cursor = f.ops.requestGroup(i, marks, g, ref)
        val answers = LinkedHashSet<InitialAp>()
        val climbs = LinkedHashSet<TaintMark>()
        var steps = 0
        while (!cursor.isComplete) {
            assertTrue(cursor.step(1, answers::add, climbs::add) <= 1)
            check(++steps < 100_000)
        }
        return Triple(answers, climbs, cursor)
    }

    @Test
    fun `group set actions equal independent scalar expansion for every admitted path tail annotation`(): Unit {
        val f = StoreFixtures()
        val a = f.field("a")
        val b = f.field("b")
        val cls = f.manager.accessors.index(ClassStaticAccessor("Box"))
        val t = f.mark("T")
        val u = f.mark("U")
        val marks = RequestMarkSet.fromIds(intArrayOf(t.id, u.id))
        val paths = listOf(emptyList(), listOf(a), listOf(a, b), listOf(b), listOf(cls), listOf(cls, a))
        for (base in listOf(x, STATIC)) for (layer in Layer.entries) for (path in paths) {
            val shapes = listOf(
                Triple(Tail.EXACT, MarkSlot.Concrete(t), ExclusionSet.Empty),
                Triple(if (layer == Layer.NORMAL) Tail.ANY_TAINT else Tail.ANY, MarkSlot.Concrete(t),
                    if (layer == Layer.NORMAL) ExclusionSet.of(a) else ExclusionSet.Empty),
                Triple(if (layer == Layer.NORMAL) Tail.STAR else Tail.ANY, MarkSlot.Star(MarkSet(intArrayOf(u.id))),
                    if (layer == Layer.NORMAL) ExclusionSet.of(a) else ExclusionSet.Empty))
            for ((tail, mark, e) in shapes) {
                val facts = f.fact(base, path, tail, mark, e, layer)
                for (requestPath in paths) {
                    val initial = policy(f, base, requestPath)
                    val ref = CallerRef(f.key("caller"), policy(f), Layer.DEMAND, f.inst(1))
                    val expectedAnswers = LinkedHashSet<InitialAp>()
                    val expectedClimbs = LinkedHashSet<TaintMark>()
                    for (leaf in f.ops.leaves(facts)) if (overlap(initial.toPattern(), leaf)) {
                        marks.forEachMark { requested ->
                            when (val action = f.ops.referenceRequestAction(initial, RequestKind.Mark(requested), leaf, ref)) {
                                is RequestAction.Answer -> expectedAnswers += action.initial
                                is RequestAction.Climb -> expectedClimbs += (action.request as RequestKind.Mark).mark
                                RequestAction.None -> Unit
                            }
                        }
                    }
                    val actual = drain(f, initial, marks, facts, ref)
                    assertEquals(expectedAnswers, actual.first, "Answer: $base $path $tail $requestPath $layer")
                    assertEquals(expectedClimbs, actual.second, "Climb: $base $path $tail $requestPath $layer")
                }
            }
        }
    }

    @Test
    fun `excluded ancestor cannot hide an explicit FLOW tip and repeated witnesses climb once`(): Unit {
        val f = StoreFixtures()
        val a = f.field("a")
        val b = f.field("b")
        val t = f.mark("T")
        val u = f.mark("U")
        val i = policy(f, path = listOf(a))
        val ref = CallerRef(f.key("caller"), policy(f), Layer.NORMAL, f.inst(1))
        val root = f.manager.flowTree(x, Layer.NORMAL, ExclusionSet.of(a), MarkSet(intArrayOf(u.id)), FlowAlgebra.LEAF_NODE)
        val marks = RequestMarkSet.fromIds(intArrayOf(t.id, u.id))
        assertTrue(drain(f, i, marks, root, ref).second.isEmpty())
        val explicit = f.manager.flowTree(x, Layer.NORMAL, ExclusionSet.of(a), root.markExclusion,
            FlowAlgebra.node(FlowLeaf.LEAF, intArrayOf(a), arrayOf(
                FlowAlgebra.node(FlowLeaf.LEAF, intArrayOf(b), arrayOf(FlowAlgebra.LEAF_NODE)))))
        val actual = drain(f, i, marks, explicit, ref)
        assertEquals(setOf(t), actual.second)
        assertTrue(actual.first.isEmpty())
        assertEquals(2L, actual.third.geometryTests)
    }

    @Test
    fun `one concrete TAINT leaf probes wide old row without scanning unrelated requested marks`(): Unit {
        val f = StoreFixtures()
        val store = RequestStore(f.cancellation)
        val i = policy(f)
        val admission = store.addMarks(i, RequestMarkSet.fromIds(IntArray(500) { it + 1 }))
        val selection = store.markMembers(admission.row, admission.horizon)
        val ref = CallerRef(f.key("caller"), f.manager.zero, Layer.DEMAND, f.inst(1))
        val exact = f.fact(mark = MarkSlot.Concrete(TaintMark(250)))
        val actual = drain(f, i, selection, exact, ref)
        assertEquals(setOf(f.manager.initial(f.pattern(mark = MarkSlot.Concrete(TaintMark(250))))), actual.first)
        assertEquals(1L, actual.third.memberScans)
        val future = f.fact(mark = MarkSlot.Concrete(TaintMark(501)))
        store.add(i, RequestKind.Mark(TaintMark(501)))
        assertTrue(drain(f, i, selection, future, ref).first.isEmpty())
    }

    @Test
    fun `same concrete mark keeps exact chain and distinct static answer paths`(): Unit {
        val f = StoreFixtures()
        val m = f.manager
        val a = f.field("a")
        val b = f.field("b")
        val cls = m.accessors.index(ClassStaticAccessor("Box"))
        val t = f.mark("T")
        val exact = m.taintAlg.leafNode(m.taintLeaves(m.markSetOf(t), MarkSet.EMPTY))
        val any = m.taintAlg.leafNode(m.taintLeaves(MarkSet.EMPTY, m.markSetOf(t)))
        val subtree = m.taintAlg.node(TaintLeaves.EMPTY, intArrayOf(a, b).sortedArray(),
            if (a < b) arrayOf(exact, any) else arrayOf(any, exact))
        val statics = m.taintTree(STATIC, Layer.NORMAL,
            m.taintAlg.node(TaintLeaves.EMPTY, intArrayOf(cls), arrayOf(subtree)), ExclusionSet.of(a))
        val ref = CallerRef(f.key("caller"), m.zero, Layer.NORMAL, f.inst(1))
        val actual = drain(f, policy(f, STATIC), RequestMarkSet.of(t), statics, ref)
        assertEquals(setOf(
            m.initial(STATIC, m.path(listOf(cls, a)), Tail.EXACT, ExclusionSet.Empty, MarkSlot.Concrete(t)),
            m.initial(STATIC, m.path(listOf(cls, b)), Tail.ANY, ExclusionSet.Empty, MarkSlot.Concrete(t))), actual.first)
        val regular = m.taintTree(x, Layer.NORMAL,
            m.taintAlg.node(exact.leaf, intArrayOf(a), arrayOf(exact)))
        assertEquals(setOf(m.initial(x, null, Tail.EXACT, ExclusionSet.Empty, MarkSlot.Concrete(t)),
            m.initial(x, null, Tail.STAR, ExclusionSet.Empty, MarkSlot.Concrete(t))),
            drain(f, policy(f), RequestMarkSet.of(t), regular, ref).first)
    }
}
