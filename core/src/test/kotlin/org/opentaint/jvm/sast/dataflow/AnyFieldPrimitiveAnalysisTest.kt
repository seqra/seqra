package org.opentaint.jvm.sast.dataflow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Argument
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.PositionModifier
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintAssignAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.jvm.ap.ifds.taint.PrimitiveTaintExt

/**
 * The star is the only source of element taint on a primitive array.
 *
 * The array-element mechanism gave element taint to an array position without a rule. It is
 * deleted. A base-only source thus stops at an element read. A whole-object source continues,
 * because the star puts the any-field part on the element.
 *
 * These two tests are a pair. Together they show that the star replaces the deleted mechanism.
 *
 * The whole-object test also needs the any-accessor fix. An element read puts the any-field part
 * on a primitive position. Before the fix the filter removed the full fact there, and the taint
 * stopped in Tree mode.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AnyFieldPrimitiveAnalysisTest : AnalysisTest() {

    companion object {
        private const val TEST_CLS = "test.samples.AnyFieldPrimitiveSample"
        private const val TAINT_MARK = "tainted" + PrimitiveTaintExt.PRIMITIVE_TRACKING_ENABLED_MODE
        private const val RULE_ID = "any-field-primitive-rule"
    }

    override val sourceFileExtension: String = "java"

    override val analysisUnrollStrategy: AnyAccessorUnrollStrategy = object : AnyAccessorUnrollStrategy {
        override fun unrollAccessor(accessor: Accessor): Boolean =
            accessor is FieldAccessor || accessor is ElementAccessor
    }

    private fun config(vararg positions: PositionBaseWithModifiers) = SerializedTaintConfig(
        entryPoint = listOf(
            SerializedRule.EntryPoint(
                function = functionMatcher(TEST_CLS, "elementFlow"),
                taint = positions.map { SerializedTaintAssignAction(kind = TAINT_MARK, pos = it) }
            )
        ),
        sink = listOf(
            sinkRule(TEST_CLS, "sink", RULE_ID, listOf<Pair<PositionBase, String>>(Argument(0) to TAINT_MARK))
        )
    )

    private fun baseOnly() = PositionBaseWithModifiers.BaseOnly(Argument(0))

    private fun anyField() =
        PositionBaseWithModifiers.WithModifiers(Argument(0), listOf(PositionModifier.AnyField))

    @Test
    fun `base-only source on a primitive array does not reach the element sink`() = assertNotReachable(
        config = config(baseOnly()),
        testCls = TEST_CLS,
        entryPointName = "elementFlow",
        testName = "base-only source, primitive array element"
    )

    @Test
    fun `whole-object source on a primitive array reaches the element sink`() = assertReachable(
        config = config(baseOnly(), anyField()),
        testCls = TEST_CLS,
        entryPointName = "elementFlow",
        ruleId = RULE_ID,
        testName = "whole-object source, primitive array element"
    )
}

class TreeAnyFieldPrimitiveAnalysisTest : AnyFieldPrimitiveAnalysisTest()

class AutomataAnyFieldPrimitiveAnalysisTest : AnyFieldPrimitiveAnalysisTest() {
    override val apMode: ApMode = ApMode.Automata
}
