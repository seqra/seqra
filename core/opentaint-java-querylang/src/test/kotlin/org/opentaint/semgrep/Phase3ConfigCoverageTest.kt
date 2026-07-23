package org.opentaint.semgrep

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.opentaint.semgrep.util.SampleBasedTest
import org.opentaint.semgrep.util.TestAnalysisRunner
import kotlin.test.Test

// Coverage for config passthrough entries changed in the redundant-star cleanup
// (Phase 1 folds + Phase 2 collapse removals). configurationRequired = true loads
// the bundled model/java/config; AnyAccessorEnabled mirrors the production unroll.
@TestInstance(PER_CLASS)
class Phase3ConfigCoverageTest : SampleBasedTest(configurationRequired = true) {
    @Test
    fun `stdlib passthrough coverage`() =
        runTest<phase3.StdlibCoverage>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    @AfterAll
    fun close() {
        closeRunner()
    }
}
