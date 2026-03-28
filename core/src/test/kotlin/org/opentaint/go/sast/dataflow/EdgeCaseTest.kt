package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EdgeCaseTest : AnalysisTest() {

    // Nil/zero value
    @Test fun nilSlice001F() = assertNotReachable("test.nilSlice001F")
    @Test fun nilMap001F() = assertNotReachable("test.nilMap001F")
    @Test fun emptyStruct001F() = assertNotReachable("test.emptyStruct001F")

    // Long assignment chain
    @Test fun longChain001T() = assertReachable("test.longChain001T")
    @Test fun longChain002F() = assertNotReachable("test.longChain002F")

    // Deep function hops
    @Test fun deepHop001T() = assertReachable("test.deepHop001T")
    @Test fun deepHop002F() = assertNotReachable("test.deepHop002F")

    // Recursion
    @Test fun recursive001T() = assertReachable("test.recursive001T")
    @Test fun recursive002F() = assertNotReachable("test.recursive002F")

    // Variable reuse
    @Test fun reuseVar001T() = assertReachable("test.reuseVar001T")
    @Test fun reuseVar002F() = assertNotReachable("test.reuseVar002F")
    @Test fun reuseVar003T() = assertReachable("test.reuseVar003T")

    // Temp variable
    @Test fun tempVar001T() = assertReachable("test.tempVar001T")
    @Test fun tempVar002F() = assertNotReachable("test.tempVar002F")

    // Multiple calls to same function
    @Test fun edgeMultiCall001T() = assertReachable("test.edgeMultiCall001T")
    @Test fun edgeMultiCall002F() = assertNotReachable("test.edgeMultiCall002F")

    // Struct with mixed taint
    @Test fun structMixed001T() = assertReachable("test.structMixed001T")
    @Test fun structMixed002F() = assertNotReachable("test.structMixed002F")
    @Test fun structMixed003F() = assertNotReachable("test.structMixed003F")

    // Swap pattern
    @Test fun swapVars001T() = assertReachable("test.swapVars001T")
    @Test fun swapVars002F() = assertNotReachable("test.swapVars002F")
}
