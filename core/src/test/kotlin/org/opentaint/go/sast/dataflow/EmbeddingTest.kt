package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmbeddingTest : AnalysisTest() {

    // Promoted field access on embedded structs: Go IR may flatten or use multi-level field paths
    @Disabled("Promoted field resolution for embedded structs not yet implemented")
    @Test fun embeddedField001T() = assertReachable("test.embeddedField001T")
    @Test fun embeddedField002F() = assertNotReachable("test.embeddedField002F")
    @Disabled("Promoted method resolution for embedded structs not yet implemented")
    @Test fun embeddedMethod001T() = assertReachable("test.embeddedMethod001T")
    @Test fun embeddedMethod002F() = assertNotReachable("test.embeddedMethod002F")

    @Disabled("Promoted field resolution for embedded structs not yet implemented")
    @Test fun embeddedDeep001T() = assertReachable("test.embeddedDeep001T")
    @Test fun embeddedDeep002F() = assertNotReachable("test.embeddedDeep002F")

    @Test fun embeddedInterface001T() = assertReachable("test.embeddedInterface001T")
    @Test fun embeddedInterface002F() = assertNotReachable("test.embeddedInterface002F")
}
