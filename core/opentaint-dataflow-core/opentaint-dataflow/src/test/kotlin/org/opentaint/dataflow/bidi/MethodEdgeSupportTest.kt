package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.DeltaWorklist
import org.opentaint.dataflow.bidi.engine.EdgeDelta
import org.opentaint.dataflow.bidi.engine.GroundingSupport
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.store.MethodEdgeStore
import kotlin.test.*

class MethodEdgeSupportTest {
    @Test fun `a guarded mixed MAY body does not hide a later unconditional exact cofactor`() {
        MethodAnalysisFixtures().use { f ->
            val store = MethodEdgeStore(f.manager, f.key, f.language, 1)
            val original = f.manager.initial(f.run1Pattern())
            val exact = f.run1Fact(path = listOf(f.field("f"))) as TaintTree
            val may = f.run1Fact(path = listOf(f.field("g")), tail = Tail.ANY) as TaintTree
            val mixed = f.manager.taintTree(exact.base, exact.layer,
                f.manager.taintAlg.mergeAdd(exact.root, may.root), anyTail = Tail.ANY)
            assertNotEquals(mixed.groupKey, exact.groupKey)
            store.addSupported(f.inst(1), original, mixed, GroundingSupport.False) { _, _ -> }
            assertNull(store.add(f.inst(1), original, exact)) // raw T2 already covers this exact leaf
            val seen = arrayListOf<Pair<Facts, GroundingSupport>>()
            store.addSupported(f.inst(1), original, exact, GroundingSupport.TRUE) { value, support -> seen += value to support }
            assertEquals(listOf(f.run1Pattern(path = listOf(f.field("f")))),
                seen.flatMap { f.ops.leaves(it.first).toList() })
            assertEquals(listOf(GroundingSupport.TRUE), seen.map { it.second })
            assertEquals(1L, store.groundingTrueRows)
            assertEquals(1L, store.groundingGuardedRows)
        }
    }

    @Test fun `T2 annotation replacement never backfills guarded merged coverage as unconditional`() {
        MethodAnalysisFixtures().use { f ->
            val store = MethodEdgeStore(f.manager, f.key, f.language, 1)
            val original = f.manager.initial(f.pattern(tail = Tail.STAR, mark = MarkSlot.STAR))
            val flow = f.ops.startFact(original) as FlowTree
            fun excluding(mark: TaintMark) = f.manager.flowTree(flow.base, flow.layer, flow.exclusion,
                MarkExclusions.of(f.manager.marks, mark), flow.root, flow.tail)
            store.addSupported(f.inst(1), original, excluding(f.mark("T")), GroundingSupport.TRUE) { _, _ -> }
            store.addSupported(f.inst(1), original, excluding(f.mark("U")), GroundingSupport.False) { _, _ -> }
            val raw = assertIs<FlowTree>(store.edgesAt(f.inst(1), original).single().second)
            assertTrue(raw.markExclusion.isEmpty) // T2 intersected the distinct annotations
            val seen = arrayListOf<Pair<Facts, GroundingSupport>>()
            store.addSupported(f.inst(1), original, flow, GroundingSupport.False) { value, support -> seen += value to support }
            assertEquals(listOf(GroundingSupport.False), seen.map { it.second })
            assertEquals(f.ops.leaves(flow).toList(), seen.flatMap { f.ops.leaves(it.first).toList() })
            seen.clear()
            store.addSupported(f.inst(1), original, flow, GroundingSupport.TRUE) { value, support -> seen += value to support }
            assertEquals(listOf(GroundingSupport.TRUE), seen.map { it.second })
        }
    }

    @Test fun `an unconditional update never assigns its support to older guarded leaves`() {
        MethodAnalysisFixtures().use { f ->
            val store = MethodEdgeStore(f.manager, f.key, f.language, 1)
            val original = f.manager.initial(f.run1Pattern())
            val broad = f.run1Fact(tail = Tail.ANY_TAINT)
            val exact = f.run1Fact(path = listOf(f.field("f")))
            val seen = arrayListOf<Pair<Facts, GroundingSupport>>()
            store.addSupported(f.inst(1), original, broad, GroundingSupport.False) { value, support -> seen += value to support }
            seen.clear()
            store.addSupported(f.inst(1), original, exact, GroundingSupport.TRUE) { value, support -> seen += value to support }
            assertEquals(listOf(f.run1Pattern(path = listOf(f.field("f")))), seen.flatMap { f.ops.leaves(it.first).toList() })
            assertTrue(seen.all { it.second == GroundingSupport.TRUE })
            assertEquals(1L, store.groundingTrueRows)
            assertEquals(1L, store.groundingGuardedRows)
        }
    }

    @Test fun `the first guarded body keeps earlier exact unconditional cofactors`() {
        MethodAnalysisFixtures().use { f ->
            val store = MethodEdgeStore(f.manager, f.key, f.language, 1)
            val original = f.manager.initial(f.run1Pattern())
            val exact = f.run1Fact(path = listOf(f.field("f")))
            val broad = f.run1Fact(tail = Tail.ANY_TAINT)
            store.addSupported(f.inst(1), original, exact, GroundingSupport.TRUE) { _, _ -> }
            val seen = arrayListOf<Pair<Facts, GroundingSupport>>()
            store.addSupported(f.inst(1), original, broad, GroundingSupport.False) { value, support -> seen += value to support }
            assertEquals(listOf(f.run1Pattern(tail = Tail.ANY_TAINT)), seen.flatMap { f.ops.leaves(it.first).toList() })
            assertTrue(seen.all { it.second == GroundingSupport.False })
            assertEquals(1L, store.groundingTrueRows)
            assertEquals(1L, store.groundingGuardedRows)
        }
    }

    @Test fun `old facts still enqueue an unconditional support improvement once`() {
        MethodAnalysisFixtures().use { f ->
            val store = MethodEdgeStore(f.manager, f.key, f.language, 1)
            val original = f.manager.initial(f.run1Pattern())
            val fact = f.run1Fact()
            store.addSupported(f.inst(1), original, fact, GroundingSupport.False) { _, _ -> }
            val seen = arrayListOf<GroundingSupport>()
            store.addSupported(f.inst(1), original, fact, GroundingSupport.TRUE) { _, support -> seen += support }
            store.addSupported(f.inst(1), original, fact, GroundingSupport.TRUE) { _, support -> seen += support }
            assertEquals(listOf(GroundingSupport.TRUE), seen)
            assertEquals(1L, store.groundingTrueRows)
            assertEquals(0L, store.groundingGuardedRows)
        }
    }

    @Test fun `unguarded packed work keeps the bulk path and needs no cofactor rows`() {
        MethodAnalysisFixtures().use { f ->
            val store = MethodEdgeStore(f.manager, f.key, f.language, 1)
            val original = f.manager.initial(f.run1Pattern())
            val both = f.manager.mergeAddDelta(f.run1Fact(path = listOf(f.field("f"))),
                f.run1Fact(path = listOf(f.field("g")))).first
            val seen = arrayListOf<Facts>()
            store.addSupported(f.inst(1), original, both, GroundingSupport.TRUE) { value, _ -> seen += value }
            assertEquals(1, seen.size)
            assertEquals(f.ops.leaves(both).toSet(), f.ops.leaves(seen.single()).toSet())
            assertEquals(0L, store.groundingTrueRows)
            assertEquals(0L, store.groundingGuardedRows)
        }
    }

    @Test fun `unchanged work preserves both supports without a permanent pass through census`() {
        for (firstGuarded in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val store = MethodEdgeStore(f.manager, f.key, f.language, 1)
            val original = f.manager.initial(f.run1Pattern())
            val fact = f.run1Fact()
            val work = DeltaWorklist()
            val supports = if (firstGuarded) listOf(GroundingSupport.False, GroundingSupport.TRUE)
                else listOf(GroundingSupport.TRUE, GroundingSupport.False)
            for (support in supports) store.forwardUnchanged(f.inst(1), original, fact, support) { value, current ->
                assertTrue(work.addUnchanged(EdgeDelta(original, f.inst(1), value, current)))
            }
            val seen = arrayListOf<GroundingSupport>()
            while (!work.isEmpty) {
                seen += work.removeNext().grounding
                work.afterProcess()
            }
            assertTrue(GroundingSupport.TRUE in seen)
            assertEquals(GroundingSupport.TRUE, seen.fold<GroundingSupport, GroundingSupport>(GroundingSupport.False) { a, b -> a.or(b) })
        }
        MethodAnalysisFixtures().use { f ->
            val store = MethodEdgeStore(f.manager, f.key, f.language, 1)
            val original = f.manager.initial(f.run1Pattern())
            store.forwardUnchanged(f.inst(1), original, f.run1Fact(), GroundingSupport.TRUE) { _, _ -> }
            assertTrue(store.edgesAt(f.inst(1)).none())
            assertEquals(0L, store.groundingTrueRows)
            assertEquals(0L, store.groundingGuardedRows)
        }
    }
}
