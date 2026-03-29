package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InterfaceDispatchTest : AnalysisTest() {

    @Test fun polymorphism001T() = assertReachable("test.polymorphism001T")
    @Test fun polymorphism002F() = assertReachable("test.polymorphism002F")

    @Test fun interfaceClass001T() = assertReachable("test.interfaceClass001T")
    @Test fun interfaceClass002F() = assertNotReachable("test.interfaceClass002F")

    @Test fun interfaceViaFunc001T() = assertReachable("test.interfaceViaFunc001T")
    @Test fun interfaceViaFunc002F() = assertReachable("test.interfaceViaFunc002F")
}
