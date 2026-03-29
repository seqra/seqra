package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StructOpsTest : AnalysisTest() {

    // Struct copy semantics
    @Test fun structCopy001T() = assertReachable("test.structCopy001T")
    @Test fun structCopy002F() = assertNotReachable("test.structCopy002F")
    @Test fun structCopy003T() = assertReachable("test.structCopy003T")

    // Struct as function argument
    @Test fun structArg001T() = assertReachable("test.structArg001T")
    @Test fun structArg002F() = assertNotReachable("test.structArg002F")

    // Struct returned from function
    @Test fun structReturn001T() = assertReachable("test.structReturn001T")
    @Test fun structReturn002F() = assertNotReachable("test.structReturn002F")

    // Nested struct modification — multi-level field chain through interprocedural calls
    @Disabled("Nested struct field: multi-level accessor chain through interproc not tracked")
    @Test fun nestedStructMod001T() = assertReachable("test.nestedStructMod001T")
    @Test fun nestedStructMod002F() = assertNotReachable("test.nestedStructMod002F")
    @Test fun nestedStructMod003F() = assertNotReachable("test.nestedStructMod003F")

    // Struct pointer field
    @Test fun structPtrField001T() = assertReachable("test.structPtrField001T")
    @Test fun structPtrField002F() = assertNotReachable("test.structPtrField002F")

    // Struct with method
    @Test fun structMethod001T() = assertReachable("test.structMethod001T")
    @Test fun structMethod002F() = assertNotReachable("test.structMethod002F")
}
