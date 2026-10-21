package org.opentaint.python.sast.dataflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.python.Argument
import org.opentaint.dataflow.configuration.python.Result
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InterproceduralFlowTest : AnalysisTest() {

    @Test
    fun testCallSimple() = assertSinkReachable(
        source = source("SimpleCall.source", "taint", Result),
        sink = sink("SimpleCall.sink", "taint", Argument(0), "call"),
        entryPointFunction = "SimpleCall.call_simple"
    )

    @Test
    fun testCallReturn() = assertSinkReachable(
        source = source("SimpleCall.source", "taint", Result),
        sink = sink("SimpleCall.sink", "taint", Argument(0), "call"),
        entryPointFunction = "SimpleCall.call_return"
    )

    @Test
    fun testCallPassThrough() = assertSinkReachable(
        source = source("SimpleCall.source", "taint", Result),
        sink = sink("SimpleCall.sink", "taint", Argument(0), "call"),
        entryPointFunction = "SimpleCall.call_pass_through"
    )

    @Test
    fun testCallChain2() = assertSinkReachable(
        source = source("ChainedCall.source", "taint", Result),
        sink = sink("ChainedCall.sink", "taint", Argument(0), "chain"),
        entryPointFunction = "ChainedCall.call_chain_2"
    )

    @Test
    fun testCallChain3() = assertSinkReachable(
        source = source("ChainedCall.source", "taint", Result),
        sink = sink("ChainedCall.sink", "taint", Argument(0), "chain"),
        entryPointFunction = "ChainedCall.call_chain_3"
    )

    @Test
    fun testCallArgKill() = assertSinkNotReachable(
        source = source("ArgumentPassing.source", "taint", Result),
        sink = sink("ArgumentPassing.sink", "taint", Argument(0), "arg"),
        entryPointFunction = "ArgumentPassing.call_arg_kill"
    )

    @Test
    fun testCallMultipleArgsPositive() = assertSinkReachable(
        source = source("ArgumentPassing.source", "taint", Result),
        sink = sink("ArgumentPassing.sink", "taint", Argument(0), "arg"),
        entryPointFunction = "ArgumentPassing.call_multiple_args_positive"
    )

    @Test
    fun testCallMultipleArgsNegative() = assertSinkNotReachable(
        source = source("ArgumentPassing.source", "taint", Result),
        sink = sink("ArgumentPassing.sink", "taint", Argument(0), "arg"),
        entryPointFunction = "ArgumentPassing.call_multiple_args_negative"
    )

    @Test
    fun testNestedArgToSink() = assertSinkReachable(
        source = source("NestedCall.source", "taint", Result),
        sink = sink("NestedCall.sink", "taint", Argument(0), "nested"),
        entryPointFunction = "NestedCall.nested_arg_to_sink"
    )

    @Test
    fun testNestedReturn() = assertSinkReachable(
        source = source("NestedCall.source", "taint", Result),
        sink = sink("NestedCall.sink", "taint", Argument(0), "nested"),
        entryPointFunction = "NestedCall.nested_return"
    )

    @Test
    fun testReturnAssignAndSink() = assertSinkReachable(
        source = source("ReturnValue.source", "taint", Result),
        sink = sink("ReturnValue.sink", "taint", Argument(0), "return"),
        entryPointFunction = "ReturnValue.return_assign_and_sink"
    )

    @Test
    fun testReturnSafeDespiteTaintedInput() = assertSinkNotReachable(
        source = source("ReturnValue.source", "taint", Result),
        sink = sink("ReturnValue.sink", "taint", Argument(0), "return"),
        entryPointFunction = "ReturnValue.return_safe_despite_tainted_input"
    )
}
