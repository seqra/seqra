package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeferTest : AnalysisTest() {

    @Test fun defer001T() = assertReachable("test.defer001T")
    @Test fun defer002F() = assertNotReachable("test.defer002F")

    @Test fun deferSink001T() = assertReachable("test.deferSink001T")
    @Test fun deferSink002F() = assertNotReachable("test.deferSink002F")

    @Test fun deferLoop001T() = assertReachable("test.deferLoop001T")

    @Test fun deferClosure001T() = assertReachable("test.deferClosure001T")
    @Test fun deferClosure002F() = assertNotReachable("test.deferClosure002F")

    @Test fun deferMultiple001T() = assertReachable("test.deferMultiple001T")
}
