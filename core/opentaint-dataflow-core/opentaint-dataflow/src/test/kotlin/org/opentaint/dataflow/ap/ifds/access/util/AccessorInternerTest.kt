package org.opentaint.dataflow.ap.ifds.access.util

import org.opentaint.dataflow.ap.ifds.AbstractionAlwaysUnrollNextAccessor
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.TypeInfoAccessor
import org.opentaint.dataflow.ap.ifds.TypeInfoGroupAccessor
import org.opentaint.dataflow.ap.ifds.ValueAccessor
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isAlwaysUnrollNext
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isFieldAccessor
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isStaticAccessor
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isTaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isTypeInfoAccessor
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class AccessorInternerTest {
    private companion object {
        const val RANDOM_SEED = 42L
        const val RANDOM_ACCESSORS_COUNT = 500
        const val MAX_STRING_LEN = 6
    }

    private val singletonAccessors: List<Accessor> = listOf(
        AnyAccessor,
        ElementAccessor,
        FinalAccessor,
        ValueAccessor,
        TypeInfoGroupAccessor,
    )

    private fun randomString(random: Random): String {
        val length = random.nextInt(0, MAX_STRING_LEN + 1)
        return buildString {
            repeat(length) { append('a' + random.nextInt(26)) }
        }
    }

    private fun randomDataAccessor(random: Random): Accessor = when (random.nextInt(4)) {
        0 -> FieldAccessor(randomString(random), randomString(random), randomString(random))
        1 -> ClassStaticAccessor(randomString(random))
        2 -> TaintMarkAccessor(randomString(random))
        else -> TypeInfoAccessor(randomString(random))
    }

    private fun sampleAccessors(): List<Accessor> {
        val random = Random(RANDOM_SEED)
        return singletonAccessors + List(RANDOM_ACCESSORS_COUNT) { randomDataAccessor(random) }
    }

    @Test
    fun `accessor(index(a)) returns the same accessor`() {
        val interner = AccessorInterner()
        for (accessor in sampleAccessors()) {
            val idx = interner.index(accessor)
            assertEquals(accessor, interner.accessor(idx), "Round-trip failed for $accessor")
        }
    }

    @Test
    fun `interner returns the same index for equal accessors`() {
        val interner = AccessorInterner()
        for (accessor in sampleAccessors()) {
            val first = interner.index(accessor)
            val second = interner.index(accessor)
            assertEquals(first, second, "Indices differ between two index() calls for $accessor")
        }
    }

    @Test
    fun `predicates on indices match predicates on accessors`() {
        val interner = AccessorInterner()
        for (accessor in sampleAccessors()) {
            val idx = interner.index(accessor)

            assertEquals(
                accessor is FieldAccessor, idx.isFieldAccessor(),
                "isFieldAccessor mismatch for $accessor",
            )
            assertEquals(
                accessor is ClassStaticAccessor, idx.isStaticAccessor(),
                "isStaticAccessor mismatch for $accessor",
            )
            assertEquals(
                accessor is TaintMarkAccessor, idx.isTaintMarkAccessor(),
                "isTaintMarkAccessor mismatch for $accessor",
            )
            assertEquals(
                accessor is TypeInfoAccessor, idx.isTypeInfoAccessor(),
                "isTypeInfoAccessor mismatch for $accessor",
            )
            assertEquals(
                accessor is AbstractionAlwaysUnrollNextAccessor, idx.isAlwaysUnrollNext(),
                "isAlwaysUnrollNext mismatch for $accessor",
            )
        }
    }
}
