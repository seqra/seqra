package org.opentaint.semgrep

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.opentaint.semgrep.util.SampleBasedTest
import kotlin.test.Test

@TestInstance(PER_CLASS)
class TypeAwarePatternTest : SampleBasedTest() {
    @Test
    fun `test generic type args in method parameter`() = runTest<example.RuleWithGenericTypeArgs>(EXPECT_STATE_VAR)

    @Test
    fun `test array return type matching`() = runTest<example.RuleWithArrayReturnType>()

    @Test
    fun `test concrete return type matching`() = runTest<example.RuleWithConcreteReturnType>()

    @AfterAll
    fun close() {
        closeRunner()
    }
}
