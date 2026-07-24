package org.opentaint.semgrep

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.opentaint.semgrep.util.SampleBasedTest
import org.opentaint.semgrep.util.TestAnalysisRunner
import kotlin.test.Test

// Verifies whether a STARRED source ($*P) propagates through a field-sensitive
// EXTERNAL getter modeled as this.<slot> -> result. This is the mechanism the
// conductor response-source stars ($*UNTRUSTED = restTemplate.exchange(...))
// depend on: if it holds, the missing conductor findings are a MODEL gap
// (okhttp/spring getters unmodeled), not a star-mechanism gap.
// configurationRequired = true loads model/java/config; AnyAccessorEnabled
// mirrors the production unroll.
@TestInstance(PER_CLASS)
class Phase3StarSourceGetterTest : SampleBasedTest(configurationRequired = true) {
    @Test
    fun `star source through field-sensitive getter`() =
        runTest<phase3.CoverageStarSourceGetter>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    @AfterAll
    fun close() {
        closeRunner()
    }
}
