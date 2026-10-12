package org.opentaint.semgrep

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.opentaint.semgrep.util.SampleBasedTest
import kotlin.test.Test

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeepFieldStoreFnTest : SampleBasedTest() {
    @Test
    fun `deep field store FN`() = runTest<taint.DeepFieldStoreFn>()

    @AfterAll
    fun close(): Unit = closeRunner()
}
