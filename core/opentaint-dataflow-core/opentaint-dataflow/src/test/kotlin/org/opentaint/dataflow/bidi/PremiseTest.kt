package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.fixtures.testApManager

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.ApManager
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.ap.InitialAp
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.ap.PremiseSet
import org.opentaint.dataflow.bidi.ap.Tail
import org.opentaint.dataflow.bidi.ap.isFlow
import org.opentaint.dataflow.bidi.ap.members
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PremiseTest {
    private val manager = testApManager(Cancellation())

    private fun initial(argument: Int, tail: Tail = Tail.EXACT): InitialAp =
        manager.initial(AccessPathBase.Argument(argument), null, tail, ExclusionSet.Empty,
            MarkSlot.Concrete(manager.marks.mark("mark-$argument")))

    @Test
    fun `premise collection is nonempty and all zero members give the zero singleton`() {
        assertFails { manager.premiseOf(emptyList()) }
        assertSame(manager.zero, manager.premiseOf(listOf(manager.zero, manager.zero)))
        assertSame(manager.zero, manager.union(manager.zero, manager.zero))
    }

    @Test
    fun `zero and duplicate members disappear beside a nonzero singleton`() {
        val i = initial(0)
        assertSame(i, manager.premiseOf(listOf(manager.zero, i, manager.zero, i)))
        assertSame(i, manager.union(manager.zero, i))
        assertSame(i, manager.union(i, manager.zero))
        assertSame(i, manager.union(i, i))
        assertEquals(1, i.nonZeroCount)
        assertFalse(i.isZero)
    }

    @Test
    fun `several members are sorted deduplicated and interned independent of input order`() {
        val a = initial(0)
        val b = initial(1)
        val c = initial(2)
        val key = manager.premiseOf(listOf(c, a, manager.zero, b, a))
        assertIs<PremiseSet>(key)
        assertEquals(listOf(a, b, c).sortedBy { it.id }, key.members.toList())
        assertEquals(3, key.size)
        assertEquals(3, key.nonZeroCount)
        assertFalse(key.isZero)
        assertSame(key, manager.premiseOf(listOf(b, c, a)))
        for (index in 0 until key.size) assertSame(key.members[index], key.member(index))
        assertFails { key.member(key.size) }
        assertFails { a.member(1) }
    }

    @Test
    fun `union is commutative associative and idempotent by canonical identity`() {
        val a = initial(0)
        val b = initial(1)
        val c = initial(2)
        assertSame(manager.union(a, b), manager.union(b, a))
        assertSame(manager.union(manager.union(a, b), c), manager.union(a, manager.union(b, c)))
        val ab = manager.union(a, b)
        assertSame(ab, manager.union(ab, ab))
        assertSame(ab, manager.union(ab, manager.zero))
        assertSame(manager.premiseOf(listOf(a, b, c)), manager.union(ab, manager.union(b, c)))
    }

    @Test
    fun `multiple members must all have concrete marks`() {
        val flow = manager.initial(AccessPathBase.Argument(0), null, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
        val otherFlow = manager.initial(AccessPathBase.Argument(1), null, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
        val concrete = initial(2)
        assertSame(flow, manager.premiseOf(listOf(flow, manager.zero)))
        assertFails { manager.premiseOf(listOf(flow, concrete)) }
        assertFails { manager.union(flow, concrete) }
        assertFails { manager.premiseOf(listOf(flow, otherFlow)) }
        assertTrue(flow.isFlow)
        assertFalse(concrete.isFlow)
        assertFalse(manager.zero.isFlow)
        assertFalse(manager.union(concrete, initial(3)).isFlow)
    }

    @Test
    fun `premise member arrays cannot change a canonical combination`() {
        val a = initial(0)
        val b = initial(1)
        val c = initial(2)
        val input = mutableListOf(a, b)
        val key = assertIs<PremiseSet>(manager.premiseOf(input))
        input[0] = c
        val exposed = key.members
        exposed[0] = c
        assertEquals(listOf(a, b).sortedBy { it.id }, key.members.toList())
        assertSame(key, manager.premiseOf(listOf(b, a)))
    }
}
