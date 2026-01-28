package org.opentaint.semgrep.util

import base.RuleSample
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta.Severity
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedItem
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintAssignAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.configuration.jvm.serialized.SinkRule
import org.opentaint.dataflow.configuration.jvm.serialized.SourceRule
import org.opentaint.semgrep.pattern.Mark
import org.opentaint.semgrep.pattern.SemgrepLoadTrace
import org.opentaint.semgrep.pattern.SemgrepRuleLoader
import org.opentaint.semgrep.pattern.createTaintConfig
import kotlin.io.path.Path
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

abstract class SampleBasedTest(
    private val configurationRequired: Boolean = false
) {
    inline fun <reified T : RuleSample> runTest(
        expectStateVar: Boolean = false,
        noinline provideAdditionalRules: (SerializedTaintConfig) -> SerializedTaintConfig = { it }
    ) = runClassTest(getFullyQualifiedClassName<T>(), expectStateVar, provideAdditionalRules)

    fun runClassTest(
        sampleClassName: String,
        expectStateVar: Boolean,
        provideAdditionalRules: (SerializedTaintConfig) -> SerializedTaintConfig
    ) {
        val data = sampleData[sampleClassName] ?: error("No sample data for $sampleClassName")

        val trace = SemgrepLoadTrace()
        val loader = SemgrepRuleLoader()
        loader.registerRuleSet(ruleSetText = data.rule, ruleRelativePath = Path(data.rulePath), rulesRoot = Path("."), trace)

        val loadedRules = loader.loadRules(Severity.Note)
        val (rule, _) = loadedRules.singleOrNull()
            ?: error("Not a single rule for ${data.rulePath}")

        val taintConfig = rule.createTaintConfig()

        val stateVarExists = doesCreateStateVar(taintConfig)
        if (!expectStateVar && stateVarExists) {
            fail("Taint config has AssignAction that creates a state var, but `expectStateVar` was set to `false`!")
        }
        if (expectStateVar && !stateVarExists) {
            fail("Taint config does not create any state var, but `expectStateVar` was set to `true`.\n" +
                    "Consider changing the test or removing the flag.")
        }

        val allSamples = hashSetOf<String>()
        data.positiveClasses.mapTo(allSamples) { it.className }
        data.negativeClasses.mapTo(allSamples) { it.className }

        val configPath = if (configurationRequired) {
            System.getenv("TAINT_CONFIGURATION")
                ?.let { Path(it) }
                ?: error("Configuration file required")
        } else {
            null
        }

        val configWithExtraRules = provideAdditionalRules(taintConfig)

        val results = runner.run(configWithExtraRules, configPath, allSamples)

        val missedPositive = hashSetOf<PositiveCase>()
        for (sample in data.positiveClasses) {
            val vulnerabilities = results[sample.className]
            assertNotNull(vulnerabilities, "No results for ${sample.className}")

            if (vulnerabilities.isEmpty()) {
                missedPositive.add(sample)
            }
        }
        assertTrue(
            missedPositive.isEmpty(),
            "Expected $missedPositive to be positive, but no vulnerability was found."
        )

        val falseNegative = hashSetOf<NegativeCase>()
        for (sample in data.negativeClasses) {
            val vulnerabilities = results[sample.className]
            assertNotNull(vulnerabilities, "No results for ${sample.className}")

            if (vulnerabilities.isEmpty()) continue

            if (sample.ignoreWithMessage != null) {
                System.err.println("Skip ${sample.className}: ${sample.ignoreWithMessage}")
                continue
            }

            falseNegative.add(sample)
        }
        assertTrue(
            falseNegative.isEmpty(),
            "Expected $falseNegative to be negative, but vulnerabilities were found."
        )
    }

    private fun List<SerializedItem?>?.getAssigns(): List<SerializedTaintAssignAction> =
        this?.mapNotNull { rule ->
            when (rule) {
                is SourceRule -> rule.taint
                is SinkRule -> rule.trackFactsReachAnalysisEnd
                else -> emptyList()
            }
        }?.flatten()
            ?: emptyList()

    private fun doesCreateStateVar(taintConfig: SerializedTaintConfig): Boolean {
        val allAssignActions = taintConfig.source.getAssigns() +
                taintConfig.entryPoint.getAssigns() +
                taintConfig.staticFieldSource.getAssigns() +
                taintConfig.methodEntrySink.getAssigns() +
                taintConfig.methodExitSink.getAssigns() +
                taintConfig.sink.getAssigns()
        return allAssignActions.any { Mark.getMarkFromString(it.kind) is Mark.StateMark }
    }

    private val samplesDb by lazy { samplesDb() }

    private val sampleData by lazy { samplesDb.loadSampleData() }

    private val runner by lazy { TestAnalysisRunner(samplesDb) }

    fun closeRunner() {
        runner.close()
        samplesDb.close()
    }

    inline fun <reified T> getFullyQualifiedClassName(): String = try {
        T::class.qualifiedName
    } catch (e: NoClassDefFoundError) {
        e.message?.replace('/', '.')
    } ?: error("No class name")

    companion object {
        @JvmStatic
        protected val EXPECT_STATE_VAR = true
    }
}
