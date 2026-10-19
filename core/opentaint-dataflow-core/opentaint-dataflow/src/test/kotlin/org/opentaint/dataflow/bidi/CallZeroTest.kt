package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import kotlin.test.*

class CallZeroTest {
    private val argument = AccessPathBase.Argument(0)
    private val result = AccessPathBase.Return

    private fun plan(callees: List<MethodKey>, sources: List<MicroEdge> = emptyList()): CallPlan = CallPlan(
        setOf(AccessPathBase.Zero, argument, result), listOf(
            CallStage.Edges(CallPoint.BEFORE, CallPoint.BOUND, StageKind.BIND_IN,
                StatementSummary.identities(setOf(AccessPathBase.Zero, argument, result))),
            CallStage.Actions(CallPoint.BOUND, CallPoint.ADDED, emptyList()),
            CallStage.Callees(CallPoint.ADDED, CallPoint.RETURNED, callees),
            CallStage.Edges(CallPoint.BOUND, CallPoint.RETURNED, StageKind.SOURCES,
                StatementSummary(setOf(AccessPathBase.Zero), sources, emptyList(), emptyMap())),
            CallStage.Edges(CallPoint.RETURNED, CallPoint.AFTER, StageKind.BIND_BACK,
                StatementSummary.identities(setOf(AccessPathBase.Zero, result))),
        ), emptyList(), CallPoint.BEFORE, CallPoint.AFTER)

    private fun core(f: MethodAnalysisFixtures, index: Int = 1, limit: Int = maxOf(1, index - 1),
                     seeds: SeedIndex = SeedIndex.EMPTY, key: MethodKey = f.key): RunMethodAnalyzer =
        f.core(index = index, L = limit, seeds = seeds, key = key,
            localFactory = MethodLocalServicesFactory(::RunMethodLocalServices),
            callFactory = MethodCallServicesFactory(::RunMethodCallServices))

    @Test fun `forward zero passes unchanged and separately runs source and resolved callee entry with its original layer`(): Unit {
        for (layer in Layer.entries) MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("source"))
            val target = PathFact(result, emptyList(), Tail.EXACT, t)
            val source = MicroEdge.of(PathEdge(ZERO_FACT, target, ExclusionSet.Empty))
            val callee = f.store.key(context = "callee")
            f.call(1, plan(listOf(callee), listOf(source)))
            f.onSubscribe = {}
            f.onLink = {}
            val actor = core(f)
            val groupedTarget = target.copy(mark = f.groupMark(t.mark))
            actor.emitAfter(f.manager.zero, f.inst(0), Reach.of(layer))
            f.drain(actor, quantum = 1)
            assertEquals(setOf(f.manager.zero to Conclusion(ZERO_FACT, ExclusionSet.Empty, layer == Layer.DEMAND),
                f.manager.zero to Conclusion(groupedTarget, ExclusionSet.Empty, true)),
                methodEntries(f.ops, actor.summaries.all()))
            val subscription = f.subscriptions.single()
            assertEquals(callee, subscription.callee)
            assertSame(f.manager.zero, subscription.ref.premise)
            assertEquals(layer, subscription.ref.callerLayer)
            assertEquals(layer, subscription.linkLayer)
            assertEquals(Reach.of(layer), subscription.added)
            assertFalse(subscription.zeroOnly)
            assertEquals(listOf(RunEvent.LinkIn(callee, subscription.ref, layer, Reach.of(layer))), f.sentLinks)
            assertTrue(f.events.none { it is RunEvent.ZeroIn })
            assertEquals(setOf(Conclusion(groupedTarget, ExclusionSet.Empty, true)),
                methodLeaves(f.ops, f.cuts.filter { it.first == Cut.CALL }.map { it.second }))
            assertNull(actor.sourceHits)
        }
    }

    @Test fun `every backward REACH premise passes but only zero to zero subscribes and sends ZeroIn to all callees`(): Unit {
        for (zero in listOf(false, true)) for (layer in Layer.entries) MethodAnalysisFixtures().use { f ->
            val callees = listOf(f.store.key(context = "calleeA"), f.store.key(context = "calleeB"))
            f.call(1, plan(callees))
            f.onSubscribe = {}
            f.onLink = { error("Backward zero rules do not send added links") }
            val actor = core(f, index = 2)
            val premise = if (zero) f.manager.zero else f.manager.initial(f.pattern(argument))
            actor.emitAfter(premise, f.inst(2), Reach.of(layer))
            f.drain(actor, quantum = 1)
            assertEquals(setOf(premise to Conclusion(ZERO_FACT, ExclusionSet.Empty, layer == Layer.DEMAND)),
                methodEntries(f.ops, actor.summaries.all()))
            assertEquals(if (zero) callees.toSet() else emptySet(), f.events.filterIsInstance<RunEvent.ZeroIn>().map { it.callee }.toSet())
            assertEquals(if (zero) 2 else 0, f.subscriptions.size)
            for (subscription in f.subscriptions) {
                assertTrue(subscription.zeroOnly)
                assertSame(f.manager.zero, subscription.ref.premise)
                assertEquals(actor.key, subscription.ref.caller)
                assertSame(f.inst(1), subscription.ref.call)
                assertEquals(layer, subscription.ref.callerLayer)
                assertEquals(layer, subscription.linkLayer)
                assertEquals(Reach.of(layer), subscription.added)
            }
            assertTrue(f.sentLinks.isEmpty())
            assertTrue(f.cuts.none { it.first == Cut.CALL || it.first == Cut.SEED })
            assertTrue(actor.sourceHits!!.entries().none())
        }
    }

    @Test fun `only backward zero to zero injects sink handoff seeds and unrestricted zero returns at their exact resume points`(): Unit {
        for (zero in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("seed"))
            val u = MarkSlot.Concrete(f.mark("returned"))
            val seedPath = listOf(f.field("seed"), f.field("deeper"))
            val returnPath = listOf(f.field("returned"), f.field("deeper"))
            val requirement = f.pattern(argument, seedPath, Tail.EXACT, t)
            val sink = localSink(listOf(requirement))
            val seeds = SeedIndex.of(listOf(Seed.Sink(sink.rule, f.key, f.inst(1), requirement)))
            val callee = f.store.key(context = "callee")
            f.call(1, plan(listOf(callee)))
            lateinit var actor: RunMethodAnalyzer
            f.onSubscribe = { subscription ->
                assertTrue(subscription.zeroOnly)
                actor.applySummary(subscription, Publication(f.manager.zero, f.fact(result, returnPath, mark = u)), 0)
            }
            f.onLink = { error("Zero return has no added-link rule") }
            actor = core(f, index = 2, limit = 1, seeds = seeds)
            val premise = if (zero) f.manager.zero else f.manager.initial(f.pattern(argument, mark = t))
            actor.emitAfter(premise, f.inst(2), Reach.NORMAL)
            f.drain(actor, quantum = 1)
            val expected = buildSet {
                add(premise to Conclusion(ZERO_FACT, ExclusionSet.Empty, false))
                if (zero) {
                    add(f.manager.zero to Conclusion(PathFact(argument, seedPath.take(1), Tail.ANY, t), ExclusionSet.Empty, true))
                    add(f.manager.zero to Conclusion(PathFact(result, returnPath.take(1), Tail.ANY, u), ExclusionSet.Empty, true))
                }
            }
            assertEquals(expected, methodEntries(f.ops, actor.summaries.all()))
            assertEquals(if (zero) 1 else 0, f.cuts.count { it.first == Cut.SEED })
            assertEquals(if (zero) 2 else 0, f.cuts.count { it.first == Cut.CALL })
            assertEquals(if (zero) 1 else 0, f.subscriptions.size)
            assertTrue(actor.sourceHits!!.entries().none())
            assertEquals(0L, actor.recordCrossings)
            assertTrue(f.events.none { it is RunEvent.MarkRequestsIn })
        }
    }

    @Test fun `restricted forward zero reaches only source edges seeded for the exact caller context statement and forward edge`(): Unit {
        for (admission in listOf("matching", "context", "statement", "edge", "absent")) MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("source"))
            val target = PathFact(result, emptyList(), Tail.EXACT, t)
            val source = MicroEdge.of(PathEdge(ZERO_FACT, target, ExclusionSet.Empty))
            val callee = f.store.key(context = "callee")
            f.call(1, plan(listOf(callee), listOf(source)))
            f.onSubscribe = {}
            f.onLink = {}
            val sourceSeed = Seed.Source(if (admission == "context") f.store.key(context = "other") else f.key,
                f.inst(if (admission == "statement") 2 else 1),
                if (admission == "edge") source.forward.copy(to = target.copy(path = listOf(f.field("other")))) else source.forward)
            val seeds = if (admission == "absent") SeedIndex.EMPTY else SeedIndex.of(listOf(sourceSeed))
            val actor = core(f, index = 3, seeds = seeds)
            actor.emitAfter(f.manager.zero, f.inst(0), Reach.NORMAL)
            f.drain(actor)
            val expected = buildSet {
                add(f.manager.zero to Conclusion(ZERO_FACT, ExclusionSet.Empty, false))
                if (admission == "matching") add(f.manager.zero to Conclusion(target, ExclusionSet.Empty, false))
            }
            assertEquals(expected, methodEntries(f.ops, actor.summaries.all()), admission)
            assertEquals(if (admission == "matching") 1 else 0, f.cuts.count { it.first == Cut.CALL }, admission)
            assertTrue(f.publications.isEmpty(), "Restricted publication still requires demand")
            assertTrue(f.events.none { it is RunEvent.MarkRequestsIn })
        }
    }
}
