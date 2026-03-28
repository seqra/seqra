package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BasicFlowTest : AnalysisTest() {

    @Test fun stringDirect() = assertReachable("test.stringDirect")

    @Test fun killByOverwrite001F() = assertNotReachable("test.killByOverwrite001F")

    @Test fun killByReassign001F() = assertNotReachable("test.killByReassign001F")

    @Test fun noKill001T() = assertReachable("test.noKill001T")
}
