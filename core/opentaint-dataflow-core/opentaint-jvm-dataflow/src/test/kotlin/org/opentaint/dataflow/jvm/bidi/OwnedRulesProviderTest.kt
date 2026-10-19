package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.jvm.*
import org.opentaint.dataflow.configuration.jvm.serialized.UserDefinedRuleInfo
import org.opentaint.dataflow.jvm.BasicTestUtils
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.forBidirectionalCore
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRRuleTestFixtures
import org.opentaint.dataflow.jvm.bidi.fixtures.NoTaintRules
import org.opentaint.dataflow.jvm.bidi.interp.OwnedRulesProvider
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.taint.PositionAccess
import org.opentaint.dataflow.taint.RuleConditionRewriter.ExprOrConstant
import org.opentaint.dataflow.taint.TaintMarkAwareConditionExpr.ContainsMarkLiteral
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.opentaint.dataflow.configuration.jvm.TaintMark as RuleMark

class OwnedRulesProviderTest : BasicTestUtils() {
    @Test fun `every sink role keeps its full condition and ignores end actions in both directions`(): Unit {
        val fixture = JIRRuleTestFixtures(cp)
        val method = fixture.method("plain")
        val node = method.instList.first()
        val condition: Condition = CommonCondition.Atom(ContainsMark(Argument(0), RuleMark("ACTIVE")))
        val invalid = PositionWithAccess(PositionWithAccess(Argument(0), PositionAccessor.AnyFieldAccessor),
            PositionAccessor.AnyFieldAccessor)
        val call = fixture.sink(listOf(AssignMark(RuleMark("END_ONLY"), invalid)), condition)
        val entry = TaintMethodEntrySink(method, condition, call.trackFactsReachAnalysisEnd,
            "entry", call.meta, null)
        val exit = TaintMethodExitSink(method, condition, call.trackFactsReachAnalysisEnd,
            "exit", call.meta, null)
        val provider = object : TaintRulesProvider by NoTaintRules {
            override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) = listOf(call)
            override fun sinkRulesForMethodEntry(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) = listOf(entry)
            override fun sinkRulesForMethodExit(method: CommonMethod, statement: CommonInst, fact: FactAp?, initialFacts: Set<InitialFactAp>?, allRelevant: Boolean) = listOf(exit)
        }
        val selected = OwnedRulesProvider(provider, fixture.manager.marks)
        val forms = fixture.forms(selected)
        val expr = ExprOrConstant(ContainsMarkLiteral(PositionAccess.Simple(AccessPathBase.Argument(0)),
            TaintMarkAccessor("ACTIVE"), false))
        val kinds = listOf(RuleKind.CALL_SINK, RuleKind.ENTRY_SINK, RuleKind.EXIT_SINK)
        for (all in listOf(false, true)) {
            val rules: List<TaintConfigurationSink> = listOf(selected.sinkRulesForMethod(method, node, null, all).single(),
                selected.sinkRulesForMethodEntry(method, node, null, all).single(),
                selected.sinkRulesForMethodExit(method, node, null, null, all).single())
            for ((rule, kind) in rules.zip(kinds)) {
                assertTrue(rule.trackFactsReachAnalysisEnd.isEmpty())
                assertSame(condition, rule.condition)
                assertTrue(forms.valid(rule))
                val occurrence = RuleOccurrenceId(FormKey(FormOwner(), method, null, node), kind, 0)
                val sinks = forms.sinks(rule, expr, occurrence)
                val statement = RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, sinks)
                for (direction in listOf(statement, statement.reversed())) {
                    assertTrue(direction.endFacts.edges.isEmpty())
                    assertTrue(direction.sinks.single().endFacts.isEmpty())
                    assertEquals(listOf("ACTIVE"), direction.sinks.single().seedPatterns().map {
                        fixture.manager.marks.name((it.fact.mark as MarkSlot.Concrete).mark)
                    })
                    assertEquals(occurrence, direction.sinks.single().occurrenceId)
                }
            }
        }
        assertTrue(listOf<TaintConfigurationSink>(call, entry, exit).all { it.trackFactsReachAnalysisEnd.isNotEmpty() })
        assertEquals(listOf("ACTIVE"), selected.observedFullNames())
    }

    @Test fun `ignored invalid end action and its relevance metadata cannot add an eligible mark`(): Unit {
        val fixture = JIRRuleTestFixtures(cp)
        val method = fixture.method("plain")
        val node = method.instList.first()
        val info = object : UserDefinedRuleInfo {
            override val relevantTaintMarks = setOf("ACTIVE", "END_ONLY")
        }
        val invalidPosition = PositionWithAccess(PositionWithAccess(Argument(0), PositionAccessor.AnyFieldAccessor),
            PositionAccessor.AnyFieldAccessor)
        val rule = fixture.sink(listOf(AssignMark(RuleMark("END_ONLY"), invalidPosition)),
            CommonCondition.Atom(ContainsMark(Argument(0), RuleMark("ACTIVE")))).copy(info = info)
        val provider = object : TaintRulesProvider by NoTaintRules {
            override val markOwners = mapOf("ACTIVE" to "outer", "END_ONLY" to "outer")
            override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                listOf(rule)
        }
        val marks = MarkTable()
        assertSame(provider, provider.forBidirectionalCore(), "A delegated provider retains overridden roles")
        val selected = OwnedRulesProvider(provider.forBidirectionalCore(), marks)
        val owned = selected.sinkRulesForMethod(method, node, null, false).single()
        assertTrue(owned.trackFactsReachAnalysisEnd.isEmpty())
        assertSame(info, owned.info)
        assertTrue(rule.trackFactsReachAnalysisEnd.isNotEmpty())
        assertEquals(listOf("ACTIVE"), selected.observedFullNames())
        marks.finishOwnershipCensus()
        val active = marks.mark("ACTIVE")
        assertEquals(listOf(active), marks.eligibleMembers(marks.groupOf(active)))
        assertTrue(fixture.forms(selected).valid(owned))
    }

    @Test fun `provider group view merges aliases and keeps original full leaves separate`(): Unit {
        val marks = MarkTable()
        marks.register("T", MarkGroupKey.Rule("joined"))
        marks.register("U", MarkGroupKey.Rule("joined"))
        marks.register("V", MarkGroupKey.Rule("other"))
        val t = marks.mark("T")
        val u = marks.mark("U")
        val v = marks.mark("V")
        marks.finishOwnershipCensus()
        val base = AccessPathBase.Argument(0)
        fun pattern(mark: org.opentaint.dataflow.bidi.ap.TaintMark) =
            Pattern(PathFact(base, emptyList(), Tail.EXACT, MarkSlot.Concrete(mark)), ExclusionSet.Empty)
        val builder = RulePredicate.Builder(Cancellation(), marks)
        val predicate = builder.build(builder.and(listOf(builder.leaf(pattern(t)), builder.leaf(pattern(u)),
            builder.leaf(pattern(v)))))
        val provider = OwnedRulesProvider(NoTaintRules, marks)
        val selected = provider.groupConditions(Cancellation()).select(predicate)
        val found = arrayListOf<Pattern>()
        selected.matchingLeaves(base, marks.group(marks.groupOf(t))) { _, leaf -> found += leaf }
        assertEquals(listOf(pattern(marks.group(marks.groupOf(t)))), found)
        assertEquals(2, selected.leaves.size)
        assertEquals(listOf(pattern(t), pattern(u), pattern(v)), predicate.leaves)
        val exact = arrayListOf<Pattern>()
        predicate.matchingLeaves(base, u) { _, leaf -> exact += leaf }
        assertEquals(listOf(pattern(u)), exact)
    }
}
