package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Argument
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Result
import org.opentaint.dataflow.go.rules.TaintRules
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExpressionTest : AnalysisTest() {

    private val intSource = TaintRules.Source("test.sourceInt", "taint", Result)
    private val intSink = TaintRules.Sink("test.sinkInt", "taint", Argument(0), "test-id")
    private val boolSource = TaintRules.Source("test.sourceBool", "taint", Result)
    private val boolSink = TaintRules.Sink("test.sinkBool", "taint", Argument(0), "test-id")

    // String concatenation
    @Test fun stringConcat001T() = assertReachable("test.stringConcat001T")
    @Test fun stringConcat002T() = assertReachable("test.stringConcat002T")
    @Test fun stringConcat003T() = assertReachable("test.stringConcat003T")
    @Test fun stringConcat004F() = assertNotReachable("test.stringConcat004F")

    // String concat assignment
    @Test fun stringConcatAssign001T() = assertReachable("test.stringConcatAssign001T")
    @Test fun stringConcatAssign002F() = assertNotReachable("test.stringConcatAssign002F")

    // Concat chain
    @Test fun stringConcatChain001T() = assertReachable("test.stringConcatChain001T")
    @Test fun stringConcatChain002F() = assertNotReachable("test.stringConcatChain002F")

    // Integer arithmetic (kills taint)
    @Test fun intArith001F() = assertSinkNotReachable(intSource, intSink, "test.intArith001F")
    @Test fun intArith002F() = assertSinkNotReachable(intSource, intSink, "test.intArith002F")

    // Boolean negation (kills taint)
    @Test fun boolNeg001F() = assertSinkNotReachable(boolSource, boolSink, "test.boolNeg001F")

    // Comparison (kills taint)
    @Test fun comparison001F() = assertSinkNotReachable(stdSource, boolSink, "test.comparison001F")
}
