package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Argument
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Result
import org.opentaint.dataflow.go.rules.TaintRules
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TypeOpsTest : AnalysisTest() {

    private val intSource = TaintRules.Source("test.sourceInt", "taint", Result)
    private val floatSink = TaintRules.Sink("test.sinkFloat", "taint", Argument(0), "test-id")
    private val anySource = TaintRules.Source("test.sourceAny", "taint", Result)

    // Type conversion
    @Test fun typeCastInt001T() = assertSinkReachable(intSource, floatSink, "test.typeCastInt001T")
    @Test fun typeCastInt002F() = assertSinkNotReachable(intSource, floatSink, "test.typeCastInt002F")

    // String to bytes and back
    @Test fun typeCastStringToBytes001T() = assertReachable("test.typeCastStringToBytes001T")
    @Test fun typeCastStringToBytes002F() = assertNotReachable("test.typeCastStringToBytes002F")

    // Interface wrapping
    @Test fun interfaceWrap001T() = assertReachable("test.interfaceWrap001T")
    @Test fun interfaceWrap002F() = assertNotReachable("test.interfaceWrap002F")

    // Type assertion
    @Test fun typeAssert001T() = assertSinkReachable(anySource, stdSink, "test.typeAssert001T")
    @Test fun typeAssert002F() = assertSinkNotReachable(anySource, stdSink, "test.typeAssert002F")

    // Type assertion with comma-ok
    // Type assertion with comma-ok produces tuple (value, ok) — needs tuple extract index sensitivity for non-call tuples
    @Disabled("Type assertion comma-ok tuple extract not yet implemented")
    @Test fun typeAssertOk001T() = assertSinkReachable(anySource, stdSink, "test.typeAssertOk001T")
    @Test fun typeAssertOk002F() = assertSinkNotReachable(anySource, stdSink, "test.typeAssertOk002F")

    // Rune conversion
    @Test fun runeConv001T() = assertSinkReachable(intSource, stdSink, "test.runeConv001T")
    @Test fun runeConv002F() = assertSinkNotReachable(intSource, stdSink, "test.runeConv002F")
}
