package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.ir.api.common.cfg.CommonInst
import kotlin.test.*

class FormApplierTest {
    private val fixture = StoreFixtures()
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val t = fixture.mark("T")
    private val mark = MarkSlot.Concrete(t)
    private val at = Place(fixture.inst(1), statementEdge = true, sources = true)
    private data class Value(val base: AccessPathBase, val events: List<String> = emptyList())
    private class Algebra : FactAlgebra<String, Value> {
        val calls = ArrayList<String>()
        val filters = HashMap<TypeFilter, Pair<String, Boolean>>()
        var allow = true
        var copies = 1
        override fun base(c: Value) = c.base
        override fun filter(c: Value, filter: TypeFilter): Value? {
            val (name, admitted) = filters.getValue(filter)
            calls += "filter:$name"
            return if (admitted) c.copy(events = c.events + name) else null
        }
        override fun applyEdge(me: MicroEdge, c: Value, premise: String, statementEdge: Boolean,
                               out: (String, Value) -> Unit) {
            calls += "apply:$statementEdge"
            repeat(copies) { out(premise, Value(me.edge.to.base, c.events + "edge")) }
        }
        override fun conjunction(cj: ConjunctiveEdge, premise: String, c: Value, node: CommonInst,
                                 out: (String, Value) -> Unit) {
            calls += "conjunction:${node.location.index}"
            out("joint", Value(cj.target.base, c.events + "conjunction"))
        }
        override fun allowsSource(node: CommonInst, me: MicroEdge): Boolean {
            calls += "allows"
            return allow
        }
        override fun sourceHit(node: CommonInst, me: MicroEdge) { calls += "hit" }
        fun typeFilter(name: String, admitted: Boolean = true): TypeFilter =
            TypeFilter(object : FactTypeChecker.FactApFilter {
                override fun check(accessor: org.opentaint.dataflow.ap.ifds.Accessor) = FactTypeChecker.FilterResult.Accept
            }).also { filters[it] = name to admitted }
    }
    private fun edge(from: AccessPathBase = x, to: AccessPathBase = y) = MicroEdge.of(
        PathEdge(PathFact(from, emptyList(), Tail.EXACT, mark), PathFact(to, emptyList(), Tail.EXACT, mark), ExclusionSet.Empty))
    private fun conjunction() = ConjunctiveEdge(listOf(fixture.pattern(x, mark = mark), fixture.pattern(y, mark = mark)),
        PathFact(y, emptyList(), Tail.EXACT, mark))

    @Test fun `statement keeps untouched input separately and still reads every conjunction`(): Unit {
        val alg = Algebra()
        val rejected = alg.typeFilter("operand", false)
        val summary = StatementSummary(setOf(y), listOf(edge(y, x)), listOf(conjunction()), mapOf(x to rejected))
        val generated = ArrayList<Pair<String, Value>>()
        val unchanged = ArrayList<Pair<String, Value>>()
        val input = Value(x)
        FormApplier(alg).statement(summary, "premise", input, at,
            { p, f -> generated += p to f }, { p, f -> unchanged += p to f })
        assertEquals(listOf("joint" to Value(y, listOf("conjunction"))), generated)
        assertEquals(listOf("premise" to input), unchanged)
        assertEquals(listOf("conjunction:1"), alg.calls)
    }

    @Test fun `touched statement filters operand then each result and has no implicit identity`(): Unit {
        val alg = Algebra()
        val operand = alg.typeFilter("operand")
        val result = alg.typeFilter("result")
        val summary = StatementSummary(setOf(x), listOf(edge()), emptyList(), mapOf(x to operand), mapOf(y to result))
        val observed = ArrayList<Pair<String, Value>>()
        FormApplier(alg).statement(summary, "p", Value(x), at, { p, f -> observed += p to f })
        assertEquals(listOf("p" to Value(y, listOf("operand", "edge", "result"))), observed)
        assertEquals(listOf("filter:operand", "apply:true", "filter:result"), alg.calls)
        observed.clear()
        FormApplier(alg).statement(StatementSummary(setOf(x), emptyList(), emptyList(), emptyMap()), "p", Value(x), at,
            { p, f -> observed += p to f })
        assertTrue(observed.isEmpty())
    }

    @Test fun `operand rejection stops stage edges conjunctions and source hooks`(): Unit {
        val alg = Algebra()
        val reject = alg.typeFilter("reject", false)
        val summary = StatementSummary(setOf(x), listOf(edge()), listOf(conjunction()), mapOf(x to reject))
        FormApplier(alg).stage(summary, "p", Value(x), at) { _, _, _ -> fail("Rejected input cannot produce results") }
        assertEquals(listOf("filter:reject"), alg.calls)
    }

    @Test fun `stage gives only generated values and identifies their producing edges`(): Unit {
        val alg = Algebra()
        val me = edge()
        val summary = StatementSummary(emptySet(), listOf(me, edge(y, x)), listOf(conjunction()), emptyMap())
        val observed = ArrayList<Pair<Value, MicroEdge?>>()
        FormApplier(alg).stage(summary, "p", Value(x), at) { _, f, producer -> observed += f to producer }
        assertEquals(listOf(Value(y, listOf("edge")), Value(y, listOf("conjunction"))), observed.map { it.first })
        assertSame(me, observed[0].second)
        assertNull(observed[1].second)
    }

    @Test fun `conjunction result filters apply for touched and untouched literal bases`(): Unit {
        for (touched in listOf(emptySet(), setOf(x))) for (admitted in listOf(false, true)) {
            val alg = Algebra()
            val output = alg.typeFilter("lhs", admitted)
            val summary = StatementSummary(touched, emptyList(), listOf(conjunction()), emptyMap(), mapOf(y to output))
            val generated = ArrayList<Value>()
            val unchanged = ArrayList<Value>()
            FormApplier(alg).statement(summary, "p", Value(x), at,
                { _, f -> generated += f }, { _, f -> unchanged += f })
            assertEquals(if (admitted) listOf(Value(y, listOf("conjunction", "lhs"))) else emptyList(), generated)
            assertEquals(if (x in touched) emptyList() else listOf(Value(x)), unchanged)
            assertEquals(listOf("conjunction:1", "filter:lhs"), alg.calls)
        }
    }

    @Test fun `source selection precedes transfer and the first hit precedes filtering and delivery`(): Unit {
        val alg = Algebra().also { it.copies = 2 }
        val source = MicroEdge.of(PathEdge(ZERO_FACT, PathFact(y, emptyList(), Tail.EXACT, mark), ExclusionSet.Empty))
        val reject = alg.typeFilter("result", false)
        val summary = StatementSummary(setOf(AccessPathBase.Zero), listOf(source), emptyList(), emptyMap(), mapOf(y to reject))
        FormApplier(alg).stage(summary, "p", Value(AccessPathBase.Zero), at) { _, _, _ -> fail("Result rejected") }
        assertEquals(listOf("allows", "apply:true", "hit", "filter:result", "filter:result"), alg.calls)
        alg.calls.clear()
        alg.allow = false
        FormApplier(alg).stage(summary, "p", Value(AccessPathBase.Zero), at) { _, _, _ -> fail("Source not selected") }
        assertEquals(listOf("allows"), alg.calls)
    }

    @Test fun `gen applies matching edges without filters source hooks or implicit input emission`(): Unit {
        val alg = Algebra()
        val source = MicroEdge.of(PathEdge(ZERO_FACT, PathFact(y, emptyList(), Tail.EXACT, mark), ExclusionSet.Empty))
        val out = ArrayList<Value>()
        FormApplier(alg).gen(listOf(source, edge()), "p", Value(AccessPathBase.Zero)) { _, f -> out += f }
        assertEquals(listOf(Value(y, listOf("edge"))), out)
        assertEquals(listOf("apply:false"), alg.calls)
    }

    @Test fun `source with no output has no hit and a place with sources off skips both hooks`(): Unit {
        val alg = Algebra().also { it.copies = 0 }
        val source = MicroEdge.of(PathEdge(ZERO_FACT, PathFact(y, emptyList(), Tail.EXACT, mark), ExclusionSet.Empty))
        val summary = StatementSummary(emptySet(), listOf(source), emptyList(), emptyMap())
        FormApplier(alg).stage(summary, "p", Value(AccessPathBase.Zero), at) { _, _, _ -> fail("No output") }
        assertEquals(listOf("allows", "apply:true"), alg.calls)
        alg.calls.clear()
        alg.copies = 1
        alg.allow = false
        val observed = ArrayList<Value>()
        FormApplier(alg).stage(summary, "p", Value(AccessPathBase.Zero), at.copy(sources = false)) { _, c, _ -> observed += c }
        assertEquals(listOf(Value(y, listOf("edge"))), observed)
        assertEquals(listOf("apply:true"), alg.calls)
    }
}
