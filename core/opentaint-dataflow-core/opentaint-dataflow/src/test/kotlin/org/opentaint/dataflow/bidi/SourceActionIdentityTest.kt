package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.fixtures.FiniteProgramFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.SourceHitStore
import kotlin.test.*

class SourceActionIdentityTest {
    @Test fun `equal source facts keep every action and only authorize the matching occurrence`() {
        FiniteProgramFixtures().use { f ->
            val method = f.method("sources", listOf(emptyList()), 0).key()
            val node = f.inst(method, 0)
            val form = FormKey(FormOwner(), method.method, method, node)
            val first = ActionId(RuleOccurrenceId(form, RuleKind.CALL_SOURCE, 0), ActionRole.SOURCE, 0)
            val second = ActionId(RuleOccurrenceId(form, RuleKind.CALL_SOURCE, 1), ActionRole.SOURCE, 0)
            val edge = PathEdge(ZERO_FACT, PathFact(AccessPathBase.Return, emptyList(), Tail.EXACT,
                MarkSlot.Concrete(f.mark("T"))), ExclusionSet.Empty)
            val hits = SourceHitStore()
            assertTrue(hits.add(method, node, edge, first))
            assertTrue(hits.add(method, node, edge, second))
            assertFalse(hits.add(method, node, edge, first))
            assertEquals(setOf(first, second), hits.occurrences().map { it.action }.toSet())
            val onlyFirst = SeedIndex.of(listOf(Seed.Source(method, node, edge, first)))
            assertTrue(onlyFirst.allowsSource(method, node, edge, first))
            assertFalse(onlyFirst.allowsSource(method, node, edge, second))
            assertFalse(onlyFirst.allowsSource(method, node, edge))
            val all = SeedIndex.of(hits.occurrences().map { Seed.Source(it.method, it.statement, it.forward, it.action) }.toList())
            assertTrue(all.allowsSource(method, node, edge, second))
            assertEquals(2, all.size)
        }
    }
}
