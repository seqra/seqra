package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StructPatternTest : AnalysisTest() {

    @Test fun structLiteral001T() = assertReachable("test.structLiteral001T")
    @Test fun structLiteral002F() = assertNotReachable("test.structLiteral002F")

    @Test fun structMultiField001T() = assertReachable("test.structMultiField001T")
    @Test fun structMultiField002F() = assertNotReachable("test.structMultiField002F")

    @Test fun structFuncReturn001T() = assertReachable("test.structFuncReturn001T")
    @Test fun structFuncReturn002F() = assertNotReachable("test.structFuncReturn002F")

    @Test fun structPtrDeref001T() = assertReachable("test.structPtrDeref001T")
    @Test fun structPtrDeref002F() = assertNotReachable("test.structPtrDeref002F")

    @Test fun structReassign001T() = assertReachable("test.structReassign001T")
    @Test fun structReassign002F() = assertNotReachable("test.structReassign002F")
}
