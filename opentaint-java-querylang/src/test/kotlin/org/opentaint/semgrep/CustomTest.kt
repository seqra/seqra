package org.opentaint.semgrep

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.opentaint.semgrep.util.SampleBasedTest
import kotlin.test.Test

@TestInstance(PER_CLASS)
class CustomTest : SampleBasedTest() {
    @Test
    fun `test simplified rule`() = runTest("custom/springPathTraversal1")
    @Test
    @Disabled
    fun `test simplified rule with File creation`() = runTest("custom/springPathTraversal2")
    @Test
    @Disabled
    fun `test origin path-traversal rule`() = runTest("custom/springPathTraversalOrigin")

    @AfterAll
    fun close(){
        closeRunner()
    }
}
