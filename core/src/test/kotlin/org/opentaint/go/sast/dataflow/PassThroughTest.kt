package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Argument
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Result
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.go.rules.TaintRules
import kotlin.test.Test
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PassThroughTest : AnalysisTest() {

    private val passthroughRule = TaintRules.Pass(
        "test.passthrough",
        PositionBaseWithModifiers.BaseOnly(Argument(0)),
        PositionBaseWithModifiers.BaseOnly(Result),
    )

    private val transformRule = TaintRules.Pass(
        "test.transform",
        PositionBaseWithModifiers.BaseOnly(Argument(0)),
        PositionBaseWithModifiers.BaseOnly(Result),
    )

    @Test fun passThrough001T() {
        val vulns = runAnalysis(stdSource, stdSink, "test.passThrough001T", extraPassRules = listOf(passthroughRule))
        assertTrue(vulns.isNotEmpty(), "Sink was not reached in test.passThrough001T")
    }

    @Test fun passThrough002F() {
        // No pass rule for sanitize() → call kills taint
        val vulns = runAnalysis(stdSource, stdSink, "test.passThrough002F")
        assertTrue(vulns.isEmpty(), "Sink should not be reached in test.passThrough002F")
    }

    @Test fun passThrough003T() {
        val vulns = runAnalysis(stdSource, stdSink, "test.passThrough003T", extraPassRules = listOf(transformRule))
        assertTrue(vulns.isNotEmpty(), "Sink was not reached in test.passThrough003T")
    }

    @Test fun passThrough004F() {
        // transform(in1, in2) returns in2 in its body, so taint from source() (arg1) flows through.
        // Pass rules are additive, they don't block body analysis.
        val vulns = runAnalysis(stdSource, stdSink, "test.passThrough004F", extraPassRules = listOf(transformRule))
        assertTrue(vulns.isNotEmpty(), "Taint flows through transform body (returns in2)")
    }
}
