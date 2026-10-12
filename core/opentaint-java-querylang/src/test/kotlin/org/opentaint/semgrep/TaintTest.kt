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

    @Test
    fun `test complex source-sink`() = runTest<taint.RuleComplexSourceSink>()

    @Test
    fun `test complex source-sink no focus`() = runTest<taint.RuleComplexSourceSinkNoFocus>()

    @Test
    fun `test rule with inside`() = runTest<taint.RuleWithInside>()

    // REPRO: a typed-receiver instance-method pattern-sanitizer
    // `(Box $B).sanitize()` is not honored, so the sanitized value is still
    // reported (the Negative sample is flagged). Mirrors the rules-level FP with
    // `(java.io.File $F).getCanonicalFile()`. Expected: passes once the engine
    // honors typed-receiver instance-method sanitizers.
    @Test
    fun `instance-method typed-receiver sanitizer repro`() = runTest<taint.InstanceSanitizerRepro>()

    @AfterAll
    fun close() {
        closeRunner()
    }
}
