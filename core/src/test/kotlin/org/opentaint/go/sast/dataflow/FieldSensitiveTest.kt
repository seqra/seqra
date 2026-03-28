package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FieldSensitiveTest : AnalysisTest() {

    // Direct field read
    @Test fun structField001T() = assertReachable("test.structField001T")
    @Test fun structField002F() = assertNotReachable("test.structField002F")

    // Field write
    @Test fun structFieldWrite001T() = assertReachable("test.structFieldWrite001T")
    @Test fun structFieldWrite002F() = assertNotReachable("test.structFieldWrite002F")

    // Through function calls
    @Test fun structFieldInterproc001T() = assertReachable("test.structFieldInterproc001T")
    @Test fun structFieldInterproc002F() = assertNotReachable("test.structFieldInterproc002F")

    // Nested struct
    @Test fun structNested001T() = assertReachable("test.structNested001T")
    @Test fun structNested002F() = assertNotReachable("test.structNested002F")
}
