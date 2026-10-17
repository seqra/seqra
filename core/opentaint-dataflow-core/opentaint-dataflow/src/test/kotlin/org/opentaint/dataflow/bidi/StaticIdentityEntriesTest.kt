package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.StaticIdentityEntries
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.MicroEdge
import org.opentaint.dataflow.bidi.interp.StatementSummary
import org.opentaint.dataflow.bidi.interp.keepEdge
import org.opentaint.dataflow.bidi.interp.starEdge
import kotlin.test.*

class StaticIdentityEntriesTest {
    private class Fixture {
        val f = StoreFixtures()
        val codec = StaticIdentityEntries(f.ops)
        val c = f.manager.accessors.index(ClassStaticAccessor("C"))
        val d = f.manager.accessors.index(ClassStaticAccessor("D"))
        val field = f.field("f")
        val sibling = f.field("g")
        val cf = initial(c, field)
        val cg = initial(c, sibling)
        val df = initial(d, field)
        fun initial(cls: Int, member: Int): InitialAp =
            f.manager.initial(f.pattern(STATIC, listOf(cls, member), Tail.STAR, MarkSlot.STAR))
        fun packed(vararg initials: InitialAp): FlowTree = initials.map(codec::single)
            .reduce { a, b -> codec.merge(a, b).first }
    }

    @Test fun `union delta retains each diagonal original without a common-premise product`() {
        val x = Fixture()
        val first = x.packed(x.cf, x.df)
        val (stored, delta) = x.codec.merge(first, x.packed(x.cf, x.cg))
        x.codec.validate(stored)
        assertEquals(setOf(x.cf, x.cg, x.df), x.codec.originals(stored).toSet())
        assertEquals(setOf(x.cg), x.codec.originals(assertNotNull(delta)).toSet())
        assertNull(x.codec.merge(stored, x.packed(x.df, x.cf, x.cg)).second)
        for (initial in x.codec.originals(stored)) {
            assertTrue(x.codec.contains(stored, initial))
            assertEquals(initial.pathData.toList(), x.f.ops.leaves(x.codec.single(initial)).single().fact.path)
        }
        assertFalse(x.codec.contains(stored, x.initial(x.d, x.sibling)))
    }

    @Test fun `class and field prefix selection partitions existing entries without inventing siblings`() {
        val x = Fixture()
        val all = x.packed(x.cf, x.cg, x.df)
        val selected = assertNotNull(x.codec.select(all, listOf(listOf(x.c, x.field), listOf(x.d))))
        assertEquals(setOf(x.cf, x.df), x.codec.originals(selected).toSet())
        assertEquals(setOf(x.cg), x.codec.originals(assertNotNull(x.codec.remainder(all, selected))).toSet())
        assertSame(all, x.codec.select(all, listOf(emptyList())))
        assertNull(x.codec.select(all, listOf(listOf(x.d, x.sibling))))
        assertNull(x.codec.remainder(all, all))
    }

    @Test fun `unknown stage decodes all while a certified root keep decodes only changed fields`() {
        val x = Fixture()
        val all = x.packed(x.cf, x.cg, x.df)
        assertNull(x.codec.affected(StatementSummary.EMPTY, all))
        assertSame(all, x.codec.affected(StatementSummary.EMPTY, all, stage = true),
            "An absent stage transfer kills rather than silently preserving entries")
        val move = starEdge(STATIC, listOf(x.c, x.field), STATIC, listOf(x.d, x.field))
        val form = StatementSummary(setOf(STATIC), listOf(MicroEdge.of(keepEdge(STATIC)), MicroEdge.of(move)),
            emptyList(), emptyMap())
        val selected = assertNotNull(x.codec.affected(form, all, stage = true))
        assertEquals(setOf(x.cf), x.codec.originals(selected).toSet())
        assertEquals(setOf(x.cg, x.df), x.codec.originals(assertNotNull(x.codec.remainder(all, selected))).toSet())
    }

    @Test fun `root class deep and annotated leaves cannot masquerade as canonical identities`() {
        val x = Fixture()
        val invalidPaths = listOf(emptyList(), listOf(x.c), listOf(x.c, x.field, x.sibling))
        for (path in invalidPaths) {
            val value = x.f.fact(STATIC, path, Tail.STAR, MarkSlot.STAR, layer = Layer.DEMAND) as FlowTree
            assertFailsWith<IllegalStateException> { x.codec.validate(value) }
        }
        val identity = x.codec.single(x.cf)
        assertFailsWith<IllegalStateException> { x.codec.validate(identity.withLayer(Layer.NORMAL) as FlowTree) }
        val excluded = x.f.fact(STATIC, listOf(x.c, x.field), Tail.STAR,
            MarkSlot.Star(x.f.manager.markSetOf(x.f.mark("T"))), layer = Layer.DEMAND) as FlowTree
        assertFailsWith<IllegalStateException> { x.codec.validate(excluded) }
        val may = x.f.fact(STATIC, listOf(x.c, x.field), Tail.ANY, MarkSlot.STAR, layer = Layer.DEMAND) as FlowTree
        assertFailsWith<IllegalStateException> { x.codec.validate(may) }
    }
}
