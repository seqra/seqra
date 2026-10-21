package org.opentaint.python.sast.dataflow

import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.python.Argument
import org.opentaint.dataflow.configuration.python.Result
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FieldSensitiveFlowTest : AnalysisTest() {

    @Test
    fun testFieldSimpleRead() = assertSinkReachable(
        source = source("ClassField.source", "taint", Result),
        sink = sink("ClassField.sink", "taint", Argument(0), "field"),
        entryPointFunction = "ClassField.field_simple_read"
    )

    @Test
    fun testFieldDifferentField() = assertSinkNotReachable(
        source = source("ClassField.source", "taint", Result),
        sink = sink("ClassField.sink", "taint", Argument(0), "field"),
        entryPointFunction = "ClassField.field_different_field"
    )

    @Test
    fun testFieldOverwrite() = assertSinkNotReachable(
        source = source("ClassField.source", "taint", Result),
        sink = sink("ClassField.sink", "taint", Argument(0), "field"),
        entryPointFunction = "ClassField.field_overwrite"
    )

    @Test
    fun testDictLiteral() = assertSinkReachable(
        source = source("DictAccess.source", "taint", Result),
        sink = sink("DictAccess.sink", "taint", Argument(0), "dict"),
        entryPointFunction = "DictAccess.dict_literal"
    )

    @Test
    fun testDictAssign() = assertSinkReachable(
        source = source("DictAccess.source", "taint", Result),
        sink = sink("DictAccess.sink", "taint", Argument(0), "dict"),
        entryPointFunction = "DictAccess.dict_assign"
    )
}
