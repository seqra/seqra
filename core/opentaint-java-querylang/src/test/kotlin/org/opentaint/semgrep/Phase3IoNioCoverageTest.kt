package org.opentaint.semgrep

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.opentaint.semgrep.util.SampleBasedTest
import org.opentaint.semgrep.util.TestAnalysisRunner
import kotlin.test.Test

// Coverage for JDK stdlib passthrough entries touched by the redundant-star
// cleanup in java-io / java-nio / java-security / java-util-stream. Each Positive
// flows taint through a changed config entry to a sink; a Positive turning red
// means the config change dropped a real flow. configurationRequired = true loads
// the bundled model/java/config; AnyAccessorEnabled mirrors the production unroll.
@TestInstance(PER_CLASS)
class Phase3IoNioCoverageTest : SampleBasedTest(configurationRequired = true) {
    @Test
    fun `io nio stream passthrough coverage`() =
        runTest<phase3.CoverageStreams>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    @Test
    fun `security passthrough coverage`() =
        runTest<phase3.CoverageSecurity>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    @Test
    fun `nio buffer coverage`() =
        runTest<phase3.CoverageBuffers>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    @AfterAll
    fun close() {
        closeRunner()
    }
}
