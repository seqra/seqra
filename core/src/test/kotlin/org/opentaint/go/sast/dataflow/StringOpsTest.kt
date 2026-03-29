package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StringOpsTest : AnalysisTest() {

    // String indexing (s[i]) — simple propagation from whole string
    @Test fun stringIndex001T() = assertReachable("test.stringIndex001T")
    @Test fun stringIndex002F() = assertNotReachable("test.stringIndex002F")

    @Test fun stringSlice001T() = assertReachable("test.stringSlice001T")
    @Test fun stringSlice002F() = assertNotReachable("test.stringSlice002F")

    @Test fun stringMultiVar001T() = assertReachable("test.stringMultiVar001T")
    @Test fun stringMultiVar002F() = assertNotReachable("test.stringMultiVar002F")

    @Test fun stringConcatLoop001T() = assertReachable("test.stringConcatLoop001T")
    @Test fun stringConcatLoop002F() = assertNotReachable("test.stringConcatLoop002F")
}
