package org.opentaint.python.sast.dataflow

import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.python.Argument
import org.opentaint.dataflow.configuration.python.Result
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AliasFlowTest : AnalysisTest() {

    private val source = source("AliasField.source", "taint", Result)
    private val sink = sink("AliasField.sink", "taint", Argument(0), "alias")

    @Test
    fun testAliasSimple() = assertSinkReachable(
        source = source,
        sink = sink,
        entryPointFunction = "AliasField.alias_simple"
    )

    @Test
    fun testAliasNone() = assertSinkNotReachable(
        source = source,
        sink = sink,
        entryPointFunction = "AliasField.alias_none"
    )

    @Test
    fun testAliasChain() = assertSinkReachable(
        source = source,
        sink = sink,
        entryPointFunction = "AliasField.alias_chain"
    )

    @Test
    fun testAliasThroughCall() = assertSinkReachable(
        source = source,
        sink = sink,
        entryPointFunction = "AliasField.alias_through_call"
    )

    @Test
    fun testAliasInterproc() = assertSinkReachable(
        source = source,
        sink = sink,
        entryPointFunction = "AliasField.alias_interproc"
    )

    @Test
    fun testAliasInterprocReceiver() = assertSinkReachable(
        source = source,
        sink = sink,
        entryPointFunction = "AliasField.alias_interproc_receiver"
    )

    @Test
    fun testAliasKwargs() = assertSinkReachable(
        source = source,
        sink = sink,
        entryPointFunction = "AliasField.alias_kwargs"
    )
}
