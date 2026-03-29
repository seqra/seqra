package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SanitizationPatternTest : AnalysisTest() {

    // Conservative: only one branch sanitizes, so taint persists
    @Test fun sanitizeConditional001T() = assertReachable("test.sanitizeConditional001T")
    @Test fun sanitizeConditional002F() = assertNotReachable("test.sanitizeConditional002F")

    @Test fun sanitizeReturn001T() = assertReachable("test.sanitizeReturn001T")
    @Test fun sanitizeReturn002F() = assertNotReachable("test.sanitizeReturn002F")

    @Test fun sanitizeChain001T() = assertReachable("test.sanitizeChain001T")
    @Test fun sanitizeChain002F() = assertNotReachable("test.sanitizeChain002F")

    @Test fun sanitizeReassign001T() = assertReachable("test.sanitizeReassign001T")
    @Test fun sanitizeReassign002F() = assertNotReachable("test.sanitizeReassign002F")
}
