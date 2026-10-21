package org.opentaint.semgrep

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.opentaint.dataflow.bidi.driver.FixedLimits
import org.opentaint.semgrep.util.SampleBasedTest
import kotlin.test.Test

/** Additive phase-3 acceptance. ExampleTest still uses its null/legacy default. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BidiExampleTest : SampleBasedTest(bidiPolicy = FixedLimits(listOf(1, 1, 2))) {
    @Test
    fun `ND pass joins both source arguments at the same call through copied summaries`(): Unit =
        runTest<example.NDRule>(EXPECT_STATE_VAR)

    @Test
    fun `multiple patterns keep their unification and negative sample controls`(): Unit =
        runTest<example.RuleWithMultiplePatternsUnification>(EXPECT_STATE_VAR)

    @Test
    fun `ellipsis unification retains the common conjunctive path`(): Unit =
        runTest<example.RuleWithMultiplePatternsEllipsisUnification>(EXPECT_STATE_VAR)

    @Test
    fun `matching and taint join alternatives preserve their individual positive and negative cases`(): Unit =
        runTest<example.JoinWithTaintAndMatchingLeft>()

    @Test
    fun `tag-expanded source and sink alternatives keep the union and clean negative controls`(): Unit =
        runTest<example.JoinTagUnion>()

    @AfterAll
    fun close(): Unit = closeRunner()
}
