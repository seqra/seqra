package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdvInterproceduralTest : AnalysisTest() {

    // Mutual recursion
    @Test fun mutualRecursion001T() = assertReachable("test.mutualRecursion001T")
    @Test fun mutualRecursion002F() = assertNotReachable("test.mutualRecursion002F")

    // Function returning function result
    @Test fun funcReturnFunc001T() = assertReachable("test.funcReturnFunc001T")
    @Test fun funcReturnFunc002F() = assertNotReachable("test.funcReturnFunc002F")

    // Multiple callers of same function
    @Test fun multiCaller001T() = assertReachable("test.multiCaller001T")
    @Test fun multiCaller002F() = assertNotReachable("test.multiCaller002F")

    // Multi-pass through functions
    @Test fun multiPass001T() = assertReachable("test.multiPass001T")
    @Test fun multiPass002F() = assertNotReachable("test.multiPass002F")

    // Side effects on argument
    @Test fun advSideEffect001T() = assertReachable("test.advSideEffect001T")
    @Test fun advSideEffect002F() = assertNotReachable("test.advSideEffect002F")

    // Builder pattern
    @Test fun builder001T() = assertReachable("test.builder001T")
    @Test fun builder002F() = assertNotReachable("test.builder002F")

    // Conditional return
    @Test fun condReturn001T() = assertReachable("test.condReturn001T")
    @Test fun condReturn002T() = assertReachable("test.condReturn002T")
}
