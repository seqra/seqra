package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.CombinedMethodContext
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.MethodContext
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.mkFalse
import org.opentaint.dataflow.configuration.mkTrue
import org.opentaint.dataflow.configuration.jvm.*
import org.opentaint.dataflow.configuration.jvm.TaintMark as RuleMark
import org.opentaint.dataflow.jvm.ap.ifds.JIRArgumentTypeMethodContext
import org.opentaint.dataflow.jvm.ap.ifds.JIRInstanceTypeMethodContext
import org.opentaint.dataflow.jvm.ap.ifds.TypeConstraintInfo
import org.opentaint.dataflow.jvm.ap.ifds.taint.ContainsMarkOnAnyField
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.bidi.fixtures.NoTaintRules
import org.opentaint.dataflow.jvm.bidi.interp.JIRBoundaryForms
import org.opentaint.dataflow.jvm.bidi.interp.JIRInterpreter
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.ext.toType
import kotlin.test.*

/** Expected paths and edges are the boundary rows, independent of the boundary builder. */
class JIRBoundaryFormsTest : BidiJIRTestUtils() {
    private val arg0 = AccessPathBase.Argument(0)
    private val arg1 = AccessPathBase.Argument(1)
    private val self = AccessPathBase.This
    private val ret = AccessPathBase.Return
    private val exc = AccessPathBase.Exception
    private val owner = "sample.bidi.BidiStatementSample"
    private val t by lazy { manager.marks.mark("T") }
    private val u by lazy { manager.marks.mark("U") }
    private val v by lazy { manager.marks.mark("V") }
    private val w by lazy { manager.marks.mark("W") }
    private val outMark by lazy { manager.marks.mark("Out") }
    private val f by lazy { manager.accessors.index(FieldAccessor(owner, "f", "java.lang.Object")) }
    private val g by lazy { manager.accessors.index(FieldAccessor(owner, "g", "java.lang.Object")) }
    private val element by lazy { manager.accessors.index(ElementAccessor) }
    private val empty = ExclusionSet.Empty
    private val meta = TaintSinkMeta("boundary sink", CommonTaintConfigurationSinkMeta.Severity.Error, null)

    private fun any(p: Position) = PositionWithAccess(p, PositionAccessor.AnyFieldAccessor)
    private fun field(p: Position, name: String = "f") = PositionWithAccess(p,
        PositionAccessor.FieldAccessor(owner, name, "java.lang.Object"))
    private fun contains(p: Position, mark: String = "T"): Condition = CommonCondition.Atom(ContainsMark(p, RuleMark(mark)))
    private fun containsAny(p: Position, mark: String = "T"): Condition = CommonCondition.Atom(ContainsMarkOnAnyField(p, RuleMark(mark)))
    private fun fact(base: AccessPathBase, path: List<Int> = emptyList(), tail: Tail = Tail.EXACT,
                     mark: org.opentaint.dataflow.bidi.ap.TaintMark = t) = PathFact(base, path, tail, MarkSlot.Concrete(mark))
    private fun edge(from: PathFact, to: PathFact) = PathEdge(from, to, empty)
    private fun conclusion(fact: PathFact, demand: Boolean = false, exclusion: ExclusionSet = empty) = Conclusion(fact, exclusion, demand)
    private fun start(i: JIRInterpreter, m: JIRMethod, context: MethodContext = EmptyMethodContext): RuleStatement {
        val k = key(m, context)
        return JIRBoundaryForms.entryRules(i, i.entries[k], k)
    }
    private fun end(i: JIRInterpreter, m: JIRMethod, node: CommonInst,
                    entry: RuleStatement = start(i, m)): ExitRules {
        val k = key(m)
        return JIRBoundaryForms.exitRules(i, i.entries[k], k, node, entry)
    }
    private fun entrySource(m: JIRMethod, actions: List<AssignMark>, c: Condition = mkTrue()) =
        TaintEntryPointSource(m, c, actions, null)
    private fun exitSource(m: JIRMethod, actions: List<AssignMark>, c: Condition = mkTrue()) =
        TaintMethodExitSource(m, c, actions, null)
    private fun entrySink(m: JIRMethod, ends: List<AssignMark> = emptyList(), c: Condition = mkTrue()) =
        TaintMethodEntrySink(m, c, ends, "entry", meta, null)
    private fun exitSink(m: JIRMethod, ends: List<AssignMark> = emptyList(), c: Condition = mkTrue()) =
        TaintMethodExitSink(m, c, ends, "exit", meta, null)

    private class BoundaryRules : TaintRulesProvider by NoTaintRules {
        var entrySources = emptyList<TaintEntryPointSource>()
        var entrySinks = emptyList<TaintMethodEntrySink>()
        var exitSources = emptyList<TaintMethodExitSource>()
        var exitSinks = emptyList<TaintMethodExitSink>()
        data class Query(val kind: String, val method: CommonMethod, val node: CommonInst,
                         val fact: FactAp?, val initial: Set<InitialFactAp>?, val allRelevant: Boolean)
        val queries = arrayListOf<Query>()
        override fun entryPointRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintEntryPointSource> {
            queries += Query("entry source", method, statement, fact, null, allRelevant)
            return entrySources
        }
        override fun sinkRulesForMethodEntry(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintMethodEntrySink> {
            queries += Query("entry sink", method, statement, fact, null, allRelevant)
            return entrySinks
        }
        override fun exitSourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintMethodExitSource> {
            queries += Query("exit source", method, statement, fact, null, allRelevant)
            return exitSources
        }
        override fun sinkRulesForMethodExit(method: CommonMethod, statement: CommonInst, fact: FactAp?, initialFacts: Set<InitialFactAp>?, allRelevant: Boolean): Iterable<TaintMethodExitSink> {
            queries += Query("exit sink", method, statement, fact, initialFacts, allRelevant)
            return exitSinks
        }
    }

    @Test
    fun `method start keeps zero and selects filters from the complete method context`(): Unit {
        val m = method("inheritedWrite")
        val parent = checkNotNull(cp.findClassOrNull("$owner\$Parent"))
        val child = checkNotNull(cp.findClassOrNull("$owner\$Child"))
        val thisType = JIRInstanceTypeMethodContext(TypeConstraintInfo(parent, exactType = true))
        val argumentType = JIRArgumentTypeMethodContext(0, TypeConstraintInfo(child, exactType = true))
        data class Row(val context: MethodContext, val filters: Map<AccessPathBase, org.opentaint.ir.api.jvm.JIRClassOrInterface>)
        val rows = listOf(
            Row(EmptyMethodContext, mapOf(self to m.enclosingClass)),
            Row(thisType, mapOf(self to parent)),
            Row(argumentType, mapOf(arg0 to child)),
            Row(JIRArgumentTypeMethodContext(1, TypeConstraintInfo(parent, false)), mapOf(arg1 to parent)),
            Row(CombinedMethodContext(thisType, argumentType), mapOf(self to parent, arg0 to child)),
            Row(CombinedMethodContext(EmptyMethodContext, thisType), mapOf(self to m.enclosingClass)),
        )
        for (row in rows) {
            val s = start(interp, m, row.context).summary
            assertEquals(setOf(AccessPathBase.Zero) + row.filters.keys, s.touched)
            assertEquals(listOf(ZERO_KEEP) + row.filters.keys.map { keepEdge(it) }, s.edges.map { it.edge })
            assertEquals(row.filters.keys, s.typeFilters.keys)
            row.filters.forEach { (base, cls) -> assertSame(interp.typeFilters.of(cls.toType()), s.typeFilters[base]) }
            assertTrue(s.resultFilters.isEmpty())
            assertTrue(s.conjunctions.isEmpty())
            assertTrue(AccessPathBase.Zero !in s.typeFilters)
        }
    }

    @Test
    fun `context filter applies to the chosen initial base and other initial bases pass unchanged`(): Unit {
        val m = method("inheritedWrite")
        val child = checkNotNull(cp.findClassOrNull("$owner\$Child"))
        val s = start(interp, m, JIRArgumentTypeMethodContext(0, TypeConstraintInfo(child, true))).summary
        val inherited = manager.accessors.index(FieldAccessor("$owner\$Parent", "inherited", "java.lang.Object"))
        val reference = FormsReference(ops)
        val at = Place(key(m).statement, statementEdge = true, sources = true)
        val admitted = conclusion(fact(arg0, listOf(inherited)))
        val rejected = conclusion(fact(arg0, listOf(f)))
        val untouched = conclusion(fact(ret, listOf(f)))
        assertEquals(listOf(admitted), reference.apply(FormsReference.Mode.STATEMENT, s, setOf(ZERO_PATTERN), admitted, at))
        assertTrue(reference.apply(FormsReference.Mode.STATEMENT, s, setOf(ZERO_PATTERN), rejected, at).isEmpty())
        assertEquals(listOf(untouched), reference.apply(FormsReference.Mode.STATEMENT, s, setOf(ZERO_PATTERN), untouched, at))
    }

    @Test
    fun `entry sources use only true conditions and source targets stay gen only with must AnyField`(): Unit {
        val m = method("copy")
        val p = BoundaryRules().apply {
            entrySources = listOf(
                entrySource(m, listOf(AssignMark(RuleMark("T"), Argument(0)), AssignMark(RuleMark("U"), any(Argument(0))))),
                entrySource(m, listOf(AssignMark(RuleMark("V"), Argument(0))), mkFalse()),
                entrySource(m, listOf(AssignMark(RuleMark("W"), Argument(0))), contains(Argument(0))),
            )
        }
        val s = start(interp(p), m)
        assertEquals(setOf(AccessPathBase.Zero, self), s.summary.touched)
        assertEquals(listOf(ZERO_KEEP, keepEdge(self), edge(ZERO_FACT, fact(arg0)),
            edge(ZERO_FACT, fact(arg0, tail = Tail.ANY_TAINT, mark = u))), s.summary.edges.map { it.edge })
        assertEquals(setOf(t, u), JIRBoundaryForms.entryMarks(s))
        assertEquals(2, s.summary.edges.count { it.isSource })
        assertTrue(s.summary.edges.filter { it.isSource }.all { !it.may && it.forward === it.edge })
        assertTrue(s.endFacts.edges.isEmpty())
        assertTrue(s.sinks.isEmpty())
    }

    @Test
    fun `entry sinks are unconditional and their zero GEN marks are not entry marks`(): Unit {
        val m = method("copy")
        val accepted = entrySink(m, listOf(AssignMark(RuleMark("U"), field(Argument(0)))))
        val p = BoundaryRules().apply {
            entrySources = listOf(entrySource(m, listOf(AssignMark(RuleMark("T"), any(Argument(0))))))
            entrySinks = listOf(accepted, entrySink(m, c = mkFalse()), entrySink(m, c = contains(Argument(0))))
        }
        val s = start(interp(p), m)
        val sink = s.sinks.single()
        assertSame(accepted, sink.rule)
        assertTrue(sink.unconditional)
        assertEquals(listOf(ZERO_PATTERN), sink.patterns)
        assertTrue(sink.seedPatterns().isEmpty())
        assertEquals(listOf(edge(ZERO_FACT, fact(arg0, listOf(f), mark = u))), s.endFacts.edges.map { it.edge })
        assertSame(sink.endFacts.single().edge, s.endFacts.edges.single().edge)
        assertTrue(s.endFacts.touched.isEmpty())
        assertTrue(s.endFacts.typeFilters.isEmpty())
        assertEquals(setOf(t), JIRBoundaryForms.entryMarks(s))
        for (layer in Layer.entries) {
            val zero = conclusion(ZERO_FACT, demand = layer == Layer.DEMAND)
            assertEquals(listOf(conclusion(fact(arg0, listOf(f), mark = u), layer == Layer.DEMAND)),
                FormsReference(ops).apply(FormsReference.Mode.GEN, s.endFacts, setOf(ZERO_PATTERN), zero,
                    Place(key(m).statement, false, false)))
        }
    }

    @Test
    fun `entry and both exit rule queries receive null fact and null initial facts`(): Unit {
        val m = method("copy")
        val p = BoundaryRules()
        val i = interp(p)
        val entry = start(i, m)
        val normal = normalExit(m)
        val exceptional = exceptionalExit(m)
        end(i, m, normal, entry)
        end(i, m, exceptional, entry)
        assertEquals(listOf("entry source", "entry sink", "exit source", "exit sink", "exit source", "exit sink"), p.queries.map { it.kind })
        assertEquals(listOf(key(m).statement, key(m).statement, normal, normal, exceptional, exceptional), p.queries.map { it.node })
        assertTrue(p.queries.all { it.method == m && it.fact == null && it.initial == null && !it.allRelevant })
    }

    @Test
    fun `exit DNF sources create independent conjunctions and map Result to the exceptional base`(): Unit {
        val m = method("inheritedWrite")
        val cond: Condition = CommonCondition.Or(listOf(
            CommonCondition.And(listOf(contains(Argument(0)), containsAny(Argument(1), "U"))),
            CommonCondition.And(listOf(contains(Argument(0)), contains(This, "V"))),
            contains(Result, "W"),
        ))
        val p = BoundaryRules().apply {
            exitSources = listOf(exitSource(m, listOf(AssignMark(RuleMark("Out"), any(field(Result)))), cond))
        }
        val i = interp(p)
        val entry = start(i, m)
        for ((node, resultBase) in listOf(normalExit(m) to ret, exceptionalExit(m) to exc)) {
            val s = end(i, m, node, entry).rules.summary
            val target = fact(resultBase, listOf(f), Tail.ANY_TAINT, outMark)
            assertEquals(setOf(AccessPathBase.Zero, arg0, arg1, self, resultBase), s.touched)
            assertEquals(setOf(ZERO_KEEP, keepEdge(arg0), keepEdge(arg1), keepEdge(self), keepEdge(resultBase),
                edge(fact(resultBase, mark = w), target)), s.edges.map { it.edge }.toSet())
            assertEquals(2, s.conjunctions.size)
            assertEquals(setOf(
                setOf(Pattern(fact(arg0), empty), Pattern(fact(arg1, tail = Tail.ANY, mark = u), empty)),
                setOf(Pattern(fact(arg0), empty), Pattern(fact(self, mark = v), empty)),
            ), s.conjunctions.map { it.literals.toSet() }.toSet())
            assertTrue(s.conjunctions.all { it.target == target && it.literals.size == 2 })
            assertNotSame(s.conjunctions[0], s.conjunctions[1])
            assertTrue(s.typeFilters.isEmpty() && s.resultFilters.isEmpty())
        }
    }

    @Test
    fun `unconditional sources and sinks occur at both exits with zero keeps and must GEN targets`(): Unit {
        val m = method("copy")
        val sink = exitSink(m, listOf(AssignMark(RuleMark("U"), any(field(Result)))))
        val p = BoundaryRules().apply {
            exitSources = listOf(exitSource(m, listOf(AssignMark(RuleMark("T"), Result))))
            exitSinks = listOf(sink)
        }
        val i = interp(p)
        val entry = start(i, m)
        for ((node, resultBase) in listOf(normalExit(m) to ret, exceptionalExit(m) to exc)) {
            val rules = end(i, m, node, entry).rules
            assertEquals(setOf(AccessPathBase.Zero), rules.summary.touched)
            assertEquals(listOf(ZERO_KEEP, edge(ZERO_FACT, fact(resultBase))), rules.summary.edges.map { it.edge })
            assertSame(sink, rules.sinks.single().rule)
            assertEquals(listOf(ZERO_PATTERN), rules.sinks.single().patterns)
            assertTrue(rules.sinks.single().seedPatterns().isEmpty())
            assertEquals(listOf(edge(ZERO_FACT, fact(resultBase, listOf(f), Tail.ANY_TAINT, u))), rules.endFacts.edges.map { it.edge })
            assertTrue(rules.endFacts.touched.isEmpty())
            for (demand in listOf(false, true)) {
                val zero = conclusion(ZERO_FACT, demand)
                val target = fact(resultBase, listOf(f), if (demand) Tail.ANY else Tail.ANY_TAINT, u)
                assertEquals(listOf(conclusion(target, demand)), FormsReference(ops).apply(FormsReference.Mode.GEN,
                    rules.endFacts, setOf(ZERO_PATTERN), zero, Place(node, false, false)))
            }
        }
    }

    @Test
    fun `exit sinks retain each DNF alternative and never add call array alternatives`(): Unit {
        val m = method("arrayWrite")
        val cond: Condition = CommonCondition.Or(listOf(contains(Argument(0)),
            CommonCondition.And(listOf(containsAny(field(Argument(1)), "U"), contains(Result, "V")))))
        val sink = exitSink(m, c = cond)
        val p = BoundaryRules().apply { exitSinks = listOf(sink) }
        val i = interp(p)
        val entry = start(i, m)
        for ((node, resultBase) in listOf(normalExit(m) to ret, exceptionalExit(m) to exc)) {
            val sinks = end(i, m, node, entry).rules.sinks
            assertEquals(2, sinks.size)
            assertEquals(setOf(0, 1), sinks.map { it.alternative }.toSet())
            assertTrue(sinks.all { it.rule === sink })
            assertEquals(setOf(
                setOf(Pattern(fact(arg0), empty)),
                setOf(Pattern(fact(arg1, listOf(f), Tail.ANY, u), empty), Pattern(fact(resultBase, mark = v), empty)),
            ), sinks.map { it.patterns.toSet() }.toSet())
            assertTrue(sinks.flatMap { it.patterns }.none { it.fact.path.firstOrNull() == element })
            assertEquals(sinks.map { it.patterns }, sinks.map { it.seedPatterns() })
        }
    }

    @Test
    fun `only normal exit exposes global drop and entry source mark metadata`(): Unit {
        val m = method("copy")
        val p = BoundaryRules().apply {
            entrySources = listOf(entrySource(m, listOf(AssignMark(RuleMark("T"), any(Argument(0))))))
            entrySinks = listOf(entrySink(m, listOf(AssignMark(RuleMark("U"), Argument(0)))))
            exitSinks = listOf(exitSink(m, c = contains(Argument(0))))
        }
        val i = interp(p)
        val entry = start(i, m)
        val normal = end(i, m, normalExit(m), entry)
        val exceptional = end(i, m, exceptionalExit(m), entry)
        assertTrue(normal.globalStateDrop)
        assertEquals(setOf(t), normal.entryMarks)
        assertEquals(MarkSet(intArrayOf(t.id)), normal.entryMarkRemoval(self))
        assertEquals(MarkSet(intArrayOf(t.id)), normal.entryMarkRemoval(AccessPathBase.Argument(73)))
        for (b in listOf(STATIC, ret, exc, AccessPathBase.Zero, AccessPathBase.LocalVar(0))) assertNull(normal.entryMarkRemoval(b))
        assertFalse(exceptional.globalStateDrop)
        assertTrue(exceptional.entryMarks.isEmpty())
        assertNull(exceptional.entryMarkRemoval(arg0))
        val noSinks = BoundaryRules().apply { entrySources = p.entrySources }
        val withoutSink = interp(noSinks)
        assertFalse(end(withoutSink, m, normalExit(m)).globalStateDrop)
    }

    @Test
    fun `normal exit entry marks remove exact and must leaves at every depth and retain other marks and E`(): Unit {
        val m = method("copy")
        val p = BoundaryRules().apply {
            entrySources = listOf(entrySource(m, listOf(AssignMark(RuleMark("T"), any(Argument(0))))))
            entrySinks = listOf(entrySink(m, listOf(AssignMark(RuleMark("U"), Argument(0)))))
        }
        val i = interp(p)
        val removal = assertNotNull(end(i, m, normalExit(m)).entryMarkRemoval(arg0))
        val both = MarkSet(intArrayOf(t.id, u.id))
        val leaf = manager.taintLeaves(both, both)
        val deep = listOf(f, element, f)
        var child = manager.taintAlg.leafNode(leaf)
        for (a in deep.asReversed()) child = manager.taintAlg.node(TaintLeaves.EMPTY, intArrayOf(a), arrayOf(child))
        val root = manager.taintAlg.node(leaf, intArrayOf(g), arrayOf(child))
        val exclusion = ExclusionSet.of(f)
        val input = manager.taintTree(arg0, Layer.NORMAL, root, exclusion)
        val result = assertNotNull(ops.withoutMarks(input, removal))
        assertEquals(Layer.NORMAL, result.layer)
        assertEquals(exclusion, assertIs<TaintTree>(result).exclusion)
        val expected = setOf(
            conclusion(fact(arg0, mark = u)),
            conclusion(fact(arg0, tail = Tail.ANY_TAINT, mark = u), exclusion = exclusion),
            conclusion(fact(arg0, listOf(g) + deep, mark = u)),
            conclusion(fact(arg0, listOf(g) + deep, Tail.ANY_TAINT, u), exclusion = exclusion),
        )
        assertEquals(expected, FormsReference(ops).conclusions(result).toSet())
        assertEquals(8, FormsReference(ops).conclusions(input).size)
    }

    @Test
    fun `reversed boundaries retain zero sink identities GEN separation and demand conjunction branches`(): Unit {
        val m = method("inheritedWrite")
        val p = BoundaryRules().apply {
            exitSources = listOf(exitSource(m, listOf(AssignMark(RuleMark("Out"), any(Result))),
                CommonCondition.And(listOf(contains(Argument(0)), containsAny(Argument(1), "U")))))
            exitSinks = listOf(exitSink(m, listOf(AssignMark(RuleMark("V"), field(Result))), containsAny(Argument(0))))
        }
        val i = interp(p)
        val entry = start(i, m)
        for (node in listOf(normalExit(m), exceptionalExit(m))) {
            val forward = end(i, m, node, entry).rules
            val reversed = forward.reversed()
            assertSame(forward.sinks.single(), reversed.sinks.single())
            assertTrue(reversed.summary.edges.any { it.edge == ZERO_KEEP })
            assertTrue(reversed.summary.typeFilters.isEmpty() && reversed.summary.resultFilters.isEmpty())
            assertTrue(reversed.summary.conjunctions.isEmpty())
            val branches = reversed.summary.edges.filter { it.conjunctive }
            assertEquals(2, branches.size)
            assertTrue(branches.all { it.may && it.forward.to.tail == Tail.ANY_TAINT && it.edge.from.tail == Tail.ANY })
            assertEquals(setOf(arg0, arg1), branches.map { it.edge.to.base }.toSet())
            assertTrue(reversed.summary.edges.all { it.edge.from.tail != Tail.ANY_TAINT && it.edge.to.tail != Tail.ANY_TAINT })
            assertTrue(reversed.endFacts.touched.isEmpty())
            assertEquals(forward.endFacts.edges.map { it.edge.to }, reversed.endFacts.edges.map { it.edge.from })
            assertTrue(reversed.endFacts.edges.all { it.edge.to == ZERO_FACT })
            val seed = reversed.sinks.single().seedPatterns().single()
            assertEquals(Pattern(fact(arg0, tail = Tail.ANY), empty), seed)
            for (branch in branches) {
                val input = conclusion(fact(branch.edge.from.base, mark = outMark))
                val results = FormsReference(ops, ReferenceAlgebra(ApMode(false, Direction.BACKWARD, 8), manager = manager))
                    .apply(FormsReference.Mode.GEN, StatementSummary(emptySet(), listOf(branch), emptyList(), emptyMap()),
                        setOf(seed), input, Place(node, false, false))
                assertEquals(listOf(conclusion(branch.edge.to, demand = true)), results)
            }
        }
    }

    @Test
    fun `whole invalid boundary rules are omitted before any partial action is produced`(): Unit {
        val m = method("copy")
        val bad = field(any(Argument(0)))
        val p = BoundaryRules().apply {
            entrySources = listOf(entrySource(m, listOf(AssignMark(RuleMark("T"), Argument(0)), AssignMark(RuleMark("U"), bad))))
            entrySinks = listOf(entrySink(m, listOf(AssignMark(RuleMark("T"), Argument(0)), AssignMark(RuleMark("U"), bad))))
            exitSources = listOf(exitSource(m, listOf(AssignMark(RuleMark("T"), Result), AssignMark(RuleMark("U"), bad))))
            exitSinks = listOf(exitSink(m, listOf(AssignMark(RuleMark("T"), Result), AssignMark(RuleMark("U"), bad))))
        }
        val i = interp(p)
        val entry = start(i, m)
        assertTrue(entry.sinks.isEmpty() && entry.endFacts.edges.isEmpty())
        assertEquals(setOf(ZERO_KEEP, keepEdge(self)), entry.summary.edges.map { it.edge }.toSet())
        for (node in listOf(normalExit(m), exceptionalExit(m))) {
            val x = end(i, m, node, entry)
            assertEquals(listOf(ZERO_KEEP), x.rules.summary.edges.map { it.edge })
            assertTrue(x.rules.summary.conjunctions.isEmpty() && x.rules.sinks.isEmpty() && x.rules.endFacts.edges.isEmpty())
            assertFalse(x.globalStateDrop)
        }
        assertFailsWith<IllegalStateException> { end(i, m, key(m).statement, entry) }
    }
}
