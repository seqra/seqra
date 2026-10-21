package org.opentaint.python.sast.dataflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.python.Argument
import org.opentaint.dataflow.configuration.python.Result
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClosureFlowTest : AnalysisTest() {

    private fun reachable(entryPoint: String) = assertSinkReachable(
        source = source("ClosureFlow.source", "taint", Result),
        sink = sink("ClosureFlow.sink", "taint", Argument(0), "closure"),
        entryPointFunction = "ClosureFlow.$entryPoint"
    )

    private fun notReachable(entryPoint: String) = assertSinkNotReachable(
        source = source("ClosureFlow.source", "taint", Result),
        sink = sink("ClosureFlow.sink", "taint", Argument(0), "closure"),
        entryPointFunction = "ClosureFlow.$entryPoint"
    )

    @Test
    fun testClosureForwardedArg() = reachable("closure_forwarded_arg")

    @Test
    fun testClosureCapturedLocal() = reachable("closure_captured_local")

    @Test
    fun testClosureCapturedParam() = reachable("closure_captured_param")

    @Test
    fun testClosureCapturedSafe() = notReachable("closure_captured_safe")

    @Test
    fun testClosureLateBinding() = reachable("closure_late_binding")

    @Test
    fun testClosureNonlocalWrite() = reachable("closure_nonlocal_write")

    @Disabled("Returned closure call is not resolved")
    @Test
    fun testClosureReturned() = reachable("closure_returned")

    @Disabled("No strong update through aliases")
    @Test
    fun testClosureNonlocalOverwrite() = notReachable("closure_nonlocal_overwrite")

    @Test
    fun testClosureOtherCaptureSafe() = notReachable("closure_other_capture_safe")
}
