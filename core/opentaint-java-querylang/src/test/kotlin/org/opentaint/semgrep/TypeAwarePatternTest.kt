package org.opentaint.semgrep

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Disabled
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

    @Test
    fun `test generic return type with metavar type arg`() = runTest<example.RuleWithGenericReturnType>()

    // A1. ResponseEntity<byte[]> — array type as a concrete type argument.
    @Test
    fun `A1 - ResponseEntity of byte array return type`() = runTest<example.RuleWithGenericByteArrayReturnType>()

    // A2. ResponseEntity<$T> — metavar type arg resolving to any concrete type,
    // including arrays and the raw form. All three method-decl forms are expected
    // to match.
    @Test
    fun `A2 - ResponseEntity metavar matches parameterized string, byte array, and raw`() =
        runTest<example.RuleWithGenericMetavarArrayArg>()

    // A3. Nested generic: ResponseEntity<List<String>>.
    @Test
    fun `A3 - nested generic ResponseEntity of List of String return type`() =
        runTest<example.RuleWithNestedGenericReturnType>()

    // A4. Two-arg generic: Map<$K, $V>.
    @Test
    fun `A4 - two-arg generic Map of K V in parameter`() = runTest<example.RuleWithTwoArgGeneric>()

    // A5. Wildcard type argument: ResponseEntity<?>. Documents current engine
    // behavior; both concrete and wildcard-typed methods match today.
    @Test
    fun `A5 - wildcard type argument ResponseEntity of question mark`() =
        runTest<example.RuleWithWildcardGeneric>()

    // A6. Raw ResponseEntity in method-decl pattern matches raw, parameterized,
    // and parameterized-with-array — documented current engine behavior.
    @Test
    fun `A6 - raw ResponseEntity method-decl pattern matches raw and parameterized forms`() =
        runTest<example.RuleWithRawResponseEntity>()

    @AfterAll
    fun close() {
        closeRunner()
    }
}
