package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.CombinedMethodContext
import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.MethodContext
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.dataflow.graph.MethodInstGraph
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.util.RefManager
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
import java.lang.reflect.InvocationTargetException
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
    fun `exit NNF source shares its predicate and maps Result to the exceptional base`(): Unit {
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
            assertEquals(setOf(ZERO_KEEP, keepEdge(arg0), keepEdge(arg1), keepEdge(self), keepEdge(resultBase)),
                s.edges.map { it.edge }.toSet())
            assertTrue(s.conjunctions.isEmpty())
            val source = s.sources.single()
            assertEquals(target, source.actions.single().target)
            assertEquals(setOf(Pattern(fact(arg0), empty), Pattern(fact(arg1, tail = Tail.ANY, mark = u), empty),
                Pattern(fact(self, mark = v), empty), Pattern(fact(resultBase, mark = w), empty)), source.predicate.leaves.toSet())
            assertEquals(RulePredicate.Kind.OR, source.predicate.kind(source.predicate.root))
            for ((ordinal, literal) in source.predicate.leaves.withIndex()) {
                assertEquals(literal.fact.base == resultBase, source.predicate.isUnary(ordinal))
                assertEquals(literal.fact.base != resultBase, source.predicate.isMulti(ordinal))
            }
            assertTrue(s.typeFilters.isEmpty() && s.resultFilters.isEmpty())
        }
    }

    @Test
    fun `conditional static exit source keeps the scalar position obligation and runtime parent identity`(): Unit {
        val m = method("copy")
        val cls = manager.accessors.index(ClassStaticAccessor(owner))
        val staticField = manager.accessors.index(FieldAccessor(owner, "sField", "java.lang.Object"))
        val position = PositionWithAccess(ClassStatic(owner),
            PositionAccessor.FieldAccessor(owner, "sField", "java.lang.Object"))
        val provider = BoundaryRules().apply {
            exitSources = listOf(exitSource(m, listOf(AssignMark(RuleMark("Out"), Result)), contains(position)))
        }
        val i = interp(provider)
        val entry = start(i, m)
        val requested = RequestKind.Position(assertNotNull(manager.path(listOf(cls, staticField))))

        fun check(summary: StatementSummary, node: CommonInst, mode: ApMode,
                  original: Pattern, input: Conclusion, expected: Set<Conclusion>,
                  expectedRequests: List<RequestKind> = emptyList(),
                  runtimeRequests: List<RequestKind> = expectedRequests.onEach {
                      require(it is RequestKind.Mark) { "Specify the runtime effects of a scalar Position row" }
                  }) {
            val premise = setOf(original)
            val rawRequests = arrayListOf<Pair<Set<Pattern>, RequestKind>>()
            val raw = linkedSetOf<Conclusion>()
            val reference = FormsReference(ops, ReferenceAlgebra(mode,
                request = { pr, request -> rawRequests += pr to request }, manager = manager))
            val at = Place(node, statementEdge = true, sources = true)
            reference.applier.statement(summary, premise, input, at, sink = { pr, result ->
                assertSame(premise, pr)
                raw += limit(result, mode.fieldLimit)
            })
            assertEquals(expected, raw, "raw mode=${mode.direction}/${mode.run1}, input=$input")
            assertEquals(expectedRequests, rawRequests.map { it.second }, "raw requests")
            rawRequests.forEach { assertSame(premise, it.first) }

            val treePremise = manager.initial(original)
            val treeRequests = arrayListOf<Pair<PremiseKey, RequestKind>>()
            val output = arrayListOf<Facts>()
            val applier = FormApplier(object : FactAlgebra<PremiseKey, Facts> {
                override fun base(c: Facts) = c.base
                override fun filter(c: Facts, filter: TypeFilter) = ops.filter(c, filter)
                override fun applyEdge(me: MicroEdge, c: Facts, premise: PremiseKey, statementEdge: Boolean,
                                       out: (PremiseKey, Facts) -> Unit) {
                    ops.applyEdge(c, premise, me.edge, statementEdge, mode, object : ApOut {
                        override fun result(f: Facts) = out(premise, f)
                        override fun markRequest(mark: org.opentaint.dataflow.bidi.ap.TaintMark) {
                            treeRequests += premise to RequestKind.Mark(mark)
                        }
                    }, me.may)
                }
                override fun conjunction(cj: ConjunctiveEdge, premise: PremiseKey, c: Facts, node: CommonInst,
                                         out: (PremiseKey, Facts) -> Unit) = error("This exit source has one literal")
                override fun sourcePredicate(source: SourceRuleOccurrence, premise: PremiseKey, c: Facts, node: CommonInst,
                                             statementEdge: Boolean, out: (PremiseKey, Facts) -> Unit) {
                    check(source.backward == (mode.direction == Direction.BACKWARD))
                    for ((ordinal, literal) in source.predicate.leaves.withIndex()) {
                        check(source.predicate.isUnary(ordinal) && !source.predicate.isMulti(ordinal))
                        for (action in source.actions) applyEdge(source.transfer(literal, action), c, premise, statementEdge, out)
                    }
                }
                override fun allowsSource(node: CommonInst, me: MicroEdge) = true
                override fun sourceHit(node: CommonInst, me: MicroEdge) = Unit
            })
            val facts = manager.factsOf(input.fact, input.exclusion,
                if (input.demand) Layer.DEMAND else Layer.NORMAL)
            applier.statement(summary, treePremise, facts, at, sink = { pr, result ->
                assertSame(treePremise, pr)
                ops.limit(result, mode.fieldLimit, object : ApOut {
                    override fun result(f: Facts) { output += f }
                    override fun markRequest(mark: org.opentaint.dataflow.bidi.ap.TaintMark) = error("A cut cannot request a mark")
                })
            })
            assertEquals(expected, output.flatMap(reference::conclusions).toSet(),
                "tree mode=${mode.direction}/${mode.run1}, input=$input")
            assertEquals(runtimeRequests, treeRequests.map { it.second }, "runtime requests")
            treeRequests.forEach { assertSame(treePremise, it.first) }
        }

        val run1 = ApMode(run1 = true, Direction.FORWARD, fieldLimit = 1)
        val restricted = ApMode(run1 = false, Direction.FORWARD, fieldLimit = 1)
        val backward = ApMode(run1 = false, Direction.BACKWARD, fieldLimit = 1)
        for ((node, resultBase) in listOf(normalExit(m) to ret, exceptionalExit(m) to exc)) {
            val forward = end(i, m, node, entry).rules.summary
            val from = fact(STATIC, listOf(cls, staticField))
            val target = fact(resultBase, mark = outMark)
            assertEquals(setOf(AccessPathBase.Zero, STATIC), forward.touched)
            assertEquals(listOf(ZERO_KEEP, keepEdge(STATIC)), forward.edges.map { it.edge })
            val source = forward.sources.single()
            assertEquals(Pattern(from, empty), source.predicate.leaves.single())
            assertEquals(target, source.actions.single().target)
            assertEquals(edge(from, target), source.transfer(source.predicate.leaves.single(), source.actions.single()).edge)
            assertTrue(forward.conjunctions.isEmpty())
            val reversed = forward.reversed()
            for (path in listOf(emptyList(), listOf(cls))) {
                val original = Pattern(PathFact(STATIC, path, Tail.STAR, MarkSlot.STAR), empty)
                val input = conclusion(original.fact, exclusion = ExclusionSet.of(g))
                check(forward, node, run1, original, input, setOf(input), listOf(requested), runtimeRequests = emptyList())
                check(forward, node, restricted, original, input, setOf(input))
                check(reversed, node, backward, original, input, setOf(input))

                val excluded = if (path.isEmpty()) cls else staticField
                val blocked = conclusion(original.fact, exclusion = ExclusionSet.of(excluded))
                check(forward, node, run1, original, blocked, setOf(blocked))
            }
            val reached = conclusion(target)
            check(reversed, node, backward, Pattern(target, empty), reached,
                setOf(reached, conclusion(from)))
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
    fun `exit sinks retain one NNF rule and never add call array alternatives`(): Unit {
        val m = method("arrayWrite")
        val cond: Condition = CommonCondition.Or(listOf(contains(Argument(0)),
            CommonCondition.And(listOf(containsAny(field(Argument(1)), "U"), contains(Result, "V")))))
        val sink = exitSink(m, c = cond)
        val p = BoundaryRules().apply { exitSinks = listOf(sink) }
        val i = interp(p)
        val entry = start(i, m)
        for ((node, resultBase) in listOf(normalExit(m) to ret, exceptionalExit(m) to exc)) {
            val sinks = end(i, m, node, entry).rules.sinks
            val compiled = sinks.single()
            assertSame(sink, compiled.rule)
            val predicate = assertNotNull(compiled.predicate)
            assertEquals(RulePredicate.Kind.OR, predicate.kind(predicate.root))
            assertEquals(setOf(Pattern(fact(arg0), empty),
                Pattern(fact(arg1, listOf(f), Tail.ANY, u), empty), Pattern(fact(resultBase, mark = v), empty)),
                compiled.patterns.toSet())
            assertEquals(predicate.leaves, compiled.patterns)
            assertTrue(compiled.patterns.none { it.fact.path.firstOrNull() == element })
            assertEquals(compiled.patterns, compiled.seedPatterns())
        }
    }

    @Test
    fun `signed contradiction has no source sink end target or drop effect at either actual boundary`(): Unit {
        val m = method("phi")
        val cls = manager.accessors.index(ClassStaticAccessor(owner))
        val staticField = manager.accessors.index(FieldAccessor(owner, "sField", "java.lang.Object"))
        val p = contains(PositionWithAccess(ClassStatic(owner),
            PositionAccessor.FieldAccessor(owner, "sField", "java.lang.Object")))
        val contradiction: Condition = CommonCondition.And(listOf(p, CommonCondition.Not(p)))
        val provider = BoundaryRules().apply {
            exitSources = listOf(exitSource(m, listOf(AssignMark(RuleMark("Out"), any(Result))), contradiction))
            exitSinks = listOf(exitSink(m, listOf(AssignMark(RuleMark("W"), field(Result, "g"))), contradiction))
        }
        val i = interp(provider)
        val caller = setOf(Pattern(fact(arg1), empty), Pattern(fact(AccessPathBase.Argument(2), mark = u), empty))
        val static = conclusion(fact(STATIC, listOf(cls, staticField)))
        val state = conclusion(fact(arg1, listOf(g)))
        val input = listOf(setOf(ZERO_PATTERN) to conclusion(ZERO_FACT), setOf(ZERO_PATTERN) to static, caller to state)
        for ((node, normal) in listOf(normalExit(m) to true, exceptionalExit(m) to false)) {
            val rules = end(i, m, node)
            assertEquals(setOf(AccessPathBase.Zero), rules.rules.summary.touched,
                "A false rule must not retain its static read footprint")
            assertTrue(rules.rules.summary.sources.isEmpty())
            assertTrue(rules.rules.sinks.isEmpty())
            assertTrue(rules.rules.endFacts.edges.isEmpty())
            assertFalse(rules.globalStateDrop)
            val observed = executeBoundary(i, m, node, input, normal)
            assertEquals(input.map { it.second }.toSet(), observed.beforeDrop)
            assertEquals(if (normal) input.toSet() else emptySet(), observed.summaries)
            assertTrue(observed.witnesses.isEmpty())
            assertTrue(observed.requests.isEmpty())
            assertTrue(observed.beforeDrop.none { (it.fact.mark as? MarkSlot.Concrete)?.mark in setOf(outMark, w) })
        }
    }

    @Test
    fun `live OR keeps its redundant AND read for G2 but preserves full caller set state at both boundaries`(): Unit {
        val m = method("phi")
        val cls = manager.accessors.index(ClassStaticAccessor(owner))
        val staticField = manager.accessors.index(FieldAccessor(owner, "sField", "java.lang.Object"))
        val a: Condition = contains(Argument(1))
        val b: Condition = contains(PositionWithAccess(ClassStatic(owner),
            PositionAccessor.FieldAccessor(owner, "sField", "java.lang.Object")), "U")
        val condition: Condition = CommonCondition.Or(listOf(a, CommonCondition.And(listOf(a, b))))
        val provider = BoundaryRules().apply {
            exitSinks = listOf(exitSink(m, listOf(AssignMark(RuleMark("W"), Result)), condition))
        }
        val i = interp(provider)
        val caller = setOf(Pattern(fact(arg1), empty), Pattern(fact(AccessPathBase.Argument(2), mark = u), empty))
        val static = conclusion(fact(STATIC, listOf(cls, staticField), mark = u))
        val state = conclusion(fact(arg1))
        for ((node, target, normal) in listOf(Triple(normalExit(m), ret, true), Triple(exceptionalExit(m), exc, false))) {
            val rules = end(i, m, node)
            val sink = rules.rules.sinks.single()
            assertEquals(setOf(Pattern(state.fact, empty), Pattern(static.fact, empty)), sink.patterns.toSet())
            val predicate = assertNotNull(sink.predicate)
            assertEquals(RulePredicate.Kind.OR, predicate.kind(predicate.root))
            assertEquals(normal, rules.globalStateDrop)
            // B alone cannot satisfy the full condition. G2 still reads it and removes a zero static.
            val zeroStatic = executeBoundary(i, m, node, listOf(setOf(ZERO_PATTERN) to static), normal)
            assertEquals(setOf(static), zeroStatic.beforeDrop)
            assertTrue(zeroStatic.summaries.isEmpty())
            assertTrue(zeroStatic.witnesses.isEmpty())
            assertTrue(zeroStatic.requests.isEmpty())
            // The same static conclusion with a complete nonzero caller set survives G2.
            val callerStatic = executeBoundary(i, m, node, listOf(caller to static), normal)
            assertEquals(if (normal) setOf(caller to static) else emptySet(), callerStatic.summaries)
            assertTrue(callerStatic.witnesses.isEmpty())
            // Once A arrives, both supported proofs use their original literal premises. END uses zero.
            for (staticPremise in listOf(setOf(ZERO_PATTERN), caller)) {
                val observed = executeBoundary(i, m, node, listOf(staticPremise to static, caller to state), normal)
                val end = conclusion(fact(target, mark = w))
                val expected = buildSet {
                    add(caller to state)
                    add(setOf(ZERO_PATTERN) to end)
                    if (staticPremise == caller) add(caller to static)
                }
                assertEquals(if (normal) expected else emptySet(), observed.summaries)
                assertEquals(setOf(static, state, end), observed.beforeDrop)
                assertEquals(2, observed.witnesses.size, "The A and A/AND/B proofs retain distinct support shapes")
                val supports = observed.witnesses.map { witness ->
                    assertEquals(key(m), witness.methodKey)
                    assertEquals(1, witness.run)
                    assertEquals(listOf(fact(target, mark = w)), witness.endFacts)
                    witness.edges.map { edge ->
                        assertEquals(Layer.NORMAL, edge.layer)
                        edge.premise.members.map { it.toPattern() }.toSet() to
                            FormsReference(ops).conclusions(edge.facts).single()
                    }.toSet()
                }.toSet()
                assertEquals(setOf(setOf(caller to state), setOf(caller to state, staticPremise to static)), supports)
                assertTrue(observed.requests.isEmpty())
            }
        }
    }

    private data class BoundarySnapshot(
        val beforeDrop: Set<Conclusion>,
        val summaries: Set<Pair<Set<Pattern>, Conclusion>>,
        val witnesses: List<SinkWitness>,
        val requests: List<Pair<InitialAp, RequestKind>>,
    )

    /** Execute the actual boundary worklist/G2 path, isolating it from unrelated method-body transfers. */
    private fun executeBoundary(i: JIRInterpreter, m: JIRMethod, node: CommonInst,
                                input: List<Pair<Set<Pattern>, Conclusion>>, normal: Boolean): BoundarySnapshot {
        val method = key(m)
        val records = PersistentRecordStore(manager)
        val vulnerabilities = ConcurrentVulnerabilityStore(manager)
        val contexts = MethodContextCache(i, object : MethodContextSource {
            override fun forms(key: MethodKey): MethodForms = i.forms(key)
            override fun graph(key: MethodKey, direction: Direction): MethodInstGraph = i.entries[key].graph(direction)
        })
        SharedObjects(manager, ops, i, languageManager, contexts, records, vulnerabilities,
            UnitResolver<CommonMethod> { UnknownUnit }, RefManager(), manager.cancellation, threads = 1).use { shared ->
            val config = RunConfig(1, 3, null, records, SeedIndex.EMPTY, listOf(method))
            val beforeDrop = linkedSetOf<Conclusion>()
            val requests = arrayListOf<Pair<InitialAp, RequestKind>>()
            val published = arrayListOf<Publication>()
            val decoder = FormsReference(ops)
            lateinit var core: RunMethodAnalyzer
            val port = object : RunnerPort<RunMethodAnalyzer> {
                override val config = config
                override val shared = shared
                override val forms = contexts.directed(Direction.FORWARD)
                override val ops = this@JIRBoundaryFormsTest.ops
                override val steps = ProtocolSteps.Inline
                override val onCut: (Cut, Facts) -> Unit = { cut, facts ->
                    assertEquals(Cut.EXIT_RULES, cut)
                    beforeDrop += decoder.conclusions(facts)
                }
                override fun enqueue(analyzer: RunMethodAnalyzer): Boolean {
                    assertSame(core, analyzer)
                    return true // This direct boundary helper drains the real actor below.
                }
                override fun analyzer(key: MethodKey): RunMethodAnalyzer {
                    assertEquals(method, key)
                    return core
                }
                override fun send(event: RunEvent): Boolean = error("A concrete boundary cannot send $event")
                override fun post(event: RunEvent.Delivery): Boolean = error("No delivery in a direct boundary")
                override fun summaryStorage(key: MethodKey): SummaryStorage = error("No external summary storage")
                override fun subscribe(sub: Subscription): Unit = error("No boundary call subscription")
                override fun link(callee: MethodKey, ref: CallerRef, linkLayer: Layer, added: Facts): Unit =
                    error("No boundary call link")
            }
            core = RunMethodAnalyzer(method, port, PublicationSink { key, batch ->
                assertEquals(method, key)
                published += batch
            }, MethodLocalServicesFactory { actor ->
                val actual = RunMethodLocalServices(actor)
                object : MethodLocalServices by actual {
                    override fun addMarkRequest(premise: InitialAp, mark: org.opentaint.dataflow.bidi.ap.TaintMark) {
                        requests += premise to RequestKind.Mark(mark)
                        actual.addMarkRequest(premise, mark)
                    }
                }
            }, MethodCallServicesFactory(::RunMethodCallServices))
            // endAt is private to the common module; no public production API is added for this test.
            val endAt = RunMethodAnalyzer::class.java.getDeclaredMethod("endAt", PremiseKey::class.java,
                CommonInst::class.java, Facts::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
            for ((premise, c) in input) {
                val key = manager.premiseOf(premise.map(manager::initial))
                val facts = manager.factsOf(c.fact, c.exclusion, if (c.demand) Layer.DEMAND else Layer.NORMAL)
                try { endAt.invoke(core, key, node, facts, normal) }
                catch (e: InvocationTargetException) { throw (e.cause ?: e) }
            }
            assertTrue(core.work(1000), "All real boundary publications must drain")
            val summaries = core.summaries.all().flatMap { (premise, facts) ->
                decoder.conclusions(facts).asSequence().map { premise.members.map { it.toPattern() }.toSet() to it }
            }.toSet()
            assertEquals(summaries, published.flatMap { publication -> decoder.conclusions(publication.conclusion).map {
                publication.premise.members.map { it.toPattern() }.toSet() to it
            } }.toSet())
            val result = BoundarySnapshot(beforeDrop, summaries, vulnerabilities.witnessesOf(1).map { it.second }.toList(), requests)
            core.freeze()
            return result
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
            val source = reversed.summary.sources.single()
            assertTrue(source.backward)
            assertEquals(forward.summary.sources.single().id, source.id)
            val branches = source.predicate.leaves.map { source.transfer(it, source.actions.single(), multi = true) }
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
