package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.*
import kotlin.test.*

class CallSourceHitTest {
    private val argument = AccessPathBase.Argument(0)
    private val result = AccessPathBase.Return

    private fun plan(callee: MethodKey, sources: List<MicroEdge> = emptyList(), sink: SinkRule? = null): CallPlan {
        val stages = arrayListOf<CallStage>(
            CallStage.Edges(CallPoint.BEFORE, CallPoint.BOUND, StageKind.BIND_IN,
                StatementSummary.identities(setOf(AccessPathBase.Zero, argument, result))),
            CallStage.Actions(CallPoint.BOUND, CallPoint.ADDED, emptyList()),
            CallStage.Callees(CallPoint.ADDED, CallPoint.RETURNED, listOf(callee)),
            CallStage.Edges(CallPoint.BOUND, CallPoint.RETURNED, StageKind.SOURCES,
                StatementSummary(setOf(AccessPathBase.Zero), sources, emptyList(), emptyMap())),
            CallStage.Edges(CallPoint.RETURNED, CallPoint.AFTER, StageKind.BIND_BACK,
                StatementSummary.identities(setOf(AccessPathBase.Zero, result))),
        )
        if (sink != null) stages += CallStage.Edges(CallPoint.BOUND, CallPoint.RETURNED, StageKind.END_FACTS,
            StatementSummary(emptySet(), sink.endFacts, emptyList(), emptyMap()), Guard.SinkTriggered(sink))
        return CallPlan(setOf(AccessPathBase.Zero, argument, result), stages, listOfNotNull(sink), CallPoint.BEFORE, CallPoint.AFTER)
    }

    private fun core(f: MethodAnalysisFixtures): RunMethodAnalyzer = f.core(index = 4, L = 3,
        localFactory = MethodLocalServicesFactory(::RunMethodLocalServices),
        callFactory = MethodCallServicesFactory(::RunMethodCallServices))

    @Test fun `actual reversed call sources record their original cached edges before the CALL cut and duplicate zero storage`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("source"))
            val root = MicroEdge.of(PathEdge(ZERO_FACT, PathFact(result, emptyList(), Tail.EXACT, t), ExclusionSet.Empty))
            val field = MicroEdge.of(PathEdge(ZERO_FACT, PathFact(result, listOf(f.field("f")), Tail.EXACT, t), ExclusionSet.Empty))
            val callee = f.store.key(context = "callee")
            f.call(1, plan(callee, listOf(root, field)))
            f.onSubscribe = {}
            f.onLink = {}
            val actor = core(f)
            var zeroCut = false
            f.onCut = { point, facts ->
                if (point == Cut.CALL && facts is Reach) {
                    zeroCut = true
                    assertTrue(actor.sourceHits!!.entries().any { it.first == actor.key && it.second === f.inst(1) &&
                        (it.third === root.forward || it.third === field.forward) })
                }
            }
            val premise = f.manager.initial(f.pattern(result, tail = Tail.ANY, mark = t))
            actor.addInitial(premise)
            f.drain(actor, quantum = 1)
            assertTrue(zeroCut)
            val expectedHits = setOf(Triple(f.key, f.inst(1), root.forward), Triple(f.key, f.inst(1), field.forward))
            assertEquals(expectedHits, actor.sourceHits!!.entries().toSet())
            assertTrue(actor.sourceHits!!.entries().any { it.third === root.forward })
            assertTrue(actor.sourceHits!!.entries().any { it.third === field.forward })
            assertEquals(setOf(premise to Conclusion(premise.toPattern().fact, ExclusionSet.Empty, true),
                premise to Conclusion(ZERO_FACT, ExclusionSet.Empty, true)), methodEntries(f.ops, actor.summaries.all()))
            val summaries = methodEntries(f.ops, actor.summaries.all())
            actor.emitAfter(premise, f.inst(2), f.fact(result, tail = Tail.ANY, mark = t, layer = Layer.DEMAND))
            f.drain(actor, quantum = 1)
            assertEquals(expectedHits, actor.sourceHits!!.entries().toSet())
            assertEquals(summaries, methodEntries(f.ops, actor.summaries.all()))
            assertEquals(0L, actor.recordCrossings)
            assertEquals(0L, actor.triggerSeeds)
        }
    }

    @Test fun `a caller source hit requires an actual matching reversed source output`(): Unit {
        for (matching in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("source"))
            val u = MarkSlot.Concrete(f.mark("other"))
            val target = PathFact(result, emptyList(), Tail.EXACT, t)
            val source = MicroEdge.of(PathEdge(ZERO_FACT, target, ExclusionSet.Empty))
            f.call(1, plan(f.store.key(context = "callee"), listOf(source)))
            f.onSubscribe = {}
            f.onLink = {}
            val actor = core(f)
            val premise = f.manager.initial(f.pattern(result, mark = if (matching) t else u))
            actor.addInitial(premise)
            f.drain(actor)
            assertEquals(if (matching) setOf(Triple(f.key, f.inst(1), source.forward)) else emptySet(), actor.sourceHits!!.entries().toSet())
            val expected = buildSet {
                add(premise to Conclusion(premise.toPattern().fact, ExclusionSet.Empty, false))
                if (matching) add(premise to Conclusion(ZERO_FACT, ExclusionSet.Empty, false))
            }
            assertEquals(expected, methodEntries(f.ops, actor.summaries.all()))
            assertEquals(0L, actor.triggerSeeds)
        }
    }

    @Test fun `actual restricted native and opposite record replay cross the callee without synthesizing a source hit`(): Unit {
        for (view in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("callee source"))
            val u = MarkSlot.Concrete(f.mark("returned"))
            val callee = f.store.key(context = "callee")
            val callerSourceTarget = PathFact(if (view) result else argument, emptyList(), Tail.EXACT, if (view) t else u)
            val callerSource = MicroEdge.of(PathEdge(ZERO_FACT, callerSourceTarget, ExclusionSet.Empty))
            f.call(1, plan(callee, listOf(callerSource)))
            val initial = f.manager.initial(f.pattern(result, mark = t))
            val record = if (view) Record(callee, Direction.FORWARD, f.manager.zero, f.fact(result, mark = t))
                else Record(callee, Direction.BACKWARD, initial, f.fact(argument, mark = u))
            f.records.add(record)
            val actor = core(f)
            val storage = SummaryStorage(callee, f.manager, f.ops, ProtocolSteps.Inline)
            val manager = SubscriptionManager(object : SubscriptionPort {
                override val config = actor.config
                override val ops = f.ops
                override val steps = ProtocolSteps.Inline
                override fun summaryStorage(key: MethodKey): SummaryStorage { assertEquals(callee, key); return storage }
                override fun applier(caller: MethodKey): SummaryApplier { assertEquals(actor.key, caller); return actor }
                override fun post(event: RunEvent.Delivery): Boolean = error("Record replay publishes no current summary")
            })
            val part = Subscription(callee, CallerRef(f.key, initial, Layer.NORMAL, f.inst(1)), Layer.NORMAL, f.fact(result, mark = t))
            manager.subscribe(part)
            f.drain(actor)
            assertEquals(setOf(initial to Conclusion(if (view) ZERO_FACT else PathFact(argument, emptyList(), Tail.EXACT, u),
                ExclusionSet.Empty, false)), methodEntries(f.ops, actor.summaries.all()))
            assertEquals(1L, actor.recordCrossings)
            assertTrue(actor.sourceHits!!.entries().none(), "Neither persisted callee edge nor caller source is executed by a record reader")
            manager.subscribe(part)
            f.drain(actor)
            assertEquals(1L, actor.recordCrossings)
            assertTrue(actor.sourceHits!!.entries().none())
            manager.freeze()
        }
    }

    @Test fun `an ignored call end fact creates neither a trigger nor a source hit`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("end"))
            val u = MarkSlot.Concrete(f.mark("sink"))
            val target = PathFact(result, emptyList(), Tail.EXACT, t)
            val end = MicroEdge.of(PathEdge(ZERO_FACT, target, ExclusionSet.Empty))
            val requirement = f.pattern(argument, mark = u)
            val sink = localSink(listOf(requirement), listOf(end))
            f.call(1, plan(f.store.key(context = "callee"), sink = sink))
            f.onSubscribe = {}
            f.onLink = {}
            val actor = core(f)
            val exact = f.manager.initial(f.pattern(result, mark = t))
            actor.addInitial(exact)
            f.drain(actor)
            assertEquals(0L, actor.triggerSeeds)
            assertTrue(actor.sourceHits!!.entries().none())
            assertTrue(actor.summaries.all().none(), "The callee has supplied no return summary")
            val any = f.manager.initial(f.pattern(result, tail = Tail.ANY, mark = t))
            actor.addInitial(any)
            f.drain(actor, quantum = 1)
            assertEquals(0L, actor.triggerSeeds)
            assertEquals(0, f.cuts.count { it.first == Cut.SEED })
            assertTrue(actor.summaries.all().none(), "Ignored end actions cannot supply a missing call return")
            assertTrue(actor.sourceHits!!.entries().none())
            assertTrue(f.vulnerabilities.witnessesOf(2).none())
        }
    }
}
