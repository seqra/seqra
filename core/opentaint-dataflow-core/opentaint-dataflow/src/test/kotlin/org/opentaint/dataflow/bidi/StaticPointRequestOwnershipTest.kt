package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import kotlin.test.*

/** Delay owner effects explicitly to exercise pending memberships in flight. */
class StaticPointRequestOwnershipTest {
    @Test fun `admit memberships before deltas and release only after the current waiting set drains`() {
        MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val path = listOf(cls, f.field("f"))
            val initial = f.manager.initial(STATIC, f.manager.path(path), Tail.STAR,
                ExclusionSet.Empty, MarkSlot.STAR)
            val local = AccessPathBase.LocalVar(0)
            val current = f.fact(local, tail = Tail.STAR, mark = MarkSlot.STAR, layer = Layer.DEMAND) as FlowTree
            val operation = OperationId(FormKey(FormOwner(), f.key.method, f.key, f.inst(1)))
            val at = OperationInput(ResumeSite.StatementInput(operation), initial, current)
            val actor = f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
            val t = f.mark("T"); val u = f.mark("U")
            val g = f.manager.marks.groupOf(t); val h = f.manager.marks.groupOf(u)
            f.statement(1, StatementSummary.identities(listOf(local)))
            val service = checkNotNull(actor.runnerPort.staticService)
            actor.needGroups(at, t); actor.needGroups(at, t)
            assertEquals(1, actor.staticBatch.interests.size,
                "Duplicate pending membership must not construct another service delta")
            val first = actor.staticBatch.interests.single()
            service.commit(StaticBatch().apply { interests += first })
            actor.staticBatch.interests.clear()
            val queued = service.commit(StaticBatch().apply {
                supplies += RegionGroups(initial.toPattern(), GroupSet.of(g))
            }).filterIsInstance<StaticOwnerEffect.PointReady>().single()
            actor.needGroups(at, u)
            val added = actor.staticBatch.interests.single()
            assertEquals(first.id, added.id, "New group joins the row while the first answer is in flight")
            service.commit(StaticBatch().apply { interests += added })
            actor.staticBatch.interests.clear()
            actor.onStaticEffect(queued)
            val later = service.commit(StaticBatch().apply {
                supplies += RegionGroups(initial.toPattern(), GroupSet.of(h))
            }).filterIsInstance<StaticOwnerEffect.PointReady>().single()
            actor.onStaticEffect(later) // Must still find the row after consuming the old reply.
            actor.needGroups(at, t)
            val fresh = actor.staticBatch.interests.single()
            assertTrue(fresh.id > first.id, "A fully answered row is released and its ID is never reused")
            assertEquals(0, service.statistics().pointSubscriptions)
            val last = service.commit(StaticBatch().apply { interests += fresh })
                .filterIsInstance<StaticOwnerEffect.PointReady>().single()
            actor.staticBatch.interests.clear()
            actor.onStaticEffect(last)
            f.drain(actor)
            val marks = f.processed.flatMap { f.ops.leaves(it.facts).toList() }
                .mapNotNull { (it.fact.mark as? MarkSlot.Concrete)?.mark }.toSet()
            assertTrue(f.manager.marks.group(g) in marks)
            assertTrue(f.manager.marks.group(h) in marks)
        }
    }
}
