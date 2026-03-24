package org.opentaint.python.sast.dataflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.python.rules.TaintRules.Sink
import org.opentaint.dataflow.python.rules.TaintRules.Source
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FieldSensitiveFlowTest : AnalysisTest() {

    // --- ClassField.py ---

    @Test
    @Disabled("Field sensitivity not yet implemented")
    fun testFieldSimpleRead() = assertSinkReachable(
        source = Source("ClassField.source", "taint", PositionBase.Result),
        sink = Sink("ClassField.sink", "taint", PositionBase.Argument(0), "field"),
        entryPointFunction = "ClassField.field_simple_read"
    )

    @Test
    @Disabled("Field sensitivity not yet implemented")
    fun testFieldDifferentField() = assertSinkNotReachable(
        source = Source("ClassField.source", "taint", PositionBase.Result),
        sink = Sink("ClassField.sink", "taint", PositionBase.Argument(0), "field"),
        entryPointFunction = "ClassField.field_different_field"
    )

    @Test
    @Disabled("Field sensitivity not yet implemented")
    fun testFieldOverwrite() = assertSinkNotReachable(
        source = Source("ClassField.source", "taint", PositionBase.Result),
        sink = Sink("ClassField.sink", "taint", PositionBase.Argument(0), "field"),
        entryPointFunction = "ClassField.field_overwrite"
    )

    // --- DictAccess.py ---

    @Test
    @Disabled("Dict access tracking not yet implemented")
    fun testDictLiteral() = assertSinkReachable(
        source = Source("DictAccess.source", "taint", PositionBase.Result),
        sink = Sink("DictAccess.sink", "taint", PositionBase.Argument(0), "dict"),
        entryPointFunction = "DictAccess.dict_literal"
    )

    @Test
    @Disabled("Dict access tracking not yet implemented")
    fun testDictAssign() = assertSinkReachable(
        source = Source("DictAccess.source", "taint", PositionBase.Result),
        sink = Sink("DictAccess.sink", "taint", PositionBase.Argument(0), "dict"),
        entryPointFunction = "DictAccess.dict_assign"
    )
}
