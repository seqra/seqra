package org.opentaint.go.sast.rules

import io.mockk.mockk
import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.go.serialized.GoNameMatcher
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedAssignAction
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedCondition
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedItem
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedRule
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedTaintConfig
import org.opentaint.dataflow.configuration.go.serialized.GoUserDefinedRuleInfo
import org.opentaint.dataflow.configuration.isTrue
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.go.GoFunctionSignature
import org.opentaint.dataflow.go.rules.GoTaintConfiguration
import org.opentaint.dataflow.go.rules.GoCombinedTaintRulesProvider
import org.opentaint.dataflow.go.rules.GoTaintRulesProvider
import org.opentaint.dataflow.go.rules.TaintRule
import org.opentaint.dataflow.go.rules.GoAssignAction
import org.opentaint.dataflow.go.rules.Position
import org.opentaint.dataflow.go.rules.selectRulesWithScope
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.type.GoIRUnsafePointerType
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class GoSemgrepRuleSelectionTest {
    private val fn = GoNameMatcher.Simple("f")
    private val pkg = GoNameMatcher.Simple("test")
    private val arg = PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(0))
    private val result = PositionBaseWithModifiers.BaseOnly(PositionBase.Result)
    private val signature = GoFunctionSignature("test.f", null, listOf(GoIRUnsafePointerType), GoIRUnsafePointerType)
    private val statement = mockk<GoIRInst>()

    private fun source(id: String, mark: String, condition: GoSerializedCondition? = null,
                       info: GoUserDefinedRuleInfo? = null) = GoSerializedRule.Source(pkg, fn, condition,
        listOf(GoSerializedAssignAction.Direct(mark, result)), info, id)
    private fun sink(id: String, mark: String) = GoSerializedRule.Sink(pkg, fn,
        GoSerializedCondition.ContainsMark(mark, arg), id = "public-$id", info = null, serializedId = id)
    private fun graph(name: String, rows: List<GoSerializedItem>, final: String, vararg marks: String) =
        TaintRuleFromSemgrep(name, TaintRuleFromSemgrep.Structure.Matching(listOf(
            TaintRuleFromSemgrep.TaintRuleGroup(rows, finalRuleIds = setOf(final)))),
            markOwners = marks.associateWith { name }, primitiveByOwner = mapOf(name to false))
    private fun config(sources: List<GoSerializedRule.Source>, sinks: List<GoSerializedRule.Sink>) =
        GoTaintConfiguration().apply { loadConfig(GoSerializedTaintConfig(source = sources, sink = sinks)) }

    @Test
    fun `active graph metadata retains unused carrier with original negative guard and live reset`() {
        val info = object : GoUserDefinedRuleInfo { override val relevantTaintMarks = linkedSetOf("T", "U") }
        val a = source("a", "T")
        val b = source("b", "U", GoSerializedCondition.Not(GoSerializedCondition.ContainsMark("U", arg)), info)
        val s = sink("s", "T")
        val provider = GoSemgrepRuleProvider(listOf(graph("r", listOf(a, b, s), "s", "T", "U")), config(listOf(a, b), listOf(s)))
        provider.selectRules(setOf("a", "s"))
        assertEquals(listOf("a"), provider.sourceRulesForCall(signature, statement, false).map { it.serializedId })
        val row = provider.sourceRulesForCall(signature, statement, true).single { it.serializedId == "b" }
        assertSame(info, row.info)
        assertEquals("U", row.actionsAfter.single().mark)
        assertEquals(setOf("T"), row.effectiveRelevantTaintMarks)
        assertTrue(row.condition is CommonCondition.Not)
    }

    @Test
    fun `whole executable catalog cascades after losing last producer`() {
        val a = source("a", "T", GoSerializedCondition.ContainsMarkOnAnyAccessor("U", arg))
        val s = sink("s", "T")
        val provider = GoSemgrepRuleProvider(listOf(graph("r", listOf(a, s), "s", "T", "U")), config(listOf(a), listOf(s)))
        provider.selectRules(setOf("a", "s"))
        assertTrue(provider.sourceRulesForCall(signature, statement, false).isEmpty())
        assertTrue(provider.sinkRulesForCall(signature, statement).isEmpty())
    }

    @Test
    fun `negative absent mark becomes true while independent producer in other method stays available`() {
        val a = source("a", "T", GoSerializedCondition.Not(GoSerializedCondition.ContainsMark("U", arg)))
        val s = sink("s", "T").copy(function = GoNameMatcher.Simple("consume"))
        val provider = GoSemgrepRuleProvider(listOf(graph("r", listOf(a, s), "s", "T", "U")), config(listOf(a), listOf(s)))
        provider.selectRules(setOf("a", "s"))
        assertTrue(provider.sourceRulesForCall(signature, statement, false).single().condition.isTrue())
        assertEquals(1, provider.sinkRulesForCall(signature.copy(name = "test.consume"), statement).size)
    }

    @Test
    fun `shared configuration retains independent selected provider views`() {
        val a = source("a", "T")
        val b = source("b", "U")
        val sa = sink("sa", "T")
        val sb = sink("sb", "U")
        val graphs = listOf(graph("ra", listOf(a, sa), "sa", "T"), graph("rb", listOf(b, sb), "sb", "U"))
        val config = config(listOf(a, b), listOf(sa, sb))
        val first = GoSemgrepRuleProvider(graphs, config)
        val second = GoSemgrepRuleProvider(graphs, config)
        first.selectRules(setOf("a", "sa"))
        second.selectRules(setOf("b", "sb"))
        assertEquals(listOf("a"), first.sourceRulesForCall(signature, statement, false).map { it.serializedId })
        assertEquals(listOf("b"), second.sourceRulesForCall(signature, statement, false).map { it.serializedId })
        assertEquals(2, config.sourceForFunction(signature, false).size)
    }

    @Test
    fun `known approximation producer participates in the global executable mark inventory`() {
        val s = sink("s", "T")
        val provider = GoSemgrepRuleProvider(listOf(graph("r", listOf(s), "s", "T")), config(emptyList(), listOf(s)))
        val approximation = config(listOf(source("approximation", "T")), emptyList())
        val combined = GoCombinedTaintRulesProvider(provider, approximation)
        combined.selectRules(setOf("s"))
        assertEquals("s", combined.sinkRulesForCall(signature, statement).single().serializedId)
        assertEquals("approximation", combined.sourceRulesForCall(signature, statement, false).single().serializedId)
    }

    @Test
    fun `ignored approximation roles do not keep unavailable marks live`() {
        val s = sink("s", "T")
        val provider = GoSemgrepRuleProvider(listOf(graph("r", listOf(s), "s", "T")), config(emptyList(), listOf(s)))
        val approximation = config(listOf(source("approximation", "T")), emptyList())
        val ignore = GoCombinedTaintRulesProvider.CombinationMode.IGNORE
        val combined = GoCombinedTaintRulesProvider(provider, approximation,
            GoCombinedTaintRulesProvider.CombinationOptions(ignore, ignore, ignore, ignore, ignore))
        combined.selectRules(setOf("s"))
        assertTrue(combined.sinkRulesForCall(signature, statement).isEmpty())
    }

    @Test
    fun `opaque approximation scope keeps identity mark semantics`() {
        val s = sink("s", "T")
        val provider = GoSemgrepRuleProvider(listOf(graph("r", listOf(s), "s", "T")), config(emptyList(), listOf(s)))
        val original = config(emptyList(), emptyList())
        val opaque = object : GoTaintRulesProvider by original {}
        val combined = GoCombinedTaintRulesProvider(provider, opaque)
        combined.selectRules(setOf("s"))
        assertEquals("s", combined.sinkRulesForCall(signature, statement).single().serializedId)
    }

    @Test
    fun `both public selector entry points install the same complete cached view`() {
        val a = source("a", "T")
        val s = sink("s", "T")
        val provider = GoSemgrepRuleProvider(listOf(graph("r", listOf(a, s), "s", "T")), config(listOf(a), listOf(s)))
        provider.selectRelevantSemgrepRules(setOf("a", "s"))
        val before = provider.sourceRulesForCall(signature, statement, false).single()
        provider.selectRules(setOf("s", "a"))
        assertSame(before, provider.sourceRulesForCall(signature, statement, false).single())
    }

    @Test
    fun `compacted false approximation cannot enable an overridden base source`() {
        val a = source("a", "T")
        val s = sink("s", "T")
        val provider = GoSemgrepRuleProvider(listOf(graph("r", listOf(a, s), "s", "T")), config(listOf(a), listOf(s)))
        val extra = source("extra", "T", GoSerializedCondition.ContainsMark("U", arg))
        val approximation = config(listOf(extra), emptyList())
        val combined = GoCombinedTaintRulesProvider(provider, approximation)
        assertEquals("extra", combined.sourceRulesForCall(signature, statement, false).single().serializedId)
        combined.selectRules(setOf("a", "s"))
        assertTrue(combined.sourceRulesForCall(signature, statement, false).isEmpty())
        assertEquals("s", combined.sinkRulesForCall(signature, statement).single().serializedId)
    }

    @Test
    fun `nested known compositions share outer producer scope before normalization`() {
        val s = sink("s", "T")
        val provider = GoSemgrepRuleProvider(listOf(graph("r", listOf(s), "s", "T")), config(emptyList(), listOf(s)))
        val inner = GoCombinedTaintRulesProvider(provider, config(emptyList(), emptyList()))
        val outer = GoCombinedTaintRulesProvider(inner, config(listOf(source("extra", "T")), emptyList()))
        outer.selectRules(setOf("s"))
        assertEquals("s", outer.sinkRulesForCall(signature, statement).single().serializedId)
    }

    @Test
    fun `opaque context dependent provider is queried once and keeps statement admission`() {
        val a = source("a", "T")
        val s = sink("s", "T")
        val provider = GoSemgrepRuleProvider(listOf(graph("r", listOf(a, s), "s", "T")), config(listOf(a), listOf(s)))
        val original = config(listOf(source("extra", "T")), emptyList())
        var queries = 0
        val opaque = object : GoTaintRulesProvider by original {
            override fun sourceRulesForCall(signature: GoFunctionSignature, statement: GoIRInst,
                                            allRelevant: Boolean): List<TaintRule.Source> {
                queries++
                return if (statement === this@GoSemgrepRuleSelectionTest.statement)
                    original.sourceRulesForCall(signature, statement, allRelevant) else emptyList()
            }
        }
        val combined = GoCombinedTaintRulesProvider(provider, opaque)
        combined.selectRules(setOf("a", "s"))
        assertEquals("extra", combined.sourceRulesForCall(signature, statement, false).single().serializedId)
        assertEquals("a", combined.sourceRulesForCall(signature, mockk<GoIRInst>(), false).single().serializedId)
        assertEquals(2, queries)
    }

    @Test
    fun `opaque wrapper can give an original empty source carrier an effect`() {
        val a = source("a", "T").copy(taint = emptyList())
        val s = sink("s", "T")
        val provider = GoSemgrepRuleProvider(listOf(graph("r", listOf(a, s), "s", "T")), config(listOf(a), listOf(s)))
        val empty = config(emptyList(), emptyList())
        val opaque = object : GoTaintRulesProvider by empty {
            override fun sourceRulesForCall(signature: GoFunctionSignature, statement: GoIRInst,
                                            allRelevant: Boolean): List<TaintRule.Source> =
                provider.sourceRulesForCall(signature, statement, allRelevant).map {
                    it.copy(actionsAfter = listOf(GoAssignAction.Direct("T", Position.Result)))
                }
        }
        val combined = GoCombinedTaintRulesProvider(provider, opaque)
        combined.selectRules(setOf("a", "s"))
        assertTrue(provider.sourceRulesForCall(signature, statement, false).single().actionsAfter.isEmpty())
        assertEquals("T", combined.sourceRulesForCall(signature, statement, false).single().actionsAfter.single().mark)
    }

    @Test
    fun `new composition after selection rejects queries until explicit reselection`() {
        val s = sink("s", "T")
        val provider = GoSemgrepRuleProvider(listOf(graph("r", listOf(s), "s", "T")), config(emptyList(), listOf(s)))
        provider.selectRules(setOf("s"))
        assertTrue(provider.sinkRulesForCall(signature, statement).isEmpty())
        val combined = GoCombinedTaintRulesProvider(provider, config(listOf(source("extra", "T")), emptyList()))
        assertFailsWith<IllegalStateException> { provider.sinkRulesForCall(signature, statement) }
        assertFailsWith<IllegalStateException> { combined.sinkRulesForCall(signature, statement) }
        combined.selectRules(setOf("s"))
        assertEquals("s", combined.sinkRulesForCall(signature, statement).single().serializedId)
    }

    @Test
    fun `opaque top level delegated provider keeps empty child carriers before rewriting`() {
        val a = source("a", "T").copy(taint = emptyList())
        val s = sink("s", "T")
        val provider = GoSemgrepRuleProvider(listOf(graph("r", listOf(a, s), "s", "T")), config(listOf(a), listOf(s)))
        val wrapper = object : GoTaintRulesProvider by provider {
            override fun sourceRulesForCall(signature: GoFunctionSignature, statement: GoIRInst,
                                            allRelevant: Boolean): List<TaintRule.Source> =
                provider.sourceRulesForCall(signature, statement, allRelevant).map {
                    it.copy(actionsAfter = listOf(GoAssignAction.Direct("T", Position.Result)))
                }
        }
        wrapper.selectRulesWithScope(setOf("a", "s"))
        assertTrue(provider.sourceRulesForCall(signature, statement, false).single().actionsAfter.isEmpty())
        assertEquals("T", wrapper.sourceRulesForCall(signature, statement, false).single().actionsAfter.single().mark)
    }

    @Test
    fun `a capability with an unknown catalog cannot certify its hidden source scope`() {
        val a = source("a", "T").copy(taint = emptyList())
        val s = sink("s", "T")
        val provider = GoSemgrepRuleProvider(listOf(graph("r", listOf(a, s), "s", "T")), config(listOf(a), listOf(s)))
        val opaque = object : GoTaintRulesProvider by provider {
            override fun sourceRulesForCall(signature: GoFunctionSignature, statement: GoIRInst,
                allRelevant: Boolean): List<TaintRule.Source> =
                provider.sourceRulesForCall(signature, statement, allRelevant).map {
                    it.copy(actionsAfter = listOf(GoAssignAction.Direct("T", Position.Result)))
                }
        }
        val known = object : GoTaintRulesProvider by opaque,
            org.opentaint.dataflow.go.rules.GoRuleSelectionCapability {
            override fun selectRules(ruleIds: Set<String>) = opaque.selectRules(ruleIds)
        }
        known.selectRulesWithScope(setOf("a", "s"))
        assertTrue(provider.sourceRulesForCall(signature, statement, false).single().actionsAfter.isEmpty())
        assertEquals("T", known.sourceRulesForCall(signature, statement, false).single().actionsAfter.single().mark)
    }
}
