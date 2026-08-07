package org.opentaint.semgrep

import org.opentaint.dataflow.configuration.ConfigurationLoader
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.PositionModifier
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the $*VAR star-operator serialization footgun: ContainsMarkOnAnyField is
 * field-shape-identical to ContainsMark, so before it got a distinct discriminating
 * key a YAML round-trip silently demoted it to plain ContainsMark, losing the
 * any-field/field-sensitive semantics.
 */
class SerializedConditionRoundTripTest {
    private val yaml = ConfigurationLoader.yaml

    private val pos = PositionBaseWithModifiers.WithModifiers(
        base = PositionBase.Argument(0),
        modifiers = listOf(PositionModifier.AnyField),
    )

    @Test
    fun `ContainsMarkOnAnyField survives a YAML round-trip`() {
        val original: SerializedCondition = SerializedCondition.ContainsMark(
            tainted = "untrusted",
            pos = pos,
        )

        val encoded = yaml.encodeToString(SerializedCondition.serializer(), original)
        val decoded = yaml.decodeFromString(SerializedCondition.serializer(), encoded)

        assertTrue(
            decoded is SerializedCondition.ContainsMark,
            "round-trip must preserve ContainsMarkOnAnyField, got ${decoded::class.simpleName}; yaml=\n$encoded",
        )
        assertEquals(original, decoded)
    }

    @Test
    fun `ContainsMark still round-trips as ContainsMark`() {
        val original: SerializedCondition = SerializedCondition.ContainsMark(
            tainted = "untrusted",
            pos = pos,
        )

        val encoded = yaml.encodeToString(SerializedCondition.serializer(), original)
        val decoded = yaml.decodeFromString(SerializedCondition.serializer(), encoded)

        assertTrue(
            decoded is SerializedCondition.ContainsMark,
            "round-trip must preserve ContainsMark, got ${decoded::class.simpleName}; yaml=\n$encoded",
        )
        assertEquals(original, decoded)
    }
}
