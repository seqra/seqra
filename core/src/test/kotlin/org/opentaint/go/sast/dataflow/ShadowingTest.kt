package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShadowingTest : AnalysisTest() {

    @Test fun shadow001T() = assertReachable("test.shadow001T")
    @Test fun shadow002F() = assertNotReachable("test.shadow002F")
    @Test fun shadow003T() = assertReachable("test.shadow003T")
    // Conservative: `if true { data = "safe" }` — analysis doesn't evaluate constant conditions
    @Test fun shadow004F() = assertReachable("test.shadow004F")

    @Test fun shadowLoop001T() = assertReachable("test.shadowLoop001T")
    @Test fun shadowLoop002F() = assertNotReachable("test.shadowLoop002F")

    @Test fun shadowParam001T() = assertReachable("test.shadowParam001T")
    @Test fun shadowParam002F() = assertNotReachable("test.shadowParam002F")
}
