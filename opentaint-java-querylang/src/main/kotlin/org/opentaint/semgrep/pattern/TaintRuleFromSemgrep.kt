package org.opentaint.org.opentaint.semgrep.pattern

import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.configuration.jvm.serialized.TaintConfiguration

data class TaintRuleFromSemgrep(
    val ruleId: String,
    val taintRules: List<TaintRuleGroup>
) {
    data class TaintRuleGroup(val rules: List<SerializedRule>)
}

fun TaintConfiguration.loadSemgrepRule(rule: TaintRuleFromSemgrep) {
    val rules = rule.taintRules.flatMap { it.rules }
    val taintConfig = SerializedTaintConfig(
        entryPoint = rules.filterIsInstance<SerializedRule.EntryPoint>(),
        source = rules.filterIsInstance<SerializedRule.Source>(),
        sink = rules.filterIsInstance<SerializedRule.Sink>(),
        passThrough = rules.filterIsInstance<SerializedRule.PassThrough>(),
        cleaner = rules.filterIsInstance<SerializedRule.Cleaner>(),
        methodExitSink = rules.filterIsInstance<SerializedRule.MethodExitSink>(),
        methodEntrySink = rules.filterIsInstance<SerializedRule.MethodEntrySink>(),
    )
    loadConfig(taintConfig)
}
