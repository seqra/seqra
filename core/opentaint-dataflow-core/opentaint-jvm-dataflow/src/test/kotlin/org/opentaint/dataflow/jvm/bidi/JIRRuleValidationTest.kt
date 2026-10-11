package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.ap.Tail
import org.opentaint.dataflow.bidi.interp.MicroEdgeBuilder
import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.mkFalse
import org.opentaint.dataflow.configuration.mkTrue
import org.opentaint.dataflow.configuration.jvm.Action
import org.opentaint.dataflow.configuration.jvm.Argument
import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.configuration.jvm.ClassStatic
import org.opentaint.dataflow.configuration.jvm.Condition
import org.opentaint.dataflow.configuration.jvm.ContainsMark
import org.opentaint.dataflow.configuration.jvm.CopyAllMarks
import org.opentaint.dataflow.configuration.jvm.CopyMark
import org.opentaint.dataflow.configuration.jvm.Position
import org.opentaint.dataflow.configuration.jvm.PositionAccessor
import org.opentaint.dataflow.configuration.jvm.PositionWithAccess
import org.opentaint.dataflow.configuration.jvm.RemoveAllMarks
import org.opentaint.dataflow.configuration.jvm.RemoveMark
import org.opentaint.dataflow.configuration.jvm.Result
import org.opentaint.dataflow.configuration.jvm.TaintCleaner
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationItem
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationSink
import org.opentaint.dataflow.configuration.jvm.TaintEntryPointSource
import org.opentaint.dataflow.configuration.jvm.TaintMark as RuleMark
import org.opentaint.dataflow.configuration.jvm.TaintMethodEntrySink
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSink
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSource
import org.opentaint.dataflow.configuration.jvm.TaintMethodSink
import org.opentaint.dataflow.configuration.jvm.TaintMethodSource
import org.opentaint.dataflow.configuration.jvm.TaintPassThrough
import org.opentaint.dataflow.configuration.jvm.TaintSinkMeta
import org.opentaint.dataflow.configuration.jvm.TaintStaticFieldSource
import org.opentaint.dataflow.configuration.jvm.This
import org.opentaint.dataflow.jvm.BasicTestUtils
import org.opentaint.dataflow.jvm.ap.ifds.taint.ContainsMarkOnAnyField
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRFormsTestFixtures
import org.opentaint.dataflow.jvm.bidi.interp.JIRRuleForms
import org.opentaint.dataflow.jvm.bidi.interp.RuleErrors
import org.opentaint.dataflow.taint.PositionAccess
import org.opentaint.dataflow.taint.RuleConditionRewriter
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JIRRuleValidationTest : BasicTestUtils() {
    private val f by lazy { JIRFormsTestFixtures(cp) }
    private val method by lazy { f.method("copy") }
    private val field by lazy {
        (f.assign("staticRead") { (it.rhv as? JIRFieldRef)?.field?.name == "sField" }.rhv as JIRFieldRef).field.field
    }
    private val t = RuleMark("T")
    private val u = RuleMark("U")
    private val meta = TaintSinkMeta("validation", CommonTaintConfigurationSinkMeta.Severity.Error, null)
    private val arg0: Position = Argument(0)
    private val arg1: Position = Argument(1)
    private val cls: Position = ClassStatic(JIRFormsTestFixtures.SAMPLE_CLASS)
    private fun any(p: Position) = PositionWithAccess(p, PositionAccessor.AnyFieldAccessor)
    private fun element(p: Position) = PositionWithAccess(p, PositionAccessor.ElementAccessor)
    private fun field(p: Position, name: String = "f") = PositionWithAccess(p,
        PositionAccessor.FieldAccessor(JIRFormsTestFixtures.SAMPLE_CLASS, name, "java.lang.Object"))
    private fun contains(p: Position): Condition = CommonCondition.Atom(ContainsMark(p, t))
    private fun containsAny(p: Position): Condition = CommonCondition.Atom(ContainsMarkOnAnyField(p, t))
    private fun source(actions: List<AssignMark>, condition: Condition = mkTrue()) =
        TaintMethodSource(method, condition, actions, null)
    private fun pass(actions: List<Action>, condition: Condition = mkTrue()) =
        TaintPassThrough(method, condition, actions, null)
    private fun cleaner(actions: List<Action>, condition: Condition = mkTrue()) =
        TaintCleaner(method, condition, actions, null)
    private fun sink(ends: List<AssignMark> = emptyList(), condition: Condition = mkTrue()) =
        TaintMethodSink(method, condition, ends, "validation", meta, null)
    private fun forms(provider: TaintRulesProvider = EmptyProvider(), errors: RuleErrors = RuleErrors(),
                      resultBase: AccessPathBase = AccessPathBase.Return) =
        JIRRuleForms(provider, f.manager, errors, f.typeFilters, resultBase)

    @Test
    fun `rule positions retain every concrete accessor and remove only terminal AnyField`(): Unit {
        val forms = forms()
        val owner = Any()
        val paths = listOf(arg0, This, Result, cls, field(element(arg0)), any(field(element(arg0))))
        val expectedBases = listOf(AccessPathBase.Argument(0), AccessPathBase.This, AccessPathBase.Return,
            AccessPathBase.ClassStatic, AccessPathBase.Argument(0), AccessPathBase.Argument(0))
        val expectedPaths = listOf(emptyList(), emptyList(), emptyList(), listOf(f.cls),
            listOf(f.element, f.field("f")), listOf(f.element, f.field("f")))
        for (i in paths.indices) {
            val p = assertNotNull(forms.pos(owner, paths[i]), "$i")
            assertEquals(expectedBases[i], p.base)
            assertEquals(expectedPaths[i], p.path)
            assertEquals(i == paths.lastIndex, p.any)
            assertEquals(i == 3, p.isClass)
        }
        assertEquals(AccessPathBase.Exception, assertNotNull(forms(resultBase = AccessPathBase.Exception)
            .pos(owner, any(Result))).base)
        val accessed = PositionAccess.Complex(PositionAccess.Complex(
            PositionAccess.Simple(AccessPathBase.Argument(0)), ElementAccessor), AnyAccessor)
        assertEquals(listOf(f.element), assertNotNull(forms.pos(owner, accessed)).path)
        for (bad in listOf(field(any(arg0)), element(any(arg0)), any(any(arg0)), any(field(any(arg0))))) {
            assertNull(forms.pos(owner, bad), "$bad")
        }
        assertNull(forms.pos(owner, PositionAccess.Complex(PositionAccess.Complex(
            PositionAccess.Simple(AccessPathBase.Argument(0)), AnyAccessor),
            FieldAccessor(JIRFormsTestFixtures.SAMPLE_CLASS, "f", "java.lang.Object"))))
    }

    @Test
    fun `each raw action position is validated even when its action kind is ignored`(): Unit {
        val forms = forms()
        for (bad in listOf(field(any(arg0)), element(any(arg0)), any(any(arg0)))) {
            val sourceCases = listOf<TaintConfigurationItem>(
                source(listOf(AssignMark(t, Result), AssignMark(u, bad))),
                TaintEntryPointSource(method, mkTrue(), listOf(AssignMark(t, arg1), AssignMark(u, bad)), null),
                TaintMethodExitSource(method, mkTrue(), listOf(AssignMark(t, Result), AssignMark(u, bad)), null),
                sink(listOf(AssignMark(t, Result), AssignMark(u, bad))),
                TaintMethodEntrySink(method, mkTrue(), listOf(AssignMark(t, Result), AssignMark(u, bad)), "entry", meta, null),
                TaintMethodExitSink(method, mkTrue(), listOf(AssignMark(t, Result), AssignMark(u, bad)), "exit", meta, null),
            )
            val badActions: List<Action> = listOf(CopyAllMarks(bad, arg1), CopyAllMarks(arg1, bad),
                CopyMark(t, bad, arg1), CopyMark(t, arg1, bad), AssignMark(t, bad), RemoveMark(t, bad), RemoveAllMarks(bad))
            val cases = sourceCases + badActions.flatMap { action -> listOf(
                pass(listOf(CopyAllMarks(arg0, arg1), action)),
                cleaner(listOf(RemoveMark(t, arg1), action)),
            ) }
            for (rule in cases) assertFalse(forms.valid(rule), "$rule")
            // A statically false branch cannot hide the bad action either.
            assertFalse(forms.valid(source(listOf(AssignMark(t, Result), AssignMark(u, bad)), mkFalse())))
            assertFalse(forms.valid(pass(listOf(CopyAllMarks(arg0, arg1), AssignMark(u, bad)), mkFalse())))
            assertFalse(forms.valid(cleaner(listOf(RemoveMark(t, arg1), CopyAllMarks(arg0, bad)), mkFalse())))
        }
        assertTrue(forms.valid(source(listOf(AssignMark(t, Result)))))
        assertTrue(forms.valid(pass(listOf(CopyAllMarks(arg0, arg1), AssignMark(t, Result)))))
        assertTrue(forms.valid(cleaner(listOf(RemoveMark(t, arg0), CopyMark(t, arg0, arg1)))))
    }

    @Test
    fun `negated literals and every discarded condition alternative are validated first`(): Unit {
        val forms = forms()
        for (bad in listOf(field(any(arg0)), any(any(arg0)))) {
            val conditions = listOf(contains(bad), containsAny(bad),
                CommonCondition.Not(contains(bad)), CommonCondition.Not(containsAny(bad)),
                CommonCondition.Or(listOf(mkTrue(), contains(bad))),
                CommonCondition.Or(listOf(contains(arg1), CommonCondition.Not(containsAny(bad)))),
                CommonCondition.And(listOf(mkFalse(), contains(bad))))
            for (condition in conditions) {
                val cases = listOf<TaintConfigurationItem>(
                    source(listOf(AssignMark(t, Result)), condition),
                    TaintEntryPointSource(method, condition, listOf(AssignMark(t, arg1)), null),
                    TaintMethodExitSource(method, condition, listOf(AssignMark(t, Result)), null),
                    sink(condition = condition),
                    TaintMethodEntrySink(method, condition, emptyList(), "entry", meta, null),
                    TaintMethodExitSink(method, condition, emptyList(), "exit", meta, null),
                    pass(listOf(CopyAllMarks(arg0, arg1)), condition),
                    cleaner(listOf(RemoveMark(t, arg1)), condition),
                )
                for (rule in cases) assertFalse(forms.valid(rule), "$rule")
            }
        }
    }

    @Test
    fun `whole rule rejection gives no partial source pass cleaner sink or end forms`(): Unit {
        val forms = forms()
        val bad = field(any(arg0))
        val src = source(listOf(AssignMark(t, Result), AssignMark(u, bad)))
        val ps = pass(listOf(CopyAllMarks(arg0, arg1), CopyMark(u, arg0, bad)))
        val cl = cleaner(listOf(RemoveMark(t, arg1), RemoveAllMarks(bad)))
        val sk = sink(listOf(AssignMark(t, Result), AssignMark(u, bad)), contains(arg1))
        val conditionSink = sink(listOf(AssignMark(t, Result)), CommonCondition.Or(listOf(contains(arg1), contains(bad))))
        val builder = MicroEdgeBuilder()
        if (forms.valid(src)) src.actionsAfter.forEach { forms.source(builder, src, emptyList(), it) }
        if (forms.valid(ps)) ps.actionsAfter.forEach { forms.pass(builder, ps, it) }
        var cleanerActions = 0
        if (forms.valid(cl)) cleanerActions = cl.actionsAfter.size
        val sinks = if (forms.valid(sk)) forms.sinks(sk, RuleConditionRewriter.trueExpr) else emptyList()
        val conditionSinks = if (forms.valid(conditionSink)) forms.sinks(conditionSink, RuleConditionRewriter.trueExpr) else emptyList()
        assertTrue(builder.build().edges.isEmpty())
        assertTrue(builder.build().conjunctions.isEmpty())
        assertEquals(0, cleanerActions)
        assertTrue(sinks.isEmpty())
        assertTrue(conditionSinks.isEmpty())
        val validSource = source(listOf(AssignMark(t, Result)))
        assertTrue(forms.valid(validSource))
        val accepted = MicroEdgeBuilder()
        forms.source(accepted, validSource, emptyList(), validSource.actionsAfter.single())
        assertEquals(1, accepted.build().edges.size)
        assertEquals(Tail.EXACT, accepted.build().edges.single().edge.to.tail)
        assertEquals(MarkSlot.Concrete(f.manager.marks.mark("T")), accepted.build().edges.single().edge.to.mark)
    }

    @Test
    fun `class source and end targets admit exact marks and reject every any shape`(): Unit {
        val forms = forms()
        for (condition in listOf<Condition>(mkTrue(), contains(arg0),
            CommonCondition.And(listOf(contains(arg0), contains(arg1))))) {
            assertTrue(forms.valid(source(listOf(AssignMark(t, cls)), condition)))
            assertFalse(forms.valid(source(listOf(AssignMark(t, Result), AssignMark(u, any(cls))), condition)))
        }
        for (literal in listOf(contains(any(arg0)), containsAny(arg0), CommonCondition.Not(containsAny(arg0)),
            CommonCondition.Or(listOf(mkTrue(), containsAny(arg0))))) {
            assertFalse(forms.valid(source(listOf(AssignMark(t, cls)), literal)), "$literal")
        }
        assertTrue(forms.valid(sink(listOf(AssignMark(t, cls)), containsAny(arg0))))
        assertFalse(forms.valid(sink(listOf(AssignMark(t, Result), AssignMark(u, any(cls))))))
        for (position in listOf(field(cls), field(field(cls)), any(field(cls)), any(field(field(cls))))) {
            assertTrue(forms.valid(source(listOf(AssignMark(t, position)))))
            assertTrue(forms.valid(sink(listOf(AssignMark(t, position)))))
        }
        // A class sink/literal itself may be exact or may; only its source/end target is constrained.
        assertTrue(forms.valid(sink(condition = containsAny(cls))))
        assertTrue(forms.valid(source(listOf(AssignMark(t, Result)), containsAny(cls))))
    }

    @Test
    fun `pass rules reject bare classes and every AnyField premise before forms`(): Unit {
        val forms = forms()
        for (bare in listOf(cls, any(cls))) for ((from, to) in listOf(bare to arg0, arg0 to bare)) {
            assertFalse(forms.valid(pass(listOf(CopyAllMarks(arg0, arg1), CopyAllMarks(from, to)))))
            assertFalse(forms.valid(pass(listOf(CopyAllMarks(arg0, arg1), CopyMark(t, from, to)))))
        }
        for (from in listOf(any(arg0), any(field(arg0)), any(field(cls)))) for (to in listOf(arg1, any(arg1))) {
            assertFalse(forms.valid(pass(listOf(CopyAllMarks(arg0, arg1), CopyAllMarks(from, to)))))
            assertFalse(forms.valid(pass(listOf(CopyAllMarks(arg0, arg1), CopyMark(t, from, to)))))
        }
        for ((from, to) in listOf(field(cls) to field(cls, "g"), field(cls) to arg0,
            arg0 to field(cls), field(field(cls)) to any(field(cls)))) {
            assertTrue(forms.valid(pass(listOf(CopyAllMarks(from, to)))))
            assertTrue(forms.valid(pass(listOf(CopyMark(t, from, to)))))
        }
    }

    @Test
    fun `static all-mark AnyField cleaners reject while named cleaners and strong kills remain valid`(): Unit {
        val forms = forms()
        for (position in listOf(cls, field(cls), field(field(cls)))) {
            assertTrue(forms.valid(cleaner(listOf(RemoveAllMarks(position)))))
            assertTrue(forms.valid(cleaner(listOf(RemoveMark(t, position)))))
            assertTrue(forms.valid(cleaner(listOf(RemoveMark(t, any(position))))))
            assertFalse(forms.valid(cleaner(listOf(RemoveMark(t, arg0), RemoveAllMarks(any(position))))))
            assertFalse(forms.valid(cleaner(listOf(RemoveAllMarks(any(position))), mkFalse())))
        }
        assertTrue(forms.valid(cleaner(listOf(RemoveAllMarks(any(arg0))))))
        assertTrue(forms.valid(cleaner(listOf(RemoveAllMarks(any(field(arg0)))))))
    }

    @Test
    fun `static read sources reject conditions wrong bases and invalid sibling actions as complete rules`(): Unit {
        val forms = forms()
        for (position in listOf(Result, any(Result), field(Result), any(field(Result)))) {
            assertTrue(forms.valid(TaintStaticFieldSource(field, mkTrue(), listOf(AssignMark(t, position)), null)))
        }
        for (condition in listOf<Condition>(mkFalse(), contains(arg0), CommonCondition.Not(contains(arg0)),
            CommonCondition.Or(listOf(mkTrue(), contains(arg0))))) {
            assertFalse(forms.valid(TaintStaticFieldSource(field, condition, listOf(AssignMark(t, Result)), null)))
        }
        for (position in listOf(arg0, This, cls, any(arg0), field(any(Result)), any(any(Result)))) {
            assertFalse(forms.valid(TaintStaticFieldSource(field, mkTrue(),
                listOf(AssignMark(t, Result), AssignMark(u, position)), null)), "$position")
        }
        val exceptional = forms(resultBase = AccessPathBase.Exception)
        assertTrue(exceptional.valid(TaintStaticFieldSource(field, mkTrue(), listOf(AssignMark(t, Result)), null)))
    }

    @Test
    fun `read query ignores invalid rules entirely and still emits an independently valid sibling rule`(): Unit {
        val read = f.assign("staticRead") { (it.rhv as? JIRFieldRef)?.field?.name == "sField" }
        val bad = TaintStaticFieldSource(field, mkTrue(),
            listOf(AssignMark(u, Result), AssignMark(u, field(any(Result)))), null)
        val wrong = TaintStaticFieldSource(field, contains(arg0), listOf(AssignMark(u, Result)), null)
        val good = TaintStaticFieldSource(field, mkTrue(), listOf(AssignMark(t, any(Result))), null)
        var queries = 0
        val provider = object : EmptyProvider() {
            override fun sourceRulesForStaticField(field: JIRField, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintStaticFieldSource> {
                queries++
                assertEquals(this@JIRRuleValidationTest.field, field)
                assertEquals(read, statement)
                assertNull(fact)
                return listOf(bad, wrong, good)
            }
        }
        val builder = MicroEdgeBuilder()
        forms(provider).readSources(builder, read)
        val summary = builder.build()
        assertEquals(1, queries)
        assertEquals(2, summary.edges.size)
        assertEquals(setOf<AccessPathBase>(AccessPathBase.Zero), summary.touched)
        val generated = summary.edges.single { it.edge.to.base != AccessPathBase.Zero }.edge
        assertEquals(AccessPathBase.Zero, generated.from.base)
        assertEquals(f.base(read.lhv), generated.to.base)
        assertEquals(Tail.ANY_TAINT, generated.to.tail)
        assertEquals(MarkSlot.Concrete(f.manager.marks.mark("T")), generated.to.mark)
        assertTrue(summary.edges.none { it.edge.to.mark == MarkSlot.Concrete(f.manager.marks.mark("U")) })
        val noValidBuilder = MicroEdgeBuilder()
        val noValid = object : EmptyProvider() {
            override fun sourceRulesForStaticField(field: JIRField, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) = listOf(bad, wrong)
        }
        forms(noValid).readSources(noValidBuilder, read)
        assertTrue(noValidBuilder.build().edges.isEmpty())
        assertTrue(noValidBuilder.build().touched.isEmpty())
    }

    @Test
    fun `pass mark-condition exception stays applied at the RuleErrors boundary`(): Unit {
        val errors = RuleErrors()
        val forms = forms(errors = errors)
        for (action in listOf<Action>(CopyAllMarks(arg0, arg1), CopyMark(t, arg0, arg1))) {
            val rule = pass(listOf(action), CommonCondition.And(listOf(contains(arg0), CommonCondition.Not(containsAny(arg1)))))
            assertTrue(forms.valid(rule))
            repeat(3) { errors.applied(rule, "pass rule has mark literals (§4.2, D24)") }
            val builder = MicroEdgeBuilder()
            assertEquals<Pair<AccessPathBase, AccessPathBase>?>(AccessPathBase.Argument(0) to AccessPathBase.Argument(1),
                forms.pass(builder, rule, action))
            assertEquals(2, builder.build().edges.size)
            assertTrue(builder.build().conjunctions.isEmpty())
            assertTrue(forms.valid(rule))
        }
        val malformed = pass(listOf(CopyAllMarks(arg0, arg1)), CommonCondition.Not(contains(field(any(arg0)))))
        assertFalse(forms.valid(malformed))
        repeat(3) { assertFalse(errors.reject(malformed, "invalid raw position")) }
        assertFalse(forms.valid(malformed))
    }

    @Test
    fun `cached validity and error boundaries agree under concurrent repeated access`(): Unit {
        val errors = RuleErrors()
        val forms = forms(errors = errors)
        val valid = source(listOf(AssignMark(t, any(Result))), contains(arg0))
        val invalid = source(listOf(AssignMark(t, Result), AssignMark(u, any(any(arg1)))))
        assertTrue(forms.valid(valid))
        assertFalse(forms.valid(invalid))
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(4)
        try {
            val work = (0 until 24).map { executor.submit(Callable {
                assertTrue(start.await(30, TimeUnit.SECONDS))
                repeat(20) {
                    assertTrue(forms.valid(valid))
                    assertFalse(forms.valid(invalid))
                    assertFalse(errors.reject(invalid, "invalid raw position"))
                    errors.applied(valid, "applied diagnostic")
                }
                Unit
            }) }
            start.countDown()
            work.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    private open class EmptyProvider : TaintRulesProvider {
        override fun entryPointRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintEntryPointSource> = emptyList()
        override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintMethodSource> = emptyList()
        override fun exitSourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintMethodExitSource> = emptyList()
        override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintMethodSink> = emptyList()
        override fun sinkRulesForMethodEntry(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintMethodEntrySink> = emptyList()
        override fun sinkRulesForMethodExit(method: CommonMethod, statement: CommonInst, fact: FactAp?, initialFacts: Set<InitialFactAp>?, allRelevant: Boolean): Iterable<TaintMethodExitSink> = emptyList()
        override fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst?, fact: FactAp?, allRelevant: Boolean): Iterable<TaintPassThrough> = emptyList()
        override fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintCleaner> = emptyList()
        override fun sourceRulesForStaticField(field: JIRField, statement: CommonInst, fact: FactAp?, allRelevant: Boolean): Iterable<TaintStaticFieldSource> = emptyList()
        override fun selectRules(ruleIds: Set<String>): Unit = Unit
    }
}
