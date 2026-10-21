package org.opentaint.python.sast.dataflow

import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.python.AnyArgument
import org.opentaint.dataflow.configuration.python.Argument
import org.opentaint.dataflow.configuration.python.KwArgument
import org.opentaint.dataflow.configuration.python.Result
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KwArgFlowTest : AnalysisTest() {

    private val src = "KwArgs.source"
    private val snk = "KwArgs.sink"

    @Test
    fun testKwIntoParam() = assertSinkReachable(
        source = source(src, "taint", Result),
        sink = sink(snk, "taint", Argument(0), "call"),
        entryPointFunction = "KwArgs.kw_into_param"
    )

    @Test
    fun testKwOutOfOrder() = assertSinkReachable(
        source = source(src, "taint", Result),
        sink = sink(snk, "taint", Argument(0), "call"),
        entryPointFunction = "KwArgs.kw_out_of_order"
    )

    @Test
    fun testKwGuardPositive() = assertSinkReachable(
        source = source(src, "taint", Result),
        sink = sink(snk, "taint", Argument(0), "call"),
        entryPointFunction = "KwArgs.kw_guard_positive"
    )

    @Test
    fun testKwGuardNegative() = assertSinkNotReachable(
        source = source(src, "taint", Result),
        sink = sink(snk, "taint", Argument(0), "call"),
        entryPointFunction = "KwArgs.kw_guard_negative"
    )

    @Test
    fun testKwVarKeywordDropped() = assertSinkNotReachable(
        source = source(src, "taint", Result),
        sink = sink(snk, "taint", Argument(0), "call"),
        entryPointFunction = "KwArgs.kw_var_keyword_dropped"
    )

    @Test
    fun testKwInstanceMethod() = assertSinkReachable(
        source = source(src, "taint", Result),
        sink = sink(snk, "taint", Argument(0), "call"),
        entryPointFunction = "KwArgs.kw_instance_method"
    )

    @Test
    fun testKwargRulePresent() = assertSinkReachable(
        source = source(src, "taint", Result),
        sink = sink("KwArgs.kw_sink", "taint", KwArgument("a"), "call"),
        entryPointFunction = "KwArgs.kw_rule_present"
    )

    @Test
    fun testKwargRuleAbsent() = assertSinkNotReachable(
        source = source(src, "taint", Result),
        sink = sink("KwArgs.kw_sink", "taint", KwArgument("a"), "call"),
        entryPointFunction = "KwArgs.kw_rule_absent"
    )

    @Test
    fun testAnyArgOverKeyword() = assertSinkReachable(
        source = source(src, "taint", Result),
        sink = sink("KwArgs.kw_sink", "taint", AnyArgument, "call"),
        entryPointFunction = "KwArgs.kw_rule_present"
    )
}
