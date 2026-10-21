package org.opentaint.python.sast.dataflow

import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.python.Argument
import org.opentaint.dataflow.configuration.python.Result
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PythonDataflowTest : AnalysisTest() {

    @Test
    fun testSimpleSample() = assertSinkReachable(
        source = source("Sample.source", "taint", Result),
        sink = sink("Sample.sink", "taint", Argument(0), "simple"),
        entryPointFunction = "Sample.sample"
    )

    @Test
    fun testSimpleNonReachableSample() = assertSinkNotReachable(
        source = source("Sample.source", "taint", Result),
        sink = sink("Sample.sink", "taint", Argument(0), "simple"),
        entryPointFunction = "Sample.sample_non_reachable"
    )
}
