package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.TestInstance
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MethodReceiverTest : AnalysisTest() {

    @Test fun methodRecvValue001T() = assertReachable("test.methodRecvValue001T")
    @Test fun methodRecvValue002F() = assertNotReachable("test.methodRecvValue002F")

    @Test fun methodRecvPtr001T() = assertReachable("test.methodRecvPtr001T")
    @Test fun methodRecvPtr002F() = assertNotReachable("test.methodRecvPtr002F")
    @Test fun methodRecvPtr003T() = assertReachable("test.methodRecvPtr003T")

    @Test fun methodRecvField001T() = assertReachable("test.methodRecvField001T")
    @Test fun methodRecvField002F() = assertNotReachable("test.methodRecvField002F")

    @Test fun methodChain001T() = assertReachable("test.methodChain001T")
    @Test fun methodChain002F() = assertNotReachable("test.methodChain002F")
}
