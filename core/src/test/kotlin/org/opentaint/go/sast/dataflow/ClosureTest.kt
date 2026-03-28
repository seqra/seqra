package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClosureTest : AnalysisTest() {

    // Basic anonymous functions (passed as args — DIRECT calls, work fine)
    @Test fun anonFunc001T() = assertReachable("test.anonFunc001T")
    @Test fun anonFunc002F() = assertNotReachable("test.anonFunc002F")
    @Test fun anonFuncDirect001T() = assertReachable("test.anonFuncDirect001T")
    @Test fun anonFuncDirect002F() = assertNotReachable("test.anonFuncDirect002F")

    // Closures that capture variables via MakeClosureExpr bindings → free vars
    @Disabled("Free-var capture propagation not yet implemented")
    @Test fun closure001T() = assertReachable("test.closure001T")
    @Test fun closure002F() = assertNotReachable("test.closure002F")
    @Disabled("Free-var capture propagation not yet implemented")
    @Test fun closureModify001T() = assertReachable("test.closureModify001T")
    // Conservative: closure side-effects not tracked
    @Disabled("Free-var capture propagation not yet implemented")
    @Test fun closureModify002F() = assertReachable("test.closureModify002F")

    // Closure returned from function
    @Disabled("Free-var capture propagation not yet implemented")
    @Test fun closureReturn001T() = assertReachable("test.closureReturn001T")
    @Test fun closureReturn002F() = assertNotReachable("test.closureReturn002F")

    // Higher-order functions (DYNAMIC call mode — unresolved)
    @Disabled("DYNAMIC call mode resolution not yet implemented")
    @Test fun higherOrder001T() = assertReachable("test.higherOrder001T")
    @Test fun higherOrder002F() = assertNotReachable("test.higherOrder002F")
    @Disabled("DYNAMIC call mode resolution not yet implemented")
    @Test fun higherOrder003T() = assertReachable("test.higherOrder003T")
    @Test fun higherOrder004F() = assertNotReachable("test.higherOrder004F")
}
