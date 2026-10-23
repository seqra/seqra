package org.opentaint.semgrep

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.opentaint.semgrep.util.SampleBasedTest
import org.opentaint.semgrep.util.TestAnalysisRunner
import kotlin.test.Test

// Coverage for JDK stdlib passthrough entries touched by the redundant-star
// cleanup: immutable collection factories, string-builder char[] overloads, and
// String / java.text factory + setter entries. configurationRequired = true loads
// the bundled model/java/config; AnyAccessorEnabled mirrors the production unroll.
@TestInstance(PER_CLASS)
class Phase3CoreCoverageTest : SampleBasedTest(configurationRequired = true) {
    @Test
    fun `collection factory coverage`() =
        runTest<phase3.CoverageCollections>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    @Test
    fun `string builder char array coverage`() =
        runTest<phase3.CoverageStringBuilders>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    @Test
    fun `string and text passthrough coverage`() =
        runTest<phase3.CoverageStrings>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    @AfterAll
    fun close() {
        closeRunner()
    }
}
