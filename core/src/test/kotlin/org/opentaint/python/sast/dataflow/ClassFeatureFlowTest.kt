package org.opentaint.python.sast.dataflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.python.rules.TaintRules.Sink
import org.opentaint.dataflow.python.rules.TaintRules.Source
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClassFeatureFlowTest : AnalysisTest() {

    // --- SimpleObject.py ---

    @Test
    @Disabled("Class method call resolution not yet implemented")
    fun testClassMethodCall() = assertSinkReachable(
        source = Source("SimpleObject.source", "taint", PositionBase.Result),
        sink = Sink("SimpleObject.sink", "taint", PositionBase.Argument(0), "class"),
        entryPointFunction = "SimpleObject.class_method_call"
    )

    @Test
    @Disabled("Class method return value tracking not yet implemented")
    fun testClassMethodReturn() = assertSinkReachable(
        source = Source("SimpleObject.source", "taint", PositionBase.Result),
        sink = Sink("SimpleObject.sink", "taint", PositionBase.Argument(0), "class"),
        entryPointFunction = "SimpleObject.class_method_return"
    )

    // --- StaticMethod.py ---

    @Test
    @Disabled("Static method call resolution not yet implemented")
    fun testStaticMethodCall() = assertSinkReachable(
        source = Source("StaticMethod.source", "taint", PositionBase.Result),
        sink = Sink("StaticMethod.sink", "taint", PositionBase.Argument(0), "static"),
        entryPointFunction = "StaticMethod.static_method_call"
    )

    @Test
    @Disabled("Classmethod call resolution not yet implemented")
    fun testClassmethodCall() = assertSinkReachable(
        source = Source("StaticMethod.source", "taint", PositionBase.Result),
        sink = Sink("StaticMethod.sink", "taint", PositionBase.Argument(0), "static"),
        entryPointFunction = "StaticMethod.classmethod_call"
    )
}
