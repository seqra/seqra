package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import kotlin.test.*

class ConcreteStaticEntriesTest {
    @Test fun `carrier keeps both initial kinds and diagonal field mark pairs`() {
        val f = StoreFixtures()
        val codec = ConcreteStaticEntries(f.ops)
        val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
        val left = f.field("left")
        val right = f.field("right")
        val t = f.mark("T")
        val u = f.mark("U")
        fun initial(field: Int, mark: TaintMark, tail: Tail) = f.manager.initial(
            f.pattern(STATIC, listOf(cls, field), tail, MarkSlot.Concrete(mark)))
        val expected = listOf(initial(left, t, Tail.EXACT), initial(left, t, Tail.STAR), initial(right, u, Tail.STAR))
        var entries: TaintTree? = null
        for (initial in expected) entries = codec.merge(entries, codec.single(initial)).first
        val packed = assertNotNull(entries)
        assertEquals(expected.toSet(), codec.originals(packed).toSet())
        assertNull(codec.merge(packed, packed).second)
        for (initial in expected) {
            val decoded = codec.current(initial)
            assertEquals(f.ops.startFact(initial).withLayer(Layer.DEMAND), decoded)
            assertTrue(codec.canonical(initial, decoded))
        }
        assertFalse(codec.originals(packed).any { it.pathData[1] == right && it.mark == MarkSlot.Concrete(t) })
        assertFalse(codec.canonical(expected.first(), codec.current(expected.last())))
        assertFalse(codec.canonical(f.manager.premiseOf(expected.take(2)), codec.current(expected.first())))
    }

    @Test fun `exact concrete identity compacts but STAR to ANY does not`() {
        val f = StoreFixtures()
        val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
        val field = f.field("f")
        val mark = MarkSlot.Concrete(f.mark("T"))
        val exact = f.manager.initial(f.pattern(STATIC, listOf(cls, field), Tail.EXACT, mark))
        val chain = f.manager.initial(f.pattern(STATIC, listOf(cls, field), Tail.STAR, mark))
        assertNull(compactRun1StaticIdentity(f.ops, exact, f.ops.startFact(exact).withLayer(Layer.DEMAND)))
        val lostSuffix = f.ops.startFact(chain).withLayer(Layer.DEMAND)
        assertSame(lostSuffix, compactRun1StaticIdentity(f.ops, chain, lostSuffix))
        val normal = f.ops.startFact(exact)
        assertSame(normal, compactRun1StaticIdentity(f.ops, exact, normal))
    }

    @Test fun `carrier rejects a must payload instead of decoding it as STAR`() {
        val f = StoreFixtures()
        val codec = ConcreteStaticEntries(f.ops)
        val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
        val field = f.field("f")
        val mark = MarkSlot.Concrete(f.mark("T"))
        val exact = f.manager.initial(f.pattern(STATIC, listOf(cls, field), Tail.EXACT, mark))
        codec.validate(codec.single(exact)) // exact-only factory tag has no any payload
        val chain = f.manager.initial(f.pattern(STATIC, listOf(cls, field), Tail.STAR, mark))
        val any = codec.single(chain)
        codec.validate(any)
        val must = f.manager.taintTree(STATIC, Layer.DEMAND, any.root, anyTail = Tail.ANY_TAINT)
        assertFailsWith<IllegalStateException> { codec.validate(must) }
    }
}
