package org.opentaint.semgrep

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.opentaint.semgrep.util.SampleBasedTest
import org.opentaint.semgrep.util.TestAnalysisRunner
import kotlin.test.Test

// Coverage for JDK javax.* passthrough config entries (java.naming, java.sql.rowset).
// Each Positive flows taint from a source, through a config passthrough, to a sink.
// configurationRequired = true loads the bundled model/java/config; AnyAccessorEnabled
// mirrors the production unroll, letting whole-object ctor taint flow back through the
// (unmodeled) getEncodedValue JDK bodies.
@TestInstance(PER_CLASS)
class Phase3JavaxCoverageTest : SampleBasedTest(configurationRequired = true) {
    @Test
    fun `javax naming directory passthrough coverage`() =
        runTest<phase3.CoverageNamingDirectory>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    @Test
    fun `javax naming ldap passthrough coverage`() =
        runTest<phase3.CoverageNamingLdap>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    @Test
    fun `javax sql rowset passthrough coverage`() =
        runTest<phase3.CoverageSql>(unrollStrategy = TestAnalysisRunner.AnyAccessorEnabled)

    @AfterAll
    fun close() {
        closeRunner()
    }
}
