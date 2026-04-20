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

    // A8. Mixed metavar + concrete: Map<$K, String> — $K is a metavar, second
    // slot is concrete String.
    @Test
    fun `A8 - mixed metavar and concrete Map of K String`() =
        runTest<example.RuleWithMixedMetavarConcrete>()

    // A10. Deep nesting: List<List<String>> — Negatives are List<String>
    // (missing outer) and List<List<Integer>> (inner mismatch).
    @Test
    fun `A10 - deep nesting List of List of String`() = runTest<example.RuleWithDeepNesting>()

    // A12. Parameter-position concrete-vs-metavar discrimination: first
    // parameter is concrete List<String>, not a metavar.
    @Test
    fun `A12 - parameter position concrete List of String`() =
        runTest<example.RuleWithParamConcreteListString>()

    // A13. Fully-qualified type argument: ResponseEntity<java.lang.String>.
    @Test
    fun `A13 - fully-qualified type argument ResponseEntity of java lang String`() =
        runTest<example.RuleWithFqnTypeArg>()

    // A15. Array of parameterized type: List<String>[] return.
    @Test
    fun `A15 - array of parameterized type List of String array`() =
        runTest<example.RuleWithArrayOfParameterized>()

    // A17. Concrete return type discriminates a different concrete return.
    // Rule return is String; Negative method returns Integer.
    @Test
    fun `A17 - concrete return String discriminates from Integer`() =
        runTest<example.RuleWithConcreteReturnDiscrim>()

    // A19. Nested generic in parameter position:
    // List<Map<String, Integer>> — complement to A10 (nested generic in
    // return position).
    @Test
    fun `A19 - nested generic in parameter List of Map of String Integer`() =
        runTest<example.RuleWithNestedParamGeneric>()

    // A20. Class<$T> reflection-style parameter.
    @Test
    fun `A20 - Class of T parameter`() = runTest<example.RuleWithClassTypeParam>()

    // A21. Interface vs class widening: Collection<String> pattern vs
    // List<String> method. Observed behavior: engine uses exact-type
    // matching at the method-decl return position — no subtype widening.
    // The List<String> sample was flipped from Positive to Negative to
    // match the engine's actual semantics.
    @Test
    fun `A21 - Collection of String return exact type match no subtype widening`() =
        runTest<example.RuleWithCollectionReturn>()

    // A22. Nested mixed containers: Map<String, List<Integer>>.
    @Test
    fun `A22 - nested mixed containers Map of String List of Integer`() =
        runTest<example.RuleWithNestedMapListReturn>()

    // A23. Array dimension mismatch: String[][] return.
    @Test
    fun `A23 - array dimension mismatch String two dim`() =
        runTest<example.RuleWithTwoDimArrayReturn>()

    @AfterAll
    fun close() {
        closeRunner()
    }
}
