package org.opentaint.jvm.sast.dataflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.jvm.Argument
import org.opentaint.dataflow.configuration.jvm.ContainsMark
import org.opentaint.dataflow.configuration.jvm.RemoveMark
import org.opentaint.dataflow.configuration.jvm.TaintCleaner
import org.opentaint.dataflow.configuration.jvm.TaintMark
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.PositionModifier
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintCleanAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.jvm.sast.dataflow.rules.TaintConfiguration

/**
 * Pins the resolver's cleaner-splitting and condition-normalisation behaviour: a
 * `SerializedRule.Cleaner` with several `cleans` actions resolves into one `TaintCleaner` per
 * action, with that action's own `RemoveMark` assumed true (any-field clean additionally assumes
 * the any-field containment) in the rule's NNF condition before grouping. Equal resulting
 * conditions are joined, and a condition the assumption forces false drops the cleaner entirely,
 * since removing an absent mark is a no-op.
 */
class CleanerNormalisationTest : AnalysisTest() {

    private companion object {
        const val TEST_CLS = "test.samples.DeepCleanSummarySample"
    }

    override val sourceFileExtension: String = "java"

    private fun resolveCleaners(rule: SerializedRule.Cleaner, methodName: String): List<TaintCleaner> {
        val method = findMethod(TEST_CLS, methodName)
        val config = TaintConfiguration(cp)
        config.loadConfig(SerializedTaintConfig(cleaner = listOf(rule)))
        return config.cleanerForMethod(method, allRelevant = false)
    }

    @Test
    fun `cleaner condition that equals its own action's mark becomes True`() {
        val rule = SerializedRule.Cleaner(
            function = functionMatcher(TEST_CLS, "clean"),
            condition = SerializedCondition.ContainsMark(
                "M", PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(0))
            ),
            cleans = listOf(
                SerializedTaintCleanAction(
                    taintKind = "M",
                    pos = PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(0))
                )
            )
        )

        val cleaners = resolveCleaners(rule, "clean")

        assertEquals(1, cleaners.size)
        assertEquals(CommonCondition.True, cleaners.single().condition)
    }

    @Test
    fun `cleaner with two actions splits into one cleaner per action`() {
        val rule = SerializedRule.Cleaner(
            function = functionMatcher(TEST_CLS, "wrapConditional"),
            condition = SerializedCondition.and(
                listOf(
                    SerializedCondition.ContainsMark(
                        "M", PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(0))
                    ),
                    SerializedCondition.ContainsMark(
                        "N", PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(1))
                    ),
                )
            ),
            cleans = listOf(
                SerializedTaintCleanAction(
                    taintKind = "M",
                    pos = PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(0))
                ),
                SerializedTaintCleanAction(
                    taintKind = "N",
                    pos = PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(1))
                ),
            )
        )

        val cleaners = resolveCleaners(rule, "wrapConditional")

        assertEquals(2, cleaners.size)

        val markM = TaintMark("M")
        val markN = TaintMark("N")

        val cleanerOfM = cleaners.single { it.actionsAfter == listOf(RemoveMark(markM, Argument(0))) }
        val cleanerOfN = cleaners.single { it.actionsAfter == listOf(RemoveMark(markN, Argument(1))) }

        assertEquals(CommonCondition.Atom(ContainsMark(Argument(1), markN)), cleanerOfM.condition)
        assertEquals(CommonCondition.Atom(ContainsMark(Argument(0), markM)), cleanerOfN.condition)
    }

    @Test
    fun `negated condition on the removed mark drops the cleaner entirely`() {
        val rule = SerializedRule.Cleaner(
            function = functionMatcher(TEST_CLS, "clean"),
            condition = SerializedCondition.not(
                SerializedCondition.ContainsMark(
                    "M", PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(0))
                )
            ),
            cleans = listOf(
                SerializedTaintCleanAction(
                    taintKind = "M",
                    pos = PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(0))
                )
            )
        )

        val cleaners = resolveCleaners(rule, "clean")

        assertTrue(cleaners.isEmpty())
    }

    @Test
    fun `any-field action assumes the any-field containment condition True`() {
        val rule = SerializedRule.Cleaner(
            function = functionMatcher(TEST_CLS, "clean"),
            condition = SerializedCondition.ContainsMark(
                "M",
                PositionBaseWithModifiers.WithModifiers(PositionBase.Argument(0), listOf(PositionModifier.AnyField))
            ),
            cleans = listOf(
                SerializedTaintCleanAction(
                    taintKind = "M",
                    pos = PositionBaseWithModifiers.WithModifiers(
                        PositionBase.Argument(0), listOf(PositionModifier.AnyField)
                    )
                )
            )
        )

        val cleaners = resolveCleaners(rule, "clean")

        assertEquals(1, cleaners.size)
        assertEquals(CommonCondition.True, cleaners.single().condition)
    }

    @Test
    fun `unrelated condition is left unchanged`() {
        val rule = SerializedRule.Cleaner(
            function = functionMatcher(TEST_CLS, "wrapConditional"),
            condition = SerializedCondition.ContainsMark(
                "N", PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(1))
            ),
            cleans = listOf(
                SerializedTaintCleanAction(
                    taintKind = "M",
                    pos = PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(0))
                )
            )
        )

        val cleaners = resolveCleaners(rule, "wrapConditional")

        assertEquals(1, cleaners.size)
        assertEquals(
            CommonCondition.Atom(ContainsMark(Argument(1), TaintMark("N"))),
            cleaners.single().condition
        )
    }
}
