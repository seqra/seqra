package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdvControlFlowTest : AnalysisTest() {

    // Switch/case
    @Test fun switchCase001T() = assertReachable("test.switchCase001T")
    @Test fun switchCase002F() = assertNotReachable("test.switchCase002F")
    @Test fun switchFallthrough001T() = assertReachable("test.switchFallthrough001T")

    // For-range
    @Test fun forRange001T() = assertReachable("test.forRange001T")
    @Test fun forRange002F() = assertNotReachable("test.forRange002F")
    @Disabled("Map range iteration taint propagation not yet implemented")
    @Test fun forRangeMap001T() = assertReachable("test.forRangeMap001T")
    @Test fun forRangeMap002F() = assertNotReachable("test.forRangeMap002F")

    // Break and continue
    @Test fun breakInLoop001T() = assertReachable("test.breakInLoop001T")
    @Test fun breakInLoop002F() = assertNotReachable("test.breakInLoop002F")
    @Test fun continueInLoop001T() = assertReachable("test.continueInLoop001T")

    // Labeled break
    @Test fun labeledBreak001T() = assertReachable("test.labeledBreak001T")

    // Nested loops
    @Test fun nestedLoop001T() = assertReachable("test.nestedLoop001T")
    @Test fun nestedLoop002F() = assertNotReachable("test.nestedLoop002F")

    // Select statement
    @Disabled("Channel send/receive taint model not yet implemented")
    @Test fun selectStmt001T() = assertReachable("test.selectStmt001T")
}
