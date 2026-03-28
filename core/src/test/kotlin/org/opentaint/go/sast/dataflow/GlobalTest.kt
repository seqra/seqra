package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GlobalTest : AnalysisTest() {

    @Test fun globalWrite001T() = assertReachable("test.globalWrite001T")
    @Test fun globalWrite002F() = assertNotReachable("test.globalWrite002F")
    @Test fun globalWriteRead001T() = assertReachable("test.globalWriteRead001T")
    @Test fun globalWriteRead002F() = assertNotReachable("test.globalWriteRead002F")

    @Test fun globalFunc001T() = assertReachable("test.globalFunc001T")
    @Test fun globalFunc002F() = assertNotReachable("test.globalFunc002F")

    @Test fun globalStruct001T() = assertReachable("test.globalStruct001T")
    @Test fun globalStruct002F() = assertNotReachable("test.globalStruct002F")
}
