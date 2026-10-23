package org.opentaint.semgrep

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.opentaint.semgrep.util.SampleBasedTest
import org.opentaint.semgrep.util.TestAnalysisRunner
import kotlin.test.Test

// Behavioural taint-isolation coverage for bean classes this branch split into
// per-property vfield slots, but which never got an executable Positive/Negative pair
// proving the split holds (star-config branch). configurationRequired = true loads the
// bundled model/java/config; AnyAccessorEnabled mirrors the production unroll.
@TestInstance(PER_CLASS)
class Phase3BeanIsolationTest : SampleBasedTest(configurationRequired = true) {
    @Test
    fun `bean property isolation coverage`() =
        runTest<phase3.CoverageBeanIsolation>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    @AfterAll
    fun close() {
        closeRunner()
    }
}
