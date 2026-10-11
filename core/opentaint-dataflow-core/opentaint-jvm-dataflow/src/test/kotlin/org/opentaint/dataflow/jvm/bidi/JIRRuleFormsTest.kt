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
    @Suppress("UNCHECKED_CAST")
    private fun rejectListMutation(xs: List<*>) {
        assertFailsWith<UnsupportedOperationException> { (xs as MutableList<Any?>).add(null) }
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
        val cube = forms.cubes("cube", condition(ContainsMarkLiteral(
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
    fun `cube constants and negated literals have source and sink true semantics`(): Unit {
        assertEquals(emptyList(), forms.cubes("false", RuleConditionRewriter.falseExpr))
        assertEquals(listOf(emptyList()), forms.cubes("true", RuleConditionRewriter.trueExpr))
        assertEquals(listOf(emptyList()), forms.cubes("not", condition(literal(arg0, negated = true))))
        val positive = literal(arg0)
        val negative = literal(arg1, "U", true)
        assertEquals(listOf(listOf(fact(arg0))), forms.cubes("and", condition(TaintMarkAwareConditionExpr.And(arrayOf(positive, negative)))))
        assertEquals(listOf(emptyList()), forms.cubes("or", condition(TaintMarkAwareConditionExpr.Or(arrayOf(positive, negative)))))
    }

    @Test
    fun `DNF distributes alternatives and deduplicates literals and complete cubes`(): Unit {
        val p = literal(arg0)
        val q = literal(arg1, "U")
        val r = literal(AccessPathBase.This, "T")
        val expr = TaintMarkAwareConditionExpr.Or(arrayOf(
            TaintMarkAwareConditionExpr.And(arrayOf(p, p, TaintMarkAwareConditionExpr.Or(arrayOf(q, r)))),
            TaintMarkAwareConditionExpr.And(arrayOf(p, q)),
        ))
        val actual = forms.cubes("dnf", condition(expr))
        assertEquals(setOf(setOf(fact(arg0), fact(arg1, mark = u)), setOf(fact(arg0), fact(AccessPathBase.This))),
            actual.map { it.toSet() }.toSet())
        assertEquals(2, actual.size)
        assertTrue(actual.all { it.size == 2 })
        assertEquals(actual, forms.cubes("dnf", condition(expr)))
    }

    @Test
    fun `rule edge cardinality gives zero plain or independent conjunction slots`(): Unit {
        val target = fact(AccessPathBase.Return, listOf(g), Tail.ANY_TAINT, u)
        val a = fact(arg0)
        val b = fact(arg1, listOf(f), Tail.ANY)
        val unconditional = MicroEdgeBuilder().also { forms.ruleEdge(it, emptyList(), target) }.build()
        assertEquals(listOf(PathEdge(ZERO_FACT, target, empty)), unconditional.edges.map { it.edge })
        assertTrue(unconditional.edges.single().isSource)
        assertFalse(unconditional.edges.single().may)
        val plain = MicroEdgeBuilder().also { forms.ruleEdge(it, listOf(a), target) }.build()
        assertEquals(listOf(PathEdge(a, target, empty)), plain.edges.map { it.edge })
        assertFalse(plain.edges.single().isSource)
        assertFalse(plain.edges.single().may)
        for (premises in listOf(listOf(a, b), listOf(a, a))) {
            val s = MicroEdgeBuilder().also { forms.ruleEdge(it, premises, target) }.build()
            assertTrue(s.edges.isEmpty())
            val conjunction = s.conjunctions.single()
            assertEquals(premises.map { Pattern(it, empty) }, conjunction.literals)
            assertEquals(target, conjunction.target)
            assertEquals(2, conjunction.literals.size)
        }
    }

    @Test
    fun `rule edge products own mutable premise target paths and conjunction lists`(): Unit {
        val fromPath = arrayListOf(f)
        val toPath = arrayListOf(g)
        val p = fact(arg0, fromPath)
        val target = fact(AccessPathBase.Return, toPath)
        val one = MicroEdgeBuilder().also { forms.ruleEdge(it, listOf(p), target) }.build().edges.single().edge
        val inputs = arrayListOf(p, p)
        val conjunction = MicroEdgeBuilder().also { forms.ruleEdge(it, inputs, target) }.build().conjunctions.single()
        fromPath.clear(); toPath.clear(); inputs.clear()
        assertEquals(listOf(f), one.from.path)
        assertEquals(listOf(g), one.to.path)
        assertEquals(listOf(Pattern(fact(arg0, listOf(f)), empty), Pattern(fact(arg0, listOf(f)), empty)), conjunction.literals)
        assertEquals(fact(AccessPathBase.Return, listOf(g)), conjunction.target)
        rejectListMutation(one.from.path)
        rejectListMutation(one.to.path)
        rejectListMutation(conjunction.literals)
        rejectListMutation(conjunction.literals[0].fact.path)
        rejectListMutation(conjunction.target.path)
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
            for (cube in listOf(emptyList(), listOf(fact(arg0)))) {
                val s = MicroEdgeBuilder().also { forms.source(it, rule, cube, a) }.build()
                assertEquals(listOf(PathEdge(cube.singleOrNull() ?: ZERO_FACT, expected, empty)), s.edges.map { it.edge })
                assertEquals(cube.isEmpty(), s.edges.single().isSource)
                assertFalse(s.edges.single().may)
                val input = Conclusion(cube.singleOrNull() ?: ZERO_FACT, empty, false)
                assertEquals(EdgeOutcome.Fact(Conclusion(expected, empty, false)), concat(input, s.edges.single().edge))
            }
        }
    }

    @Test
    fun `source conjunction has concrete exact or may inputs and must target with empty exclusions`(): Unit {
        val a = AssignMark(RuleMark("U"), any(Result))
        val rule = v.source(listOf(a))
        val cube = listOf(fact(arg0), fact(arg1, listOf(f), Tail.ANY))
        val s = MicroEdgeBuilder().also { forms.source(it, rule, cube, a) }.build()
        assertTrue(s.edges.isEmpty())
        val conjunction = s.conjunctions.single()
        assertEquals(cube.map { Pattern(it, empty) }, conjunction.literals)
        assertEquals(fact(AccessPathBase.Return, tail = Tail.ANY_TAINT, mark = u), conjunction.target)
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
    fun `end facts share source exact and must targets in GEN mode`(): Unit {
        val actions = listOf(AssignMark(RuleMark("T"), Result), AssignMark(RuleMark("U"), any(field(Argument(0)))))
        val rule = v.sink(actions)
        val ends = forms.endFacts(rule)
        assertEquals(listOf(PathEdge(ZERO_FACT, fact(AccessPathBase.Return), empty),
            PathEdge(ZERO_FACT, fact(arg0, listOf(f), Tail.ANY_TAINT, u), empty)), ends.map { it.edge })
        assertTrue(ends.all { it.isSource && !it.may && it.forward === it.edge })
        assertTrue(forms.endFacts(v.sink()).isEmpty())
    }

    @Test
    fun `sink constants use zero unconditional pattern and false has no alternatives`(): Unit {
        val rule = v.sink(listOf(AssignMark(RuleMark("U"), any(Result))))
        val sink = forms.sinks(rule, RuleConditionRewriter.trueExpr).single()
        assertSame(rule, sink.rule)
        assertEquals(0, sink.alternative)
        assertEquals(listOf(ZERO_PATTERN), sink.patterns)
        assertTrue(sink.unconditional)
        assertTrue(sink.seedPatterns().isEmpty())
        assertEquals(listOf(PathEdge(ZERO_FACT, fact(AccessPathBase.Return, tail = Tail.ANY_TAINT, mark = u), empty)), sink.endFacts.map { it.edge })
        assertTrue(forms.sinks(rule, RuleConditionRewriter.falseExpr).isEmpty())
        assertEquals(listOf(ZERO_PATTERN), forms.sinks(rule, condition(literal(arg0, negated = true))).single().patterns)
    }

    @Test
    fun `single argument sink has stable root then element alternatives with unchanged tail`(): Unit {
        val rule = v.sink()
        val lit = ContainsMarkOnAnyAccessorLiteral(PositionAccess.Complex(PositionAccess.Simple(arg0),
            FieldAccessor(owner, "f", "java.lang.Object")), TaintMarkAccessor("T"), false)
        val expected = listOf(listOf(Pattern(fact(arg0, listOf(f), Tail.ANY), empty)),
            listOf(Pattern(fact(arg0, listOf(e, f), Tail.ANY), empty)))
        repeat(3) {
            val sinks = forms.sinks(rule, condition(lit)) { it == 0 }
            assertEquals(listOf(0, 1), sinks.map { it.alternative })
            assertEquals(expected, sinks.map { it.patterns })
            assertTrue(sinks.all { it.rule === rule && !it.unconditional && !it.conjunctive })
        }
        assertEquals(expected.take(1), forms.sinks(rule, condition(lit)).map { it.patterns })
    }

    @Test
    fun `conjunctive sink expands independent array choices and deduplicates alternatives`(): Unit {
        val rule = v.sink()
        val p = literal(arg0)
        val q = literal(arg1, "U")
        val expr = TaintMarkAwareConditionExpr.Or(arrayOf(
            TaintMarkAwareConditionExpr.And(arrayOf(p, q, p)),
            TaintMarkAwareConditionExpr.And(arrayOf(p, q)),
        ))
        val sinks = forms.sinks(rule, condition(expr)) { true }
        val expected = setOf(
            setOf(Pattern(fact(arg0), empty), Pattern(fact(arg1, mark = u), empty)),
            setOf(Pattern(fact(arg0, listOf(e)), empty), Pattern(fact(arg1, mark = u), empty)),
            setOf(Pattern(fact(arg0), empty), Pattern(fact(arg1, listOf(e), mark = u), empty)),
            setOf(Pattern(fact(arg0, listOf(e)), empty), Pattern(fact(arg1, listOf(e), mark = u), empty)),
        )
        assertEquals(expected, sinks.map { it.patterns.toSet() }.toSet())
        assertEquals(listOf(0, 1, 2, 3), sinks.map { it.alternative })
        assertTrue(sinks.all { it.conjunctive && it.patterns.size == 2 })
        assertEquals(sinks.map { it.patterns }, forms.sinks(rule, condition(expr)) { true }.map { it.patterns })
    }

    @Test
    fun `array expansion is not applied to this result or static sink positions`(): Unit {
        for (base in listOf(AccessPathBase.This, AccessPathBase.Return, STATIC)) {
            val queries = arrayListOf<Int>()
            val sinks = forms.sinks(v.sink(), condition(literal(base))) { queries += it; true }
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
