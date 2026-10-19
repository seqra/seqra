package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.bidi.ap.MarkGroupKey
import org.opentaint.dataflow.bidi.ap.RawMarkDeclarations
import org.opentaint.dataflow.bidi.ap.encodeGroup
import org.opentaint.dataflow.jvm.ap.ifds.taint.PrimitiveTaintExt
import org.opentaint.dataflow.jvm.bidi.interp.finalizeMarkDeclarations
import org.opentaint.dataflow.jvm.bidi.interp.mergeRawMarkDeclarations
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class MarkDeclarationFinalizerTest {
    private val suffix = PrimitiveTaintExt.PRIMITIVE_TRACKING_ENABLED_MODE

    @Test fun `explicit imported owner wins after all raw branches merge`() {
        val name = "imported$suffix"
        val owner = MarkGroupKey.Rule("outer#join")
        val reference = RawMarkDeclarations(setOf(name), emptyMap(), emptyMap())
        // A creation claim is a declaration even without a retained serialized use.
        val creation = RawMarkDeclarations(emptySet(), mapOf(name to owner), mapOf(owner to true))
        for (parts in listOf(listOf(reference, creation), listOf(creation, reference))) {
            val raw = mergeRawMarkDeclarations(parts)
            assertEquals(setOf(name), raw.names)
            val result = finalizeMarkDeclarations(raw, Cancellation()).fullKey(name)
            assertEquals(name, result.name)
            assertEquals(encodeGroup(owner, true), result.group)
        }
    }

    @Test fun `plain model names share a uniform immutable group and unknown names fail`() {
        val result = finalizeMarkDeclarations(
            RawMarkDeclarations(linkedSetOf("T", "U"), emptyMap(), emptyMap()), Cancellation())
        assertEquals(encodeGroup(MarkGroupKey.SharedModel, false), result.fullKey("T").group)
        assertEquals(result.fullKey("T").group, result.fullKey("U").group)
        assertFailsWith<IllegalArgumentException> { result.fullKey("not declared") }
    }

    @Test fun `mixed SharedModel or declared rule flags are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            finalizeMarkDeclarations(RawMarkDeclarations(setOf("T", "U$suffix"), emptyMap(), emptyMap()), Cancellation())
        }
        val owner = MarkGroupKey.Rule("outer")
        for ((name, flag) in listOf("T" to true, "U$suffix" to false)) {
            assertFailsWith<IllegalArgumentException> {
                finalizeMarkDeclarations(RawMarkDeclarations(setOf(name), mapOf(name to owner), mapOf(owner to flag)), Cancellation())
            }
        }
    }

    @Test fun `raw conflicting claims fail but repeated equal claims do not`() {
        val owner = MarkGroupKey.Rule("outer")
        val declaration = RawMarkDeclarations(setOf("T"), mapOf("T" to owner), mapOf(owner to false))
        assertEquals(declaration, mergeRawMarkDeclarations(listOf(declaration, declaration)))
        assertFailsWith<IllegalArgumentException> {
            mergeRawMarkDeclarations(listOf(declaration,
                RawMarkDeclarations(emptySet(), mapOf("T" to MarkGroupKey.Model("outer")), emptyMap())))
        }
        assertFailsWith<IllegalArgumentException> {
            mergeRawMarkDeclarations(listOf(declaration,
                RawMarkDeclarations(emptySet(), emptyMap(), mapOf(owner to true))))
        }
    }

    @Test fun `raw merge detaches collections and finalization retains full names`() {
        val names = linkedSetOf("T")
        val owners = linkedMapOf<String, MarkGroupKey>("T" to MarkGroupKey.Rule("R:;%%primitive%%"))
        val flags = linkedMapOf<MarkGroupKey, Boolean>(owners.getValue("T") to false)
        val raw = mergeRawMarkDeclarations(listOf(RawMarkDeclarations(names, owners, flags)))
        val result = finalizeMarkDeclarations(raw, Cancellation())
        names += "late"
        owners.clear()
        flags.clear()
        assertEquals(setOf("T"), raw.names)
        assertEquals("T", result.fullKey("T").name)
        assertEquals(encodeGroup(MarkGroupKey.Rule("R:;%%primitive%%"), false), result.fullKey("T").group)
        assertNotEquals(encodeGroup(MarkGroupKey.Model("R:;%%primitive%%"), false), result.fullKey("T").group)
        assertFailsWith<UnsupportedOperationException> { (raw.names as MutableSet).add("mutation") }
    }

    @Test fun `finalization checks cancellation before publishing a declaration`() {
        val cancellation = Cancellation().also { it.cancel() }
        assertFailsWith<Cancellation.Cancelled> {
            finalizeMarkDeclarations(RawMarkDeclarations(setOf("T"), emptyMap(), emptyMap()), cancellation)
        }
    }
}
