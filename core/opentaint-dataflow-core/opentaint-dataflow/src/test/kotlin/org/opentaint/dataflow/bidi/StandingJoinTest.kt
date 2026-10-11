package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.*
import kotlin.test.*

class StandingJoinTest {
    private fun <T> permutations(values: List<T>): Sequence<List<T>> = sequence {
        if (values.isEmpty()) yield(emptyList())
        else for (index in values.indices) for (suffix in permutations(values.filterIndexed { i, _ -> i != index }))
            yield(listOf(values[index]) + suffix)
    }

    @Test fun `two sided owned joins see each matching pair once in every arrival order`(): Unit {
        data class A(val value: Int)
        data class B(val value: Int)
        val events = listOf<Any>(A(1), A(2), A(9), B(1), B(2), B(8))
        val expected = setOf(A(1) to B(1), A(2) to B(2))
        var schedules = 0
        for (schedule in permutations(events)) {
            val a = LinkedHashSet<A>()
            val b = LinkedHashSet<B>()
            val output = ArrayList<Pair<A, B>>()
            val join = StandingJoin<A, B>({ x -> b.asSequence().filter { it.value == x.value } },
                { y -> a.asSequence().filter { it.value == y.value } }) { x, y -> output += x to y }
            for (event in schedule + schedule) when (event) {
                is A -> if (a.add(event)) join.newA(event)
                is B -> if (b.add(event)) join.newB(event)
            }
            assertEquals(expected, output.toSet())
            assertEquals(expected.size, output.size)
            schedules++
        }
        assertEquals(720, schedules)
    }

    @Test fun `request and link indexes form a symmetric standing join including second callers`(): Unit {
        val fixture = StoreFixtures()
        val m = fixture.manager
        val x = AccessPathBase.Argument(0)
        val t = fixture.mark("T")
        val u = fixture.mark("U")
        val f = fixture.field("f")
        val g = fixture.field("g")
        val cls = m.accessors.index(org.opentaint.dataflow.ap.ifds.ClassStaticAccessor("Box"))
        val root = m.initial(fixture.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR))
        val staticRoot = m.initial(fixture.pattern(STATIC, tail = Tail.STAR, mark = MarkSlot.STAR))
        val reqs = listOf(root to RequestKind.Mark(t), root to RequestKind.Mark(u),
            staticRoot to RequestKind.Position(m.path(listOf(cls, f))!!))
        val first = CallerRef(fixture.key("one"), root, Layer.NORMAL, fixture.inst(10))
        val second = CallerRef(fixture.key("two"), root, Layer.DEMAND, fixture.inst(11))
        val links = listOf(
            Link(fixture.pattern(x, listOf(f), Tail.EXACT, MarkSlot.Concrete(t)), Layer.NORMAL, first),
            Link(fixture.pattern(x, listOf(f), Tail.EXACT, MarkSlot.Concrete(t)), Layer.NORMAL, second),
            Link(fixture.pattern(STATIC, listOf(cls, f, g), Tail.EXACT, MarkSlot.Concrete(u)), Layer.NORMAL, first))
        val expected = reqs.flatMap { request -> links.filter { link ->
            val query = when (val kind = request.second) {
                is RequestKind.Mark -> request.first.toPattern()
                is RequestKind.Position -> fixture.pattern(STATIC, kind.path.toList(), Tail.STAR, MarkSlot.STAR)
            }
            overlap(link.addedFact, query)
        }.map { request to it } }.toSet()
        for (schedule in permutations((0..5).toList())) {
            val requests = RequestStore()
            val added = AddedFactStore(m)
            val observed = ArrayList<Pair<Pair<InitialAp, RequestKind>, Link>>()
            val join = StandingJoin<Pair<InitialAp, RequestKind>, Link>({ (i, kind) ->
                when (kind) {
                    is RequestKind.Mark -> added.overlapping(i.base, i.path)
                    is RequestKind.Position -> added.overlapping(STATIC, kind.path)
                }
            }, { requests.overlapping(it.addedFact) }) { request, link -> observed += request to link }
            for (event in schedule + schedule) {
                if (event < reqs.size) {
                    val request = reqs[event]
                    if (requests.add(request.first, request.second)) join.newA(request)
                } else {
                    val link = links[event - reqs.size]
                    val fact = m.factsOf(link.addedFact.fact, link.addedFact.exclusion, link.linkLayer)
                    added.add(link.caller, link.linkLayer, fact)?.let { join.newB(link) }
                }
            }
            assertEquals(expected, observed.toSet())
            assertEquals(expected.size, observed.size)
        }
    }

    @Test fun `callback failures stop the join and keep original positional lambda signatures`(): Unit {
        val calls = ArrayList<Pair<Int, String>>()
        val join = StandingJoin<Int, String>({ sequenceOf("A", "B") }, { sequenceOf(1, 2) },
            { a, b -> calls += a to b; if (b == "A") error("stop") })
        assertFailsWith<IllegalStateException> { join.newA(1) }
        assertEquals(listOf(1 to "A"), calls)
        calls.clear()
        assertFailsWith<IllegalStateException> { join.newB("A") }
        assertEquals(listOf(1 to "A"), calls)
    }
}
