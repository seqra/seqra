package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import kotlin.test.*

class CutPointTest {
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return

    @Test fun `generated statement result is cut before storage and keeps its exact premise`(): Unit {
        val f = MethodAnalysisFixtures()
        f.shared.use {
            val field = f.field("f")
            val g = f.field("g")
            val mt = MarkSlot.Concrete(f.mark("T"))
            val me = MicroEdge.of(PathEdge(PathFact(x, emptyList(), Tail.EXACT, mt),
                PathFact(y, listOf(field, g), Tail.EXACT, mt), ExclusionSet.Empty))
            f.statement(1, StatementSummary(setOf(x), listOf(me), emptyList(), emptyMap()))
            val core = f.core(index = 3, L = 1)
            val initial = f.manager.initial(f.pattern(x, mark = mt))
            core.addInitial(initial)
            f.drain(core, quantum = 1)
            val before = f.cuts.filter { it.first == Cut.STATEMENT }.map { it.second }
            assertEquals(setOf(Conclusion(PathFact(y, listOf(field, g), Tail.EXACT, mt), ExclusionSet.Empty, false)),
                methodLeaves(f.ops, before))
            assertEquals(setOf(Conclusion(PathFact(y, listOf(field), Tail.ANY, mt), ExclusionSet.Empty, true)),
                methodLeaves(f.ops, core.edges.edgesAt(f.inst(2), initial).map { it.second }.toList()))
            assertTrue(f.processed.all { it.premise === initial })
            assertTrue(f.requests.isEmpty())
        }
    }

    @Test fun `result filters run before statement cut and rejected deep leaves never reach the cut callback`(): Unit {
        val f = MethodAnalysisFixtures()
        f.shared.use {
            val field = f.field("f")
            val g = f.field("g")
            val mt = MarkSlot.Concrete(f.mark("T"))
            val edges = listOf(listOf(field), listOf(field, g)).map { path -> MicroEdge.of(PathEdge(
                PathFact(x, emptyList(), Tail.EXACT, mt), PathFact(y, path, Tail.EXACT, mt), ExclusionSet.Empty)) }
            f.statement(1, StatementSummary(setOf(x), edges, emptyList(), emptyMap(), mapOf(y to rejecting(f, g))))
            val core = f.core(index = 3, L = 1)
            val premise = f.manager.initial(f.pattern(x, mark = mt))
            core.addInitial(premise)
            f.drain(core, quantum = 1)
            val expected = Conclusion(PathFact(y, listOf(field), Tail.EXACT, mt), ExclusionSet.Empty, false)
            assertEquals(setOf(expected), methodLeaves(f.ops, f.cuts.filter { it.first == Cut.STATEMENT }.map { it.second }))
            assertEquals(setOf(premise to expected), methodEntries(f.ops, core.summaries.all().withoutPrecomputedStaticDefault()))
            assertEquals(0L, core.demandLayerEdges)
            assertTrue(f.requests.isEmpty())
        }
    }

    @Test fun `untouched propagation keeps the processed Facts identity and bypasses operand filters storage and statement cuts`(): Unit {
        val f = MethodAnalysisFixtures()
        f.shared.use {
            val field = f.field("f")
            val mt = MarkSlot.Concrete(f.mark("T"))
            f.statement(1, StatementSummary(setOf(y), emptyList(), emptyList(), mapOf(x to rejecting(f, field))))
            val core = f.core(index = 3)
            val premise = f.manager.initial(f.pattern(x, listOf(field), mark = mt))
            val input = f.fact(x, listOf(field), mark = mt)
            core.emitUnchanged(EdgeDelta(premise, f.inst(0), input))
            f.drain(core, quantum = 1)
            assertEquals(listOf(1, 2), f.processed.map { it.node.location.index })
            assertTrue(f.processed.all { it.premise === premise && it.facts === input })
            assertTrue(core.edges.edgesAt(f.inst(1), premise).none())
            assertTrue(core.edges.edgesAt(f.inst(2), premise).none())
            assertTrue(f.cuts.none { it.first == Cut.STATEMENT })
            assertEquals(setOf(premise to Conclusion(PathFact(x, listOf(field), Tail.EXACT, mt), ExclusionSet.Empty, false)),
                methodEntries(f.ops, core.summaries.all().withoutPrecomputedStaticDefault()))
            assertEquals(1L, core.edgeDeltas, "Only the accepted end summary is stored")
        }
    }

    @Test fun `method cut points preserve premise and marks and demote overflow without requests`(): Unit {
        val f = MethodAnalysisFixtures()
        f.shared.use {
            val core = f.core(index = 3, L = 1)
            val field = f.field("f")
            val g = f.field("g")
            val h = f.field("h")
            val t = f.mark("T")
            val u = f.mark("U")
            val cases = listOf(
                f.fact(y, listOf(field, g), tail = Tail.ANY_TAINT, mark = MarkSlot.Concrete(t),
                    exclusion = ExclusionSet.of(h)) to f.manager.initial(f.pattern(x, mark = MarkSlot.Concrete(t))),
                f.fact(y, listOf(field, g), tail = Tail.STAR, mark = MarkSlot.Star(MarkSet(intArrayOf(u.id))),
                    exclusion = ExclusionSet.of(h)) to f.manager.initial(f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR)),
            )
            for (point in listOf(Cut.STATEMENT, Cut.ENTRY_RULES, Cut.EXIT_RULES, Cut.SEED)) for ((input, premise) in cases) {
                val output = ArrayList<Pair<PremiseKey, Facts>>()
                core.cut(point, premise, input) { pr, facts -> output += pr to facts }
                assertSame(input, f.cuts.last().second)
                assertEquals(point, f.cuts.last().first)
                assertTrue(output.all { it.first === premise })
                val mark = if (input is FlowTree) MarkSlot.Star(MarkSet(intArrayOf(u.id))) else MarkSlot.Concrete(t)
                assertEquals(setOf(Conclusion(PathFact(y, listOf(field), Tail.ANY, mark), ExclusionSet.Empty, true)),
                    methodLeaves(f.ops, output.map { it.second }))
            }
            assertTrue(f.requests.isEmpty())
        }
    }

    @Test fun `entry source and context filters precede entry cuts while backward entry reversal drops both filter maps`(): Unit {
        val f = MethodAnalysisFixtures()
        f.shared.use {
            val field = f.field("f")
            val g = f.field("g")
            val mt = MarkSlot.Concrete(f.mark("T"))
            val short = MicroEdge.of(PathEdge(ZERO_FACT, PathFact(y, listOf(field), Tail.EXACT, mt), ExclusionSet.Empty))
            val deep = MicroEdge.of(PathEdge(ZERO_FACT, PathFact(y, listOf(field, g), Tail.EXACT, mt), ExclusionSet.Empty))
            f.entryRules(RuleStatement(StatementSummary(setOf(AccessPathBase.Zero, x),
                listOf(MicroEdge.of(ZERO_KEEP), identityEdge(x), short, deep), emptyList(),
                mapOf(x to rejecting(f, g)), mapOf(y to rejecting(f, g))), StatementSummary.EMPTY, emptyList()))
            val initialX = f.manager.initial(f.pattern(x, listOf(g), mark = mt))
            // This isolates generic boundary/filter order using a later forward
            // run, whose initial grammar permits the explicit x.g premise.
            val sourceSeeds = SeedIndex.of(listOf(Seed.Source(f.key, f.inst(0), short.forward),
                Seed.Source(f.key, f.inst(0), deep.forward)))
            val forward = f.core(index = 3, L = 1, seeds = sourceSeeds)
            forward.addRootZero()
            forward.addInitial(initialX)
            f.drain(forward, quantum = 1)
            assertTrue(forward.edges.edgesAt(f.inst(0), initialX).none(), "The context operand filter rejects x.g")
            val entryCuts = f.cuts.filter { it.first == Cut.ENTRY_RULES }.map { it.second }
            assertEquals(setOf(
                Conclusion(ZERO_FACT, ExclusionSet.Empty, false),
                Conclusion(PathFact(y, listOf(field), Tail.EXACT, mt), ExclusionSet.Empty, false),
            ), methodLeaves(f.ops, entryCuts))
            assertEquals(setOf(f.manager.zero to Conclusion(ZERO_FACT, ExclusionSet.Empty, false),
                f.manager.zero to Conclusion(PathFact(y, listOf(field), Tail.EXACT, mt), ExclusionSet.Empty, false)),
                methodEntries(f.ops, forward.summaries.all().withoutPrecomputedStaticDefault()))

            f.cuts.clear()
            f.processed.clear()
            f.published.clear()
            val backward = f.core(index = 2, L = 3)
            val initialY = f.manager.initial(f.pattern(y, listOf(field, g), mark = mt))
            backward.addInitial(initialY)
            backward.addInitial(initialX)
            f.drain(backward, quantum = 1)
            val backExpected = setOf(
                initialY to Conclusion(ZERO_FACT, ExclusionSet.Empty, false),
                initialY to Conclusion(PathFact(y, listOf(field, g), Tail.EXACT, mt), ExclusionSet.Empty, false),
                initialX to Conclusion(PathFact(x, listOf(g), Tail.EXACT, mt), ExclusionSet.Empty, false),
            )
            assertEquals(backExpected, methodEntries(f.ops, backward.summaries.all().withoutPrecomputedStaticDefault()))
            assertEquals(setOf(Triple(f.key, f.inst(0), deep.forward)), assertNotNull(backward.sourceHits).entries().toSet())
            assertTrue(f.cuts.any { it.first == Cut.ENTRY_RULES && it.second === Reach.NORMAL })
            assertTrue(f.requests.isEmpty())
        }
    }

    @Test fun `backward exit seed takes its SEED cut before start storage and uses the original zero premise`(): Unit {
        val f = MethodAnalysisFixtures()
        f.shared.use {
            val field = f.field("f")
            val g = f.field("g")
            val h = f.field("h")
            val mt = MarkSlot.Concrete(f.mark("T"))
            val rule = object : CommonTaintConfigurationSink {
                override val id = "seed-cut"
                override val meta = object : CommonTaintConfigurationSinkMeta {
                    override val message = "seed cut"
                    override val severity = CommonTaintConfigurationSinkMeta.Severity.Error
                }
            }
            val requirement = f.pattern(y, listOf(field, g, h), Tail.EXACT, mt)
            val seeds = SeedIndex.of(listOf(Seed.Sink(rule, f.key, f.inst(2), requirement)))
            val core = f.core(index = 2, L = 1, seeds = seeds)
            core.addRootZero()
            val seedCuts = f.cuts.filter { it.first == Cut.SEED }.map { it.second }
            assertEquals(setOf(Conclusion(requirement.fact, ExclusionSet.Empty, false)), methodLeaves(f.ops, seedCuts))
            val expected = setOf(Conclusion(ZERO_FACT, ExclusionSet.Empty, false),
                Conclusion(PathFact(y, listOf(field), Tail.ANY, mt), ExclusionSet.Empty, true))
            assertEquals(expected, methodLeaves(f.ops, core.edges.edgesAt(f.inst(2), f.manager.zero).map { it.second }.toList()))
            f.drain(core, quantum = 1)
            assertEquals(setOf(f.manager.zero), core.initials.all().toSet())
            assertEquals(expected.map { f.manager.zero to it }.toSet(), methodEntries(f.ops, core.summaries.all().withoutPrecomputedStaticDefault()))
            assertEquals(2L, core.demandLayerEdges)
            assertTrue(f.requests.isEmpty())
        }
    }

    @Test fun `exit rule generated overflow takes the EXIT_RULES cut before becoming a DEMAND summary`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val field = f.field("f")
            val g = f.field("g")
            val mt = MarkSlot.Concrete(f.mark("T"))
            val source = MicroEdge.of(PathEdge(ZERO_FACT, PathFact(y, listOf(field, g), Tail.EXACT, mt), ExclusionSet.Empty))
            f.exitRules(2, ExitRules(RuleStatement(StatementSummary(setOf(AccessPathBase.Zero),
                listOf(MicroEdge.of(ZERO_KEEP), source), emptyList(), emptyMap()), StatementSummary.EMPTY, emptyList()),
                false, emptySet()))
            val core = f.core(L = 1)
            core.addRootZero()
            f.drain(core, quantum = 1)
            val generated = f.cuts.filter { it.first == Cut.EXIT_RULES && it.second.base == y }.map { it.second }
            assertEquals(setOf(Conclusion(PathFact(y, listOf(field, g), Tail.EXACT, mt), ExclusionSet.Empty, false)),
                methodLeaves(f.ops, generated))
            val expected = setOf<Pair<PremiseKey, Conclusion>>(
                f.manager.zero to Conclusion(ZERO_FACT, ExclusionSet.Empty, false),
                f.manager.zero to Conclusion(PathFact(y, listOf(field), Tail.ANY, mt), ExclusionSet.Empty, true),
            )
            assertEquals(expected, methodEntries(f.ops, core.summaries.all().withoutPrecomputedStaticDefault()))
            assertEquals(expected, methodEntries(f.ops, f.publications.flatten().withoutPrecomputedStaticDefault().asSequence().map { it.premise to it.conclusion }))
            assertEquals(expected, methodEntries(f.ops, core.summaries.demandEdges().withoutPrecomputedStaticDefault()),
                "Run-1 also retains normal rows as guidance")
            assertEquals(1L, core.demandLayerEdges, "The boundary result adds a summary delta without a successor edge")
            assertTrue(f.cuts.none { it.first == Cut.STATEMENT })
            assertTrue(f.requests.isEmpty())
        }
    }

    private fun rejecting(f: MethodAnalysisFixtures, index: Int): TypeFilter {
        val rejected = f.manager.accessors.accessor(index)
        val may = object : FactTypeChecker.FactApFilter {
            override fun check(accessor: Accessor): FactTypeChecker.FilterResult =
                if (accessor == rejected) FactTypeChecker.FilterResult.Reject else FactTypeChecker.FilterResult.FilterNext(this)
        }
        return TypeFilter(may)
    }
}
