package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Argument
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Result
import org.opentaint.dataflow.go.rules.TaintRules
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SampleTest : AnalysisTest() {
    @Test
    fun sample() = assertSinkReachable(
        TaintRules.Source("test.source", "taint", Result),
        TaintRules.Sink("test.sink", "taint", Argument(0), "test-id"),
        "test.sample"
    )

    @Test
    fun sampleNonReachable() = assertSinkNotReachable(
        TaintRules.Source("test.source", "taint", Result),
        TaintRules.Sink("test.sink", "taint", Argument(0), "test-id"),
        "test.sampleNonReachable"
    )
}
