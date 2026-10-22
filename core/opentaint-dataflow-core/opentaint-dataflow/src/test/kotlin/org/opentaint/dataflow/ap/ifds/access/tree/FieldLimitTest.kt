package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.TypeInfoAccessor
import org.opentaint.dataflow.ap.ifds.TypeInfoGroupAccessor
import org.opentaint.dataflow.ap.ifds.access.AnyAccessorUnrollStrategy
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.RefManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FieldLimitTest {
    private companion object {
        val FIELD_A = FieldAccessor("C", "a", "C")
        val FIELD_B = FieldAccessor("C", "b", "C")
        val FIELD_C = FieldAccessor("C", "c", "C")
        val FIELD_D = FieldAccessor("C", "d", "C")
        val MARK = TaintMarkAccessor("m")
    }

    private object UnrollStrategy : AnyAccessorUnrollStrategy {
        override fun unrollAccessor(accessor: Accessor): Boolean =
            accessor is FieldAccessor || accessor is ElementAccessor
    }

    private val manager = TreeApManager(UnrollStrategy, RefManager(), Cancellation())
    private val base = AccessPathBase.This

    private fun finalFact(vararg accessors: Accessor): AccessTree {
        var fact = manager.createFinalAp(base, ExclusionSet.Empty)
        for (accessor in accessors.reversed()) {
            fact = fact.prependAccessor(accessor)
        }
        return fact as AccessTree
    }

    private fun initialFact(vararg accessors: Accessor): InitialFactAp {
        var fact = manager.mostAbstractInitialAp(base)
        for (accessor in accessors.reversed()) {
            fact = fact.prependAccessor(accessor)
        }
        return fact
    }

    private fun merged(a: AccessTree, b: AccessTree): AccessTree =
        AccessTree(manager, base, a.access.mergeAdd(b.access), a.exclusions)

    private fun FactAp.fieldDepth(): Int =
        (0..32).first { !exceedsFieldLimit(it) }

    @Test
    fun `fields count towards field depth`() {
        assertEquals(0, finalFact().fieldDepth())
        assertEquals(1, finalFact(FIELD_A).fieldDepth())
        assertEquals(3, finalFact(FIELD_A, FIELD_B, FIELD_C).fieldDepth())

        assertEquals(0, initialFact().fieldDepth())
        assertEquals(3, initialFact(FIELD_A, FIELD_B, FIELD_C).fieldDepth())
    }

    @Test
    fun `fact exceeds limit iff field depth is greater than limit`() {
        val fact = finalFact(FIELD_A, FIELD_B, FIELD_C)
        assertTrue(fact.exceedsFieldLimit(2))
        assertFalse(fact.exceedsFieldLimit(3))
        assertFalse(fact.exceedsFieldLimit(4))

        val initial = initialFact(FIELD_A, FIELD_B, FIELD_C)
        assertTrue(initial.exceedsFieldLimit(2))
        assertFalse(initial.exceedsFieldLimit(3))
    }

    @Test
    fun `element accessor counts towards field depth`() {
        assertEquals(2, finalFact(FIELD_A, ElementAccessor).fieldDepth())
        assertEquals(3, finalFact(ElementAccessor, ElementAccessor, FIELD_A).fieldDepth())

        assertEquals(2, initialFact(FIELD_A, ElementAccessor).fieldDepth())
    }

    @Test
    fun `any accessor counts as one`() {
        assertEquals(1, finalFact(AnyAccessor).fieldDepth())
        assertEquals(2, finalFact(FIELD_A, AnyAccessor).fieldDepth())
        assertEquals(2, finalFact(AnyAccessor, AnyAccessor, FIELD_A).fieldDepth())
    }

    @Test
    fun `non field accessors do not count`() {
        assertEquals(0, finalFact(MARK).fieldDepth())
        assertEquals(2, finalFact(FIELD_A, FIELD_B, MARK).fieldDepth())
        assertEquals(1, finalFact(TypeInfoGroupAccessor, TypeInfoAccessor("T"), FIELD_A).fieldDepth())

        assertEquals(0, initialFact(FinalAccessor).fieldDepth())
        assertEquals(1, initialFact(FIELD_A, FinalAccessor).fieldDepth())
        assertEquals(2, initialFact(FIELD_A, FIELD_B, MARK).fieldDepth())
        assertEquals(1, initialFact(TypeInfoGroupAccessor, TypeInfoAccessor("T"), FIELD_A).fieldDepth())
    }

    @Test
    fun `branching tree takes the deepest branch`() {
        val shallow = finalFact(FIELD_A)
        val deep = finalFact(FIELD_B, FIELD_C, FIELD_D)
        val tree = merged(shallow, deep)

        assertEquals(3, tree.fieldDepth())
        assertEquals(3, merged(deep, shallow).fieldDepth())
        assertTrue(tree.exceedsFieldLimit(2))
        assertFalse(tree.exceedsFieldLimit(3))
    }

    @Test
    fun `negative limit is always exceeded`() {
        assertTrue(finalFact().exceedsFieldLimit(-1))
        assertTrue(initialFact().exceedsFieldLimit(-1))
    }
}
