package org.opentaint.semgrep.pattern

import org.opentaint.dataflow.configuration.jvm.serialized.AnalysisEndSink
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedFieldRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedItem
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig

data class TaintRuleFromSemgrep(
    val ruleId: String,
    val taintRules: List<TaintRuleGroup>
) {
    val size: Int get() = taintRules.sumOf { it.size }

    data class TaintRuleGroup(val rules: List<SerializedItem>) {
        val size: Int get() = rules.size
    }
}

fun TaintRuleFromSemgrep.createTaintConfig(): SerializedTaintConfig {
    val rules = taintRules.flatMap { it.rules }
    return SerializedTaintConfig(
        entryPoint = rules.filterIsInstance<SerializedRule.EntryPoint>(),
        source = rules.filterIsInstance<SerializedRule.Source>(),
        methodExitSource = rules.filterIsInstance<SerializedRule.MethodExitSource>(),
        sink = rules.filterIsInstance<SerializedRule.Sink>(),
        passThrough = rules.filterIsInstance<SerializedRule.PassThrough>(),
        cleaner = rules.filterIsInstance<SerializedRule.Cleaner>(),
        methodExitSink = rules.filterIsInstance<SerializedRule.MethodExitSink>(),
        analysisEndSink = rules.filterIsInstance<AnalysisEndSink>(),
        methodEntrySink = rules.filterIsInstance<SerializedRule.MethodEntrySink>(),
        staticFieldSource = rules.filterIsInstance<SerializedFieldRule.SerializedStaticFieldSource>(),
    )
}
