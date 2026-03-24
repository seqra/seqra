package org.opentaint.python.sast.dataflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.python.rules.TaintRules.Sink
import org.opentaint.dataflow.python.rules.TaintRules.Source
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InterproceduralFlowTest : AnalysisTest() {

    // --- SimpleCall.py ---

    @Test
    fun testCallSimple() = assertSinkReachable(
        source = Source("SimpleCall.source", "taint", PositionBase.Result),
        sink = Sink("SimpleCall.sink", "taint", PositionBase.Argument(0), "call"),
        entryPointFunction = "SimpleCall.call_simple"
    )

    @Test
    fun testCallReturn() = assertSinkReachable(
        source = Source("SimpleCall.source", "taint", PositionBase.Result),
        sink = Sink("SimpleCall.sink", "taint", PositionBase.Argument(0), "call"),
        entryPointFunction = "SimpleCall.call_return"
    )

    @Test
    fun testCallPassThrough() = assertSinkReachable(
        source = Source("SimpleCall.source", "taint", PositionBase.Result),
        sink = Sink("SimpleCall.sink", "taint", PositionBase.Argument(0), "call"),
        entryPointFunction = "SimpleCall.call_pass_through"
    )

    // --- ChainedCall.py ---

    @Test
    fun testCallChain2() = assertSinkReachable(
        source = Source("ChainedCall.source", "taint", PositionBase.Result),
        sink = Sink("ChainedCall.sink", "taint", PositionBase.Argument(0), "chain"),
        entryPointFunction = "ChainedCall.call_chain_2"
    )

    @Test
    fun testCallChain3() = assertSinkReachable(
        source = Source("ChainedCall.source", "taint", PositionBase.Result),
        sink = Sink("ChainedCall.sink", "taint", PositionBase.Argument(0), "chain"),
        entryPointFunction = "ChainedCall.call_chain_3"
    )

    // --- ArgumentPassing.py ---

    @Test
    fun testCallArgKill() = assertSinkNotReachable(
        source = Source("ArgumentPassing.source", "taint", PositionBase.Result),
        sink = Sink("ArgumentPassing.sink", "taint", PositionBase.Argument(0), "arg"),
        entryPointFunction = "ArgumentPassing.call_arg_kill"
    )

    @Test
    fun testCallMultipleArgsPositive() = assertSinkReachable(
        source = Source("ArgumentPassing.source", "taint", PositionBase.Result),
        sink = Sink("ArgumentPassing.sink", "taint", PositionBase.Argument(0), "arg"),
        entryPointFunction = "ArgumentPassing.call_multiple_args_positive"
    )

    @Test
    fun testCallMultipleArgsNegative() = assertSinkNotReachable(
        source = Source("ArgumentPassing.source", "taint", PositionBase.Result),
        sink = Sink("ArgumentPassing.sink", "taint", PositionBase.Argument(0), "arg"),
        entryPointFunction = "ArgumentPassing.call_multiple_args_negative"
    )

    // --- ReturnValue.py ---

    @Test
    fun testReturnAssignAndSink() = assertSinkReachable(
        source = Source("ReturnValue.source", "taint", PositionBase.Result),
        sink = Sink("ReturnValue.sink", "taint", PositionBase.Argument(0), "return"),
        entryPointFunction = "ReturnValue.return_assign_and_sink"
    )

    @Test
    fun testReturnSafeDespiteTaintedInput() = assertSinkNotReachable(
        source = Source("ReturnValue.source", "taint", PositionBase.Result),
        sink = Sink("ReturnValue.sink", "taint", PositionBase.Argument(0), "return"),
        entryPointFunction = "ReturnValue.return_safe_despite_tainted_input"
    )
}
