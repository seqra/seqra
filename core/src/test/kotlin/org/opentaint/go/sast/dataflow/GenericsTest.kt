package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Argument
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Result
import org.opentaint.dataflow.go.rules.TaintRules
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GenericsTest : AnalysisTest() {

    private val intSource = TaintRules.Source("test.sourceInt", "taint", Result)
    private val intSink = TaintRules.Sink("test.sinkInt", "taint", Argument(0), "test-id")

    // Generic identity function
    @Test fun genericFunc001T() = assertReachable("test.genericFunc001T")
    @Test fun genericFunc002F() = assertNotReachable("test.genericFunc002F")
    @Test fun genericFuncInt001T() = assertSinkReachable(intSource, intSink, "test.genericFuncInt001T")

    // Generic box container
    @Test fun genericBox001T() = assertReachable("test.genericBox001T")
    @Test fun genericBox002F() = assertNotReachable("test.genericBox002F")
    @Test fun genericBoxSet001T() = assertReachable("test.genericBoxSet001T")

    // Generic pair
    @Test fun genericPair001T() = assertReachable("test.genericPair001T")
    @Test fun genericPair002F() = assertNotReachable("test.genericPair002F")
}
