package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.SourceHitStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceHitStoreTest {
    private val fixture = StoreFixtures()
    private val f = fixture.field("f")
    private val g = fixture.field("g")
    private val t = fixture.mark("T")
    private val u = fixture.mark("U")
    private fun source(path: List<Int> = listOf(f), tail: Tail = Tail.ANY_TAINT, mark: TaintMark = t) = PathEdge(
        PathFact(AccessPathBase.Zero, emptyList(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO)),
        PathFact(AccessPathBase.Return, path, tail, MarkSlot.Concrete(mark)), ExclusionSet.Empty)

    @Test
    fun `equal forward source edges deduplicate even after one has a compiled cache`(): Unit {
        val store = SourceHitStore()
        val first = source()
        val equal = source(listOf(f))
        assertNull(first.compiled)
        fixture.ops.compiled(first)
        assertNotNull(first.compiled)
        assertNull(equal.compiled)
        assertEquals(first, equal)
        assertEquals(first.hashCode(), equal.hashCode())
        assertTrue(store.add(fixture.method, fixture.inst(10), first))
        assertFalse(store.add(fixture.key(), fixture.inst(10), equal))
        assertFalse(store.add(fixture.method, fixture.inst(10), first.copy()))
        assertEquals(setOf(Triple(fixture.method, fixture.inst(10), first)), store.entries().toSet())
    }

    @Test
    fun `source hit identity retains method context entry statement and the complete forward edge`(): Unit {
        val store = SourceHitStore()
        val edge = source()
        val entries = listOf(
            Triple(fixture.method, fixture.inst(10), edge),
            Triple(fixture.key("other-context"), fixture.inst(10), edge),
            Triple(fixture.key(entry = 1), fixture.inst(10), edge),
            Triple(fixture.method, fixture.inst(11), edge),
            Triple(fixture.method, fixture.inst(10), source(listOf(g))),
            Triple(fixture.method, fixture.inst(10), source(tail = Tail.EXACT)),
            Triple(fixture.method, fixture.inst(10), source(mark = u)),
        )
        assertTrue(store.entries().none())
        for ((method, statement, forward) in entries) assertTrue(store.add(method, statement, forward))
        assertEquals(entries.toSet(), store.entries().toSet())
        for ((method, statement, forward) in entries.reversed()) assertFalse(store.add(method, statement, forward.copy()))
        assertEquals(entries.size, store.entries().count())
    }

    @Test
    fun `source seed identity is stable in independent backward run stores`(): Unit {
        val first = SourceHitStore()
        val second = SourceHitStore()
        val edge = source()
        assertTrue(first.add(fixture.method, fixture.inst(10), edge))
        assertTrue(second.add(fixture.key(), fixture.inst(10), source()))
        assertEquals(first.entries().toSet(), second.entries().toSet())
    }
}
