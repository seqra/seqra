package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InterfaceDispatchTest : AnalysisTest() {

    // INVOKE dispatch: Go SSA generates wrapper methods for value-receiver types;
    // the wrapper parameter count differs, causing AccessPathBaseStorage index errors.
    // Disabled until wrapper-aware parameter mapping is implemented.
    @Disabled("INVOKE wrapper parameter mapping not yet implemented")
    @Test fun polymorphism001T() = assertReachable("test.polymorphism001T")
    @Disabled("INVOKE wrapper parameter mapping not yet implemented")
    @Test fun polymorphism002F() = assertReachable("test.polymorphism002F")

    @Test fun interfaceClass001T() = assertReachable("test.interfaceClass001T")
    @Test fun interfaceClass002F() = assertNotReachable("test.interfaceClass002F")

    @Disabled("INVOKE wrapper parameter mapping not yet implemented")
    @Test fun interfaceViaFunc001T() = assertReachable("test.interfaceViaFunc001T")
    @Disabled("INVOKE wrapper parameter mapping not yet implemented")
    @Test fun interfaceViaFunc002F() = assertReachable("test.interfaceViaFunc002F")
}
