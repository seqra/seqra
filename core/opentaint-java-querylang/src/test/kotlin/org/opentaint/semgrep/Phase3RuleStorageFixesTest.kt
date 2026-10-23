package org.opentaint.semgrep

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.opentaint.semgrep.util.SampleBasedTest
import org.opentaint.semgrep.util.TestAnalysisRunner
import kotlin.test.Test

// Behavioural coverage for the nine taint bugs fixed by removing the generic
// <rule-storage> carrier slot from the Java taint-model config (star-config branch).
// configurationRequired = true loads the bundled model/java/config; AnyAccessorEnabled
// mirrors the production unroll.
@TestInstance(PER_CLASS)
class Phase3RuleStorageFixesTest : SampleBasedTest(configurationRequired = true) {
    @Test
    fun `rule-storage cleanup fixes coverage`() =
        runTest<phase3.CoverageRuleStorageFixes>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    @AfterAll
    fun close() {
        closeRunner()
    }
}
