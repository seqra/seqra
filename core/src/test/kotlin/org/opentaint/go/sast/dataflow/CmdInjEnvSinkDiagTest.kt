package org.opentaint.go.sast.dataflow

import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.go.serialized.GoNameMatcher
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedAssignAction
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedCondition
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedRule.PassThrough
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedRule.Sink
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedRule.Source
import org.opentaint.dataflow.configuration.go.serialized.GoSinkMetaData
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Result
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.This
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.PositionModifier
import org.opentaint.go.config.GoDefaultConfigLoader
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CmdInjEnvSinkDiagTest : AnalysisTest() {

    override val commonPassRules: List<PassThrough> =
        GoDefaultConfigLoader.loadConfig()?.passThrough ?: emptyList()

    private val getenvSource = Source(
        pkg = GoNameMatcher.Simple("os"),
        function = GoNameMatcher.Simple("Getenv"),
        condition = null,
        taint = listOf(GoSerializedAssignAction("taint", PositionBaseWithModifiers.BaseOnly(Result))),
        info = null,
    )

    private val combinedOutputSink = Sink(
        pkg = GoNameMatcher.Pattern("(.*/)?exec\\.Cmd\\)"),
        function = GoNameMatcher.Simple("CombinedOutput"),
        condition = GoSerializedCondition.ContainsMark("taint", PositionBaseWithModifiers.WithModifiers(This, listOf(PositionModifier.AnyField))),
        trackFactsReachAnalysisEnd = emptyList(),
        id = "cmdinj-test",
        meta = GoSinkMetaData("Taint sink: CombinedOutput"),
        info = null,
    )

    @Test
    fun diag() = printFactsAt("test.cmdInjEnvSink001T", source = getenvSource, sink = combinedOutputSink)

    @Test
    fun reachableViaEnvSubPath() =
        assertSinkReachable(getenvSource, combinedOutputSink, "test.cmdInjEnvSink001T")

    @Test
    fun negativeStaysClean() =
        assertSinkNotReachable(getenvSource, combinedOutputSink, "test.cmdInjEnvSink002F")
}
