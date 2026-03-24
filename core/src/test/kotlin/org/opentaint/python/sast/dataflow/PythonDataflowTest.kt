package org.opentaint.python.sast.dataflow

import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.python.rules.TaintRules.Sink
import org.opentaint.dataflow.python.rules.TaintRules.Source
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PythonDataflowTest : AnalysisTest() {

    @Test
    fun testSimpleSample() = assertSinkReachable(
        source = Source("Sample.source", "taint", PositionBase.Result),
        sink = Sink("Sample.sink", "taint", PositionBase.Argument(0), "simple"),
        entryPointFunction = "Sample.sample"
    )

    @Test
    fun testSimpleNonReachableSample() = assertSinkNotReachable(
        source = Source("Sample.source", "taint", PositionBase.Result),
        sink = Sink("Sample.sink", "taint", PositionBase.Argument(0), "simple"),
        entryPointFunction = "Sample.sample_non_reachable"
    )
}
