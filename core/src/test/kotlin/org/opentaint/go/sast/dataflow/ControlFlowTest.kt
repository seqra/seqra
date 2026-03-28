package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ControlFlowTest : AnalysisTest() {

    @Test fun conditionalIf001T() = assertReachable("test.conditionalIf001T")
    @Test fun conditionalIf002F() = assertNotReachable("test.conditionalIf002F")

    @Test fun forBody001T() = assertReachable("test.forBody001T")
    @Test fun forBody002F() = assertNotReachable("test.forBody002F")
}
