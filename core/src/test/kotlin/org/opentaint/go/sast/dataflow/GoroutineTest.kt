package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GoroutineTest : AnalysisTest() {

    // Channel send/receive: GoIRSend not handled in sequent flow function
    @Disabled("Channel send/receive taint model not yet implemented")
    @Test fun channel001T() = assertReachable("test.channel001T")
    @Test fun channel002F() = assertNotReachable("test.channel002F")

    @Disabled("Channel send/receive taint model not yet implemented")
    @Test fun goroutineChan001T() = assertReachable("test.goroutineChan001T")
    @Test fun goroutineChan002F() = assertNotReachable("test.goroutineChan002F")

    @Disabled("Free-var capture propagation not yet implemented")
    @Test fun goroutineShared001T() = assertReachable("test.goroutineShared001T")

    @Disabled("Channel send/receive taint model not yet implemented")
    @Test fun bufferedChan001T() = assertReachable("test.bufferedChan001T")
    @Test fun bufferedChan002F() = assertNotReachable("test.bufferedChan002F")

    @Disabled("Channel send/receive taint model not yet implemented")
    @Test fun chanArg001T() = assertReachable("test.chanArg001T")
    @Test fun chanArg002F() = assertNotReachable("test.chanArg002F")
}
