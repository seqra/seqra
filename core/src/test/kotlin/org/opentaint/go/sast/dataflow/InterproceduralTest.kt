package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InterproceduralTest : AnalysisTest() {

    // Return value passing
    @Test fun returnValuePassing001F() = assertNotReachable("test.returnValuePassing001F")
    @Test fun returnValuePassing002T() = assertReachable("test.returnValuePassing002T")

    // Argument passing
    @Test fun argPassing001F() = assertNotReachable("test.argPassing001F")
    @Test fun argPassing002T() = assertReachable("test.argPassing002T")
    @Test fun argPassing005F() = assertNotReachable("test.argPassing005F")
    @Test fun argPassing006T() = assertReachable("test.argPassing006T")

    // Deep call chains
    @Test fun deepCall001T() = assertReachable("test.deepCall001T")
    @Test fun deepCall002T() = assertReachable("test.deepCall002T")
    @Test fun deepCall003T() = assertReachable("test.deepCall003T")
    @Test fun deepCallClean001F() = assertNotReachable("test.deepCallClean001F")

    // Argument position sensitivity
    @Test fun argPosition001T() = assertReachable("test.argPosition001T")
    @Test fun argPosition002F() = assertNotReachable("test.argPosition002F")
}
