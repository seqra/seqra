package org.opentaint.semgrep.util

import org.junit.jupiter.api.AfterAll
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.org.opentaint.semgrep.pattern.conversion.SemgrepRuleAutomataBuilder
import org.opentaint.org.opentaint.semgrep.pattern.conversion.taint.TaintRuleMeta
import org.opentaint.org.opentaint.semgrep.pattern.conversion.taint.convertAutomataToTaintRules
import org.opentaint.org.opentaint.semgrep.pattern.parseSemgrepYaml
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class SampleBasedTest {
    fun runTest(ruleName: String) {
        val data = sampleData[ruleName] ?: error("No sample data for $ruleName")

        val ruleYaml = parseSemgrepYaml(data.rule)
        val rule = ruleYaml.rules.singleOrNull() ?: error("Not a single rule for $ruleName")
        check(rule.languages.contains("java"))

        val ruleAutomata = SemgrepRuleAutomataBuilder().build(rule)
        assertNotNull(ruleAutomata, "Could not convert rule to Automata")

//        ruleAutomata.forEach { it.view() }

        val rules = ruleAutomata.flatMapIndexed { idx, automata ->
            val automataId = "${rule.id}_$idx"
            val meta = TaintRuleMeta(automataId, rule.id, SerializedRule.SinkMetaData())
            convertAutomataToTaintRules(automata, meta)
        }

        val taintConfig = SerializedTaintConfig(
            entryPoint = rules.filterIsInstance<SerializedRule.EntryPoint>(),
            source = rules.filterIsInstance<SerializedRule.Source>(),
            sink = rules.filterIsInstance<SerializedRule.Sink>(),
            passThrough = rules.filterIsInstance<SerializedRule.PassThrough>(),
            cleaner = rules.filterIsInstance<SerializedRule.Cleaner>(),
            methodExitSink = rules.filterIsInstance<SerializedRule.MethodExitSink>(),
            methodEntrySink = rules.filterIsInstance<SerializedRule.MethodEntrySink>(),
        )

        val allSamples = hashSetOf<String>()
        data.positiveClasses.mapTo(allSamples) { it.className }
        data.negativeClasses.mapTo(allSamples) { it.className }

        val results = runner.run(taintConfig, allSamples)

        for (sample in data.positiveClasses) {
            val vulnerabilities = results[sample.className]
            assertNotNull(vulnerabilities, "No results for ${sample.className}")

            assertTrue(
                vulnerabilities.isNotEmpty(),
                "Expected $sample to be positive, but no vulnerability was found."
            )
        }

        for (sample in data.negativeClasses) {
            val vulnerabilities = results[sample.className]
            assertNotNull(vulnerabilities, "No results for ${sample.className}")

            if (vulnerabilities.isEmpty()) continue

            if (sample.ignoreWithMessage != null) {
                System.err.println("Skip ${sample.className}: ${sample.ignoreWithMessage}")
                continue
            }

            assertTrue(
                false,
                "Expected $sample to be negative, but vulnerabilities were found: $vulnerabilities"
            )
        }
    }

    companion object {
        private val samplesDb by lazy { samplesDb() }

        val sampleData by lazy { samplesDb.loadSampleData() }

        private val runner by lazy { TestAnalysisRunner(samplesDb) }

        @AfterAll
        @JvmStatic
        fun closeRunner() {
            runner.close()
            samplesDb.close()
        }
    }
}
