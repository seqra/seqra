package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.FiniteProgramFixtures
import org.opentaint.dataflow.bidi.store.CallerRef
import kotlin.test.*

/** Publications retain ordinary premise/AP membership, without auxiliary revisions. */
class ConditionalPublicationTest {
    @Test fun `an unchanged summary is published once and replayed with its actual payload`() {
        FiniteProgramFixtures().use { f ->
            val method = f.method("writer", listOf(emptyList()), 0).key()
            val full = f.mark("T")
            f.ap.marks.finishFixtureOwnershipCensus()
            val mark = MarkSlot.Concrete(f.ap.marks.group(f.ap.marks.groupOf(full)))
            val fact = f.fact(AccessPathBase.Return, mark = mark, layer = Layer.DEMAND)
            val publication = Publication(f.ap.zero, fact)
            val index = PublicationIndex(f.ap, f.ops)
            assertEquals(listOf(publication), index.addAll(listOf(publication)))
            repeat(8) { assertTrue(index.addAll(listOf(publication)).isEmpty()) }
            val query = Subscription(method, CallerRef(method, f.ap.zero, Layer.NORMAL, f.inst(method, 0)),
                Layer.NORMAL, Reach.NORMAL)
            val replay = index.candidates(query, ApMode(true, Direction.FORWARD, 1)).map { it.first }.toList()
            assertEquals(1, replay.size)
            assertEquals(f.ops.leaves(fact).toSet(), f.ops.leaves(replay.single().conclusion).toSet())
            assertTrue(replay.single().premise.isZero)
        }
    }

    @Test fun `a fresh AP leaf extends the same summary without losing its earlier leaf`() {
        FiniteProgramFixtures().use { f ->
            val method = f.method("writer", listOf(emptyList()), 0).key()
            val full = f.mark("T")
            f.ap.marks.finishFixtureOwnershipCensus()
            val mark = MarkSlot.Concrete(f.ap.marks.group(f.ap.marks.groupOf(full)))
            val left = f.fact(AccessPathBase.Return, listOf(f.field("left")), mark = mark, layer = Layer.DEMAND)
            val right = f.fact(AccessPathBase.Return, listOf(f.field("right")), mark = mark, layer = Layer.DEMAND)
            val index = PublicationIndex(f.ap, f.ops)
            assertTrue(index.addAll(listOf(Publication(f.ap.zero, left))).isNotEmpty())
            assertTrue(index.addAll(listOf(Publication(f.ap.zero, right))).isNotEmpty())
            assertTrue(index.addAll(listOf(Publication(f.ap.zero, left), Publication(f.ap.zero, right))).isEmpty())
            val query = Subscription(method, CallerRef(method, f.ap.zero, Layer.NORMAL, f.inst(method, 0)),
                Layer.NORMAL, Reach.NORMAL)
            val replay = index.candidates(query, ApMode(true, Direction.FORWARD, 1)).map { it.first }.toList()
            assertEquals((f.ops.leaves(left) + f.ops.leaves(right)).toSet(),
                replay.flatMap { f.ops.leaves(it.conclusion).toList() }.toSet())
            assertTrue(replay.all { it.layer == Layer.DEMAND && it.premise.isZero })
        }
    }
}
