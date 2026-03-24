package org.opentaint.python.sast.dataflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.python.rules.TaintRules.Sink
import org.opentaint.dataflow.python.rules.TaintRules.Source
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BuiltinPassThroughFlowTest : AnalysisTest() {

    private fun source() = Source("BuiltinPassThrough.source", "taint", PositionBase.Result)
    private fun sink() = Sink("BuiltinPassThrough.sink", "taint", PositionBase.Argument(0), "builtin")

    // --- String method pass-through ---

    @Test
    fun testStrUpper() = assertSinkReachable(
        source = source(), sink = sink(),
        entryPointFunction = "BuiltinPassThrough.builtin_str_upper"
    )

    @Test
    fun testStrLower() = assertSinkReachable(
        source = source(), sink = sink(),
        entryPointFunction = "BuiltinPassThrough.builtin_str_lower"
    )

    @Test
    fun testStrStrip() = assertSinkReachable(
        source = source(), sink = sink(),
        entryPointFunction = "BuiltinPassThrough.builtin_str_strip"
    )

    @Test
    fun testStrReplace() = assertSinkReachable(
        source = source(), sink = sink(),
        entryPointFunction = "BuiltinPassThrough.builtin_str_replace"
    )

    @Test
    fun testStrConstructor() = assertSinkReachable(
        source = source(), sink = sink(),
        entryPointFunction = "BuiltinPassThrough.builtin_str_constructor"
    )

    @Test
    fun testStrEncode() = assertSinkReachable(
        source = source(), sink = sink(),
        entryPointFunction = "BuiltinPassThrough.builtin_str_encode"
    )

    @Test
    fun testStrFormat() = assertSinkReachable(
        source = source(), sink = sink(),
        entryPointFunction = "BuiltinPassThrough.builtin_str_format"
    )

    // --- F-string ---

    @Test
    @Disabled("F-string desugaring varies by mypy version; needs investigation")
    fun testFstring() = assertSinkReachable(
        source = source(), sink = sink(),
        entryPointFunction = "BuiltinPassThrough.builtin_fstring"
    )

    // --- String concatenation ---

    @Test
    fun testStrConcat() = assertSinkReachable(
        source = source(), sink = sink(),
        entryPointFunction = "BuiltinPassThrough.builtin_str_concat"
    )

    @Test
    fun testStrConcatReverse() = assertSinkReachable(
        source = source(), sink = sink(),
        entryPointFunction = "BuiltinPassThrough.builtin_str_concat_reverse"
    )
}
