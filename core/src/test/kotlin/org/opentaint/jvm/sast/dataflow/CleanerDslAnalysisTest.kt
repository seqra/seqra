package org.opentaint.jvm.sast.dataflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy
import org.opentaint.dataflow.bidi.ap.Layer
import org.opentaint.dataflow.bidi.driver.ReportState
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
 * Executable specification of the cleaner DSL.
 *
 * Each matrix run covers every plain/AnyField source, cleaner, and sink combination. It also
 * reaches the value itself, field depths 1..5, and a depth-1 field hidden behind call stacks 1..5.
 * The parameter controls how many independent marks coexist in the facts.
 */
open class CleanerDslAnalysisTest : AnalysisTest() {
    private companion object {
        const val TEST_CLS = "test.samples.CleanerDslSample"
        const val MATRIX_RULE_PREFIX = "cleaner-dsl-matrix"
    }

    override val sourceFileExtension: String = "java"

    override val analysisUnrollStrategy: AnyAccessorUnrollStrategy = object : AnyAccessorUnrollStrategy {
        override fun unrollAccessor(accessor: Accessor): Boolean =
            accessor is FieldAccessor || accessor is ElementAccessor
    }

    private enum class Reach(val methodPart: String) {
        Plain("Plain"),
        AnyField("Any"),
    }

    private data class MatrixCase(
        val source: Reach,
        val cleaner: Reach,
        val sink: Reach,
    ) {
        val sinkMethodPrefix: String
            get() = "sink${source.methodPart}${cleaner.methodPart}${sink.methodPart}"

        val id: String
            get() = "${source.name}-${cleaner.name}-${sink.name}"
    }

    private val matrixCases = Reach.entries.flatMap { source ->
        Reach.entries.flatMap { cleaner ->
            Reach.entries.map { sink -> MatrixCase(source, cleaner, sink) }
        }
    }

    private data class MatrixPoint(
        val methodSuffix: String,
        val id: String,
        val fieldDepth: Int,
    )

    private val matrixPoints =
        (0..5).map { depth ->
            MatrixPoint(methodSuffix = "Depth$depth", id = "field-depth$depth", fieldDepth = depth)
        } +
            (1..5).map { depth ->
                MatrixPoint(methodSuffix = "StackDepth$depth", id = "stack-depth$depth", fieldDepth = 1)
            }

    private fun marks(count: Int): List<String> =
        (1..count).map { "mark$it" }

    private fun positions(base: PositionBase, reach: Reach): List<PositionBaseWithModifiers> =
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

    private fun sourceRule(method: String, reach: Reach, marks: List<String>) =
        SerializedRule.Source(
            function = functionMatcher(TEST_CLS, method),
            taint = marks.flatMap { mark ->
                positions(PositionBase.Result, reach).map { position ->
                    SerializedTaintAssignAction(kind = mark, pos = position)
                }
            }
        )

    private fun anyOnlySourceRule(method: String, mark: String) =
        SerializedRule.Source(
            function = functionMatcher(TEST_CLS, method),
            taint = listOf(
                SerializedTaintAssignAction(
                    kind = mark,
                    pos = PositionBaseWithModifiers.WithModifiers(
                        PositionBase.Result,
                        listOf(PositionModifier.AnyField),
                    ),
                )
            ),
        )

    private fun cleanerRule(
        method: String,
        reach: Reach,
        marks: List<String>,
        cleansResult: Boolean = false,
    ) =
        SerializedRule.Cleaner(
            function = functionMatcher(TEST_CLS, method),
            cleans = marks.flatMap { mark ->
                buildList {
                    addAll(positions(Argument(0), reach))
                    if (cleansResult) addAll(positions(PositionBase.Result, reach))
                }.map { position ->
                    SerializedTaintCleanAction(
                        taintKind = mark,
                        pos = position,
                    )
                }
            }
        )

    private fun sinkRule(
        method: String,
        reach: Reach,
        mark: String,
        ruleId: String,
    ) = SerializedRule.Sink(
        condition = SerializedCondition.or(
            positions(Argument(0), reach).map { position ->
                SerializedCondition.ContainsMark(mark, position)
            }
        ),
        function = functionMatcher(TEST_CLS, method),
        id = ruleId,
        meta = SinkMetaData(note = ruleId),
    )

    private fun matrixRuleId(case: MatrixCase, point: MatrixPoint, mark: String): String =
        "$MATRIX_RULE_PREFIX-${case.id}-${point.id}-$mark"

    private fun matrixEntryPoint(
        markCount: Int,
        sourceReach: Reach,
    ) = SerializedRule.EntryPoint(
        function = functionMatcher(
            TEST_CLS,
            "${sourceReach.methodPart.lowercase()}Marks$markCount",
        ),
        taint = listOf(Argument(0), Argument(1)).flatMap { argument ->
            marks(markCount).flatMap { mark ->
                positions(argument, sourceReach).map { position ->
                    SerializedTaintAssignAction(kind = mark, pos = position)
                }
            }
        },
    )

    private fun matrixConfig(
        markCount: Int,
        sourceReach: Reach,
        cleanersEnabled: Boolean = true,
    ): SerializedTaintConfig {
        val marks = marks(markCount)
        return SerializedTaintConfig(
            entryPoint = listOf(matrixEntryPoint(markCount, sourceReach)),
            cleaner = if (cleanersEnabled) {
                listOf(
                    cleanerRule("applyPlainClean", Reach.Plain, marks),
                    cleanerRule("applyAnyClean", Reach.AnyField, marks),
                )
            } else {
                emptyList()
            },
            sink = matrixCases.filter { it.source == sourceReach }.flatMap { case ->
                matrixPoints.flatMap { point ->
                    marks.map { mark ->
                        sinkRule(
                            method = case.sinkMethodPrefix + point.methodSuffix,
                            reach = case.sink,
                            mark = mark,
                            ruleId = matrixRuleId(case, point, mark),
                        )
                    }
                }
            },
        )
    }

    private fun matrixFindings(
        markCount: Int,
        sourceReach: Reach,
        cleanersEnabled: Boolean = true,
    ): Set<String> =
        runAnalysis(
            config = matrixConfig(markCount, sourceReach, cleanersEnabled),
            entryPointClass = TEST_CLS,
            entryPointMethod = "${sourceReach.methodPart.lowercase()}Marks$markCount",
        ).mapTo(hashSetOf()) { it.vulnerability.rule.id }

    @ParameterizedTest(name = "{0} simultaneous mark(s)")
    @ValueSource(ints = [1, 2, 3, 4, 5])
    fun `plain and AnyField matrix is exact through field and call depths one to five`(markCount: Int) {
        for (sourceReach in Reach.entries) {
            val expected = buildSet {
                for (case in matrixCases.filter { it.source == sourceReach }) {
                    for (point in matrixPoints) {
                        val survives =
                            case.source == Reach.AnyField &&
                                case.cleaner == Reach.Plain &&
                                (case.sink == Reach.AnyField || point.fieldDepth > 0)

                        if (survives) {
                            marks(markCount).mapTo(this) { mark ->
                                matrixRuleId(case, point, mark)
                            }
                        }
                    }
                }
            }

            assertEquals(expected, matrixFindings(markCount, sourceReach))
        }
    }

    @Test
    fun `without cleaners AnyField sources reach both sink forms at every depth`() {
        val findings = matrixFindings(
            markCount = 5,
            sourceReach = Reach.AnyField,
            cleanersEnabled = false,
        )
        val expectedControls = buildSet {
            for (case in matrixCases.filter { it.source == Reach.AnyField }) {
                for (point in matrixPoints) {
                    marks(5).mapTo(this) { mark ->
                        matrixRuleId(case, point, mark)
                    }
                }
            }
        }

        assertEquals(expectedControls, findings)
    }

    @ParameterizedTest(name = "clean {0} of 5 marks")
    @ValueSource(ints = [1, 2, 3, 4, 5])
    fun `AnyField cleaner removes only the selected marks`(cleanCount: Int) {
        val allMarks = marks(5)
        val config = SerializedTaintConfig(
            entryPoint = listOf(
                SerializedRule.EntryPoint(
                    function = functionMatcher(TEST_CLS, "cleanMarks$cleanCount"),
                    taint = allMarks.flatMap { mark ->
                        positions(Argument(0), Reach.AnyField).map { position ->
                            SerializedTaintAssignAction(kind = mark, pos = position)
                        }
                    },
                )
            ),
            cleaner = listOf(
                cleanerRule("applyAnyClean", Reach.AnyField, allMarks.take(cleanCount))
            ),
            sink = allMarks.map { mark ->
                sinkRule(
                    "markSelectiveSink",
                    Reach.AnyField,
                    mark,
                    "mark-selective-$mark",
                )
            },
        )

        val findings = findingIds(config, "cleanMarks$cleanCount")
        val expected = allMarks.drop(cleanCount).mapTo(hashSetOf()) { "mark-selective-$it" }

        assertEquals(expected, findings)
    }

    @Test
    fun `field stores distinguish a plain cleaner from an AnyField cleaner`() {
        val mark = "field-store"
        val config = SerializedTaintConfig(
            source = listOf(sourceRule("sourcePlain", Reach.Plain, listOf(mark))),
            cleaner = listOf(
                cleanerRule("cleanPlain", Reach.Plain, listOf(mark), cleansResult = true),
                cleanerRule("cleanAny", Reach.AnyField, listOf(mark), cleansResult = true),
            ),
            sink = listOf(
                sinkRule("fieldStorePlainSink", Reach.Plain, mark, "field-store-plain"),
                sinkRule("fieldStoreAnySink", Reach.AnyField, mark, "field-store-any"),
                sinkRule("fieldStoreAfterAnyCleanSink", Reach.AnyField, mark, "field-store-cleaned"),
            ),
        )

        val findings = runAnalysis(config, TEST_CLS, "fieldStoreExamples")
            .mapTo(hashSetOf()) { it.vulnerability.rule.id }

        assertEquals(setOf("field-store-any"), findings)
    }

    @Test
    fun `nested helper AnyField cleaner removes a nested AnyField source`() {
        assertHelperCleaned(
            entryPoint = "nestedHelperCleanerExample",
            sink = "nestedHelperAnySink",
            mark = "nested-helper",
            sourceReach = Reach.AnyField,
        )
    }

    @Test
    fun `helper source is cleaned before an AnyField sink`() {
        assertHelperCleaned(
            entryPoint = "helperSourceAndCleanerExample",
            sink = "helperSourceAnySink",
            mark = "helper-source",
            sourceReach = Reach.Plain,
        )
    }

    @Test
    fun `AnyField sink hidden in a helper stays silent after cleaning`() {
        assertHelperCleaned(
            entryPoint = "helperSinkExample",
            sink = "helperSinkAnySink",
            mark = "helper-sink",
            sourceReach = Reach.Plain,
        )
    }

    private fun helperConfig(
        entryPointSink: String,
        mark: String,
        sourceReach: Reach = Reach.AnyField,
        cleanersEnabled: Boolean = true,
    ) = SerializedTaintConfig(
        source = listOf(sourceRule("source${sourceReach.methodPart}", sourceReach, listOf(mark))),
        cleaner = if (cleanersEnabled) {
            listOf(
                cleanerRule(
                    "cleanAny",
                    Reach.AnyField,
                    listOf(mark),
                    cleansResult = true,
                )
            )
        } else {
            emptyList()
        },
        sink = listOf(sinkRule(entryPointSink, Reach.AnyField, mark, "$mark-sink")),
    )

    private fun assertHelperCleaned(
        entryPoint: String,
        sink: String,
        mark: String,
        sourceReach: Reach,
    ) {
        assertEquals(
            emptySet<String>(),
            findingIds(helperConfig(sink, mark, sourceReach), entryPoint),
        )
        assertEquals(
            setOf("$mark-sink"),
            findingIds(
                helperConfig(sink, mark, sourceReach, cleanersEnabled = false),
                entryPoint,
            ),
            "The no-cleaner control must reach the same helper sink",
        )
    }

    private fun findingIds(
        config: SerializedTaintConfig,
        entryPoint: String,
    ): Set<String> =
        runAnalysis(config, TEST_CLS, entryPoint)
            .mapTo(hashSetOf()) { it.vulnerability.rule.id }

    @Test
    fun `branch-specific cleaners do not clean the opposite alternative at a join`() {
        val aMark = "a-mark"
        val xMark = "x-mark"
        val config = SerializedTaintConfig(
            source = listOf(
                sourceRule("sourceA", Reach.AnyField, listOf(aMark)),
                sourceRule("sourceX", Reach.AnyField, listOf(xMark)),
            ),
            cleaner = listOf(
                cleanerRule("cleanA", Reach.AnyField, listOf(aMark), cleansResult = true),
                cleanerRule("cleanX", Reach.AnyField, listOf(xMark), cleansResult = true),
            ),
            sink = listOf(
                sinkRule("sinkA", Reach.AnyField, aMark, "conditional-a"),
                sinkRule("sinkX", Reach.AnyField, xMark, "conditional-x"),
            ),
        )

        val findings = runAnalysis(config, TEST_CLS, "conditionalExample")
            .mapTo(hashSetOf()) { it.vulnerability.rule.id }

        assertEquals(setOf("conditional-a", "conditional-x"), findings)
    }

    @Test
    fun `returning exact cleaner follows AnyField and preserves unrelated marks`() {
        val cleanedMark = "cleaned"
        val unrelatedMark = "unrelated"
        val config = SerializedTaintConfig(
            entryPoint = listOf(
                SerializedRule.EntryPoint(
                    function = functionMatcher(TEST_CLS, "returningPlainCleaner"),
                    taint = listOf(cleanedMark, unrelatedMark).flatMap { mark ->
                        positions(Argument(0), Reach.AnyField).map { position ->
                            SerializedTaintAssignAction(kind = mark, pos = position)
                        }
                    },
                )
            ),
            cleaner = listOf(
                cleanerRule(
                    "cleanPlain",
                    Reach.Plain,
                    listOf(cleanedMark),
                    cleansResult = true,
                )
            ),
            sink = listOf(
                sinkRule(
                    "returningPlainSink",
                    Reach.AnyField,
                    cleanedMark,
                    "returning-cleaned-any",
                ),
                sinkRule(
                    "returningPlainSink",
                    Reach.Plain,
                    unrelatedMark,
                    "returning-unrelated-exact",
                ),
            ),
        )

        if (useBidiEntry) {
            val result = runBidiAcceptance(config, TEST_CLS, "returningPlainCleaner", bidiLimits)
            assertEquals(mapOf(
                "returning-unrelated-exact" to ReportState.DEMAND,
                "returning-cleaned-any" to ReportState.DEMAND,
            ), result.report.entries.associate { it.key.rule.id to it.state })
            assertEquals(setOf("returning-unrelated-exact", "returning-cleaned-any"),
                result.output.mapTo(linkedSetOf()) { it.vulnerability.ruleId })
            assertTrue(result.witnesses.all { !it.confirmed },
                "Both actual findings remain unproved candidates under the minimal policy")
            assertTrue(result.witnesses.any { it.rule.id == "returning-unrelated-exact" &&
                it.literals.isNotEmpty() && it.literals.all { literal -> literal.layer == Layer.NORMAL } },
                "The unrelated mark still has observed NORMAL literal support")
        } else {
            assertEquals(
                setOf("returning-unrelated-exact"),
                findingIds(config, "returningPlainCleaner"),
            )
        }
    }

    @Test
    fun `result AnyField cleaner does not abort an unrelated fact`() {
        val cleanedMark = "absent"
        val unrelatedMark = "unrelated"
        val config = SerializedTaintConfig(
            entryPoint = listOf(
                SerializedRule.EntryPoint(
                    function = functionMatcher(TEST_CLS, "returningAnyCleaner"),
                    taint = positions(Argument(0), Reach.AnyField).map { position ->
                        SerializedTaintAssignAction(kind = unrelatedMark, pos = position)
                    },
                )
            ),
            cleaner = listOf(
                cleanerRule(
                    "cleanAny",
                    Reach.AnyField,
                    listOf(cleanedMark),
                    cleansResult = true,
                )
            ),
            sink = listOf(
                sinkRule(
                    "returningAnySink",
                    Reach.Plain,
                    unrelatedMark,
                    "returning-any-unrelated",
                )
            ),
        )

        assertEquals(
            setOf("returning-any-unrelated"),
            findingIds(config, "returningAnyCleaner"),
        )
    }

    @Test
    fun `AnyField-only source matches the root and materialized child`() {
        val mark = "any-only"
        val config = SerializedTaintConfig(
            source = listOf(anyOnlySourceRule("sourceAnyOnly", mark)),
            sink = listOf(
                sinkRule("anyOnlyRootSink", Reach.AnyField, mark, "any-only-root"),
                sinkRule("anyOnlyChildSink", Reach.Plain, mark, "any-only-child"),
            ),
        )

        assertEquals(
            setOf("any-only-root", "any-only-child"),
            findingIds(config, "anyOnlySourceExample"),
        )
    }

    @Test
    fun `AnyField-only source survives a recursive field store`() {
        val mark = "recursive-any-only"
        val config = SerializedTaintConfig(
            source = listOf(anyOnlySourceRule("sourceAnyOnly", mark)),
            sink = listOf(
                sinkRule(
                    "recursiveAnyOnlyRootSink",
                    Reach.AnyField,
                    mark,
                    "recursive-any-only-root",
                ),
                sinkRule(
                    "recursiveAnyOnlyChildSink",
                    Reach.AnyField,
                    mark,
                    "recursive-any-only-child",
                ),
                sinkRule(
                    "recursiveAnyOnlyDepth2Sink",
                    Reach.Plain,
                    mark,
                    "recursive-any-only-depth2",
                ),
            ),
        )

        assertEquals(
            setOf(
                "recursive-any-only-root",
                "recursive-any-only-child",
                "recursive-any-only-depth2",
            ),
            findingIds(config, "recursiveAnyOnlyStoreExample"),
        )
    }
}
