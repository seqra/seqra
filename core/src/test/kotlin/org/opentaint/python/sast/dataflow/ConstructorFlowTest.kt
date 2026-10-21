package org.opentaint.python.sast.dataflow

import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.python.Argument
import org.opentaint.dataflow.configuration.python.Result
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConstructorFlowTest : AnalysisTest() {

    @Test
    fun testConstructorArgToSink() = assertSinkReachable(
        source = source("ConstructorArgFlow.source", "taint", Result),
        sink = sink("ConstructorArgFlow.sink", "taint", Argument(0), "ctor"),
        entryPointFunction = "ConstructorArgFlow.ctor_arg_to_sink"
    )

    @Test
    fun testConstructorClassQnSink() = assertSinkReachable(
        source = source("ConstructorArgFlow.source", "taint", Result),
        sink = sink("ConstructorArgFlow.MyService", "taint", Argument(0), "ctor"),
        entryPointFunction = "ConstructorArgFlow.ctor_arg_to_sink"
    )

    @Test
    fun testNoInitClassChainedMethod() = assertSinkReachable(
        source = source("ConstructorArgFlow.source", "taint", Result),
        sink = sink("ConstructorArgFlow.sink", "taint", Argument(0), "ctor"),
        entryPointFunction = "ConstructorArgFlow.no_init_chained_method"
    )

    @Test
    fun testConstructorClassQnSinkNotReachableWhenArgUntainted() = assertSinkNotReachable(
        source = source("ConstructorArgFlow.source", "taint", Result),
        sink = sink("ConstructorArgFlow.MyService", "taint", Argument(0), "ctor"),
        entryPointFunction = "ConstructorArgFlow.ctor_untainted_arg"
    )

    @Test
    fun testConstructorFieldToSink() = assertSinkReachable(
        source = source("ConstructorFieldFlow.source", "taint", Result),
        sink = sink("ConstructorFieldFlow.sink", "taint", Argument(0), "field"),
        entryPointFunction = "ConstructorFieldFlow.ctor_field_to_sink"
    )

    @Test
    fun testConstructorFieldViaMethod() = assertSinkReachable(
        source = source("ConstructorFieldViaMethod.source", "taint", Result),
        sink = sink("ConstructorFieldViaMethod.sink", "taint", Argument(0), "via-method"),
        entryPointFunction = "ConstructorFieldViaMethod.ctor_field_via_method"
    )
}
