package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.*

class CallServiceLifetimeTest {
    private fun plan(f: MethodAnalysisFixtures): CallPlan = CallPlan(
        setOf(AccessPathBase.Argument(0), AccessPathBase.Return),
        listOf(CallStage.Edges(CallPoint.BEFORE, CallPoint.ADDED, StageKind.BIND_IN,
                StatementSummary.identities(listOf(AccessPathBase.Argument(0)))),
            CallStage.Callees(CallPoint.ADDED, CallPoint.RETURNED, listOf(f.store.key("callee"))),
            CallStage.Edges(CallPoint.RETURNED, CallPoint.AFTER, StageKind.BIND_BACK,
                StatementSummary.identities(listOf(AccessPathBase.Return)))),
        emptyList(), CallPoint.BEFORE, CallPoint.AFTER)

    @Test fun `cancellation rejects a summary return before cuts storage or work and activation permits its first result`(): Unit {
        MethodAnalysisFixtures().use { f ->
            f.call(1, plan(f))
            val core = f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices),
                callFactory = MethodCallServicesFactory(::RunMethodCallServices))
            val initial = f.manager.initial(f.pattern())
            val part = Subscription(f.store.key("callee"), CallerRef(f.key, f.manager.zero, Layer.NORMAL, f.inst(1)),
                Layer.NORMAL, f.fact())
            val pub = Publication(initial, f.fact(AccessPathBase.Return))
            f.cancellation.cancel()
            assertFailsWith<Cancellation.Cancelled> { core.applySummary(part, pub, 0) }
            assertTrue(f.cuts.isEmpty())
            assertTrue(f.enqueued.isEmpty())
            assertEquals(0L, core.recordCrossings)
            f.cancellation.activate()
            assertTrue(core.edges.edgesAt(f.inst(2), f.manager.zero).none())
            assertTrue(core.summaries.all().none())
            core.applySummary(part, pub, 0)
            f.drain(core, quantum = 1)
            assertEquals(setOf(f.manager.zero to Conclusion(f.pattern(AccessPathBase.Return).fact, ExclusionSet.Empty, false)),
                methodEntries(f.ops, core.summaries.all()))
            assertTrue(f.cuts.any { it.first == Cut.CALL })
            assertEquals(0L, core.recordCrossings)
        }
    }

    @Test fun `complete actor freeze drops its call service owner and every call cache and rejects later processing`(): Unit {
        MethodAnalysisFixtures().use { f ->
            f.call(1, plan(f))
            lateinit var service: RunMethodCallServices
            val core = f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices),
                callFactory = MethodCallServicesFactory { owner -> RunMethodCallServices(owner).also { service = it } })
            val part = Subscription(f.store.key("callee"), CallerRef(f.key, f.manager.zero, Layer.NORMAL, f.inst(1)),
                Layer.NORMAL, f.fact())
            core.applySummary(part, Publication(f.manager.initial(f.pattern()), f.fact(AccessPathBase.Return)), 0)
            f.drain(core)
            assertTrue(core.work(1))
            val steps = core.steps
            assertTrue(steps > 0)
            core.freeze()
            val ownerFields = service.javaClass.declaredFields.filter { it.type == RunMethodAnalyzer::class.java }
            assertTrue(ownerFields.isNotEmpty(), "The service explicitly owns its live method")
            for (field in ownerFields) {
                field.isAccessible = true
                assertNull(field.get(service), field.name)
            }
            for (field in service.javaClass.declaredFields.filter { Map::class.java.isAssignableFrom(it.type) }) {
                field.isAccessible = true
                val cache = field.get(service) as Map<*, *>?
                assertTrue(cache == null || cache.isEmpty(), field.name)
            }
            assertFailsWith<IllegalStateException> {
                service.process(EdgeDelta(f.manager.zero, f.inst(1), Reach.NORMAL))
            }
            assertFailsWith<IllegalStateException> {
                service.applySummary(part, Publication(f.manager.initial(f.pattern()), f.fact(AccessPathBase.Return)), 0)
            }
            assertFailsWith<IllegalStateException> {
                service.applyRecord(part, Record(part.callee, Direction.FORWARD,
                    f.manager.initial(f.pattern()), f.fact(AccessPathBase.Return)))
            }
            assertFailsWith<IllegalStateException> {
                service.applyRecordView(part, RecordLeafView(f.pattern(),
                    Conclusion(f.pattern(AccessPathBase.Return).fact, ExclusionSet.Empty, false)))
            }
            assertEquals(steps, core.steps)
            assertTrue(f.events.isEmpty())
            assertTrue(f.subscriptions.isEmpty())
            assertTrue(f.sentLinks.isEmpty())
        }
    }

    @Test fun `cancelled native and view returns preserve crossing counts and closure until activation`(): Unit {
        for (native in listOf(true, false)) MethodAnalysisFixtures().use { f ->
            f.call(1, plan(f))
            val core = f.core(index = 3, localFactory = MethodLocalServicesFactory(::RunMethodLocalServices),
                callFactory = MethodCallServicesFactory(::RunMethodCallServices))
            val part = Subscription(f.store.key("callee"), CallerRef(f.key, f.manager.zero, Layer.NORMAL, f.inst(1)),
                Layer.NORMAL, f.fact())
            val record = Record(part.callee, Direction.FORWARD, f.manager.initial(f.pattern()),
                f.fact(AccessPathBase.Return))
            val view = RecordLeafView(f.pattern(), Conclusion(f.pattern(AccessPathBase.Return).fact,
                ExclusionSet.Empty, false))
            val apply: () -> Unit = {
                if (native) core.applyRecord(part, record) else core.applyRecordView(part, view)
            }
            f.cancellation.cancel()
            assertFailsWith<Cancellation.Cancelled> { apply() }
            assertEquals(0L, core.recordCrossings)
            assertTrue(f.cuts.isEmpty())
            assertTrue(f.enqueued.isEmpty())
            f.cancellation.activate()
            assertTrue(core.edges.edgesAt(f.inst(2), f.manager.zero).none())
            apply()
            assertEquals(1L, core.recordCrossings)
            assertEquals(setOf(f.manager.zero to Conclusion(f.pattern(AccessPathBase.Return).fact,
                ExclusionSet.Empty, false)), methodEntries(f.ops,
                core.edges.edgesAt(f.inst(2), f.manager.zero)))
            assertTrue(f.cuts.any { it.first == Cut.CALL })
            assertTrue(f.subscriptions.isEmpty())
            assertTrue(f.sentLinks.isEmpty())
        }
    }

    @Test fun `cancellation during subscription replay prevents sending its callee link`(): Unit {
        MethodAnalysisFixtures().use { f ->
            f.call(1, plan(f))
            lateinit var calls: RunMethodCallServices
            val core = f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices),
                callFactory = MethodCallServicesFactory { owner -> RunMethodCallServices(owner).also { calls = it } })
            f.onSubscribe = { f.cancellation.cancel() }
            f.onLink = { error("A cancelled replay must stop before its link") }
            val item = EdgeDelta(f.manager.initial(f.pattern()), f.inst(1), f.fact())
            assertFailsWith<Cancellation.Cancelled> { calls.process(item) }
            assertEquals(1, f.subscriptions.size)
            assertTrue(f.sentLinks.isEmpty())
            assertTrue(f.cuts.isEmpty())
            assertTrue(f.enqueued.isEmpty())
            assertEquals(0L, core.recordCrossings)
            f.cancellation.activate()
            f.onSubscribe = {}
            f.onLink = {}
            calls.process(item)
            assertEquals(1, f.sentLinks.size)
            assertEquals(item.premise, f.sentLinks.single().ref.premise)
            assertTrue(core.edges.edgesAt(f.inst(2), item.premise).none())
        }
    }
}
