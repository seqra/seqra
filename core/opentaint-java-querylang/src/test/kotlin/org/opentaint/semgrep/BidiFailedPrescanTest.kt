package org.opentaint.semgrep

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Real prescan failure, followed by the real entry and the real Semgrep rule filter. */
@EnabledIfEnvironmentVariable(named = "OPENTAINT_TEST_BIDI", matches = "true")
class BidiFailedPrescanTest {
    @Test
    fun `a joined failed prescan must not return OK for an empty positive result`(): Unit {
        samplesDb().use { samples ->
            val data = samples.loadSampleData().getValue("example.Rule")
            val loader = SemgrepRuleLoader(listOf(JavaLanguageStrategy()))
            loader.registerRuleSet(data.rule, Path(data.rulePath), Path("."), SemgrepLoadTrace())
            @Suppress("UNCHECKED_CAST")
            val rule = loader.loadRules().rulesWithMeta.single().first as TaintRuleFromSemgrep<SerializedItem>
            val positive = "example.Rule\$PositiveSimple"
            val policy = FixedLimits((1..9).toList())
            TestAnalysisRunner(samples, policy).use { control ->
                val result = control.run(rule, SerializedTaintConfig(), false, setOf(positive))
                assertTrue(result.getValue(positive).isNotEmpty(), "The complete-prescan control must find its sink")
                assertEquals(TaintAnalysisUnitRunnerManager.Status.OK, control.lastPrescanStatus)
            }
            TestAnalysisRunner(samples, policy, IllegalStateException("one-shot prescan resolver fault")).use { fault ->
                // The shared helper rejects a failed final status. The public adapter must retain it.
                assertFailsWith<AssertionError>("An incomplete prescan must not yield final OK") {
                    fault.run(rule, SerializedTaintConfig(), false, setOf(positive))
                }
                assertEquals(TaintAnalysisUnitRunnerManager.Status.EXCEPTION, fault.lastPrescanStatus)
                assertEquals(TaintAnalysisUnitRunnerManager.Status.EXCEPTION, fault.lastAnalysisStatus?.analysisStatus)
            }
        }
    }
}
