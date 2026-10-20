package org.opentaint.python.sast.dataflow

import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.configuration.python.Argument
import org.opentaint.dataflow.python.rules.PIRCombinedTaintRulesProvider
import org.opentaint.dataflow.python.rules.PIRTaintRulesProvider
import kotlin.test.Test
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TraceResolutionTest : AnalysisTest() {

    @Test
    fun testAttributeSourceTraceResolves() = assertResolves(
        attributeSource("tainted_attr", "taint"),
        sink("TraceResolution.sink", "taint", Argument(0), "attr"),
        "TraceResolution.attr_source_to_sink",
    )

    @Test
    fun testConditionalAttributeSourceTraceResolves() = assertResolves(
        runAnalysis(loadSemgrepRules("/python-trace"), "TraceResolution.conditional_attr_source_to_sink"),
        "TraceResolution.conditional_attr_source_to_sink",
    )

    @Test
    fun testUnconditionalSinkResolves() = assertResolves(
        runAnalysis(owaspRules(), "TraceResolution.weakrand_unconditional_sink"),
        "TraceResolution.weakrand_unconditional_sink",
    )

    @Test
    fun testCallSideEffectOnReceiverResolves() = assertResolves(
        runAnalysis(owaspRules(), "TraceResolution.call_side_effect_on_receiver"),
        "TraceResolution.call_side_effect_on_receiver",
    )

    @Test
    fun testContainerLiteralResolves() = assertResolves(
        runAnalysis(owaspRules(), "TraceResolution.container_literal_control"),
        "TraceResolution.container_literal_control",
    )

    private fun assertResolves(source: TestSource, sink: TestSink, entryPoint: String) =
        assertResolves(runAnalysis(source, sink, entryPoint), entryPoint)

    private fun assertResolves(traces: List<VulnerabilityWithTrace>, entryPoint: String) {
        assertTrue(traces.isNotEmpty(), "No vulnerability with a resolved trace found for $entryPoint")
    }

    private fun owaspRules(): PIRTaintRulesProvider = PIRCombinedTaintRulesProvider(
        loadDefaultConfig(),
        loadSemgrepRules("/python-owasp"),
        PIRCombinedTaintRulesProvider.CombinationOptions(
            source = PIRCombinedTaintRulesProvider.CombinationMode.EXTEND,
            sink = PIRCombinedTaintRulesProvider.CombinationMode.EXTEND,
        ),
    )
}
