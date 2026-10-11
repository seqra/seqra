package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.store.InitialFactStore
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import kotlin.test.*

class InitialFactStoreTest {
    @Test fun `exact canonical identity retains every tail mark and field exclusion`() {
        val fixture = StoreFixtures()
        val m = fixture.manager
        val f = fixture.field("f")
        val g = fixture.field("g")
        val t = fixture.mark("T")
        val u = fixture.mark("U")
        val store = InitialFactStore()
        val initials = mutableListOf(m.zero)
        for (path in listOf(emptyList(), listOf(f))) {
            for (tail in listOf(Tail.STAR, Tail.ANY)) initials += m.initial(fixture.pattern(path = path, tail = tail, mark = MarkSlot.STAR))
            initials += m.initial(fixture.pattern(path = path, tail = Tail.STAR, mark = MarkSlot.STAR, exclusion = ExclusionSet.of(g)))
            for (mark in listOf(t, u)) for (tail in Tail.entries) {
                initials += m.initial(fixture.pattern(path = path, tail = tail, mark = MarkSlot.Concrete(mark)))
                if (tail == Tail.STAR || tail == Tail.ANY_TAINT)
                    initials += m.initial(fixture.pattern(path = path, tail = tail, mark = MarkSlot.Concrete(mark), exclusion = ExclusionSet.of(g)))
            }
        }
        initials += initials.filterNot { it.isZero }.map {
            m.initial(it.toPattern().copy(fact = it.toPattern().fact.copy(base = org.opentaint.dataflow.ap.ifds.AccessPathBase.Return)))
        }
        assertEquals(initials.size, initials.toSet().size)
        assertTrue(store.all().isEmpty())
        for (i in initials) {
            assertTrue(store.add(i))
            assertFalse(store.add(i))
            assertFalse(store.add(m.initial(i.toPattern())))
        }
        assertEquals(initials.toSet(), store.all().toSet())
    }
}
