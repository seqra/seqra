package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PointerPatternTest : AnalysisTest() {

    @Test fun ptrAlias001T() = assertReachable("test.ptrAlias001T")
    @Test fun ptrAlias002F() = assertNotReachable("test.ptrAlias002F")

    @Test fun ptrField001T() = assertReachable("test.ptrField001T")
    @Test fun ptrField002F() = assertNotReachable("test.ptrField002F")

    @Test fun ptrFunc001T() = assertReachable("test.ptrFunc001T")
    @Test fun ptrFunc002F() = assertNotReachable("test.ptrFunc002F")

    @Test fun ptrDeref001T() = assertReachable("test.ptrDeref001T")
    @Test fun ptrDeref002F() = assertNotReachable("test.ptrDeref002F")
}
