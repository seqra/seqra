package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChannelPatternTest : AnalysisTest() {

    @Test fun chanDirection001T() = assertReachable("test.chanDirection001T")
    @Test fun chanDirection002F() = assertNotReachable("test.chanDirection002F")

    @Test fun chanMultiSend001T() = assertReachable("test.chanMultiSend001T")
    @Test fun chanMultiSend002F() = assertNotReachable("test.chanMultiSend002F")

    @Test fun chanFunc001T() = assertReachable("test.chanFunc001T")
    @Test fun chanFunc002F() = assertNotReachable("test.chanFunc002F")

    @Test fun chanPassThrough001T() = assertReachable("test.chanPassThrough001T")
    @Test fun chanPassThrough002F() = assertNotReachable("test.chanPassThrough002F")

    @Test fun chanLoop001T() = assertReachable("test.chanLoop001T")
    @Test fun chanLoop002F() = assertNotReachable("test.chanLoop002F")
}
