package org.opentaint.semgrep

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.opentaint.semgrep.util.SampleBasedTest
import kotlin.test.Test

@TestInstance(PER_CLASS)
class TaintTest : SampleBasedTest() {
    @Test
    fun `test rule`() = runTest<taint.Rule>()

    @Test
    fun `test rule no focus`() = runTest<taint.RuleNoFocus>()

    @Test
    fun `test rule no meta`() = runTest<taint.RuleNoMeta>()

    @Test
    fun `test with pass`() = runTest<taint.RuleWithPass>()

    @AfterAll
    fun close() {
        closeRunner()
    }
}
