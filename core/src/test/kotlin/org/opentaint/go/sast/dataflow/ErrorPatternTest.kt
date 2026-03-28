package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ErrorPatternTest : AnalysisTest() {

    @Test fun errorReturn001T() = assertReachable("test.errorReturn001T")
    @Test fun errorReturn002F() = assertNotReachable("test.errorReturn002F")
    @Test fun errorReturn003T() = assertReachable("test.errorReturn003T")

    @Test fun errorWrap001T() = assertReachable("test.errorWrap001T")
    @Test fun errorWrap002F() = assertNotReachable("test.errorWrap002F")

    @Test fun earlyReturn001T() = assertReachable("test.earlyReturn001T")
    @Test fun earlyReturn002F() = assertNotReachable("test.earlyReturn002F")
}
