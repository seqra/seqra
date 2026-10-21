package org.opentaint.python.sast.dataflow

import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.python.Argument
import org.opentaint.dataflow.configuration.python.Result
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CallResolutionFlowTest : AnalysisTest() {

    private fun source() = source("CallResolution.source", "taint", Result)
    private fun sink() = sink("CallResolution.sink", "taint", Argument(0), "call-resolution")
    private fun quoteCleaner() = cleaner("shlex.quote", "taint", Argument(0))

    @Test
    fun testMixedCalleeUnknownPassRule() = assertSinkReachable(
        source = source(), sink = sink(),
        entryPointFunction = "CallResolution.mixed_callee_unknown_pass_rule"
    )

    @Test
    fun testMixedCalleeRealOnly() = assertSinkNotReachable(
        source = source(), sink = sink(),
        entryPointFunction = "CallResolution.mixed_callee_real_only"
    )

    @Test
    fun testUnknownCalleesOneCleaner() = assertSinkReachable(
        source = source(), sink = sink(),
        entryPointFunction = "CallResolution.unknown_callees_one_cleaner",
        cleaners = listOf(quoteCleaner()),
    )

    @Test
    fun testUnknownCalleeCleaner() = assertSinkNotReachable(
        source = source(), sink = sink(),
        entryPointFunction = "CallResolution.unknown_callee_cleaner",
        cleaners = listOf(quoteCleaner()),
    )

    @Test
    fun testTaintedCalleeValueSurvivesCall() = assertSinkReachable(
        source = source(), sink = sink(),
        entryPointFunction = "CallResolution.tainted_callee_value_survives_call"
    )
}
