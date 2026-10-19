package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.FiniteProgramFixtures
import org.opentaint.dataflow.bidi.store.CallerRef
import kotlin.test.*

class ConditionalPublicationTest {
    @Test fun `new support for the same summary is delivered without retaining support versions`() {
        FiniteProgramFixtures().use { f ->
            val method = f.method("writer", listOf(emptyList()), 0).key()
            val full = f.mark("T")
            f.ap.marks.finishFixtureOwnershipCensus()
            val mark = MarkSlot.Concrete(f.ap.marks.group(f.ap.marks.groupOf(full)))
            fun guard(argument: Int): GroundingSupport {
                val literal = f.pattern(AccessPathBase.Argument(argument), mark = mark)
                return GroundingSupport.require(listOf(GroundingRequirement(f.ap.initial(literal),
                    f.fact(AccessPathBase.Argument(argument), mark = mark, layer = Layer.DEMAND), literal)))
            }
            val first = guard(0)
            val second = guard(1)
            val fact = f.fact(AccessPathBase.Return, mark = mark, layer = Layer.DEMAND)
            val index = PublicationIndex(f.ap, f.ops)
            assertEquals(first, index.addAll(listOf(Publication(f.ap.zero, fact, first))).single().grounding)
            assertTrue(index.addAll(listOf(Publication(f.ap.zero, fact, first))).isEmpty())
            assertEquals(first.or(second), index.addAll(listOf(Publication(f.ap.zero, fact, second))).single().grounding)
            assertTrue(index.addAll(listOf(Publication(f.ap.zero, fact, first))).isEmpty())
            val query = Subscription(method, CallerRef(method, f.ap.zero, Layer.NORMAL, f.inst(method, 0)),
                Layer.NORMAL, Reach.NORMAL)
            val replay = index.candidates(query, ApMode(true, Direction.FORWARD, 1)).map { it.first }
            assertEquals(1, replay.size)
            assertEquals(first.or(second), replay.single().grounding)
        }
    }

    @Test fun `an unconditional exact cofactor is kept when a guarded broader result arrives`() {
        FiniteProgramFixtures().use { f ->
            val method = f.method("writer", listOf(emptyList()), 0).key()
            val full = f.mark("T")
            f.ap.marks.finishFixtureOwnershipCensus()
            val mark = MarkSlot.Concrete(f.ap.marks.group(f.ap.marks.groupOf(full)))
            val condition = f.pattern(AccessPathBase.Argument(0), mark = mark)
            val guard = GroundingSupport.require(listOf(GroundingRequirement(f.ap.initial(condition),
                f.fact(AccessPathBase.Argument(0), mark = mark, layer = Layer.DEMAND), condition)))
            val exact = f.fact(AccessPathBase.Return, mark = mark, layer = Layer.DEMAND)
            val broad = f.fact(AccessPathBase.Return, tail = Tail.ANY, mark = mark, layer = Layer.DEMAND)
            val index = PublicationIndex(f.ap, f.ops)
            index.addAll(listOf(Publication(f.ap.zero, exact)))
            index.addAll(listOf(Publication(f.ap.zero, broad, guard)))
            val query = Subscription(method, CallerRef(method, f.ap.zero, Layer.NORMAL, f.inst(method, 0)),
                Layer.NORMAL, Reach.NORMAL)
            val replay = index.candidates(query, ApMode(true, Direction.FORWARD, 1)).map { it.first }
            val supportsByTail = replay.associate { f.ops.leaves(it.conclusion).single().fact.tail to it.grounding }
            assertEquals(GroundingSupport.TRUE, supportsByTail[Tail.EXACT])
            assertEquals(guard, supportsByTail[Tail.ANY])
        }
    }
}
