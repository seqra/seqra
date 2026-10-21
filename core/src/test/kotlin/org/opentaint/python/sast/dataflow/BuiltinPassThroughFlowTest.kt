package org.opentaint.python.sast.dataflow

import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.python.Argument
import org.opentaint.dataflow.configuration.python.Result
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BuiltinPassThroughFlowTest : AnalysisTest() {

    private fun source() = source("BuiltinPassThrough.source", "taint", Result)
    private fun sink() = sink("BuiltinPassThrough.sink", "taint", Argument(0), "builtin")

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

    @Test
    fun testFstring() = assertSinkReachable(
        source = source(), sink = sink(),
        entryPointFunction = "BuiltinPassThrough.builtin_fstring"
    )

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
