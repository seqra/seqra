package org.opentaint.semgrep

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.opentaint.semgrep.util.SampleBasedTest
import kotlin.test.Test

@TestInstance(PER_CLASS)
class ExampleTest : SampleBasedTest() {
    @Test
    fun `test rule`() = runTest<example.Rule>()

    @Test
    fun `test rule with pattern-inside`() = runTest<example.RuleWithPatternInside>()

    @Test
    fun `test rule with allowed constant`() = runTest<example.RuleWithAllowedConstant>()

    @Test
    fun `test rule with signature`() = runTest<example.RuleWithSignature>()

    @Test
    fun `test rule with pattern-not-inside prefix`() = runTest<example.RuleWithNotInsidePrefix>()

    @Test
    @Disabled // todo: loop assign vars
    fun `test rule with pattern-not-inside suffix`() = runTest<example.RuleWithNotInsideSuffix>()

    @Test
    fun `test rule pattern-not with signature`() = runTest<example.RulePatternNotWithSignature>()

    @Test
    fun `test rule with real pattern-inside sequence`() = runTest<example.RuleWithRealInsideSequence>()

    @Test
    fun `test rule with artificial pattern-inside sequence`() = runTest<example.RuleWithArtificialInsideSequence>()

    @Test
    @Disabled // todo: loop assign vars
    fun `test rule with artificial reverse pattern-inside sequence`() = runTest<example.RuleWithArtificialInsideSequenceReverse>()

    @Test
    fun `test simple pass`() = runTest<example.RuleWithSimplePass>()

    @Test
    @Disabled // todo: loop assign vars
    fun `test rule with several suffix cleaners`() = runTest<example.RuleWithSeveralSuffixCleaners>()

    @Test
    fun `test rule cookie`() = runTest<example.RuleCookie>()

    @Test
    fun `test rule with static field`() = runTest<example.RuleWithStaticField>()

    @AfterAll
    fun close(){
        closeRunner()
    }
}
