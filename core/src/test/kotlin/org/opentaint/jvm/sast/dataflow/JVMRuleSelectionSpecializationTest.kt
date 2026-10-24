package org.opentaint.jvm.sast.dataflow

import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.isTrue
import org.opentaint.dataflow.configuration.jvm.*
import org.opentaint.dataflow.configuration.jvm.serialized.*
import org.opentaint.dataflow.configuration.selection.*
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.originalPassThroughRulesForAliases
import org.opentaint.dataflow.jvm.ap.ifds.taint.selectRulesWithScope
import org.opentaint.dataflow.jvm.ap.ifds.taint.currentMarkReduction
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.jvm.sast.ast.BasicTestUtils
import org.opentaint.jvm.sast.dataflow.rules.JVMRuleSpecialization
import org.opentaint.jvm.sast.dataflow.rules.RuleResolutionStatistics
import org.opentaint.jvm.sast.dataflow.rules.TaintConfiguration
import org.opentaint.jvm.sast.rules.JIRSemgrepRuleProvider
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import kotlin.test.*

class JVMRuleSelectionSpecializationTest : BasicTestUtils() {
    override val sourceFileExtension = "java"
    private val owner = "test.samples.RuleSelectionSpecializationSample"
    private val result = PositionBaseWithModifiers.BaseOnly(PositionBase.Result)
    private val arg0 = PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(0))
    private fun function(name: String = "text") = SerializedFunctionNameMatcher.Simple(
        SerializedSimpleNameMatcher.Simple("test.samples"),
        SerializedSimpleNameMatcher.Simple("RuleSelectionSpecializationSample"), SerializedSimpleNameMatcher.Simple(name))
    private fun assign(mark: String, position: PositionBaseWithModifiers = result) = SerializedTaintAssignAction(mark, pos = position)
    private fun source(id: String, mark: String, condition: SerializedCondition? = null) =
        SerializedRule.Source(function(), condition = condition, taint = listOf(assign(mark)), serializedId = id)
    private fun snapshot(execution: Set<String>, metadata: Set<String> = execution,
                         known: Set<String> = execution + metadata,
                         plan: ReducedMarkPlan = ReducedMarkPlan.ALL) =
        RuleSelectionSnapshot(1, execution, metadata, known, plan)
    private fun selected(config: TaintConfiguration, snapshot: RuleSelectionSnapshot,
                         statistics: RuleResolutionStatistics? = null): TaintConfiguration.ResolverView =
        config.resolverView(statistics).also { view -> view.install(TaintConfiguration.ResolverSelection(
            snapshot, { item, metadata -> snapshot.keepsRaw(item.serializedId, role(item), metadata) },
            { row, metadata, _ -> JVMRuleSpecialization.normalize(row, metadata, snapshot.markPlan) })) }
    private fun role(item: SerializedItem): RuleRole = when (item) {
        is SourceRule -> RuleRole.SOURCE
        is SinkRule -> RuleRole.SINK
        is SerializedRule.Cleaner -> RuleRole.CLEANER
        is SerializedRule.PassThrough -> RuleRole.PASS
    }

    @Test fun `rejected real twenty by twenty binder does zero context work`() {
        val raw = SerializedRule.Source(function("many"), taint = listOf(
            assign("T", PositionBaseWithModifiers.BaseOnly(PositionBase.AnyArgument("a"))),
            assign("T", PositionBaseWithModifiers.BaseOnly(PositionBase.AnyArgument("b")))), serializedId = "reject")
        val config = TaintConfiguration(cp).also { it.loadConfig(SerializedTaintConfig(source = listOf(raw))) }
        val lateStats = RuleResolutionStatistics()
        val late = config.sourceForMethod(findMethod(owner, "many"), false, config.resolverView(lateStats))
        assertEquals(400, late.size)
        assertEquals(1L, lateStats.binderCalls)
        assertEquals(400L, lateStats.contextVisits)
        val earlyStats = RuleResolutionStatistics()
        val early = config.sourceForMethod(findMethod(owner, "many"), false,
            selected(config, snapshot(emptySet(), known = setOf("reject")), earlyStats))
        assertEquals(late.filter { it.serializedId != "reject" }, early)
        assertEquals(0L, earlyStats.binderCalls)
        assertEquals(0L, earlyStats.contextVisits)
    }

    @Test fun `fallback sink query binds rejected row and preserves generated report id`() {
        val raw = listOf("reject", "keep").map { SerializedRule.Sink(function(), serializedId = it) }
        val config = TaintConfiguration(cp).also { it.loadConfig(SerializedTaintConfig(sink = raw)) }
        val method = findMethod(owner, "text")
        val reference = config.sinkForMethod(method, false)
        // Matching storage uses a set, so serialization order is not the
        // resolver order. Keep the SECOND actual reference candidate: the
        // discarded first candidate must still consume generated-id-1.
        assertEquals(listOf("generated-id-1", "generated-id-2"), reference.map { it.id })
        val keptId = reference.last().serializedId!!
        val stats = RuleResolutionStatistics()
        val rows = config.sinkForMethod(method, false,
            view = selected(config, snapshot(setOf(keptId), known = setOf("reject", "keep")), stats))
        assertEquals(reference.filter { it.serializedId == keptId }.map { it.id }, rows.map { it.id })
        assertEquals(2L, stats.binderCalls)
        assertEquals(2L, stats.contextVisits)
    }

    @Test fun `explicit and CWE sinks use stable early gate`() {
        val raw = listOf(SerializedRule.Sink(function(), id = "discard", serializedId = "reject"),
            SerializedRule.Sink(function(), meta = SinkMetaData(cwe = listOf(79)), serializedId = "keep"))
        val config = TaintConfiguration(cp).also { it.loadConfig(SerializedTaintConfig(sink = raw)) }
        val stats = RuleResolutionStatistics()
        val rows = config.sinkForMethod(findMethod(owner, "text"), false,
            view = selected(config, snapshot(setOf("keep"), known = setOf("reject", "keep")), stats))
        assertEquals(listOf("CWE-79"), rows.map { it.id })
        assertEquals(1L, stats.binderCalls)
    }

    @Test fun `true false caches and unfiltered direct view are independent in either query order`() {
        for (metadataFirst in listOf(false, true)) {
            val config = TaintConfiguration(cp).also { it.loadConfig(SerializedTaintConfig(
                source = listOf(source("exec", "T"), source("meta", "U")))) }
            val view = selected(config, snapshot(setOf("exec"), setOf("meta")))
            val method = findMethod(owner, "text")
            for (metadata in listOf(metadataFirst, !metadataFirst, metadataFirst)) {
                assertEquals(listOf(if (metadata) "meta" else "exec"),
                    config.sourceForMethod(method, metadata, view).map { it.serializedId })
            }
            assertEquals(listOf("exec", "meta"), config.sourceForMethod(method, false).map { it.serializedId })
        }
    }

    @Test fun `ordinary and no END sink caches retain their own full results`() {
        val config = TaintConfiguration(cp).also { it.loadConfig(SerializedTaintConfig(sink = listOf(
            SerializedRule.Sink(function(), id = "stable", trackFactsReachAnalysisEnd = listOf(assign("END")), serializedId = "keep")))) }
        val view = selected(config, snapshot(setOf("keep")))
        val method = findMethod(owner, "text")
        assertEquals(1, config.sinkForMethod(method, false, false, view).single().trackFactsReachAnalysisEnd.size)
        assertTrue(config.sinkForMethod(method, false, true, view).single().trackFactsReachAnalysisEnd.isEmpty())
        assertEquals(1, config.sinkForMethod(method, false, false, view).single().trackFactsReachAnalysisEnd.size)
    }

    @Test fun `executable compaction preserves original row and action indices`() {
        val missing = SerializedCondition.ContainsMark("U", arg0)
        val raw = listOf(source("empty", "U"), source("false", "T", missing),
            source("survive", "T", SerializedCondition.Not(missing)).copy(taint = listOf(assign("U"), assign("T"))))
        val config = TaintConfiguration(cp).also { it.loadConfig(SerializedTaintConfig(source = raw)) }
        val view = selected(config, snapshot(raw.mapTo(linkedSetOf()) { it.serializedId!! },
            plan = ReducedMarkPlan(setOf("T"), setOf("T", "U"))))
        val rows = config.sourceForMethod(findMethod(owner, "text"), false, view)
        val row = rows.single()
        assertEquals("survive", row.serializedId)
        assertTrue(row.condition.isTrue())
        assertEquals(listOf("T"), row.actionsAfter.map { it.mark.name })
        assertEquals(2, row.ruleOrdinal(0))
        assertEquals(1, row.actionOrdinal(0))
        assertEquals(3, rows.originalRuleCount())
    }

    @Test fun `metadata keeps guard and unused mark position carrier for a live reset`() {
        val missing = SerializedCondition.ContainsMark("U", arg0)
        val info = object : UserDefinedRuleInfo { override val relevantTaintMarks = linkedSetOf("T", "U") }
        val raw = source("carrier", "U", SerializedCondition.Not(missing)).copy(info = info)
        val config = TaintConfiguration(cp).also { it.loadConfig(SerializedTaintConfig(source = listOf(raw))) }
        val view = selected(config, snapshot(setOf("carrier"), plan = ReducedMarkPlan(setOf("T"), setOf("T", "U"))))
        val row = config.sourceForMethod(findMethod(owner, "text"), true, view).single()
        assertFalse(row.condition.isTrue())
        assertEquals(listOf("U"), row.actionsAfter.map { it.mark.name })
        assertEquals(listOf(0 to "T"), row.resetMarks())
        assertEquals(0, row.actionOrdinal(0))
    }

    @Test fun `original pass alias lane survives false executable mark guard`() {
        val raw = SerializedRule.PassThrough(function(), condition = SerializedCondition.ContainsMark("U", arg0),
            copy = listOf(SerializedTaintPassAction(null, arg0, result)))
        val config = TaintConfiguration(cp).also { it.loadConfig(SerializedTaintConfig(passThrough = listOf(raw))) }
        val view = selected(config, snapshot(emptySet(), plan = ReducedMarkPlan(emptySet(), setOf("U"))))
        val provider = JIRTaintRulesProvider(config, resolverView = view)
        val method = findMethod(owner, "text")
        assertTrue(config.passThroughForMethod(method, false, view).isEmpty())
        assertEquals(1, provider.originalPassThroughRulesForAliases(method).single().actionsAfter.filterIsInstance<CopyAllMarks>().size)
    }

    @Test fun `wrapper expansion uses original discarded action prefix`() {
        val method = findMethod(owner, "text")
        val original = TaintEntryPointSource(method, org.opentaint.dataflow.configuration.mkTrue(),
            listOf(AssignMark(TaintMark("U"), Argument(0)), AssignMark(TaintMark("T"), Result)), null)
        val row = JVMRuleSpecialization.normalize(JVMRuleSpecialization.indexed(original, 0, 1), false,
            ReducedMarkPlan(setOf("T"), setOf("T", "U"))) as TaintEntryPointSource
        val expanded = expandSpecializedActions(row, row.actionsAfter,
            { (it as TaintEntryPointSource).actionsAfter }) { listOf(it, it) }
        assertEquals(listOf(2, 3), expanded.specialization!!.actionOrdinals)
        assertEquals(listOf("T", "T"), expanded.actions.map { it.mark.name })
    }

    @Test fun `opaque delegated alias provider keeps its own pass override`() {
        val base = JIRTaintRulesProvider(TaintConfiguration(cp))
        val method = findMethod(owner, "text")
        val custom = TaintPassThrough(method, org.opentaint.dataflow.configuration.mkTrue(),
            listOf(CopyAllMarks(Argument(0), Result)), null)
        val opaque = object : TaintRulesProvider by base {
            override fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst?, fact: FactAp?,
                                                  allRelevant: Boolean): Iterable<TaintPassThrough> = listOf(custom)
        }
        assertEquals(listOf(custom), opaque.originalPassThroughRulesForAliases(method).toList())
    }

    @Test fun `unknown executable scope retains an actionless source carrier`() {
        val raw = SerializedRule.Source(function(), taint = emptyList(), serializedId = "carrier")
        val config = TaintConfiguration(cp).also { it.loadConfig(SerializedTaintConfig(source = listOf(raw))) }
        val view = selected(config, snapshot(setOf("carrier"), plan = ReducedMarkPlan.ALL))
        val row = config.sourceForMethod(findMethod(owner, "text"), false, view).single()
        assertEquals("carrier", row.serializedId)
        assertTrue(row.actionsAfter.isEmpty())
    }

    @Test fun `changed executable scope rejects a cached selected query until reselection`() {
        val sink = SerializedRule.Sink(function(), condition = SerializedCondition.ContainsMark("U", arg0),
            id = "sink", serializedId = "sink")
        val rows: List<SerializedItem> = listOf(source("source", "T"), sink)
        val graph = TaintRuleFromSemgrep("group", TaintRuleFromSemgrep.Structure.Matching(listOf(
            TaintRuleFromSemgrep.TaintRuleGroup(rows, finalRuleIds = setOf("sink")))),
            markOwners = mapOf("T" to "group", "U" to "group"), primitiveByOwner = mapOf("group" to false))
        val main = JIRSemgrepRuleProvider(listOf(graph), TaintConfiguration(cp))
        main.selectRules(setOf("source", "sink"))
        val method = findMethod(owner, "text")
        val statement = method.instList.first()
        assertTrue(main.sinkRulesForMethod(method, statement, null).none())
        val extra = JIRTaintRulesProvider(TaintConfiguration(cp).also {
            it.loadConfig(SerializedTaintConfig(source = listOf(source("extra", "U"))))
        })
        val combined = JIRCombinedTaintRulesProvider(main, extra)
        assertFailsWith<IllegalStateException> { main.sinkRulesForMethod(method, statement, null).toList() }
        combined.selectRules(setOf("source", "sink"))
        assertEquals(listOf("sink"), main.sinkRulesForMethod(method, statement, null).map { it.id })
    }

    @Test fun `resolver views cannot apply one configuration selection to another owner`() {
        val first = TaintConfiguration(cp).also { it.loadConfig(SerializedTaintConfig(source = listOf(source("first", "T")))) }
        val second = TaintConfiguration(cp).also { it.loadConfig(SerializedTaintConfig(source = listOf(source("second", "U")))) }
        val foreign = selected(first, snapshot(setOf("first")))
        val method = findMethod(owner, "text")
        assertFailsWith<IllegalStateException> { second.sourceForMethod(method, false, foreign) }
        assertEquals(listOf("second"), second.sourceForMethod(method, false).map { it.serializedId })
    }

    @Test fun `opaque pure delegation keeps original alias actions through conservative selection boundary`() {
        val pass = SerializedRule.PassThrough(function(), condition = SerializedCondition.ContainsMark("U", arg0),
            copy = listOf(SerializedTaintPassAction(null, arg0, result)), serializedId = "pass")
        val graph = TaintRuleFromSemgrep<SerializedItem>("group", TaintRuleFromSemgrep.Structure.Matching(listOf(
            TaintRuleFromSemgrep.TaintRuleGroup(listOf(pass), finalRuleIds = setOf("pass")))),
            markOwners = mapOf("U" to "group"), primitiveByOwner = mapOf("group" to false))
        val main = JIRSemgrepRuleProvider(listOf(graph), TaintConfiguration(cp))
        val opaque = object : TaintRulesProvider by main {}
        opaque.selectRulesWithScope(setOf("pass"))
        assertTrue(main.currentMarkReduction()!!.plan.protectAll)
        val method = findMethod(owner, "text")
        assertEquals(1, opaque.originalPassThroughRulesForAliases(method).single().actionsAfter.size)
    }

    @Test fun `bound Object CM complements compact after original root and element expansion`() {
        val cm = SerializedCondition.ContainsMark("T", arg0)
        val raw = listOf(
            SerializedRule.Source(function("many"), condition = SerializedCondition.And(listOf(cm, SerializedCondition.Not(cm))),
                taint = listOf(assign("T")), serializedId = "false"),
            SerializedRule.Source(function("many"), condition = SerializedCondition.Or(listOf(cm, SerializedCondition.Not(cm))),
                taint = listOf(assign("T")), serializedId = "true"))
        val config = TaintConfiguration(cp).also { it.loadConfig(SerializedTaintConfig(source = raw)) }
        val method = findMethod(owner, "many")
        val original = config.sourceForMethod(method, false)
        assertEquals(2, original.size)
        val view = selected(config, snapshot(setOf("false", "true"),
            plan = ReducedMarkPlan(setOf("T"), setOf("T"))))
        val reduced = config.sourceForMethod(method, false, view)
        assertEquals(listOf("true"), reduced.map { it.serializedId })
        assertTrue(reduced.single().condition.isTrue())
        assertEquals(original.single { it.serializedId == "true" }.actionsAfter, reduced.single().actionsAfter)
        val metadata = config.sourceForMethod(method, true, view)
        assertEquals(2, metadata.size, "Metadata must retain its original relevant-resolved guards")
        assertTrue(metadata.all { it.specialization!!.original.originalCondition() == it.condition })
    }

    @Test fun `public Semgrep base remains unfiltered after inherited selection publication`() {
        val keep = source("keep", "T")
        val rejected = source("reject", "U")
        val sink = SerializedRule.Sink(function(), condition = SerializedCondition.ContainsMark("T", arg0),
            id = "sink", serializedId = "sink")
        val missing = SerializedRule.Sink(function(), condition = SerializedCondition.ContainsMark("U", arg0),
            id = "missing", serializedId = "missing")
        val rows: List<SerializedItem> = listOf(keep, rejected, sink, missing)
        val graph = TaintRuleFromSemgrep("group", TaintRuleFromSemgrep.Structure.Matching(listOf(
            TaintRuleFromSemgrep.TaintRuleGroup(rows, finalRuleIds = setOf("sink")))),
            markOwners = mapOf("T" to "group", "U" to "group"), primitiveByOwner = mapOf("group" to false))
        val main = JIRSemgrepRuleProvider(listOf(graph), TaintConfiguration(cp))
        main.selectRelevantSemgrepRules(setOf("keep", "sink", "missing"))
        val method = findMethod(owner, "text")
        val statement = method.instList.first()
        assertEquals(setOf("keep"), main.sourceRulesForMethod(method, statement, null).map { it.serializedId }.toSet())
        assertEquals(setOf("sink"), main.sinkRulesForMethod(method, statement, null).map { it.id }.toSet())
        assertEquals(setOf("keep", "reject"), main.base.sourceRulesForMethod(method, statement, null).map { it.serializedId }.toSet())
        assertEquals(setOf("sink", "missing"), main.base.sinkRulesForMethod(method, statement, null).map { it.id }.toSet())
        assertTrue(main.base.sourceRulesForMethod(method, statement, null).all { it.specialization == null })
    }
}
