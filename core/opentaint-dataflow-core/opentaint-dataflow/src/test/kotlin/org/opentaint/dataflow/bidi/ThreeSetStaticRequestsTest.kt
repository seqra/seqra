package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.FiniteProgramFixtures
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import kotlin.test.*

/** Tests the selected storage lifetime and canonical-answer contracts. */
class ThreeSetStaticRequestsTest {
    private fun field(f: FiniteProgramFixtures): Pattern = Pattern(PathFact(STATIC,
        listOf(f.ap.accessors.index(ClassStaticAccessor("C")), f.field("f")), Tail.STAR, MarkSlot.STAR),
        ExclusionSet.Empty)

    @Test fun `answered field request has no service subscription`() {
        FiniteProgramFixtures().use { f ->
            val owner = f.method("reader", listOf(emptyList()), 0).key()
            val g = f.ap.marks.groupOf(f.mark("T")); val position = field(f)
            val service = Run1StaticService(f.ap)
            service.commit(StaticBatch().apply { interests += StaticInterest(owner, 1, position, GroupSet.of(g)) })
            assertEquals(1, service.statistics().pointSubscriptions)
            val ready = service.commit(StaticBatch().apply { supplies += RegionGroups(position, GroupSet.of(g)) })
            assertEquals(1, ready.filterIsInstance<StaticOwnerEffect.PointReady>().size)
            assertEquals(0, service.statistics().pointSubscriptions,
                "The service must release an answered request instead of keeping delivered history")
        }
    }

    @Test fun `an exact group supply answers with the canonical field descriptor`() {
        FiniteProgramFixtures().use { f ->
            val owner = f.method("reader", listOf(emptyList()), 0).key()
            val g = f.ap.marks.groupOf(f.mark("T")); val position = field(f)
            val service = Run1StaticService(f.ap)
            service.commit(StaticBatch().apply { interests += StaticInterest(owner, 1, position, GroupSet.of(g)) })
            val exact = position.copy(fact = position.fact.copy(tail = Tail.EXACT))
            val reply = service.commit(StaticBatch().apply { supplies += RegionGroups(exact, GroupSet.of(g)) })
                .filterIsInstance<StaticOwnerEffect.PointReady>().single()
            assertEquals(position, reply.supply.region,
                "The descriptor is C.f.*; ordinary marked current-fact construction can use ANY")
        }
    }

    @Test fun `unrequested group writes do not wake existing FLOW writers`() {
        FiniteProgramFixtures().use { f ->
            val owner = f.method("reader", listOf(emptyList()), 0).key()
            val g = f.ap.marks.groupOf(f.mark("T")); val position = field(f)
            val service = Run1StaticService(f.ap)
            service.commit(StaticBatch().apply { flowWriters += StaticFlowWriter(owner, 1, position) })
            val unrequested = service.commit(StaticBatch().apply { supplies += RegionGroups(position, GroupSet.of(g)) })
            assertTrue(unrequested.filterIsInstance<StaticOwnerEffect.WriterInterest>().isEmpty())
            val requested = service.commit(StaticBatch().apply {
                interests += StaticInterest(owner, 2, position, GroupSet.of(g))
            })
            assertEquals(1, requested.filterIsInstance<StaticOwnerEffect.WriterInterest>().size)
            assertEquals(1, requested.filterIsInstance<StaticOwnerEffect.PointReady>().size)
        }
    }
    @Test fun `all-static FLOW writer does not enumerate inputs on unrequested pool growth`() {
        MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val inputPath = listOf(cls, f.field("input")); val outputPath = listOf(cls, f.field("output"))
            val g = f.manager.marks.groupOf(f.mark("T"))
            val initial = f.manager.initial(STATIC, f.manager.path(inputPath), Tail.STAR,
                ExclusionSet.Empty, MarkSlot.STAR)
            val input = f.fact(STATIC, inputPath, Tail.STAR, MarkSlot.STAR, layer = Layer.DEMAND)
            val output = f.fact(STATIC, outputPath, Tail.STAR, MarkSlot.STAR, layer = Layer.DEMAND)
            val edge = starEdge(STATIC, inputPath, STATIC, outputPath)
            val operation = OperationId(FormKey(FormOwner(), f.key.method, f.key, f.inst(1)))
            val actor = f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
            actor.emitOperationResult(OperationInput(ResumeSite.StatementInput(operation), initial, input),
                StaticOperation.Write(operation, Pattern(edge.to, ExclusionSet.Empty), edge), output) { _, _ ->
                fail("Static values must not enter the ordinary successor queue")
            }
            f.drain(actor)
            val service = checkNotNull(actor.runnerPort.staticService)
            val unrequested = service.commit(StaticBatch().apply {
                supplies += RegionGroups(f.pattern(STATIC, inputPath, Tail.STAR, MarkSlot.STAR), GroupSet.of(g))
            })
            assertTrue(unrequested.isEmpty(),
                "Pool growth must not replay an all-static FLOW writer before its output group is requested")
            val requested = service.commit(StaticBatch().apply {
                interests += StaticInterest(f.key, Long.MAX_VALUE,
                    f.pattern(STATIC, outputPath, Tail.STAR, MarkSlot.STAR), GroupSet.of(g))
            })
            assertEquals(1, requested.filterIsInstance<StaticOwnerEffect.WriterInterest>().size)
        }
    }

}
