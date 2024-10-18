package org.opentaint.ir.impl

import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.opentaint.ir.impl.fs.asByteCodeLocation
import org.opentaint.ir.impl.index.Hierarchy
import org.opentaint.ir.impl.index.HierarchyIndex
import org.opentaint.ir.impl.index.InMemoryGlobalIdsStore
import org.opentaint.ir.impl.index.ReversedUsageIndex
import org.opentaint.ir.impl.index.ReversedUsages
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class IndexSerializationTest : LibrariesMixin {

    val globalIds = InMemoryGlobalIdsStore()

    @Test
    fun `hierarchy index serialization`() {
        val location = guavaLib.asByteCodeLocation()
        val index = HierarchyIndex(
            globalIds, location, mapOf(
                1 to persistentSetOf(1, 3, 4),
                2 to persistentSetOf(7, 8),
            )
        )
        val out = ByteArrayOutputStream()
        Hierarchy.serialize(index, out)

        val input = ByteArrayInputStream(out.toByteArray())
        val result = Hierarchy.deserialize(globalIds, location, input)
        with(result.parentToSubClasses) {
            assertEquals(2, size)
            assertEquals(sortedSetOf(1, 3, 4), get(1)?.toSortedSet())
            assertEquals(sortedSetOf(7, 8), get(2)?.toSortedSet())
        }
    }

    @Test
    fun `reversed usages index serialization`() {
        val location = guavaLib.asByteCodeLocation()
        val index = ReversedUsageIndex(
            location,
            globalIds,
            fieldsUsages = mapOf(
                1 to persistentSetOf(1, 3, 4),
                2 to persistentSetOf(7, 8),
            ).toImmutableMap(),
            methodsUsages = mapOf(
                11 to persistentSetOf(11, 13, 14),
                12 to persistentSetOf(17, 18),
            ).toImmutableMap(),

            )
        val out = ByteArrayOutputStream()
        ReversedUsages.serialize(index, out)

        val input = ByteArrayInputStream(out.toByteArray())
        val result = ReversedUsages.deserialize(globalIds, location, input)
        with(result.methodsUsages) {
            assertEquals(2, size)
            assertEquals(sortedSetOf(11, 13, 14), get(11)?.toSortedSet())
            assertEquals(sortedSetOf(17, 18), get(12)?.toSortedSet())
        }

        with(result.fieldsUsages) {
            assertEquals(2, size)
            assertEquals(sortedSetOf(1, 3, 4), get(1)?.toSortedSet())
            assertEquals(sortedSetOf(7, 8), get(2)?.toSortedSet())
        }
    }
}