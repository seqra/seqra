package org.opentaint.semgrep.pattern

import org.opentaint.dataflow.configuration.jvm.serialized.SerializedFieldRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedItem
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig

/**
 * Test helper: flattens a generated rule into a [SerializedTaintConfig] so the star-operator
 * rule-generation tests can inspect the emitted source/sink/passThrough/cleaner items directly.
 *
 * The production analyzer no longer needs this (rules flow through the rule provider), so it lives
 * in the test scope.
 */
fun TaintRuleFromSemgrep<SerializedItem>.createTaintConfig(): SerializedTaintConfig {
    val rules = taintRules.flatMap { it.rules }
    return SerializedTaintConfig(
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
}
