package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CombinationTest : AnalysisTest() {

    @Test fun combStructInterface001T() = assertReachable("test.combStructInterface001T")
    @Test fun combStructInterface002F() = assertNotReachable("test.combStructInterface002F")

    @Disabled("Free-var capture propagation not yet implemented")
    @Test fun combClosureField001T() = assertReachable("test.combClosureField001T")
    @Test fun combClosureField002F() = assertNotReachable("test.combClosureField002F")

    @Test fun combMapFunc001T() = assertReachable("test.combMapFunc001T")
    @Test fun combMapFunc002F() = assertNotReachable("test.combMapFunc002F")

    @Test fun combSliceLoop001T() = assertReachable("test.combSliceLoop001T")
    @Test fun combSliceLoop002F() = assertNotReachable("test.combSliceLoop002F")

    @Test fun combPtrMethod001T() = assertReachable("test.combPtrMethod001T")
    @Test fun combPtrMethod002F() = assertNotReachable("test.combPtrMethod002F")

    @Test fun combNestedFunc001T() = assertReachable("test.combNestedFunc001T")
    @Test fun combNestedFunc002F() = assertNotReachable("test.combNestedFunc002F")

    @Disabled("Free-var capture propagation not yet implemented")
    @Test fun combDeepChain001T() = assertReachable("test.combDeepChain001T")
    @Test fun combDeepChain002F() = assertNotReachable("test.combDeepChain002F")

    @Test fun combStructSlice001T() = assertReachable("test.combStructSlice001T")
    @Test fun combStructSlice002F() = assertNotReachable("test.combStructSlice002F")

    @Test fun combSequence001T() = assertReachable("test.combSequence001T")
    @Test fun combSequence002F() = assertNotReachable("test.combSequence002F")
}
