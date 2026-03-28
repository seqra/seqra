package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MultiReturnTest : AnalysisTest() {

    @Test fun multiReturn001T() = assertReachable("test.multiReturn001T")
    @Test fun multiReturn002F() = assertNotReachable("test.multiReturn002F")
    @Test fun multiReturn003T() = assertReachable("test.multiReturn003T")
    @Test fun multiReturn004F() = assertNotReachable("test.multiReturn004F")

    @Test fun threeReturn001T() = assertReachable("test.threeReturn001T")
    @Test fun threeReturn002F() = assertNotReachable("test.threeReturn002F")
    @Test fun threeReturn003T() = assertReachable("test.threeReturn003T")

    @Test fun namedReturn001T() = assertReachable("test.namedReturn001T")
    @Test fun namedReturn002F() = assertNotReachable("test.namedReturn002F")

    @Test fun blankIdentifier001T() = assertReachable("test.blankIdentifier001T")
    @Test fun blankIdentifier002F() = assertNotReachable("test.blankIdentifier002F")
}
