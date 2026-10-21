package org.opentaint.semgrep

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.opentaint.dataflow.bidi.driver.FixedLimits
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedItem
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.semgrep.pattern.SemgrepLoadTrace
import org.opentaint.semgrep.pattern.SemgrepRuleLoader
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import org.opentaint.semgrep.pattern.conversion.JavaLanguageStrategy
import org.opentaint.semgrep.util.TestAnalysisRunner
import org.opentaint.semgrep.util.loadSampleData
import org.opentaint.semgrep.util.samplesDb
import kotlin.io.path.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Entry annotation rules must not report an unannotated clean callee as their method exit. */
@EnabledIfEnvironmentVariable(named = "OPENTAINT_TEST_BIDI", matches = "true")
class BidiMethodExitScopeTest {
    @Test
    fun `an entry annotation rule must not report the object clean callee exit`(): Unit =
        checkExitScope("example.RuleReturnWithNotInsideSignature")

    @Test
    fun `an entry annotation rule must not report the string clean callee exit`(): Unit =
        checkExitScope("custom.RuleReturnWithNotInsideSignatureStrConcat")

    private fun checkExitScope(sampleClass: String) {
        samplesDb().use { samples ->
            val data = samples.loadSampleData().getValue(sampleClass)
            val loader = SemgrepRuleLoader(listOf(JavaLanguageStrategy()))
            loader.registerRuleSet(data.rule, Path(data.rulePath), Path("."), SemgrepLoadTrace())
            @Suppress("UNCHECKED_CAST")
            val rule = loader.loadRules().rulesWithMeta.single().first as TaintRuleFromSemgrep<SerializedItem>
            val unexpected = linkedMapOf<Boolean, Int>()
            for (aliases in listOf(true, false)) {
                TestAnalysisRunner(samples, FixedLimits(listOf(1) + (1..8).toList()),
                    useAliasAnalysis = aliases, observeFlow = true).use { runner ->
                    val result = runner.run(rule, SerializedTaintConfig(), sampleClass.startsWith("custom."),
                        linkedSetOf("$sampleClass\$Positive", "$sampleClass\$Negative"))
                    assertTrue(result.getValue("$sampleClass\$Positive").isNotEmpty(),
                        "The positive control must reach its sink with aliases=$aliases")
                    unexpected[aliases] = result.getValue("$sampleClass\$Negative").size
                    println("BIDI_EXIT_SCOPE rule=$sampleClass aliases=$aliases unexpected=${unexpected[aliases]}")
                }
            }
            assertEquals(mapOf(true to 0, false to 0), unexpected,
                "The annotation rule must not report the unannotated callee")
        }
    }
}
