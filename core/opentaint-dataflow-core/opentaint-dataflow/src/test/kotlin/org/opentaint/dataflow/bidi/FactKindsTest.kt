package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.*
import kotlin.test.*

class FactKindsTest {
    private val x = AccessPathBase.Argument(0)
    private val z = AccessPathBase.Argument(1)
    private val y = AccessPathBase.Return

    @Test fun `method insertion enforces both directions of K6 and multi-premise TAINT before mutation`(): Unit {
        val f = MethodAnalysisFixtures()
        f.shared.use {
            val core = f.core(index = 3)
            val mt = MarkSlot.Concrete(f.mark("T"))
            val concrete = f.manager.initial(f.pattern(x, mark = mt))
            val flow = f.manager.initial(f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR))
            val other = f.manager.initial(f.pattern(z, mark = mt))
            val nd = f.manager.union(concrete, other)
            val taint = f.fact(y, mark = mt)
            val abstract = f.fact(y, tail = Tail.STAR, mark = MarkSlot.STAR)
            for ((premise, input) in listOf(flow to taint, flow to Reach.NORMAL, concrete to abstract,
                f.manager.zero to abstract, nd to abstract, nd to Reach.NORMAL, nd to Reach.DEMAND)) {
                assertFails { core.emitAfter(premise, f.inst(0), input) }
                assertTrue(core.edges.edgesAt(f.inst(1), premise).none())
            }
            assertEquals(0L, core.edgeDeltas)
            assertEquals(0L, core.demandLayerEdges)
        }
    }

    @Test fun `REACH keeps concrete backward and zero premises and both layers independently`(): Unit {
        val f = MethodAnalysisFixtures()
        f.shared.use {
            val core = f.core(index = 2)
            val concrete = f.manager.initial(f.pattern(y))
            for (premise in listOf(f.manager.zero, concrete)) for (layer in Layer.entries) {
                core.emitAfter(premise, f.inst(2), Reach.of(layer))
                core.emitAfter(premise, f.inst(2), Reach.of(layer))
                assertSame(Reach.of(layer), core.edges.edgesAt(f.inst(1), premise)
                    .single { it.second.layer == layer }.second)
            }
            assertEquals(4L, core.edgeDeltas)
            assertEquals(2L, core.demandLayerEdges)
        }
    }

    @Test fun `must tail exclusions remain distinct premise keys while FLOW annotations remain complete stored values`(): Unit {
        val f = MethodAnalysisFixtures()
        f.shared.use {
            val core = f.core(index = 3)
            val g = f.field("g")
            val h = f.field("h")
            val t = f.mark("T")
            val u = f.mark("U")
            val mt = MarkSlot.Concrete(t)
            val prems = listOf(
                f.manager.initial(f.pattern(x, tail = Tail.ANY, mark = mt)),
                f.manager.initial(f.pattern(x, tail = Tail.ANY_TAINT, mark = mt)),
                f.manager.initial(f.pattern(x, tail = Tail.ANY_TAINT, mark = mt, exclusion = ExclusionSet.of(intArrayOf(g)))),
                f.manager.initial(f.pattern(x, tail = Tail.ANY_TAINT, mark = mt, exclusion = ExclusionSet.of(intArrayOf(h)))),
            )
            prems.forEach(core::addInitial)
            assertEquals(prems.toSet(), core.initials.all().toSet())
            for (premise in prems) {
                val initialFacts = core.edges.edgesAt(f.inst(0), premise).single().second
                assertEquals(setOf(Conclusion(premise.toPattern().fact, premise.exclusion, premise.tail == Tail.ANY)),
                    methodLeaves(f.ops, listOf(initialFacts)))
            }
            val flowPremise = f.manager.initial(f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR))
            val flowValues = listOf(
                f.fact(y, tail = Tail.STAR, mark = MarkSlot.Star(MarkSet(intArrayOf(t.id))), exclusion = ExclusionSet.of(intArrayOf(g))),
                f.fact(y, tail = Tail.STAR, mark = MarkSlot.Star(MarkSet(intArrayOf(u.id))), exclusion = ExclusionSet.of(intArrayOf(h))),
            )
            flowValues.forEach { core.emitAfter(flowPremise, f.inst(0), it) }
            assertEquals(methodLeaves(f.ops, flowValues),
                methodLeaves(f.ops, core.edges.edgesAt(f.inst(1), flowPremise).map { it.second }.toList()))
            assertEquals(1L, core.demandLayerEdges)
        }
    }

    @Test fun `accepted demand link edge and summary deltas each count once and exact duplicates count nothing`(): Unit {
        val f = MethodAnalysisFixtures()
        f.shared.use {
            val mt = MarkSlot.Concrete(f.mark("T"))
            val me = MicroEdge.of(PathEdge(PathFact(x, emptyList(), Tail.EXACT, mt),
                PathFact(y, emptyList(), Tail.ANY, mt), ExclusionSet.Empty))
            f.statement(1, StatementSummary(setOf(x), listOf(me), emptyList(), emptyMap()))
            val demand = DemandStore.Builder(f.manager).apply {
                add(f.key, DemandPattern(f.pattern(x, tail = Tail.ANY, mark = mt), f.pattern(y, tail = Tail.ANY, mark = mt)))
            }.build()
            val core = f.core(index = 3, demand = demand)
            val ref = CallerRef(f.key, f.manager.zero, Layer.NORMAL, f.inst(1))
            val added = f.fact(x, mark = mt, layer = Layer.DEMAND)
            f.admit(core, ref, Layer.DEMAND, added)
            f.drain(core, quantum = 1)
            val initial = core.initials.all().single()
            assertEquals(3L, core.demandLayerEdges, "One accepted added fact, one method delta and one summary delta")
            assertEquals(3L, core.edgeDeltas, "Initial method edge, generated method edge and raw summary")
            val expected = Conclusion(PathFact(y, emptyList(), Tail.ANY, mt), ExclusionSet.Empty, true)
            assertEquals(setOf(initial to expected), methodEntries(f.ops, core.summaries.all()))
            assertEquals(setOf(initial to expected), methodEntries(f.ops, core.summaries.demandEdges()))
            assertEquals(setOf(initial to expected), methodEntries(f.ops, f.publications.flatten().asSequence()
                .map { it.premise to it.conclusion }))
            f.admit(core, ref, Layer.DEMAND, added)
            f.drain(core)
            assertEquals(3L, core.demandLayerEdges)
            assertEquals(3L, core.edgeDeltas)
            assertEquals(2, f.addedLinks.size, "Both deliveries are observed; only emission is deduplicated")
        }
    }

    @Test fun `demand link alone is counted when the statement kills every emitted initial result`(): Unit {
        val f = MethodAnalysisFixtures()
        f.shared.use {
            f.statement(1, StatementSummary(setOf(x), emptyList(), emptyList(), emptyMap()))
            val core = f.core()
            val ref = CallerRef(f.key, f.manager.zero, Layer.NORMAL, f.inst(1))
            val added = f.fact(x, layer = Layer.DEMAND)
            f.admit(core, ref, Layer.DEMAND, added)
            f.drain(core, quantum = 1)
            assertEquals(1L, core.demandLayerEdges)
            assertEquals(1L, core.edgeDeltas, "Only the killed ordinary start is counted")
            assertTrue(core.summaries.all().none(), "Shared static defaults do not fabricate method summaries")
            assertTrue(f.publications.isEmpty())
            f.admit(core, ref, Layer.DEMAND, added)
            assertEquals(1L, core.demandLayerEdges)
            assertEquals(1L, core.edgeDeltas, "Only the killed ordinary start is counted")
        }
    }

    @Test fun `restriction narrows publication without rewriting the raw summary or raw selection`(): Unit {
        val f = MethodAnalysisFixtures()
        f.shared.use {
            val field = f.field("f")
            val mt = MarkSlot.Concrete(f.mark("T"))
            val demand = DemandStore.Builder(f.manager).apply {
                add(f.key, DemandPattern(f.pattern(x, tail = Tail.ANY, mark = mt), f.pattern(y, listOf(field), Tail.EXACT, mt)))
            }.build()
            f.statement(1, StatementSummary(setOf(x), listOf(MicroEdge.of(PathEdge(
                PathFact(x, emptyList(), Tail.EXACT, mt), PathFact(y, emptyList(), Tail.ANY, mt), ExclusionSet.Empty))),
                emptyList(), emptyMap()))
            val core = f.core(index = 3, demand = demand)
            val initial = f.manager.initial(f.pattern(x, mark = mt))
            core.addInitial(initial)
            f.drain(core, quantum = 1)
            val raw = Conclusion(PathFact(y, emptyList(), Tail.ANY, mt), ExclusionSet.Empty, true)
            val piece = Conclusion(PathFact(y, listOf(field), Tail.EXACT, mt), ExclusionSet.Empty, true)
            assertEquals(setOf(initial to raw), methodEntries(f.ops, core.summaries.all()))
            assertEquals(setOf(initial to piece), methodEntries(f.ops, core.summaries.demandEdges()))
            assertEquals(setOf(initial to piece), methodEntries(f.ops, f.publications.flatten().asSequence()
                .map { it.premise to it.conclusion }))
            assertEquals(2L, core.demandLayerEdges)
        }
    }

    @Test fun `raw demand summaries remain when missing exit demand or wrong exit mark rejects every publication`(): Unit {
        for (missingExit in listOf(false, true)) {
            val f = MethodAnalysisFixtures()
            f.shared.use {
                val mt = MarkSlot.Concrete(f.mark("T"))
                val mu = MarkSlot.Concrete(f.mark("U"))
                val demand = DemandStore.Builder(f.manager).apply {
                    add(f.key, DemandPattern(f.pattern(x, tail = Tail.ANY, mark = mt),
                        if (missingExit) null else f.pattern(y, tail = Tail.ANY, mark = mu)))
                }.build()
                f.statement(1, StatementSummary(setOf(x), listOf(MicroEdge.of(PathEdge(
                    PathFact(x, emptyList(), Tail.EXACT, mt), PathFact(y, emptyList(), Tail.ANY, mt), ExclusionSet.Empty))),
                    emptyList(), emptyMap()))
                val core = f.core(index = 3, demand = demand)
                val initial = f.manager.initial(f.pattern(x, mark = mt))
                core.addInitial(initial)
                f.drain(core)
                assertEquals(setOf(initial to Conclusion(PathFact(y, emptyList(), Tail.ANY, mt), ExclusionSet.Empty, true)),
                    methodEntries(f.ops, core.summaries.all()))
                assertTrue(core.summaries.demandEdges().none())
                assertTrue(f.publications.isEmpty())
                assertEquals(2L, core.demandLayerEdges)
            }
        }
    }

    @Test fun `normal multi-premise publication retains the full key and normal layer while backward zero bypasses reduction`(): Unit {
        val f = MethodAnalysisFixtures()
        f.shared.use {
            val mt = MarkSlot.Concrete(f.mark("T"))
            val mu = MarkSlot.Concrete(f.mark("U"))
            val first = f.manager.initial(f.pattern(x, mark = mt))
            val second = f.manager.initial(f.pattern(z, mark = mu))
            val premise = f.manager.union(f.manager.zero, f.manager.union(first, second))
            assertEquals(setOf(first, second), premise.members.toSet())
            val demand = DemandStore.Builder(f.manager).apply {
                add(f.key, DemandPattern(f.pattern(x, tail = Tail.ANY, mark = mt), f.pattern(y, tail = Tail.ANY, mark = mt)))
                add(f.key, DemandPattern(f.pattern(z, tail = Tail.ANY, mark = mu), f.pattern(y, tail = Tail.ANY, mark = mt)))
            }.build()
            val core = f.core(index = 3, demand = demand)
            val facts = f.fact(y, mark = mt)
            core.emitAfter(premise, f.inst(2), facts)
            f.drain(core)
            val expected = Conclusion(PathFact(y, emptyList(), Tail.EXACT, mt), ExclusionSet.Empty, false)
            assertEquals(setOf(premise to expected), methodEntries(f.ops, core.summaries.all()))
            assertEquals(setOf(premise to expected), methodEntries(f.ops, core.summaries.demandEdges()))
            assertTrue(f.publications.flatten().all { it.premise === premise && it.layer == Layer.NORMAL })
            assertEquals(0L, core.demandLayerEdges)

            f.published.clear()
            val back = f.core(index = 2)
            back.emitAfter(f.manager.zero, f.inst(0), f.fact(x, mark = mt))
            back.emitAfter(first, f.inst(0), f.fact(x, mark = mt))
            f.drain(back)
            val backwards = Conclusion(PathFact(x, emptyList(), Tail.EXACT, mt), ExclusionSet.Empty, false)
            assertEquals(setOf(f.manager.zero to backwards, first to backwards), methodEntries(f.ops, back.summaries.all()))
            assertEquals(setOf(f.manager.zero to backwards), methodEntries(f.ops, back.summaries.demandEdges()))
            assertEquals(setOf(f.manager.zero to backwards), methodEntries(f.ops, f.publications.flatten().asSequence()
                .map { it.premise to it.conclusion }))
            assertEquals(0L, back.demandLayerEdges)
        }
    }

    @Test fun `second caller of an existing DEMAND fact adds one link but no repeated initial work or publication`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val mt = MarkSlot.Concrete(f.mark("T"))
            f.statement(1, StatementSummary(setOf(x), listOf(MicroEdge.of(PathEdge(
                PathFact(x, emptyList(), Tail.EXACT, mt), PathFact(y, emptyList(), Tail.ANY, mt), ExclusionSet.Empty))),
                emptyList(), emptyMap()))
            val demand = DemandStore.Builder(f.manager).apply {
                add(f.key, DemandPattern(f.pattern(x, tail = Tail.ANY, mark = mt), f.pattern(y, tail = Tail.ANY, mark = mt)))
            }.build()
            val core = f.core(index = 3, demand = demand)
            val first = CallerRef(f.key, f.manager.zero, Layer.NORMAL, f.inst(1))
            val second = first.copy(caller = f.store.key(context = "second caller"))
            val added = f.fact(x, mark = mt, layer = Layer.DEMAND)
            f.admit(core, first, Layer.DEMAND, added)
            f.drain(core, quantum = 1)
            val initials = core.initials.all().toSet()
            val processed = f.processed.toList()
            val raw = methodEntries(f.ops, core.summaries.all())
            val selected = methodEntries(f.ops, core.summaries.demandEdges())
            val publications = f.publications.toList()
            val previousSteps = core.steps
            val previousEdges = core.edgeDeltas
            val previousDemand = core.demandLayerEdges
            f.admit(core, second, Layer.DEMAND, added)
            f.drain(core, quantum = 1)
            assertEquals(previousDemand, core.demandLayerEdges, "Demand input presence is one scalar per actor, not a retained caller-group count")
            assertEquals(previousEdges, core.edgeDeltas)
            assertEquals(previousSteps, core.steps)
            assertEquals(initials, core.initials.all().toSet())
            assertEquals(processed, f.processed)
            assertEquals(raw, methodEntries(f.ops, core.summaries.all()))
            assertEquals(selected, methodEntries(f.ops, core.summaries.demandEdges()))
            assertEquals(publications, f.publications)
            assertEquals(listOf(first, second), f.addedLinks.map { it.first })
            assertEquals(setOf(first, second), f.links(core).asSequence().map { it.caller }.toSet())
            f.admit(core, second, Layer.DEMAND, added)
            f.drain(core)
            assertEquals(previousDemand, core.demandLayerEdges, "Demand input presence is one scalar per actor, not a retained caller-group count")
            assertEquals(3, f.addedLinks.size)
        }
    }
}
