package org.opentaint.jvm.sast.rules

import org.opentaint.common.sast.rules.SemgrepRuleProvider
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationItem
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSink
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedFieldRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedItem
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.BidirectionalRulesProviderFactory
import org.opentaint.dataflow.jvm.ap.ifds.taint.forBidirectionalCore
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.jvm.sast.dataflow.JIRTaintRulesProvider
import org.opentaint.jvm.sast.dataflow.rules.TaintConfiguration
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep

class JIRSemgrepRuleProvider(
    rules: List<TaintRuleFromSemgrep<SerializedItem>>,
    taintConfiguration: TaintConfiguration
) : TaintRulesProvider, BidirectionalRulesProviderFactory, SemgrepRuleProvider<SerializedItem, TaintConfigurationItem>(rules) {
    val base = JIRTaintRulesProvider(taintConfiguration)
    override val markOwners: Map<String, String> = buildMap {
        for (rule in rules) for ((mark, owner) in rule.markOwners) {
            val previous = put(mark, owner)
            require(previous == null || previous == owner) { "Conflicting generated mark owner: $mark" }
        }
    }

    override fun bidirectionalRulesView(): TaintRulesProvider {
        val selected = base.forBidirectionalCore()
        return object : TaintRulesProvider by this {
            override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                selected.sinkRulesForMethod(method, statement, fact, allRelevant).select(allRelevant)
            override fun sinkRulesForMethodEntry(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
                selected.sinkRulesForMethodEntry(method, statement, fact, allRelevant).select(allRelevant)
            override fun sinkRulesForMethodExit(method: CommonMethod, statement: CommonInst, fact: FactAp?,
                                              initialFacts: Set<InitialFactAp>?, allRelevant: Boolean) =
                selected.sinkRulesForMethodExit(method, statement, fact, initialFacts, allRelevant).select(allRelevant)
        }
    }

    override fun SerializedItem.ruleItemId(): String? = serializedId
    override fun TaintConfigurationItem.resolvedRuleId(): String? = serializedId

    init {
        val rules = rules.flatMap { groups -> groups.taintRules.flatMap { it.rules } }
        val serializedCfg = SerializedTaintConfig(
            entryPoint = rules.filterIsInstance<SerializedRule.EntryPoint>(),
            source = rules.filterIsInstance<SerializedRule.Source>(),
            methodExitSource = rules.filterIsInstance<SerializedRule.MethodExitSource>(),
            sink = rules.filterIsInstance<SerializedRule.Sink>(),
            passThrough = rules.filterIsInstance<SerializedRule.PassThrough>(),
            cleaner = rules.filterIsInstance<SerializedRule.Cleaner>(),
            methodExitSink = rules.filterIsInstance<SerializedRule.MethodExitSink>(),
            methodEntrySink = rules.filterIsInstance<SerializedRule.MethodEntrySink>(),
            staticFieldSource = rules.filterIsInstance<SerializedFieldRule.SerializedStaticFieldSource>(),
        )
        taintConfiguration.loadConfig(serializedCfg)
    }

    override fun selectRules(ruleIds: Set<String>) {
        base.selectRules(ruleIds)
        selectRelevantSemgrepRules(ruleIds)
    }

    override fun entryPointRulesForMethod(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        allRelevant: Boolean
    ) = base.entryPointRulesForMethod(method, statement, fact, allRelevant).select(allRelevant)

    override fun sourceRulesForMethod(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        allRelevant: Boolean
    ) = base.sourceRulesForMethod(method, statement, fact, allRelevant).select(allRelevant)

    override fun exitSourceRulesForMethod(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        allRelevant: Boolean
    ) = base.exitSourceRulesForMethod(method, statement, fact, allRelevant).select(allRelevant)

    override fun sinkRulesForMethod(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        allRelevant: Boolean
    ) = base.sinkRulesForMethod(method, statement, fact, allRelevant).select(allRelevant)

    override fun sinkRulesForMethodEntry(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        allRelevant: Boolean
    ) = base.sinkRulesForMethodEntry(method, statement, fact, allRelevant).select(allRelevant)

    override fun sinkRulesForMethodExit(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        initialFacts: Set<InitialFactAp>?,
        allRelevant: Boolean
    ): Iterable<TaintMethodExitSink> =
        base.sinkRulesForMethodExit(method, statement, fact, initialFacts, allRelevant).select(allRelevant)

    override fun sourceRulesForStaticField(
        field: JIRField,
        statement: CommonInst,
        fact: FactAp?,
        allRelevant: Boolean
    ) = base.sourceRulesForStaticField(field, statement, fact, allRelevant).select(allRelevant)

    override fun passTroughRulesForMethod(
        method: CommonMethod,
        statement: CommonInst?,
        fact: FactAp?,
        allRelevant: Boolean
    ) = base.passTroughRulesForMethod(method, statement, fact, allRelevant)

    override fun cleanerRulesForMethod(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        allRelevant: Boolean
    ) = base.cleanerRulesForMethod(method, statement, fact, allRelevant)
}
