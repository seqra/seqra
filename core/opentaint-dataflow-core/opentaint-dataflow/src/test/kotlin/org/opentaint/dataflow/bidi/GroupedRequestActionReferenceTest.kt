package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.ReferenceRequestAction as RequestAction

import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.BoundedDenotation
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.*
import kotlin.test.*

/** N1 filtering is compared with the full raw pair relation and independently decoded actions. */
class GroupedRequestActionReferenceTest {
    private data class Action(val premise: Pattern, val kind: RequestKind, val result: Pattern?, val climb: Pattern?)

    @Test
    fun `selective grouped visitor preserves every Answer and Climb across full caller and link annotations`(): Unit {
        val f = StoreFixtures()
        val x = AccessPathBase.Argument(0)
        val a = f.field("a")
        val b = f.field("b")
        val cls = f.manager.accessors.index(ClassStaticAccessor("Box"))
        val t = f.mark("T")
        val u = f.mark("U")
        val fresh = f.mark("fresh")
        val universe = BoundedDenotation(listOf(x, STATIC), listOf(a, b, cls, f.field("fresh")),
            listOf(t, u, fresh), 3)
        val requests = buildList {
            for (base in listOf(x, STATIC)) for (path in listOf(emptyList(), listOf(a), listOf(b), listOf(cls))) {
                val initial = f.manager.initial(f.pattern(base, path, Tail.STAR, MarkSlot.STAR))
                add(initial to RequestKind.Mark(t))
                add(initial to RequestKind.Position(f.manager.path(listOf(cls, a))!!))
                add(initial to RequestKind.Mark(u))
                add(initial to RequestKind.Position(f.manager.path(listOf(cls))!!))
            }
        }
        fun admitted(initial: InitialAp, kind: RequestKind, added: Pattern): Boolean {
            val query = if (kind is RequestKind.Position)
                Pattern(PathFact(STATIC, kind.path.toList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty)
            else initial.toPattern()
            val positions = universe.spatial(query)
            return universe.spatial(added).any { it in positions }
        }
        fun expected(initial: InitialAp, kind: RequestKind, added: Pattern, caller: CallerRef): Action? {
            if (!admitted(initial, kind, added)) return null
            val origin = initial.toPattern()
            return when (kind) {
                is RequestKind.Mark -> when (val mark = added.fact.mark) {
                    is MarkSlot.Concrete -> if (mark.mark != kind.mark) null else {
                        val answer = when {
                            initial.base == STATIC && added.fact.path.take(initial.pathData.size) == initial.pathData.toList() ->
                                if (added.fact.tail == Tail.ANY_TAINT)
                                    added.copy(fact = added.fact.copy(tail = Tail.ANY), exclusion = ExclusionSet.Empty)
                                else added
                            added.fact.tail == Tail.EXACT && added.fact.path == origin.fact.path ->
                                origin.copy(fact = origin.fact.copy(tail = Tail.EXACT, mark = mark))
                            else -> origin.copy(fact = origin.fact.copy(mark = mark))
                        }
                        Action(origin, kind, answer, null)
                    }
                    is MarkSlot.Star -> if (kind.mark in mark.excluded) null
                        else Action(origin, kind, null, (caller.premise as InitialAp).toPattern())
                }
                is RequestKind.Position -> when {
                    added.fact.path.take(kind.path.size) == kind.path.toList() ->
                        Action(origin, kind, Pattern(PathFact(STATIC, kind.path.toList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty), null)
                    (caller.premise as InitialAp).base == STATIC -> Action(origin, kind, null, caller.premise.toPattern())
                    else -> null
                }
            }
        }
        for (reverse in listOf(false, true)) {
            val store = RequestStore(f.cancellation)
            for ((i, kind) in if (reverse) requests.asReversed() else requests) {
                assertTrue(store.add(i, kind))
                assertFalse(store.add(i, kind))
            }
            for (base in listOf(x, STATIC)) for (path in listOf(emptyList(), listOf(a), listOf(b), listOf(cls), listOf(cls, a)))
                for (tail in listOf(Tail.EXACT, Tail.ANY, Tail.ANY_TAINT, Tail.STAR)) {
                    val concrete = listOf(MarkSlot.Concrete(t), MarkSlot.Concrete(u))
                    val abstract = listOf(MarkSlot.STAR, MarkSlot.Star(MarkSet(intArrayOf(t.id))))
                    val marks = when (tail) {
                        Tail.ANY_TAINT, Tail.EXACT -> concrete
                        Tail.STAR -> abstract
                        Tail.ANY -> concrete + abstract
                    }
                    for (mark in marks) for (exclusion in if (tail in listOf(Tail.STAR, Tail.ANY_TAINT))
                        listOf(ExclusionSet.Empty, ExclusionSet.of(cls), ExclusionSet.of(a)) else listOf(ExclusionSet.Empty)) {
                        val added = f.pattern(base, path, tail, mark, exclusion)
                        for (context in listOf("caller-A", "caller-B")) for (callerBase in listOf(x, STATIC))
                            for (callerLayer in Layer.entries) for (linkLayer in Layer.entries) {
                                val initial = f.manager.initial(f.pattern(callerBase, tail = Tail.STAR,
                                    mark = if (mark is MarkSlot.Star) MarkSlot.STAR else MarkSlot.Concrete(t)))
                                val caller = CallerRef(f.key(context), initial, callerLayer, f.inst(1))
                                val want = requests.mapNotNull { (i, kind) -> expected(i, kind, added, caller) }.toSet()
                                val got = LinkedHashSet<Action>()
                                store.visitActionCandidates(added) { i, kind ->
                                    when (val action = f.ops.referenceRequestAction(i, kind, added, caller)) {
                                        RequestAction.None -> Unit
                                        is RequestAction.Answer -> got += Action(i.toPattern(), kind, action.initial.toPattern(), null)
                                        is RequestAction.Climb -> got += Action(i.toPattern(), kind, null, action.premise.toPattern())
                                    }
                                }
                                assertEquals(want, got, "added=$added context=$context caller=$callerLayer link=$linkLayer reverse=$reverse")
                            }
                    }
                }
        }
    }
}
