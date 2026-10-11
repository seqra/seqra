package org.opentaint.jvm.sast.dataflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase.Argument
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.PositionModifier
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintAssignAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintCleanAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.configuration.jvm.serialized.SinkMetaData

/**
 * Control-flow examples complementing [CleanerDslAnalysisTest]'s structural matrix.
 *
 * A checkpoint declares the complete set of marks that may survive there. The test DSL emits a
 * separate sink for every mark, so an assertion detects both missing and unexpectedly retained
 * facts.
 */
open class CleanerDslControlFlowAnalysisTest : AnalysisTest() {
    private companion object {
        const val TEST_CLS = "test.samples.CleanerDslControlFlowSample"
        val MARKS = (1..5).map { "m$it" }
    }

    override val sourceFileExtension: String = "java"

    override val analysisUnrollStrategy: AnyAccessorUnrollStrategy = object : AnyAccessorUnrollStrategy {
        override fun unrollAccessor(accessor: Accessor): Boolean =
            accessor is FieldAccessor || accessor is ElementAccessor
    }

    private enum class Reach {
        Plain,
        AnyField,
    }

    private data class Checkpoint(
        val method: String,
        val survivingMarks: Set<String>,
    ) {
        val expectedRuleIds: Set<String>
            get() = survivingMarks.mapTo(hashSetOf()) { ruleId(it) }

        fun ruleId(mark: String): String = "$method-$mark"
    }

    private fun checkpoint(
        method: String,
        vararg survivingMarks: String,
    ) = Checkpoint(method, survivingMarks.toSet())

    private fun positions(
        base: PositionBase,
        reach: Reach,
    ): List<PositionBaseWithModifiers> =
        buildList {
            add(PositionBaseWithModifiers.BaseOnly(base))
            if (reach == Reach.AnyField) {
                add(
                    PositionBaseWithModifiers.WithModifiers(
                        base,
                        listOf(PositionModifier.AnyField),
                    )
                )
            }
        }

    private fun source(
        method: String,
        reach: Reach,
        marks: List<String>,
    ) = SerializedRule.Source(
        function = functionMatcher(TEST_CLS, method),
        taint = marks.flatMap { mark ->
            positions(PositionBase.Result, reach).map { position ->
                SerializedTaintAssignAction(kind = mark, pos = position)
            }
        },
    )

    private fun cleaner(
        method: String,
        reach: Reach,
        marks: List<String>,
    ) = SerializedRule.Cleaner(
        function = functionMatcher(TEST_CLS, method),
        cleans = marks.flatMap { mark ->
            positions(Argument(0), reach).map { position ->
                SerializedTaintCleanAction(taintKind = mark, pos = position)
            }
        },
    )

    private fun sink(
        checkpoint: Checkpoint,
        mark: String,
    ) = SerializedRule.Sink(
        condition = SerializedCondition.or(
            positions(Argument(0), Reach.AnyField).map { position ->
                SerializedCondition.ContainsMark(mark, position)
            }
        ),
        function = functionMatcher(TEST_CLS, checkpoint.method),
        id = checkpoint.ruleId(mark),
        meta = SinkMetaData(note = checkpoint.ruleId(mark)),
    )

    private val sources = MARKS.mapIndexed { index, mark ->
        source("sourceM${index + 1}", Reach.Plain, listOf(mark))
    }

    private val cleaners = listOf(
        cleaner("cleanM1Plain", Reach.Plain, listOf("m1")),
        cleaner("cleanM1Any", Reach.AnyField, listOf("m1")),
        cleaner("cleanM2Any", Reach.AnyField, listOf("m2")),
        cleaner("cleanM3Any", Reach.AnyField, listOf("m3")),
        cleaner("cleanM4Any", Reach.AnyField, listOf("m4")),
        cleaner("cleanM5Any", Reach.AnyField, listOf("m5")),
        cleaner("cleanM12Any", Reach.AnyField, listOf("m1", "m2")),
        cleaner("cleanM34Any", Reach.AnyField, listOf("m3", "m4")),
        cleaner("cleanAllAny", Reach.AnyField, MARKS),
    )

    private fun anyFieldEntryPoint(
        entryPoint: String,
        arguments: List<Int>,
    ) = SerializedRule.EntryPoint(
        function = functionMatcher(TEST_CLS, entryPoint),
        taint = arguments.flatMap { argument ->
            MARKS.flatMap { mark ->
                positions(Argument(argument), Reach.AnyField).map { position ->
                    SerializedTaintAssignAction(kind = mark, pos = position)
                }
            }
        },
    )

    private fun assertScenario(
        entryPoint: String,
        taintedArguments: List<Int>,
        vararg checkpoints: Checkpoint,
    ) {
        val config = SerializedTaintConfig(
            entryPoint = taintedArguments
                .takeIf { it.isNotEmpty() }
                ?.let { listOf(anyFieldEntryPoint(entryPoint, it)) },
            source = sources,
            cleaner = cleaners,
            sink = checkpoints.flatMap { checkpoint ->
                MARKS.map { mark -> sink(checkpoint, mark) }
            },
        )
        val actual = runAnalysis(config, TEST_CLS, entryPoint)
            .mapTo(hashSetOf()) { it.vulnerability.rule.id }
        val expected = checkpoints.flatMapTo(hashSetOf()) { it.expectedRuleIds }

        assertEquals(expected, actual)
    }

    private fun assertSourceScenario(
        entryPoint: String,
        vararg checkpoints: Checkpoint,
    ) = assertScenario(entryPoint, emptyList(), *checkpoints)

    @Test
    fun `marks are accumulated and removed independently in a long sequence`() {
        assertSourceScenario(
            "sequentialMarks",
            checkpoint("sequenceStartSink", "m1", "m2", "m3"),
            checkpoint("sequenceAfterM1Sink", "m2", "m3"),
            checkpoint("sequenceAfterM2Sink", "m3"),
            checkpoint("sequenceAfterM4SourceSink", "m3", "m4"),
            checkpoint("sequenceAfterM3Sink", "m4"),
            checkpoint("sequenceAllCleanSink"),
            checkpoint("sequenceNestedAfterPlainSink", "m1"),
            checkpoint("sequenceNestedAfterAnySink"),
        )
    }

    @Test
    fun `different branch cleaners retain every mark surviving at least one path`() {
        assertScenario(
            "divergentBranches",
            taintedArguments = listOf(0),
            checkpoint("divergentJoinSink", *MARKS.toTypedArray()),
            checkpoint("divergentAfterM5Sink", "m1", "m2", "m3", "m4"),
            checkpoint("convergentJoinSink", "m3", "m4"),
        )
    }

    @Test
    fun `early-return summaries distinguish maybe-cleaned from always-cleaned values`() {
        assertScenario(
            "earlyReturnSummaries",
            taintedArguments = listOf(0, 1),
            checkpoint("maybeCleanReturnSink", *MARKS.toTypedArray()),
            checkpoint("alwaysCleanReturnSink", "m3", "m4", "m5"),
        )
    }

    @Test
    fun `AnyField mark exclusions follow one alias without leaking to sibling facts`() {
        assertScenario(
            "aliasesAndReassignment",
            taintedArguments = listOf(0),
            checkpoint("aliasOriginalSink", *MARKS.toTypedArray()),
            checkpoint("reassignedOldSink", "m3", "m4", "m5"),
            checkpoint("reassignedNewSink", "m1"),
            checkpoint("unsanitizedOriginalSink", *MARKS.toTypedArray()),
            checkpoint("independentReassignmentSink", "m1"),
        )
    }

    @Test
    fun `five cleaners compose through five helper summaries`() {
        assertScenario(
            "deepCleanerPipeline",
            taintedArguments = listOf(0, 1),
            checkpoint("deepPipelineCleanedSink"),
            checkpoint("deepPipelineControlSink", *MARKS.toTypedArray()),
        )
    }

    @Test
    fun `do-while cleaner applies once while a while cleaner may not apply`() {
        assertScenario(
            "doWhileCleaner",
            taintedArguments = listOf(0),
            checkpoint("doWhileSink", "m2", "m3", "m4", "m5"),
        )
        assertScenario(
            "zeroOrMoreCleaner",
            taintedArguments = listOf(0),
            checkpoint("zeroOrMoreSink", *MARKS.toTypedArray()),
        )
    }

    @Test
    fun `cleaners on independent branch values do not cross-contaminate`() {
        assertScenario(
            "independentBranchValues",
            taintedArguments = listOf(0, 1),
            checkpoint("independentLeftJoinSink", *MARKS.toTypedArray()),
            checkpoint("independentRightJoinSink", *MARKS.toTypedArray()),
            checkpoint("independentLeftCleanedSink"),
            checkpoint("independentRightUnchangedSink", *MARKS.toTypedArray()),
        )
    }

    @Test
    fun `a clean does not suppress sources introduced later`() {
        assertSourceScenario(
            "cleanThenRetain",
            checkpoint("cleanBeforeNewSourceSink"),
            checkpoint("newSourceAfterCleanSink", "m2"),
            checkpoint("newSourceCleanedSink"),
        )
    }
}
