package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.IncreasingLimits
import org.opentaint.dataflow.bidi.fixtures.FiniteProgramFixtures
import org.opentaint.dataflow.bidi.fixtures.TestMark
import org.opentaint.dataflow.bidi.fixtures.testDeclarations
import org.opentaint.dataflow.bidi.store.DemandStore
import kotlin.test.*

class GroupedRunScheduleTest {
    @Test fun `group run and first full backward run use the same field limit`() {
        assertEquals(listOf(1, 1, 2, 3, 4, 5), (1..6).map(IncreasingLimits::fieldLimit))
        assertFailsWith<IllegalArgumentException> { IncreasingLimits.fieldLimit(0) }
    }

    @Test fun `shared static demand is available for an unvisited method and keeps the actual full mark`() {
        FiniteProgramFixtures(markDeclarations = testDeclarations(
            TestMark("full T", MarkGroupKey.Rule("outer rule")),
        )).use { f ->
            val method = f.method("unvisited", listOf(emptyList()), 0).key()
            val full = f.mark("full T")
            val cls = f.ap.accessors.index(ClassStaticAccessor("C"))
            val field = f.field("f")
            val path = listOf(cls, field)
            val actual = f.fact(STATIC, path, mark = MarkSlot.Concrete(full))
            val demand = DemandStore.Builder(f.ap).apply { includeStaticGroups() }.build()
            val guides = demand.near(method, f.ops.leaves(actual).single())
            assertEquals(1, guides.size)
            assertTrue(f.ap.marks.isGroup((guides.single().entry.fact.mark as MarkSlot.Concrete).mark))
            val initials = f.ops.emit(guides.single(), actual)
            assertTrue(initials.isNotEmpty())
            assertTrue(initials.all { it.mark == MarkSlot.Concrete(full) })
            val flow = f.fact(STATIC, path, Tail.STAR, MarkSlot.STAR)
            assertTrue(f.ops.emit(guides.single(), flow).isEmpty(), "Guidance cannot supply a missing full mark")
        }
    }
}
