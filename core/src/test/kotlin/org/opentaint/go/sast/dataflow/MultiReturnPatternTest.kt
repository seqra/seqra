package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MultiReturnPatternTest : AnalysisTest() {

    @Test fun multiRetSwap001T() = assertReachable("test.multiRetSwap001T")
    @Test fun multiRetSwap002F() = assertNotReachable("test.multiRetSwap002F")

    @Test fun multiRetChain001T() = assertReachable("test.multiRetChain001T")
    @Test fun multiRetChain002F() = assertNotReachable("test.multiRetChain002F")

    @Test fun multiRetFunc001T() = assertReachable("test.multiRetFunc001T")
    @Test fun multiRetFunc002F() = assertNotReachable("test.multiRetFunc002F")

    @Test fun multiRetIgnore001T() = assertReachable("test.multiRetIgnore001T")
    @Test fun multiRetIgnore002F() = assertNotReachable("test.multiRetIgnore002F")
}
