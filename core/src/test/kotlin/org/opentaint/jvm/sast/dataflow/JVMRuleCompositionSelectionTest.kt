package org.opentaint.jvm.sast.dataflow

import io.mockk.mockk
import org.opentaint.dataflow.configuration.isFalse
import org.opentaint.dataflow.configuration.isTrue
import org.opentaint.dataflow.configuration.jvm.serialized.*
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.selectRulesWithScope
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.jvm.sast.ast.BasicTestUtils
import org.opentaint.jvm.sast.dataflow.rules.TaintConfiguration
import org.opentaint.jvm.sast.rules.JIRSemgrepRuleProvider
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JVMRuleCompositionSelectionTest : BasicTestUtils() {
    override val sourceFileExtension = "java"
    private val argument = PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(0))
    private val statement = mockk<CommonInst>()
    private val result = PositionBaseWithModifiers.BaseOnly(PositionBase.Result)
    private val matcher = SerializedFunctionNameMatcher.Simple(
        SerializedSimpleNameMatcher.Simple("test.samples.bidi"),
        SerializedSimpleNameMatcher.Simple("BidiAcceptanceSample"),
        SerializedSimpleNameMatcher.Simple("mayPass"),
    )
    private fun cm(mark: String) = SerializedCondition.ContainsMark(mark, argument)
    private fun source(id: String, mark: String, guard: SerializedCondition? = null,
                       relevant: Set<String> = setOf(mark)) = SerializedRule.Source(
        matcher, condition = guard, taint = listOf(SerializedTaintAssignAction(mark, pos = result)),
        info = object : UserDefinedRuleInfo { override val relevantTaintMarks = relevant }, serializedId = id,
    )
    private fun provider(condition: SerializedCondition,
                         source: SerializedRule.Source = source("main-source", "T")): JIRSemgrepRuleProvider {
        val sink = SerializedRule.Sink(matcher, condition = condition, id = "public-sink", serializedId = "main-sink")
        val rows: List<SerializedItem> = listOf(source, sink)
        val graph = TaintRuleFromSemgrep("main", TaintRuleFromSemgrep.Structure.Matching(listOf(
            TaintRuleFromSemgrep.TaintRuleGroup(rows, finalRuleIds = setOf("main-sink")))),
            markOwners = mapOf("T" to "main", "U" to "main", "V" to "main"),
            primitiveByOwner = mapOf("main" to false))
        return JIRSemgrepRuleProvider(listOf(graph), TaintConfiguration(cp))
    }
    private fun extra(vararg sources: SerializedRule.Source): JIRTaintRulesProvider =
        JIRTaintRulesProvider(TaintConfiguration(cp).apply {
            loadConfig(SerializedTaintConfig(source = sources.toList()))
        })

    @Test fun `known extra producer keeps the Semgrep mark condition live`() {
        val main = provider(cm("U"))
        val combined = JIRCombinedTaintRulesProvider(main, extra(source("extra", "U")))
        combined.selectRules(setOf("main-source", "main-sink"))
        val method = findMethod("test.samples.bidi.BidiAcceptanceSample", "mayPass")
        val sink = main.sinkRulesForMethod(method, statement, null).single()
        assertFalse(sink.condition.isFalse())
        assertTrue(main.currentMarkReduction()!!.plan.isLive("U"))
    }

    @Test fun `an ignored extra source does not keep a missing producer live`() {
        val main = provider(cm("U"))
        val ignore = JIRCombinedTaintRulesProvider.CombinationMode.IGNORE
        val combined = JIRCombinedTaintRulesProvider(main, extra(source("extra", "U")),
            JIRCombinedTaintRulesProvider.CombinationOptions(ignore, ignore, ignore, ignore, ignore))
        combined.selectRules(setOf("main-source", "main-sink"))
        val method = findMethod("test.samples.bidi.BidiAcceptanceSample", "mayPass")
        assertTrue(main.sinkRulesForMethod(method, statement, null).none())
        assertFalse(main.currentMarkReduction()!!.plan.isLive("U"))
    }

    @Test fun `an opaque extra scope keeps condition specialization on the compatibility path`() {
        val main = provider(cm("U"))
        val opaque = object : TaintRulesProvider by extra(source("extra", "U")) {}
        JIRCombinedTaintRulesProvider(main, opaque).selectRules(setOf("main-source", "main-sink"))
        val method = findMethod("test.samples.bidi.BidiAcceptanceSample", "mayPass")
        assertTrue(main.currentMarkReduction()!!.plan.protectAll)
        assertTrue(main.sinkRulesForMethod(method, statement, null).any())
    }

    @Test fun `folding an extra condition to true does not suppress an originally different base condition`() {
        val main = provider(SerializedCondition.Or(listOf(cm("T"), cm("V"))))
        val combined = JIRCombinedTaintRulesProvider(main,
            extra(source("extra", "V", SerializedCondition.Not(cm("U")))))
        combined.selectRules(setOf("main-source", "main-sink"))
        val method = findMethod("test.samples.bidi.BidiAcceptanceSample", "mayPass")
        val rows = combined.sourceRulesForMethod(method, statement, null).toList()
        assertEquals(listOf(setOf("V"), setOf("T")),
            rows.map { it.actionsAfter.map { action -> action.mark.name }.toSet() })
        assertTrue(rows.all { it.condition.isTrue() })
    }

    @Test fun `a pruned extra source cannot enable a source it originally overrode`() {
        val main = provider(cm("T"))
        val combined = JIRCombinedTaintRulesProvider(main, extra(source("extra", "U")))
        combined.selectRules(setOf("main-source", "main-sink"))
        val method = findMethod("test.samples.bidi.BidiAcceptanceSample", "mayPass")
        assertTrue(combined.sourceRulesForMethod(method, statement, null).none())
        // Metadata U has no live reset target. Its original true condition still
        // suppresses the base T reset, exactly as in the original composition.
        assertTrue(combined.sourceRulesForMethod(method, statement, null, allRelevant = true).none())
    }

    @Test fun `repeating the complete composed selection keeps its cached rule values`() {
        val main = provider(cm("T"))
        val combined = JIRCombinedTaintRulesProvider(main, extra())
        combined.selectRules(setOf("main-source", "main-sink"))
        val method = findMethod("test.samples.bidi.BidiAcceptanceSample", "mayPass")
        val first = main.sourceRulesForMethod(method, statement, null).single()
        val selection = main.currentMarkReduction()!!.identity
        combined.selectRules(setOf("main-source", "main-sink"))
        assertSame(selection, main.currentMarkReduction()!!.identity)
        assertSame(first, main.sourceRulesForMethod(method, statement, null).single())
    }

    @Test fun `an opaque outer wrapper can add an action to an original empty source`() {
        val main = provider(cm("T"), source("main-source", "T").copy(taint = emptyList()))
        val opaque = object : TaintRulesProvider by main {
            override fun sourceRulesForMethod(method: org.opentaint.ir.api.common.CommonMethod,
                statement: CommonInst, fact: org.opentaint.dataflow.ap.ifds.access.FactAp?, allRelevant: Boolean) =
                main.sourceRulesForMethod(method, statement, fact, allRelevant).map { row ->
                    row.copy(actionsAfter = listOf(org.opentaint.dataflow.configuration.jvm.AssignMark(
                        org.opentaint.dataflow.configuration.jvm.TaintMark("T"),
                        org.opentaint.dataflow.configuration.jvm.Result)))
                }
        }
        opaque.selectRulesWithScope(setOf("main-source", "main-sink"))
        val method = findMethod("test.samples.bidi.BidiAcceptanceSample", "mayPass")
        assertEquals(listOf("T"), opaque.sourceRulesForMethod(method, statement, null)
            .flatMap { row -> row.actionsAfter.map { it.mark.name } })
        assertTrue(main.currentMarkReduction()!!.plan.protectAll)
    }
}
