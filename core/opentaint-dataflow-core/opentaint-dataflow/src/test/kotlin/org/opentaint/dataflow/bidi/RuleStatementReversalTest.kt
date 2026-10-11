package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RuleStatementReversalTest {
    private val fixture = StoreFixtures()
    private val m = fixture.manager
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val z = AccessPathBase.Argument(1)
    private val f = fixture.field("f")
    private val g = fixture.field("g")
    private val h = fixture.field("h")
    private val t = fixture.mark("T")
    private val u = fixture.mark("U")
    private val mt = MarkSlot.Concrete(t)
    private val mu = MarkSlot.Concrete(u)
    private val rule = object : CommonTaintConfigurationSink {
        override val id = "sink"
        override val meta = object : CommonTaintConfigurationSinkMeta {
            override val message = "test sink"
            override val severity = CommonTaintConfigurationSinkMeta.Severity.Warning
        }
    }
    private fun p(base: AccessPathBase = x, path: List<Int> = emptyList(), tail: Tail = Tail.EXACT,
                  mark: MarkSlot = mt) = Pattern(PathFact(base, path, tail, mark), ExclusionSet.Empty)
    private fun source(base: AccessPathBase, path: List<Int>, tail: Tail = Tail.EXACT, mark: MarkSlot = mt) =
        MicroEdge.of(PathEdge(ZERO_FACT, PathFact(base, path, tail, mark), ExclusionSet.Empty))
    private fun statement(touched: Set<AccessPathBase>, edges: List<MicroEdge>,
                          conjunctions: List<ConjunctiveEdge> = emptyList(),
                          filters: Map<AccessPathBase, TypeFilter> = emptyMap(),
                          results: Map<AccessPathBase, TypeFilter> = emptyMap()) =
        StatementSummary(touched, edges, conjunctions, filters, results)
    private val accept = TypeFilter(object : FactTypeChecker.FactApFilter {
        override fun check(accessor: org.opentaint.dataflow.ap.ifds.Accessor) = FactTypeChecker.FilterResult.Accept
    })

    @Test
    fun `sink constructors require nonempty concrete exact or may literals`(): Unit {
        assertFails { SinkRule(rule, 0, emptyList(), emptyList()) }
        for (tail in listOf(Tail.STAR, Tail.ANY_TAINT)) {
            assertFails { SinkRule(rule, 0, listOf(p(tail = tail)), emptyList()) }
            assertFails { SinkRule(rule, 0, listOf(p(), p(tail = tail)), emptyList()) }
        }
        for (tail in listOf(Tail.EXACT, Tail.ANY)) for (mark in listOf(MarkSlot.STAR, MarkSlot.Star(MarkSet(intArrayOf(t.id))))) {
            assertFails { SinkRule(rule, 0, listOf(p(tail = tail, mark = mark)), emptyList()) }
        }
        for (tail in listOf(Tail.EXACT, Tail.ANY)) {
            val sink = SinkRule(rule, 3, listOf(p(tail = tail)), emptyList())
            assertFalse(sink.unconditional)
            assertFalse(sink.conjunctive)
            assertEquals(listOf(p(tail = tail)), sink.seedPatterns())
        }
    }

    @Test
    fun `zero sink is unconditional while every conjunction literal keeps its own exact or may seed`(): Unit {
        val zero = SinkRule(rule, 0, listOf(ZERO_PATTERN), emptyList())
        assertTrue(zero.unconditional)
        assertFalse(zero.conjunctive)
        assertTrue(zero.seedPatterns().isEmpty())
        val exact = p(path = listOf(f))
        val any = p(y, listOf(g), Tail.ANY, mu)
        val sink = SinkRule(rule, 2, listOf(exact, any, exact), emptyList())
        assertFalse(sink.unconditional)
        assertTrue(sink.conjunctive)
        assertEquals(listOf(exact, any, exact), sink.seedPatterns())
        assertEquals(3, sink.seedPatterns().size)
        for (seed in sink.seedPatterns()) {
            val facts = fixture.ops.targetTree(seed.fact, Layer.NORMAL)
            assertEquals(if (seed.fact.tail == Tail.ANY) Layer.DEMAND else Layer.NORMAL, facts.layer)
            assertEquals(setOf(seed), fixture.ops.leaves(facts).toSet())
        }
    }

    @Test
    fun `sink owns its pattern paths and list containers while retaining rule and end micro-edge identities`(): Unit {
        val path = mutableListOf(f, g)
        val patterns = mutableListOf(p(path = path), p(y, listOf(h), Tail.ANY, mu))
        val end = source(z, listOf(h), Tail.ANY_TAINT)
        val endEdges = mutableListOf(end)
        val sink = SinkRule(rule, 4, patterns, endEdges)
        path[0] = h
        patterns.clear()
        endEdges.clear()
        val expected = listOf(p(path = listOf(f, g)), p(y, listOf(h), Tail.ANY, mu))
        assertEquals(expected, sink.patterns)
        assertSame(rule, sink.rule)
        assertSame(end, sink.endFacts.single())
        assertSame(end.edge, sink.endFacts.single().edge)
        assertSame(end.forward, sink.endFacts.single().forward)
        try { (sink.patterns as? MutableList<Pattern>)?.clear() } catch (_: UnsupportedOperationException) { }
        try { (sink.seedPatterns() as? MutableList<Pattern>)?.clear() } catch (_: UnsupportedOperationException) { }
        try { (sink.patterns[0].fact.path as? MutableList<Int>)?.set(0, h) } catch (_: UnsupportedOperationException) { }
        try { (sink.endFacts as? MutableList<MicroEdge>)?.clear() } catch (_: UnsupportedOperationException) { }
        assertEquals(expected, sink.patterns)
        assertEquals(expected, sink.seedPatterns())
        assertSame(end, sink.endFacts.single())
    }

    @Test
    fun `rule reversal reverses statement and GEN edges separately drops filters and retains sink triggers`(): Unit {
        val edge = MicroEdge.of(PathEdge(p(x, listOf(f)).fact, p(y, listOf(g), mark = mu).fact, ExclusionSet.Empty))
        val end = source(z, listOf(h), Tail.ANY_TAINT)
        val body = statement(setOf(x), listOf(edge), filters = mapOf(x to accept), results = mapOf(y to accept))
        val ends = statement(emptySet(), listOf(end), filters = mapOf(AccessPathBase.Zero to accept), results = mapOf(z to accept))
        val sink = SinkRule(rule, 1, listOf(p()), listOf(end))
        val reversed = RuleStatement(body, ends, listOf(sink)).reversed()
        assertEquals(PathEdge(p(y, listOf(g), mark = mu).fact, p(x, listOf(f)).fact, ExclusionSet.Empty),
            reversed.summary.edges.single { !it.isIdentity }.edge)
        val reverseEnd = reversed.endFacts.edges.single()
        assertEquals(PathEdge(p(z, listOf(h), Tail.ANY).fact, ZERO_FACT, ExclusionSet.Empty), reverseEnd.edge)
        assertSame(edge.forward, reversed.summary.edges.single { !it.isIdentity }.forward)
        assertSame(end.forward, reverseEnd.forward)
        assertFalse(reverseEnd.conjunctive)
        assertFalse(reverseEnd.may)
        assertTrue(reversed.summary.typeFilters.isEmpty() && reversed.summary.resultFilters.isEmpty())
        assertTrue(reversed.endFacts.typeFilters.isEmpty() && reversed.endFacts.resultFilters.isEmpty())
        assertTrue(reversed.endFacts.touched.isEmpty())
        assertTrue(reversed.endFacts.conjunctions.isEmpty())
        assertSame(sink, reversed.sinks.single())
        assertSame(end, reversed.sinks.single().endFacts.single())
    }

    @Test
    fun `GEN target pass-over adds only targets not already touched by reversed statement forms`(): Unit {
        val body = statement(setOf(x), listOf(identityEdge(x), MicroEdge.of(starEdge(x, listOf(f), y, listOf(g)))))
        val endFacts = statement(emptySet(), listOf(source(x, listOf(f)), source(y, listOf(g)), source(z, listOf(h))))
        val reversed = RuleStatement(body, endFacts, emptyList()).reversed()
        val identities = reversed.summary.edges.filter { it.isIdentity }
        assertEquals(setOf(x, y, z), identities.map { it.edge.from.base }.toSet())
        for (base in listOf(x, y, z)) assertEquals(1, identities.count { it.edge.from.base == base })
        assertEquals(setOf(x, y, z), reversed.summary.touched)
        assertEquals(3, reversed.endFacts.edges.size)
        assertTrue(reversed.endFacts.edges.none { it.isIdentity })
        assertTrue(reversed.endFacts.edges.all { it.edge.to == ZERO_FACT })
    }

    @Test
    fun `rule forms own the sink list and preserve cached forward micro-edge and conjunction objects`(): Unit {
        val edge = identityEdge(x)
        val conjunction = ConjunctiveEdge(listOf(p(x), p(z, mark = mu)), p(y, tail = Tail.ANY_TAINT).fact)
        val body = statement(setOf(x, z, y), listOf(edge), listOf(conjunction))
        val first = SinkRule(rule, 0, listOf(p()), emptyList())
        val second = SinkRule(rule, 1, listOf(p(y, tail = Tail.ANY)), emptyList())
        val sinks = mutableListOf(first, second)
        val form = RuleStatement(body, StatementSummary.EMPTY, sinks)
        sinks.clear()
        assertSame(body, form.summary)
        assertSame(edge, form.summary.edges.single())
        assertSame(conjunction, form.summary.conjunctions.single())
        assertEquals(listOf(first, second), form.sinks)
        val reversed = form.reversed()
        assertTrue(reversed.summary.conjunctions.isEmpty())
        assertEquals(2, reversed.summary.edges.count { it.conjunctive })
        assertSame(first, reversed.sinks[0])
        assertSame(second, reversed.sinks[1])
        try { (form.sinks as? MutableList<SinkRule>)?.clear() } catch (_: UnsupportedOperationException) { }
        try { (reversed.sinks as? MutableList<SinkRule>)?.clear() } catch (_: UnsupportedOperationException) { }
        assertEquals(listOf(first, second), form.sinks)
        assertEquals(listOf(first, second), reversed.sinks)
    }

    @Test
    fun `exit entry marks are owned and removed only from this and arguments while G2 metadata stays forward`(): Unit {
        val marks = linkedSetOf(u, t)
        val form = RuleStatement(statement(setOf(x), listOf(identityEdge(x))), StatementSummary.EMPTY, emptyList())
        val exit = ExitRules(form, globalStateDrop = true, entryMarks = marks)
        marks.clear()
        assertEquals(setOf(t, u), exit.entryMarks)
        val expected = MarkSet(intArrayOf(t.id, u.id))
        for (base in listOf(AccessPathBase.This, x, z, AccessPathBase.Argument(63)))
            assertEquals(expected, exit.entryMarkRemoval(base))
        for (base in listOf(AccessPathBase.Zero, AccessPathBase.Return, AccessPathBase.Exception, STATIC,
            AccessPathBase.LocalVar(0), AccessPathBase.Constant("str", "value"), AccessPathBase.CleanerTemporary()))
            assertNull(exit.entryMarkRemoval(base))
        try { (exit.entryMarks as? MutableSet<TaintMark>)?.clear() } catch (_: UnsupportedOperationException) { }
        assertEquals(setOf(t, u), exit.entryMarks)
        assertEquals(expected, exit.entryMarkRemoval(AccessPathBase.This))
        // The backward result is a RuleStatement: it carries neither G2 flag nor entry-mark removal metadata.
        val backward: RuleStatement = exit.reversed()
        assertEquals(form.summary.reversed().edges.map { it.edge }, backward.summary.edges.map { it.edge })
        assertTrue(exit.globalStateDrop)
        assertNull(ExitRules.EMPTY.entryMarkRemoval(AccessPathBase.This))
        assertNull(ExitRules(form, false, emptySet()).entryMarkRemoval(x))
    }

    @Test
    fun `primitive clean keeps identity and kill reverses its statement edges and drops filters`(): Unit {
        val cleaner = Cleaner(x, m.path(listOf(f)), CleanReach.BELOW, t)
        val clean = CleanStep.Clean(cleaner)
        assertSame(clean, clean.reversed())
        assertSame(cleaner, (clean.reversed() as CleanStep.Clean).cleaner)
        val edge = MicroEdge.of(starEdge(STATIC, listOf(f), x, listOf(g)))
        val keeps = statement(setOf(STATIC), listOf(edge), filters = mapOf(STATIC to accept), results = mapOf(x to accept))
        val kill = CleanStep.Kill(keeps)
        val reversed = kill.reversed() as CleanStep.Kill
        assertNotSame(kill, reversed)
        assertEquals(revEdge(edge.edge), reversed.keepEdges.edges.single { !it.isIdentity }.edge)
        assertTrue(reversed.keepEdges.typeFilters.isEmpty() && reversed.keepEdges.resultFilters.isEmpty())
        assertEquals(setOf(STATIC, x), reversed.keepEdges.touched)
        assertEquals(listOf(x), reversed.keepEdges.edges.filter { it.isIdentity }.map { it.edge.from.base })
    }

    @Test
    fun `field reversal swaps reversed read and write while sharing its fresh temporary and root cleaner`(): Unit {
        val temporary = AccessPathBase.CleanerTemporary()
        val otherTemporary = AccessPathBase.CleanerTemporary()
        assertNotSame(temporary, otherTemporary)
        val cleaner = Cleaner(temporary, null, CleanReach.EXACT, t)
        val readEdge = MicroEdge.of(starEdge(x, listOf(f), temporary, emptyList()))
        val writeEdge = MicroEdge.of(starEdge(temporary, emptyList(), x, listOf(f)))
        val read = statement(setOf(temporary), listOf(readEdge))
        val write = statement(setOf(x), listOf(writeEdge), filters = mapOf(temporary to accept), results = mapOf(x to accept))
        val field = CleanStep.Field(temporary, read, cleaner, write)
        val reversed = field.reversed() as CleanStep.Field
        assertSame(temporary, reversed.temporary)
        assertSame(cleaner, reversed.cleaner)
        assertNull(reversed.cleaner.path)
        assertEquals(revEdge(writeEdge.edge), reversed.read.edges.single { !it.isIdentity }.edge)
        assertEquals(revEdge(readEdge.edge), reversed.write.edges.single { !it.isIdentity }.edge)
        assertSame(writeEdge.forward, reversed.read.edges.single { !it.isIdentity }.forward)
        assertSame(readEdge.forward, reversed.write.edges.single { !it.isIdentity }.forward)
        assertTrue(reversed.read.typeFilters.isEmpty() && reversed.read.resultFilters.isEmpty())
        assertFails { CleanStep.Field(temporary, read, Cleaner(x, null, CleanReach.EXACT, t), write) }
        assertFails { CleanStep.Field(temporary, read, Cleaner(temporary, m.path(listOf(f)), CleanReach.EXACT, t), write) }
    }
}
