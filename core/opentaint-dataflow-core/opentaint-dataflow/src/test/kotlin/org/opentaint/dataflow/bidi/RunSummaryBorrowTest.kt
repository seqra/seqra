package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.RunSummaryStore
import kotlin.test.*

class RunSummaryBorrowTest {
    @Test fun `raw borrowing sees the current T2 annotation replacement`() {
        val f = StoreFixtures()
        val summaries = RunSummaryStore(f.manager)
        val initial = f.manager.initial(f.pattern(tail = Tail.STAR, mark = MarkSlot.STAR))
        val flow = f.ops.startFact(initial) as FlowTree
        fun excluding(mark: TaintMark) = f.manager.flowTree(flow.base, flow.layer, flow.exclusion,
            MarkExclusions.of(f.manager.marks, mark), flow.root, flow.tail)
        assertTrue(summaries.borrow(initial).none())
        val first = excluding(f.mark("T"))
        assertNotNull(summaries.add(initial, first))
        assertEquals(first.markExclusion, assertIs<FlowTree>(summaries.borrow(initial).single()).markExclusion)
        assertNotNull(summaries.add(initial, excluding(f.mark("U"))))
        assertTrue(assertIs<FlowTree>(summaries.borrow(initial).single()).markExclusion.isEmpty)
        assertNull(summaries.add(initial, first))
        assertEquals(1, summaries.borrow(initial).count())
    }

    @Test fun `selected guidance does not replace the borrowed raw relation`() {
        val f = StoreFixtures()
        val summaries = RunSummaryStore(f.manager)
        val initial = f.manager.initial(f.pattern())
        val raw = f.fact()
        val guidance = f.fact(path = listOf(f.field("f")))
        assertNotNull(summaries.add(initial, raw))
        assertNotNull(summaries.addDemand(initial, guidance))
        assertEquals(listOf(raw), summaries.borrow(initial).toList())
        assertEquals(listOf(guidance), summaries.demandEdges().map { it.second }.toList())
    }
}
