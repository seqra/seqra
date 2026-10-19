package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.*
import kotlin.test.*

/** Ignored end actions cannot create triggers. Independent backward Sink seeds and
 * actual cached source-hit identity checks remain active below. */
class EndFactTriggerTest {
    private val argument = AccessPathBase.Argument(0)
    private val result = AccessPathBase.Return

    @Test fun `an ignored reversed end action cannot seed a confirmed trigger without a handoff seed`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("end"))
            val requirement = f.pattern(argument, listOf(f.field("sink")), Tail.EXACT, t)
            val target = PathFact(result, emptyList(), Tail.EXACT, u)
            val end = MicroEdge.of(PathEdge(ZERO_FACT, target, ExclusionSet.Empty))
            val sink = localSink(listOf(requirement), listOf(end))
            f.exitRules(2, ExitRules(localRules(listOf(sink)), false, emptySet()))
            val known = SinkWitness(sink.alternative, f.key,
                listOf(SinkEdge(f.manager.zero, Layer.NORMAL, f.fact(argument, requirement.fact.path, mark = t))),
                1, emptyList()).also { it.confirmed = true }
            f.vulnerabilities.add(VulnerabilityKey(sink.rule, f.key.method, f.inst(2)), known)
            val core = localCore(f, index = 2)
            val initial = f.manager.initial(f.pattern(result, mark = u))
            core.addInitial(initial)
            f.drain(core, quantum = 1)
            assertEquals(0L, core.triggerSeeds)
            assertNull((core.localServices as RunMethodLocalServices).conjunctions)
            assertEquals(setOf(initial to Conclusion(target, ExclusionSet.Empty, false)),
                methodEntries(f.ops, core.summaries.all()))
            assertTrue(f.vulnerabilities.witnessesOf(2).none())
            assertTrue(f.vulnerabilities.witnessesOf(1).single().second.confirmed)
            assertTrue(core.sourceHits!!.entries().none(), "An end fact is not an unconditional source")
            assertTrue(f.events.isEmpty())
        }
    }

    @Test fun `ignored end actions add no seeds for multiple exact and may requirements`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val v = MarkSlot.Concrete(f.mark("end"))
            val first = f.pattern(argument, listOf(f.field("f")), Tail.EXACT, t)
            val second = f.pattern(AccessPathBase.Argument(1), tail = Tail.ANY, mark = u)
            val target = PathFact(result, emptyList(), Tail.EXACT, v)
            val end = MicroEdge.of(PathEdge(ZERO_FACT, target, ExclusionSet.Empty))
            val a = localSink(listOf(first, second), listOf(end), alternative = 0)
            val b = localSink(listOf(first, second), listOf(end), alternative = 1, rule = a.rule)
            f.exitRules(2, ExitRules(localRules(listOf(a, b)), false, emptySet()))
            val core = localCore(f, index = 2)
            val exact = f.manager.initial(f.pattern(result, mark = v))
            val any = f.manager.initial(f.pattern(result, tail = Tail.ANY, mark = v))
            core.addInitial(exact)
            f.drain(core)
            core.addInitial(any)
            f.drain(core, quantum = 1)
            assertEquals(0L, core.triggerSeeds, "End actions do not create alternative triggers")
            assertEquals(setOf(exact to Conclusion(target, ExclusionSet.Empty, false),
                any to Conclusion(target.copy(tail = Tail.ANY), ExclusionSet.Empty, true)),
                methodEntries(f.ops, core.summaries.all()))
            assertEquals(0, f.cuts.count { it.first == Cut.SEED })
            assertTrue(f.vulnerabilities.witnessesOf(2).none())
            assertTrue(core.sourceHits!!.entries().none())
        }
    }

    @Test fun `ignored end actions create no trigger at entry or exit`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("end"))
            val requirement = f.pattern(argument, mark = t)
            val target = PathFact(result, emptyList(), Tail.EXACT, u)
            val sink = localSink(listOf(requirement), listOf(MicroEdge.of(PathEdge(ZERO_FACT, target, ExclusionSet.Empty))))
            f.entryRules(localRules(listOf(sink)))
            f.exitRules(2, ExitRules(localRules(listOf(sink)), false, emptySet()))
            val core = localCore(f, index = 2)
            val exact = f.manager.initial(f.pattern(result, mark = u))
            val any = f.manager.initial(f.pattern(result, tail = Tail.ANY, mark = u))
            core.addInitial(exact)
            f.drain(core, quantum = 1)
            assertEquals(0L, core.triggerSeeds)
            assertEquals(0, f.cuts.count { it.first == Cut.SEED })
            core.addInitial(any)
            f.drain(core, quantum = 1)
            assertEquals(0L, core.triggerSeeds)
            assertEquals(0, f.cuts.count { it.first == Cut.SEED })
            assertEquals(setOf(exact to Conclusion(target, ExclusionSet.Empty, false),
                any to Conclusion(target.copy(tail = Tail.ANY), ExclusionSet.Empty, true)),
                methodEntries(f.ops, core.summaries.all()))
        }
    }

    @Test fun `an unrelated path or mark does not fire a reversed end fact trigger`(): Unit {
        for (wrongMark in listOf(false, true)) MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("end"))
            val v = MarkSlot.Concrete(f.mark("other"))
            val field = f.field("f")
            val target = PathFact(result, listOf(field), Tail.EXACT, u)
            val sink = localSink(listOf(f.pattern(argument, mark = t)),
                listOf(MicroEdge.of(PathEdge(ZERO_FACT, target, ExclusionSet.Empty))))
            f.exitRules(2, ExitRules(localRules(listOf(sink)), false, emptySet()))
            val input = target.copy(path = if (wrongMark) target.path else listOf(f.field("g")),
                mark = if (wrongMark) v else u)
            val initial = f.manager.initial(Pattern(input, ExclusionSet.Empty))
            val core = localCore(f, index = 2)
            core.addInitial(initial)
            f.drain(core)
            assertEquals(0L, core.triggerSeeds)
            assertTrue(f.cuts.none { it.first == Cut.SEED })
            assertEquals(setOf(initial to Conclusion(input, ExclusionSet.Empty, false)), methodEntries(f.ops, core.summaries.all()))
            assertTrue(core.sourceHits!!.entries().none())
            assertTrue(f.vulnerabilities.witnessesOf(2).none())
        }
    }

    @Test fun `actual cached source forms are hit before duplicate zero storage while an end fact creates no source hit`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("trigger"))
            val rootSource = MicroEdge.of(PathEdge(ZERO_FACT, PathFact(argument, emptyList(), Tail.EXACT, t), ExclusionSet.Empty))
            val fieldSource = MicroEdge.of(PathEdge(ZERO_FACT, PathFact(argument, listOf(f.field("f")), Tail.EXACT, t), ExclusionSet.Empty))
            val end = MicroEdge.of(PathEdge(ZERO_FACT, PathFact(argument, listOf(f.field("end")), Tail.EXACT, t), ExclusionSet.Empty))
            val trigger = f.pattern(AccessPathBase.Argument(1), mark = u)
            val sink = localSink(listOf(trigger), listOf(end))
            f.entryRules(localRules(listOf(sink), StatementSummary(setOf(AccessPathBase.Zero),
                listOf(MicroEdge.of(ZERO_KEEP), rootSource, fieldSource), emptyList(), emptyMap())))
            val core = localCore(f, index = 2)
            val initial = f.manager.initial(f.pattern(argument, tail = Tail.ANY, mark = t))
            core.addInitial(initial)
            f.drain(core, quantum = 1)
            val hits = core.sourceHits!!.entries().toList()
            assertEquals(setOf(Triple(f.key, f.inst(0), rootSource.forward), Triple(f.key, f.inst(0), fieldSource.forward)), hits.toSet())
            assertTrue(hits.any { it.third === rootSource.forward })
            assertTrue(hits.any { it.third === fieldSource.forward })
            assertTrue(hits.none { it.third === end.forward })
            assertEquals(0L, core.triggerSeeds)
            assertEquals(setOf(initial to Conclusion(initial.toPattern().fact, ExclusionSet.Empty, true),
                initial to Conclusion(ZERO_FACT, ExclusionSet.Empty, true)),
                methodEntries(f.ops, core.summaries.all()))
            assertTrue(f.vulnerabilities.witnessesOf(2).none())
        }
    }

    @Test fun `handoff sink seeds use the actual context and statement and are cut before backward start storage`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val path = listOf(f.field("f"), f.field("g"), f.field("h"))
            val exact = f.pattern(argument, path, Tail.EXACT, t)
            val any = f.pattern(AccessPathBase.Argument(1), tail = Tail.ANY, mark = u)
            val rule = localSink(listOf(exact, any)).rule
            val elsewhere = f.pattern(AccessPathBase.This, mark = t)
            val seeds = SeedIndex.of(listOf(Seed.Sink(rule, f.key, f.inst(2), exact),
                Seed.Sink(rule, f.key, f.inst(2), any),
                Seed.Sink(rule, f.store.key(context = "other"), f.inst(2), elsewhere),
                Seed.Sink(rule, f.key, f.inst(1), elsewhere)))
            val core = f.core(index = 2, L = 1, seeds = seeds,
                localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
            core.addRootZero()
            val expected = setOf(Conclusion(ZERO_FACT, ExclusionSet.Empty, false),
                Conclusion(PathFact(argument, path.take(1), Tail.ANY, t), ExclusionSet.Empty, true),
                Conclusion(any.fact, ExclusionSet.Empty, true))
            assertEquals(expected, methodLeaves(f.ops, core.edges.edgesAt(f.inst(2), f.manager.zero).map { it.second }.toList()))
            assertEquals(setOf(Conclusion(exact.fact, ExclusionSet.Empty, false), Conclusion(any.fact, ExclusionSet.Empty, true)),
                methodLeaves(f.ops, f.cuts.filter { it.first == Cut.SEED }.map { it.second }))
            f.drain(core, quantum = 1)
            assertEquals(expected.map { f.manager.zero to it }.toSet(), methodEntries(f.ops, core.summaries.all()))
            assertEquals(0L, core.triggerSeeds)
            assertTrue(core.sourceHits!!.entries().none())
            assertTrue(f.vulnerabilities.witnessesOf(2).none())
        }
    }

    @Test fun `an ignored unconditional end action generates neither zero nor literal seeds`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val t = MarkSlot.Concrete(f.mark("end"))
            val target = PathFact(result, emptyList(), Tail.EXACT, t)
            val sink = localSink(listOf(ZERO_PATTERN), listOf(MicroEdge.of(PathEdge(ZERO_FACT, target, ExclusionSet.Empty))))
            f.exitRules(2, ExitRules(localRules(listOf(sink)), false, emptySet()))
            val initial = f.manager.initial(Pattern(target, ExclusionSet.Empty))
            val core = localCore(f, index = 2)
            core.addInitial(initial)
            f.drain(core)
            assertEquals(setOf(initial to Conclusion(target, ExclusionSet.Empty, false)), methodEntries(f.ops, core.summaries.all()))
            assertEquals(0L, core.triggerSeeds)
            assertTrue(f.cuts.none { it.first == Cut.SEED })
            assertTrue(core.sourceHits!!.entries().none())
            assertTrue(f.vulnerabilities.witnessesOf(2).none())
        }
    }
}
