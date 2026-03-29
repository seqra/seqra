package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClosurePatternTest : AnalysisTest() {

    @Test fun closureCapture001T() = assertReachable("test.closureCapture001T")
    @Test fun closureCapture002F() = assertNotReachable("test.closureCapture002F")

    @Test fun closureTwoVars001T() = assertReachable("test.closureTwoVars001T")
    @Test fun closureTwoVars002F() = assertNotReachable("test.closureTwoVars002F")

    @Disabled("Nested closure returned from outer: MakeClosureExpr not visible at call site")
    @Test fun closureNested001T() = assertReachable("test.closureNested001T")
    @Test fun closureNested002F() = assertNotReachable("test.closureNested002F")

    @Test fun closureAssign001T() = assertReachable("test.closureAssign001T")
    @Test fun closureAssign002F() = assertNotReachable("test.closureAssign002F")

    @Test fun closureSlice001T() = assertReachable("test.closureSlice001T")
    @Test fun closureSlice002F() = assertNotReachable("test.closureSlice002F")
}
