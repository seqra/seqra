package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.configuration.jvm.*
import org.opentaint.dataflow.configuration.jvm.TaintMark as RuleMark
import org.opentaint.dataflow.jvm.BasicTestUtils
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRRuleTestFixtures
import org.opentaint.dataflow.jvm.bidi.fixtures.RecordingTaintRules
import org.opentaint.dataflow.taint.PositionAccess
import org.opentaint.dataflow.taint.RuleConditionRewriter
import org.opentaint.dataflow.taint.RuleConditionRewriter.ExprOrConstant
import org.opentaint.dataflow.taint.TaintMarkAwareConditionExpr
import org.opentaint.dataflow.taint.TaintMarkAwareConditionExpr.ContainsMarkLiteral
import org.opentaint.dataflow.taint.TaintMarkAwareConditionExpr.ContainsMarkOnAnyAccessorLiteral
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Expectations are raw position and edge rows, independent of the rule-form implementation. */
class JIRRuleFormsTest : BasicTestUtils() {
    private val v by lazy { JIRRuleTestFixtures(cp) }
    private val forms by lazy { v.forms() }
    private val arg0 = AccessPathBase.Argument(0)
    private val arg1 = AccessPathBase.Argument(1)
    private val owner = JIRRuleTestFixtures.SAMPLE_CLASS
    private val t by lazy { v.manager.marks.mark("T") }
    private val u by lazy { v.manager.marks.mark("U") }
    private val e by lazy { v.manager.accessors.index(ElementAccessor) }
    private val f by lazy { v.manager.accessors.index(FieldAccessor(owner, "f", "java.lang.Object")) }
    private val g by lazy { v.manager.accessors.index(FieldAccessor(owner, "g", "java.lang.Object")) }
    private val cls by lazy { v.manager.accessors.index(ClassStaticAccessor(owner)) }
    private val empty = ExclusionSet.Empty
    private fun any(p: Position) = PositionWithAccess(p, PositionAccessor.AnyFieldAccessor)
    private fun field(p: Position, name: String = "f") = PositionWithAccess(p,
        PositionAccessor.FieldAccessor(owner, name, "java.lang.Object"))
    private fun element(p: Position) = PositionWithAccess(p, PositionAccessor.ElementAccessor)
    private fun fact(base: AccessPathBase, path: List<Int> = emptyList(), tail: Tail = Tail.EXACT,
                     mark: org.opentaint.dataflow.bidi.ap.TaintMark = t) = PathFact(base, path, tail, MarkSlot.Concrete(mark))
    private fun star(base: AccessPathBase, path: List<Int> = emptyList(), tail: Tail = Tail.STAR) =
        PathFact(base, path, tail, MarkSlot.STAR)
    private fun literal(base: AccessPathBase, mark: String = "T", negated: Boolean = false) =
        ContainsMarkLiteral(PositionAccess.Simple(base), TaintMarkAccessor(mark), negated)
    private fun condition(expr: TaintMarkAwareConditionExpr) = ExprOrConstant(expr)
    private fun edges(builder: MicroEdgeBuilder) = builder.build().edges.map { it.edge }
    /** Small test oracle only: enumerate the already compiled DAG for expected Boolean support sets. */
    private fun predicateChoices(rule: Any, condition: ExprOrConstant): List<List<PathFact>> =
        choices(forms.predicate(rule, condition)).map { it.map { pattern -> pattern.fact } }

    private fun choices(predicate: RulePredicate): List<Set<Pattern>> {
        val values = ArrayList<List<Set<Pattern>>>()
        for (node in 0 until predicate.nodeCount) {
            val children = ArrayList<Int>().also { out -> predicate.childrenOf(node, out::add) }
            values += when (predicate.kind(node)) {
                RulePredicate.Kind.FALSE -> emptyList()
                RulePredicate.Kind.TRUE -> listOf(emptySet())
                RulePredicate.Kind.LEAF -> listOf(setOf(predicate.leaves[predicate.leafOrdinal(node)]))
                RulePredicate.Kind.OR -> children.flatMap { values[it] }.distinct()
                RulePredicate.Kind.AND -> children.fold(listOf(emptySet<Pattern>())) { acc, child ->
                    acc.flatMap { a -> values[child].map { b -> a + b } }.distinct()
                }
            }
        }
        return values[predicate.root]
    }

    private fun sinks(rule: TaintConfigurationSink, condition: ExprOrConstant,
                      mayBeArray: (Int) -> Boolean = { false }): List<SinkRule> {
        val method = v.method("plain")
        val id = RuleOccurrenceId(FormKey(FormOwner(), method, null, method.instList.first()), RuleKind.CALL_SINK, 0)
        return forms.sinks(rule, condition, id, mayBeArray)
    }

    private fun occurrence(rule: TaintConfigurationSource, ordinal: Int = 0): RuleOccurrenceId {
        val method = (rule as TaintMethodSource).method as org.opentaint.ir.api.jvm.JIRMethod
        return RuleOccurrenceId(FormKey(FormOwner(), method, null, method.instList.first()), RuleKind.CALL_SOURCE, ordinal)
    }

    @Suppress("UNCHECKED_CAST")
    private fun rejectListMutation(xs: List<*>) {
        val failure = kotlin.test.assertFails { (xs as MutableList<Any?>).add(null) }
        assertTrue(failure is UnsupportedOperationException || failure is ClassCastException)
    }

    @Test
    fun `position table keeps bases complete paths and final AnyField separately`(): Unit {
        data class Row(val position: Position, val base: AccessPathBase, val path: List<Int>, val any: Boolean)
        val rows = listOf(
            Row(Argument(0), arg0, emptyList(), false),
            Row(This, AccessPathBase.This, emptyList(), false),
            Row(Result, AccessPathBase.Return, emptyList(), false),
            Row(ClassStatic(owner), STATIC, listOf(cls), false),
            Row(any(Argument(0)), arg0, emptyList(), true),
            Row(any(field(element(Argument(1)))), arg1, listOf(e, f), true),
            Row(field(field(Argument(0)), "g"), arg0, listOf(f, g), false),
            Row(field(ClassStatic(owner)), STATIC, listOf(cls, f), false),
        )
        for (row in rows) {
            val p = assertNotNull(forms.pos(row, row.position))
            assertEquals(row.base, p.base, row.toString())
            assertEquals(row.path, p.path, row.toString())
            assertEquals(row.any, p.any, row.toString())
            assertEquals(row.base == STATIC && row.path.size == 1, p.isClass, row.toString())
        }
    }

    @Test
    fun `exceptional result base remaps Result but preserves other bases and paths`(): Unit {
        val exceptional = AccessPathBase.LocalVar(93)
        val x = v.forms(resultBase = exceptional)
        val result = assertNotNull(x.pos("result", any(field(Result))))
        assertEquals(exceptional, result.base)
        assertEquals(listOf(f), result.path)
        assertTrue(result.any)
        assertEquals(arg0, assertNotNull(x.pos("arg", Argument(0))).base)
        assertEquals(STATIC, assertNotNull(x.pos("static", ClassStatic(owner))).base)
    }

    @Test
    fun `invalid AnyField placement is a null result for raw and resolved positions`(): Unit {
        val duplicate = any(any(Argument(0)))
        val afterAny = field(any(Argument(0)))
        assertNull(forms.pos("duplicate", duplicate))
        assertNull(forms.pos("after", afterAny))
        val raw = PositionAccess.Complex(PositionAccess.Complex(PositionAccess.Simple(arg0), AnyAccessor),
            FieldAccessor(owner, "f", "java.lang.Object"))
        assertNull(forms.pos("resolved", raw))
        assertNotNull(forms.pos("valid", any(field(Argument(0)))))
    }

    @Test
    fun `position side constructors distinguish literal source and copy-all tails`(): Unit {
        for (any in listOf(false, true)) {
            val p = assertNotNull(forms.pos("row", if (any) any(field(Argument(0))) else field(Argument(0))))
            assertEquals(fact(arg0, listOf(f), if (any) Tail.ANY else Tail.EXACT), p.fact(t))
            assertEquals(fact(arg0, listOf(f), if (any) Tail.ANY_TAINT else Tail.EXACT), p.sourceFact(t))
            assertEquals(star(arg0, listOf(f), if (any) Tail.ANY else Tail.STAR), p.star())
        }
        assertEquals(t, forms.mark(RuleMark("T")))
        assertEquals(t, forms.mark("T"))
        assertSame(forms.node(listOf(f, g)), forms.node(listOf(f, g)))
        assertNull(forms.node(emptyList()))
    }

    @Test
    fun `generated position and side paths are immutable owned values`(): Unit {
        val p = assertNotNull(forms.pos("owned", field(Argument(0))))
        rejectListMutation(p.path)
        for (side in listOf(p.fact(t), p.sourceFact(t), p.star())) {
            assertEquals(listOf(f), side.path)
            rejectListMutation(side.path)
        }
        val cube = predicateChoices("cube", condition(ContainsMarkLiteral(
            PositionAccess.Complex(PositionAccess.Simple(arg0), FieldAccessor(owner, "f", "java.lang.Object")),
            TaintMarkAccessor("T"), false)))
        rejectListMutation(cube.single().single().path)
    }

    @Test
    fun `ordinary and on-any literals use exact and may with concrete marks`(): Unit {
        val raw = PositionAccess.Complex(PositionAccess.Simple(arg0), FieldAccessor(owner, "f", "java.lang.Object"))
        assertEquals(fact(arg0, listOf(f)), forms.literal("exact", ContainsMarkLiteral(raw, TaintMarkAccessor("T"), false)))
        assertEquals(fact(arg0, listOf(f), Tail.ANY), forms.literal("any", ContainsMarkOnAnyAccessorLiteral(raw, TaintMarkAccessor("T"), false)))
        assertEquals(fact(arg0, listOf(f), Tail.ANY), forms.literal("suffix",
            ContainsMarkLiteral(PositionAccess.Complex(raw, AnyAccessor), TaintMarkAccessor("T"), false)))
        assertNull(forms.literal("bad", ContainsMarkLiteral(PositionAccess.Complex(
            PositionAccess.Complex(raw, AnyAccessor), ElementAccessor), TaintMarkAccessor("T"), false)))
    }

    @Test
    fun `predicate constants and negated literals have source and sink true semantics`(): Unit {
        assertEquals(emptyList(), predicateChoices("false", RuleConditionRewriter.falseExpr))
        assertEquals(listOf(emptyList()), predicateChoices("true", RuleConditionRewriter.trueExpr))
        assertEquals(listOf(emptyList()), predicateChoices("not", condition(literal(arg0, negated = true))))
        val positive = literal(arg0)
        val negative = literal(arg1, "U", true)
        assertEquals(listOf(listOf(fact(arg0))), predicateChoices("and", condition(TaintMarkAwareConditionExpr.And(arrayOf(positive, negative)))))
        assertEquals(listOf(emptyList()), predicateChoices("or", condition(TaintMarkAwareConditionExpr.Or(arrayOf(positive, negative)))))
    }

    @Test
    fun `NNF keeps the truth table without materializing alternatives`(): Unit {
        val p = literal(arg0)
        val q = literal(arg1, "U")
        val r = literal(AccessPathBase.This, "T")
        val expr = TaintMarkAwareConditionExpr.Or(arrayOf(
            TaintMarkAwareConditionExpr.And(arrayOf(p, p, TaintMarkAwareConditionExpr.Or(arrayOf(q, r)))),
            TaintMarkAwareConditionExpr.And(arrayOf(p, q)),
        ))
        val actual = predicateChoices("dnf", condition(expr))
        assertEquals(setOf(setOf(fact(arg0), fact(arg1, mark = u)), setOf(fact(arg0), fact(AccessPathBase.This))),
            actual.map { it.toSet() }.toSet())
        assertEquals(2, actual.size)
        assertTrue(actual.all { it.size == 2 })
        assertEquals(actual, predicateChoices("dnf", condition(expr)))
    }

    @Test
    fun `predicate keeps unary multi repeated bindings and satisfiable footprint without distributing`(): Unit {
        val p = literal(arg0)
        val q = literal(arg1, "U")
        val expression = TaintMarkAwareConditionExpr.Or(arrayOf(p,
            TaintMarkAwareConditionExpr.And(arrayOf(p, p, q))))
        val predicate = forms.predicate("mixed", condition(expression))
        assertEquals(setOf(Pattern(fact(arg0), empty), Pattern(fact(arg1, mark = u), empty)), predicate.leaves.toSet())
        val pi = predicate.leaves.indexOf(Pattern(fact(arg0), empty))
        val qi = predicate.leaves.indexOf(Pattern(fact(arg1, mark = u), empty))
        assertTrue(predicate.isUnary(pi)); assertTrue(predicate.isMulti(pi))
        assertFalse(predicate.isUnary(qi)); assertTrue(predicate.isMulti(qi))
        val repeated = forms.predicate("repeated", condition(TaintMarkAwareConditionExpr.And(arrayOf(p, p))))
        assertEquals(1, repeated.leaves.size)
        assertTrue(repeated.isUnary(0)); assertFalse(repeated.isMulti(0))
        assertEquals(setOf(setOf(Pattern(fact(arg0), empty)), predicate.leaves.toSet()), choices(predicate).toSet())
    }

    @Test
    fun `twenty binary source choices compile to a linear predicate with separate actions`(): Unit {
        val expression = TaintMarkAwareConditionExpr.And(Array(20) { index ->
            TaintMarkAwareConditionExpr.Or(arrayOf(literal(AccessPathBase.Argument(2 * index)),
                literal(AccessPathBase.Argument(2 * index + 1))))
        })
        val rule = v.source(listOf(AssignMark(RuleMark("T"), Result), AssignMark(RuleMark("U"), any(Result))))
        val summary = MicroEdgeBuilder().also { forms.source(it, rule, occurrence(rule), condition(expression)) }.buildStage()
        val source = summary.sources.single()
        assertEquals(40, source.predicate.leaves.size)
        assertEquals(61, source.predicate.nodeCount)
        assertEquals(60, source.predicate.childCount)
        assertEquals(2, source.actions.size)
        assertEquals(listOf(fact(AccessPathBase.Return), fact(AccessPathBase.Return, tail = Tail.ANY_TAINT, mark = u)),
            source.actions.map { it.target })
        assertTrue(summary.edges.isEmpty()); assertTrue(summary.conjunctions.isEmpty())
        assertTrue(source.predicate.leaves.indices.all { source.predicate.isMulti(it) && !source.predicate.isUnary(it) })
        rejectListMutation(source.predicate.leaves)
        source.predicate.leaves.forEach { rejectListMutation(it.fact.path) }
    }

    @Test
    fun `optional compilation counters sum actual compact graphs and constants`(): Unit {
        val statistics = FormCacheStatistics()
        val counted = org.opentaint.dataflow.jvm.bidi.interp.JIRRuleForms(forms.rules, v.manager,
            org.opentaint.dataflow.jvm.bidi.interp.RuleErrors(), v.typeFilters, statistics = statistics)
        counted.predicate("false", RuleConditionRewriter.falseExpr)
        counted.predicate("true", RuleConditionRewriter.trueExpr)
        counted.predicate("and", condition(TaintMarkAwareConditionExpr.And(arrayOf(literal(arg0), literal(arg1))))) { true }
        val snapshot = statistics.snapshot()
        assertEquals(3L, snapshot.compiledPredicates)
        assertEquals(9L, snapshot.compiledNodes)
        assertEquals(4L, snapshot.compiledLeaves)
        assertEquals(0L, snapshot.accesses)
        assertEquals(0L, snapshot.buildAttempts)
    }

    @Test
    fun `empty source action list emits no descriptor but preserves its compiled exit read footprint`(): Unit {
        val rule = v.source(emptyList())
        val builder = MicroEdgeBuilder()
        val predicate = forms.source(builder, rule, occurrence(rule), condition(literal(arg0)))
        val summary = builder.buildStage()
        assertEquals(listOf(Pattern(fact(arg0), empty)), predicate.leaves)
        assertTrue(summary.edges.isEmpty())
        assertTrue(summary.sources.isEmpty())
        assertTrue(summary.conjunctions.isEmpty())
        assertTrue(summary.touched.isEmpty())
    }

    @Test
    fun `source target table uses exact or must and retains no may annotation`(): Unit {
        for (position in listOf<Position>(Result, any(field(Result)))) {
            val a = AssignMark(RuleMark("U"), position)
            val rule = v.source(listOf(a))
            assertTrue(forms.valid(rule))
            val expected = if (position == Result) fact(AccessPathBase.Return, mark = u)
                else fact(AccessPathBase.Return, listOf(f), Tail.ANY_TAINT, u)
            assertEquals(expected, forms.markTarget(rule, a))
            val unconditional = MicroEdgeBuilder().also {
                forms.source(it, rule, occurrence(rule), RuleConditionRewriter.trueExpr)
            }.build()
            assertEquals(listOf(PathEdge(ZERO_FACT, expected, empty)), unconditional.edges.map { it.edge })
            assertTrue(unconditional.edges.single().isSource)
            assertFalse(unconditional.edges.single().may)
            val summary = MicroEdgeBuilder().also {
                forms.source(it, rule, occurrence(rule), condition(literal(arg0)))
            }.build()
            assertTrue(summary.edges.isEmpty())
            val source = summary.sources.single()
            assertEquals(listOf(Pattern(fact(arg0), empty)), source.predicate.leaves)
            assertEquals(expected, source.actions.single().target)
            assertEquals(PathEdge(fact(arg0), expected, empty), source.transfer(source.predicate.leaves.single(), source.actions.single()).edge)
            assertEquals(EdgeOutcome.Fact(Conclusion(expected, empty, false)),
                concat(Conclusion(fact(arg0), empty, false), source.transfer(source.predicate.leaves.single(), source.actions.single()).edge))
        }
    }

    @Test
    fun `source conjunction has concrete exact or may inputs and must target with empty exclusions`(): Unit {
        val a = AssignMark(RuleMark("U"), any(Result))
        val rule = v.source(listOf(a))
        val fieldLiteral = ContainsMarkOnAnyAccessorLiteral(PositionAccess.Complex(PositionAccess.Simple(arg1),
            FieldAccessor(owner, "f", "java.lang.Object")), TaintMarkAccessor("T"), false)
        val expression = TaintMarkAwareConditionExpr.And(arrayOf(literal(arg0), fieldLiteral))
        val summary = MicroEdgeBuilder().also { forms.source(it, rule, occurrence(rule), condition(expression)) }.build()
        assertTrue(summary.edges.isEmpty()); assertTrue(summary.conjunctions.isEmpty())
        val source = summary.sources.single()
        assertEquals(setOf(Pattern(fact(arg0), empty), Pattern(fact(arg1, listOf(f), Tail.ANY), empty)),
            source.predicate.leaves.toSet())
        assertTrue(source.predicate.leaves.indices.all { source.predicate.isMulti(it) && !source.predicate.isUnary(it) })
        assertEquals(fact(AccessPathBase.Return, tail = Tail.ANY_TAINT, mark = u), source.actions.single().target)
    }

    @Test
    fun `copy-all pass table uses abstract star source and star or may target`(): Unit {
        for (any in listOf(false, true)) {
            val action = CopyAllMarks(field(Argument(0)), if (any) any(field(Result, "g")) else field(Result, "g"))
            val rule = v.pass(listOf(action))
            assertTrue(forms.valid(rule))
            val b = MicroEdgeBuilder()
            assertEquals(arg0 to AccessPathBase.Return, forms.pass(b, rule, action))
            val s = b.buildStage()
            val expected = PathEdge(star(arg0, listOf(f)), star(AccessPathBase.Return, listOf(g), if (any) Tail.ANY else Tail.STAR), empty)
            assertEquals(listOf(PathEdge(star(arg0), star(arg0), empty), expected), s.edges.map { it.edge })
            assertEquals(setOf(arg0, AccessPathBase.Return), s.touched)
            assertEquals(any, s.edges.last().may)
            assertFalse(s.edges.last().isSource)
            val input = Conclusion(fact(arg0, listOf(f)), empty, false)
            assertEquals(EdgeOutcome.Fact(Conclusion(fact(AccessPathBase.Return, listOf(g), if (any) Tail.ANY else Tail.EXACT), empty, any)),
                concat(input, expected, edgeDemand = any))
        }
    }

    @Test
    fun `copy-mark pass uses its own literal and ignored action makes no edge`(): Unit {
        for (any in listOf(false, true)) {
            val action = CopyMark(RuleMark("T"), field(Argument(0)), if (any) any(Result) else Result)
            val rule = v.pass(listOf(action))
            val b = MicroEdgeBuilder()
            assertEquals(arg0 to AccessPathBase.Return, forms.pass(b, rule, action))
            val expected = PathEdge(fact(arg0, listOf(f)), fact(AccessPathBase.Return, tail = if (any) Tail.ANY else Tail.EXACT), empty)
            assertEquals(listOf(PathEdge(star(arg0), star(arg0), empty), expected), edges(b))
            assertEquals(any, b.build().edges.last().may)
            assertTrue(b.build().conjunctions.isEmpty())
            assertEquals(EdgeOutcome.Fact(Conclusion(expected.to, empty, any)),
                concat(Conclusion(expected.from, empty, false), expected, edgeDemand = any))
        }
        val b = MicroEdgeBuilder()
        assertNull(forms.pass(b, v.pass(emptyList()), AssignMark(RuleMark("T"), Result)))
        assertTrue(b.build().edges.isEmpty())
    }

    @Test
    fun `new core ignores end actions before rule position validation`(): Unit {
        val invalid = field(any(Argument(0)))
        val rule = v.sink(listOf(AssignMark(RuleMark("T"), invalid)))
        assertTrue(forms.valid(rule))
        assertTrue(forms.endFacts(rule).isEmpty())
        assertTrue(sinks(rule, RuleConditionRewriter.trueExpr).single().endFacts.isEmpty())
        assertTrue(rule.trackFactsReachAnalysisEnd.isNotEmpty(), "The shared full rule is unchanged")
    }

    @Test
    fun `equal source action tuples retain separate stable occurrences through reversal`(): Unit {
        val action = AssignMark(RuleMark("T"), Result)
        val rule = v.source(listOf(action, action))
        val id = occurrence(rule)
        val builder = MicroEdgeBuilder()
        forms.source(builder, rule, id, RuleConditionRewriter.trueExpr)
        val full = builder.build()
        assertEquals(2, full.edges.size)
        assertEquals(full.edges[0].edge, full.edges[1].edge)
        assertEquals(listOf(ActionId(id, ActionRole.SOURCE, 0), ActionId(id, ActionRole.SOURCE, 1)),
            full.edges.map { it.actionId })
        assertEquals(full.edges.mapNotNull { it.actionId }, full.reversed().edges.mapNotNull { it.actionId })
    }

    @Test
    fun `sink constants use zero unconditional pattern and false has no alternatives`(): Unit {
        val rule = v.sink(listOf(AssignMark(RuleMark("U"), any(Result))))
        val sink = sinks(rule, RuleConditionRewriter.trueExpr).single()
        assertSame(rule, sink.rule)
        assertEquals(0, sink.alternative)
        assertEquals(listOf(ZERO_PATTERN), sink.patterns)
        assertTrue(sink.unconditional)
        assertTrue(sink.seedPatterns().isEmpty())
        assertTrue(sink.endFacts.isEmpty())
        assertTrue(sinks(rule, RuleConditionRewriter.falseExpr).isEmpty())
        assertEquals(listOf(ZERO_PATTERN), sinks(rule, condition(literal(arg0, negated = true))).single().patterns)
    }

    @Test
    fun `single argument sink uses one local root or element predicate with unchanged tail`(): Unit {
        val rule = v.sink()
        val lit = ContainsMarkOnAnyAccessorLiteral(PositionAccess.Complex(PositionAccess.Simple(arg0),
            FieldAccessor(owner, "f", "java.lang.Object")), TaintMarkAccessor("T"), false)
        val expected = listOf(listOf(Pattern(fact(arg0, listOf(f), Tail.ANY), empty)),
            listOf(Pattern(fact(arg0, listOf(e, f), Tail.ANY), empty)))
        repeat(3) {
            val sinks = sinks(rule, condition(lit)) { it == 0 }
            val sink = sinks.single()
            assertEquals(0, sink.alternative)
            assertEquals(expected.flatten(), sink.patterns)
            assertEquals(expected.map { it.toSet() }.toSet(), choices(assertNotNull(sink.predicate)).toSet())
            assertSame(rule, sink.rule)
            assertFalse(sink.unconditional)
            assertTrue(sink.predicate!!.leaves.indices.all { sink.predicate!!.isUnary(it) && !sink.predicate!!.isMulti(it) })
        }
        assertEquals(expected.take(1), sinks(rule, condition(lit)).map { it.patterns })
    }

    @Test
    fun `conjunctive sink keeps array OR choices without a branch product`(): Unit {
        val rule = v.sink()
        val p = literal(arg0)
        val q = literal(arg1, "U")
        val expr = TaintMarkAwareConditionExpr.Or(arrayOf(
            TaintMarkAwareConditionExpr.And(arrayOf(p, q, p)),
            TaintMarkAwareConditionExpr.And(arrayOf(p, q)),
        ))
        val sinks = sinks(rule, condition(expr)) { true }
        val expected = setOf(
            setOf(Pattern(fact(arg0), empty), Pattern(fact(arg1, mark = u), empty)),
            setOf(Pattern(fact(arg0, listOf(e)), empty), Pattern(fact(arg1, mark = u), empty)),
            setOf(Pattern(fact(arg0), empty), Pattern(fact(arg1, listOf(e), mark = u), empty)),
            setOf(Pattern(fact(arg0, listOf(e)), empty), Pattern(fact(arg1, listOf(e), mark = u), empty)),
        )
        val predicate = assertNotNull(sinks.single().predicate)
        assertEquals(expected, choices(predicate).toSet())
        assertEquals(4, predicate.leaves.size)
        assertEquals(7, predicate.nodeCount)
        assertTrue(predicate.leaves.indices.all { predicate.isMulti(it) && !predicate.isUnary(it) })
        assertEquals(sinks.map { it.patterns }, sinks(rule, condition(expr)) { true }.map { it.patterns })
    }

    @Test
    fun `array expansion is not applied to this result or static sink positions`(): Unit {
        for (base in listOf(AccessPathBase.This, AccessPathBase.Return, STATIC)) {
            val queries = arrayListOf<Int>()
            val sinks = sinks(v.sink(), condition(literal(base))) { queries += it; true }
            assertEquals(listOf(listOf(Pattern(fact(base), empty))), sinks.map { it.patterns })
            assertTrue(queries.isEmpty())
        }
    }

    @Test
    fun `static read queries every reduced rule with null fact and rewrites Result to lhs`(): Unit {
        val inst = v.method("staticRead").instList.instructions.filterIsInstance<JIRAssignInst>().first { it.rhv is JIRFieldRef }
        val ref = inst.rhv as JIRFieldRef
        val field = ref.field.field
        val a = AssignMark(RuleMark("T"), Result)
        val b = AssignMark(RuleMark("U"), any(Result))
        val first = TaintStaticFieldSource(field, JIRRuleTestFixtures.trueCondition, listOf(a), null)
        val second = TaintStaticFieldSource(field, JIRRuleTestFixtures.trueCondition, listOf(b), null)
        val invalid = TaintStaticFieldSource(field, JIRRuleTestFixtures.trueCondition, listOf(AssignMark(RuleMark("T"), Argument(0))), null)
        val provider = RecordingTaintRules(listOf(first, second, invalid))
        val x = v.forms(provider)
        val lhs = assertNotNull(org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.accessPathBase(inst.lhv))
        val builder = MicroEdgeBuilder()
        x.readSources(builder, inst)
        val summary = builder.build()
        assertEquals(setOf(AccessPathBase.Zero), summary.touched)
        assertEquals(listOf(ZERO_KEEP, PathEdge(ZERO_FACT, fact(lhs), empty),
            PathEdge(ZERO_FACT, fact(lhs, tail = Tail.ANY_TAINT, mark = u), empty)), summary.edges.map { it.edge })
        val query = provider.staticQueries.single()
        assertSame(field, query.field)
        assertSame(inst, query.statement)
        assertNull(query.fact)
        assertFalse(query.allRelevant)
        assertTrue(summary.edges.drop(1).all { it.isSource && !it.may })
    }

    @Test
    fun `no static read rule and nonstatic read leave builder unchanged`(): Unit {
        val provider = RecordingTaintRules()
        val x = v.forms(provider)
        val static = v.method("staticRead").instList.instructions.filterIsInstance<JIRAssignInst>().first { it.rhv is JIRFieldRef }
        val instance = v.method("plain").instList.instructions.filterIsInstance<JIRAssignInst>().first { it.rhv is JIRFieldRef }
        val builder = MicroEdgeBuilder()
        x.readSources(builder, static)
        assertTrue(builder.build().edges.isEmpty())
        assertTrue(builder.build().touched.isEmpty())
        x.readSources(builder, instance)
        assertEquals(1, provider.staticQueries.size)
        assertTrue(builder.build().edges.isEmpty())
    }
}
