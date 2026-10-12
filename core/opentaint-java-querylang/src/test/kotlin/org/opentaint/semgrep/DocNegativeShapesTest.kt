package org.opentaint.semgrep

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.opentaint.semgrep.util.SampleBasedTest
import kotlin.test.Test

/**
 * Pins negative-clause exclusion behavior.
 *
 * The open defect: when the tracked value's declared type is
 * `java.lang.Object`, a structural negative that observes the value does not
 * exclude the match. `ObjectTypedValueDoc` and `ObjectTypedValueCastDoc`
 * assert the desired semantics and stay red until it is fixed.
 *
 * Everything else passes and isolates the defect: the same rule shapes with a
 * declared type (String, a custom class) exclude correctly, the excluded
 * call's own parameter type is irrelevant, and an Object-typed value is
 * excluded correctly when the negative rebinds it through a receiver call.
 */
@TestInstance(PER_CLASS)
class DocNegativeShapesTest : SampleBasedTest() {

    // --- the defect ---

    @Test
    fun `test Object-typed value is not excluded`() = runTest<example.ObjectTypedValueDoc>()

    @Test
    fun `test Object-typed value is not excluded through a cast`() =
        runTest<example.ObjectTypedValueCastDoc>()

    // --- controls isolating it ---

    @Test
    fun `test declared-type value is excluded`() = runTest<example.TypedValueControlDoc>()

    @Test
    fun `test excluded call parameter type is irrelevant`() =
        runTest<example.ObjectParameterControlDoc>()

    @Test
    fun `test Object-typed value is excluded when the negative rebinds it`() =
        runTest<example.ObjectTypedValueReceiverDoc>()

    // --- clause shapes, all with declared-type values ---

    @Test
    fun `test full-form pattern-not with an observing event`() =
        runTest<example.ArgFullPatternNotDoc>()

    @Test
    fun `test leading-ellipsis pattern-not with an observing event`() =
        runTest<example.ArgObserverPatternNotDoc>()

    @Test
    fun `test pattern-not with a self-sanitizing reassignment`() =
        runTest<example.SanitizePatternNotDoc>()

    @Test
    fun `test pattern-not with a receiver-call reassignment`() =
        runTest<example.ReceiverSanitizePatternNotDoc>()

    @Test
    fun `test pattern-not-inside with an observing event`() =
        runTest<example.ArgNotInsideAnchoredDoc>()

    @Test
    fun `test pattern-not-inside with a self-sanitizing reassignment`() =
        runTest<example.SanitizeNotInsideDoc>()

    @Test
    fun `test pattern-not-inside excluding a configured receiver`() =
        runTest<example.AllowlistNotInsideDoc>()

    // --- positive controls: the excluded events match when required ---

    @Test
    fun `test observing event matches positively`() = runTest<example.ArgEventSanityDoc>()

    @Test
    fun `test reassignment event matches positively`() =
        runTest<example.SanitizeEventSanityDoc>()

    @AfterAll
    fun close() {
        closeRunner()
    }
}
