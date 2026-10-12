package org.opentaint.jvm.sast.dataflow

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.opentaint.dataflow.configuration.TaintCleanReach
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Argument
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.PositionModifier
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintAssignAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintCleanAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig

/** The cleaned field must stay tainted when the cleaner requires an absent root mark. */
@EnabledIfEnvironmentVariable(named = "OPENTAINT_TEST_BIDI", matches = "true")
class CleanerNormalisationReachTest : AnalysisTest() {
    override val sourceFileExtension: String = "java"

    private val sample = "test.samples.DeepCleanSummarySample"
    private val entry = "helperCleanReadFlow"
    private val mark = "normalisation-reach"
    private val ruleId = "normalisation-reach-sink"
    private val root = PositionBaseWithModifiers.BaseOnly(Argument(0))
    private val field = PositionBaseWithModifiers.WithModifiers(Argument(0),
        listOf(PositionModifier.Field("test.samples.DeepCleanSummarySample\$Box", "f", "java.lang.String")))

    private fun config(reach: TaintCleanReach, markRoot: Boolean): SerializedTaintConfig = SerializedTaintConfig(
        entryPoint = listOf(SerializedRule.EntryPoint(
            function = functionMatcher(sample, entry),
            taint = buildList {
                add(SerializedTaintAssignAction(mark, pos = field))
                if (markRoot) add(SerializedTaintAssignAction(mark, pos = root))
            })),
        cleaner = listOf(SerializedRule.Cleaner(
            function = functionMatcher(sample, "clean"),
            condition = SerializedCondition.ContainsMark(mark, root),
            cleans = listOf(SerializedTaintCleanAction(mark, root, reach)))),
        sink = listOf(sinkRule(sample, "sink", ruleId, listOf(Argument(0) to mark))),
    )

    @Test
    fun `exact self-conditioned removal keeps the field mark`() {
        assertReachable(config(TaintCleanReach.Exact, markRoot = false), sample, entry, ruleId,
            "Exact root removal must retain the field mark")
    }

    @Test
    fun `broad removal with an absent root mark keeps the field mark`() {
        assertReachable(config(TaintCleanReach.ExactAndAnyField, markRoot = false), sample, entry, ruleId,
            "A false root condition must retain the field mark")
    }

    @Test
    fun `unconditional broad removal clears the field mark`(): Unit {
        val conditional = config(TaintCleanReach.ExactAndAnyField, markRoot = true)
        val unconditional = conditional.copy(cleaner = conditional.cleaner.orEmpty().map { it.copy(condition = null) })
        assertNotReachable(unconditional, sample, entry, "Broad removal control must clear the field mark")
    }
}
