package org.opentaint.python.sast.dataflow

import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.python.Argument
import org.opentaint.dataflow.configuration.python.Result
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MatchFlowTest : AnalysisTest() {

    @Test
    fun testMatchCapture() = assertSinkReachable(
        source = source("MatchFlow.source", "taint", Result),
        sink = sink("MatchFlow.sink", "taint", Argument(0), "match"),
        entryPointFunction = "MatchFlow.match_capture"
    )

    @Test
    fun testMatchValueThenWildcard() = assertSinkReachable(
        source = source("MatchFlow.source", "taint", Result),
        sink = sink("MatchFlow.sink", "taint", Argument(0), "match"),
        entryPointFunction = "MatchFlow.match_value_then_wildcard"
    )

    @Test
    fun testMatchAs() = assertSinkReachable(
        source = source("MatchFlow.source", "taint", Result),
        sink = sink("MatchFlow.sink", "taint", Argument(0), "match"),
        entryPointFunction = "MatchFlow.match_as"
    )

    @Test
    fun testMatchGuard() = assertSinkReachable(
        source = source("MatchFlow.source", "taint", Result),
        sink = sink("MatchFlow.sink", "taint", Argument(0), "match"),
        entryPointFunction = "MatchFlow.match_guard"
    )

    @Test
    fun testMatchCaptureClean() = assertSinkNotReachable(
        source = source("MatchFlow.source", "taint", Result),
        sink = sink("MatchFlow.sink", "taint", Argument(0), "match"),
        entryPointFunction = "MatchFlow.match_capture_clean"
    )
}
