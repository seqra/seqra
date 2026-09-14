package org.opentaint.jvm.sast.dataflow

import org.junit.jupiter.api.Test
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Argument
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.PositionModifier.AnyField
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.configuration.jvm.serialized.SinkMetaData

class AnyFieldInterproceduralAnalysisTest : AnalysisTest() {
    companion object {
        private const val TEST_CLASS = "test.samples.AnyFieldInterproceduralSample"
        private const val TAINT_MARK = "tainted"
        private const val RULE_ID = "any-field-interprocedural"
    }

    override val sourceFileExtension: String = "java"

    override val analysisUnrollStrategy: AnyAccessorUnrollStrategy = object : AnyAccessorUnrollStrategy {
        override fun unrollAccessor(accessor: Accessor): Boolean = accessor is FieldAccessor
    }

    private val config = SerializedTaintConfig(
        source = listOf(sourceRule(TEST_CLASS, "source", TAINT_MARK)),
        sink = listOf(
            SerializedRule.Sink(
                function = functionMatcher(TEST_CLASS, "sink"),
                // The sink accepts the container, so the mark has to be found below an
                // arbitrary field rather than on argument 0 itself.
                condition = SerializedCondition.ContainsMark(
                    tainted = TAINT_MARK,
                    pos = PositionBaseWithModifiers.WithModifiers(Argument(0), listOf(AnyField)),
                ),
                id = RULE_ID,
                meta = SinkMetaData(note = "Taint reaches a field of sink argument"),
            )
        ),
    )

    @Test
    fun `any-field sink unfolds a field across two call frames`() = assertReachable(
        config = config,
        testCls = TEST_CLASS,
        entryPointName = "fieldFlow",
        ruleId = RULE_ID,
        testName = "interprocedural any-field sink",
    )
}
