package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.HandOffFixtures
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import kotlin.test.*

class HandOffTest {
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Argument(1)
    private val returned = AccessPathBase.Return
    private val bases = listOf(AccessPathBase.Zero, x, y, returned, AccessPathBase.This, AccessPathBase.ClassStatic)

    /** Root queries enumerate each stated base. Remove only the universally implicit zero pattern. */
    private fun explicit(f: HandOffFixtures, handOff: HandOff, key: MethodKey): Set<DemandPattern> =
        bases.flatMap { handOff.demand.near(key, it, null) }.filterTo(LinkedHashSet()) {
            it != DemandPattern(f.manager.zero.toPattern(), null)
        }

    /** The run-1 handoff adds one symbolic static guide for each eligible group. */
    private fun implicitStatics(f: HandOffFixtures): Set<DemandPattern> = if (f.index != 1) emptySet() else
        f.manager.marks.groupAtoms().mapTo(LinkedHashSet()) { group ->
            val mark = MarkSlot.Concrete(group)
            DemandPattern(f.pattern(STATIC, tail = Tail.ANY, mark = mark),
                f.pattern(STATIC, tail = Tail.STAR, mark = mark))
        }

    private fun rule(id: String): RuleId = object : CommonTaintConfigurationSink {
        override val id = id
        override val meta = object : CommonTaintConfigurationSinkMeta {
            override val message = id
            override val severity = CommonTaintConfigurationSinkMeta.Severity.Warning
        }
    }

    private fun summary(vararg edges: PathEdge): StatementSummary =
        StatementSummary(edges.map { it.from.base }.toSet(), edges.map(MicroEdge::of), emptyList(), emptyMap())

    private fun rules(sinks: List<SinkRule>, summary: StatementSummary = StatementSummary.EMPTY): RuleStatement =
        RuleStatement(summary, StatementSummary(emptySet(), sinks.flatMap { it.endFacts }, emptyList(), emptyMap()), sinks)

    private fun witness(f: HandOffFixtures, key: MethodKey, rule: RuleId, statement: Int,
                        alternative: Int = 0, confirmed: Boolean = false): SinkWitness =
        SinkWitness(alternative, key, listOf(SinkEdge(f.manager.zero, Layer.NORMAL, Reach.NORMAL)), f.index)
            .also {
                f.core(key)
                it.confirmed = confirmed
                f.vulnerabilities.add(VulnerabilityKey(rule, key.method, f.inst(statement)), it)
            }

    private fun decoded(ops: ApOps, rows: Sequence<Pair<PremiseKey, Facts>>): Set<Pair<PremiseKey, Conclusion>> =
        rows.flatMap { (premise, facts) ->
            ops.leaves(facts).map { premise to Conclusion(it.fact, it.exclusion, facts.layer == Layer.DEMAND) }
        }.toSet()

    @Test
    fun `forward handoff reads selected demand pieces and leaves normal singleton records and raw summaries separate`(): Unit {
        for (index in listOf(1, 3)) HandOffFixtures(index).use { f ->
            val key = f.key()
            val a = f.field("a")
            val b = f.field("b")
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val initial = f.initial(x, listOf(a), mark = t)
            val recordFact = f.fact(returned, listOf(b), mark = u)
            val selectedFact = f.fact(returned, listOf(b), Tail.ANY, u, layer = Layer.DEMAND)
            f.raw(key, initial, recordFact)
            f.raw(key, initial, selectedFact)
            f.selected(key, initial, selectedFact)
            val result = f.result()
            val before = result.analyzers.single().summaries.all().toList()
            val handOff = HandOff.of(f.config, result, f.report, f.shared)
            val expected = DemandPattern(f.pattern(returned, listOf(b), Tail.ANY, u), initial.toPattern())
            assertEquals(setOf(expected) + implicitStatics(f), explicit(f, handOff, key))
            assertEquals(mapOf(key to 1), handOff.demandEdges)
            assertEquals(0, handOff.seeds.size)
            assertEquals(before, result.analyzers.single().summaries.all().toList())
            assertTrue(f.records.byEntry(key, initial.toPattern()).none(), "Hand-off does not persist raw records")
            f.records.persist(f.config.direction, result.analyzers.asSequence().map { it.key to it.summaries })
            val record = f.records.byEntry(key, initial.toPattern()).single()
            assertSame(initial, record.premise)
            assertEquals(recordFact, record.conclusion)
            assertEquals(setOf(expected) + implicitStatics(f), explicit(f, handOff, key))
        }
    }

    @Test
    fun `a selected normal conjunction creates one independent backward pattern for each full premise member`(): Unit {
        HandOffFixtures().use { f ->
            val key = f.key()
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val v = MarkSlot.Concrete(f.mark("V"))
            val a = f.initial(x, listOf(f.field("a")), mark = t)
            val b = f.initial(y, listOf(f.field("b")), mark = u)
            val premise = f.premise(a, b)
            val target = f.pattern(returned, listOf(f.field("result")), mark = v)
            val normal = f.fact(target.fact.base, target.fact.path, target.fact.tail, target.fact.mark)
            f.raw(key, premise, normal)
            f.selected(key, premise, normal)
            val result = f.result()
            val handOff = HandOff.of(f.config, result, f.report, f.shared)
            assertEquals(setOf(DemandPattern(target, a.toPattern()), DemandPattern(target, b.toPattern())) + implicitStatics(f),
                explicit(f, handOff, key))
            assertEquals(mapOf(key to 2), handOff.demandEdges)
            assertEquals(listOf(premise to normal), result.analyzers.single().summaries.demandEdges().toList())
            f.records.persist(Direction.FORWARD, result.analyzers.asSequence().map { it.key to it.summaries })
            assertTrue(f.records.byEntry(key, a.toPattern()).none())
            assertTrue(f.records.byEntry(key, b.toPattern()).none())
        }
    }

    @Test
    fun `backward handoff separates zero seed paths nonzero selected requirements and normal raw records`(): Unit {
        HandOffFixtures(index = 2).use { f ->
            val key = f.key()
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val v = MarkSlot.Concrete(f.mark("V"))
            val initial = f.initial(returned, mark = u)
            val recordFact = f.fact(x, listOf(f.field("record")), mark = t)
            val demandPattern = f.pattern(x, listOf(f.field("needed")), mark = t)
            val demandFact = f.fact(demandPattern.fact.base, demandPattern.fact.path, mark = t, layer = Layer.DEMAND)
            val seedPattern = f.pattern(y, listOf(f.field("seed")), mark = v)
            val seedFact = f.fact(seedPattern.fact.base, seedPattern.fact.path, mark = v)
            f.raw(key, initial, recordFact)
            f.raw(key, initial, demandFact)
            f.selected(key, initial, demandFact)
            f.raw(key, f.manager.zero, seedFact)
            f.selected(key, f.manager.zero, seedFact)
            f.raw(key, f.manager.zero, Reach.NORMAL)
            f.selected(key, f.manager.zero, Reach.NORMAL)
            val result = f.result()
            val handOff = HandOff.of(f.config, result, f.report, f.shared)
            assertEquals(setOf(DemandPattern(demandPattern, initial.toPattern()), DemandPattern(seedPattern, null)),
                explicit(f, handOff, key))
            assertEquals(mapOf(key to 2), handOff.demandEdges)
            assertEquals(setOf(DemandPattern(f.manager.zero.toPattern(), null)),
                handOff.demand.near(f.key("not analysed"), AccessPathBase.Zero, null).toSet())
            assertEquals(0, handOff.seeds.size)
            f.records.persist(Direction.BACKWARD, result.analyzers.asSequence().map { it.key to it.summaries })
            val record = f.records.byEntry(key, initial.toPattern()).single()
            assertEquals(recordFact, record.conclusion)
            assertTrue(f.records.byEntry(key, f.manager.zero.toPattern()).none())
        }
    }

    @Test
    fun `backward source seeds retain real hit context statement and forward edge while records create no hit`(): Unit {
        HandOffFixtures(index = 2).use { f ->
            val a = f.key("A")
            val b = f.key("B")
            val recordOnly = f.key("record only")
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val first = PathEdge(ZERO_FACT, PathFact(x, emptyList(), Tail.EXACT, t), ExclusionSet.Empty)
            val other = PathEdge(ZERO_FACT, PathFact(x, emptyList(), Tail.EXACT, u), ExclusionSet.Empty)
            assertTrue(f.hit(a, f.inst(0), first))
            assertFalse(f.hit(a, f.inst(0), PathEdge(first.from, first.to, first.exclusion)))
            assertTrue(f.hit(a, f.inst(1), first))
            assertTrue(f.hit(a, f.inst(0), other))
            assertTrue(f.hit(b, f.inst(0), first))
            val native = f.initial(returned, mark = t)
            f.raw(recordOnly, native, f.fact(x, mark = t))
            val result = f.result()
            f.records.persist(Direction.BACKWARD, result.analyzers.asSequence().map { it.key to it.summaries })
            val handOff = HandOff.of(f.config, result, f.report, f.shared)
            val expected = setOf(Seed.Source(a, f.inst(0), first), Seed.Source(a, f.inst(1), first),
                Seed.Source(a, f.inst(0), other), Seed.Source(b, f.inst(0), first))
            val actual = listOf(a, b, recordOnly).flatMap { key -> (0..2).flatMap { handOff.seeds.at(key, f.inst(it)) } }
            assertEquals(expected, actual.toSet())
            assertEquals(4, handOff.seeds.size)
            assertTrue(actual.all { it is Seed.Source })
            for (seed in actual.filterIsInstance<Seed.Source>()) {
                assertTrue(seed.edge === first || seed.edge === other)
                assertTrue(handOff.seeds.allowsSource(seed.method, seed.statement, seed.edge))
            }
            assertFalse(handOff.seeds.allowsSource(recordOnly, f.inst(0), first))
            assertFalse(handOff.seeds.allowsSource(a, f.inst(2), first))
            assertFalse(handOff.seeds.allowsSource(b, f.inst(0), other))
            assertTrue(handOff.seeds.sinkMethods().isEmpty())
            assertTrue(handOff.demandEdges.isEmpty())
            assertTrue(f.records.byEntry(recordOnly, native.toPattern()).any())
            assertTrue(explicit(f, handOff, recordOnly).isEmpty())
        }
    }

    @Test
    fun `sink handoff seeds every matching cached alternative at demand witness contexts with exact deduplication`(): Unit {
        HandOffFixtures(index = 3).use { f ->
            val a = f.key("A")
            val b = f.key("B")
            val absent = f.key("no witness")
            val demandRule = rule("demand")
            val confirmedRule = rule("confirmed")
            val unmatchedRule = rule("not the reported rule")
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val p = f.pattern(x, listOf(f.field("p")), mark = t)
            val q = f.pattern(y, tail = Tail.ANY, mark = u)
            val excluded = f.pattern(returned, mark = t)
            val alternatives = listOf(
                SinkRule(demandRule, 0, listOf(p, q, p), emptyList()),
                SinkRule(demandRule, 1, listOf(p), emptyList()),
                SinkRule(demandRule, 2, listOf(ZERO_PATTERN), emptyList()),
                SinkRule(confirmedRule, 0, listOf(excluded), emptyList()),
                SinkRule(unmatchedRule, 0, listOf(excluded), emptyList()),
            )
            f.finite.exitRules(2, ExitRules(rules(alternatives), false, emptySet()))
            witness(f, a, demandRule, 2, alternative = 0)
            witness(f, a, demandRule, 2, alternative = 1)
            witness(f, b, demandRule, 2, alternative = 0)
            witness(f, a, confirmedRule, 2, confirmed = true)
            val result = f.result()
            f.report.add(f.config, result)
            val handOff = HandOff.of(f.config, result, f.report, f.shared)
            val expected = setOf(Seed.Sink(demandRule, a, f.inst(2), p), Seed.Sink(demandRule, a, f.inst(2), q),
                Seed.Sink(demandRule, b, f.inst(2), p), Seed.Sink(demandRule, b, f.inst(2), q))
            assertEquals(expected, (handOff.seeds.at(a, f.inst(2)) + handOff.seeds.at(b, f.inst(2))).toSet())
            assertEquals(4, handOff.seeds.size, "Literal, alternative and witness repetitions do not duplicate derived sink seeds")
            assertEquals(setOf(a, b), handOff.seeds.sinkMethods())
            assertTrue(handOff.seeds.at(absent, f.inst(2)).isEmpty())
            assertTrue(handOff.seeds.at(a, f.inst(1)).isEmpty())
            assertTrue(handOff.seeds.at(a, f.inst(2)).filterIsInstance<Seed.Sink>().all { it.rule === demandRule })
            assertTrue(handOff.demandEdges.isEmpty())
        }
    }

    @Test fun `predicate handoff keeps the full positive OR scope only within witnessed contexts`(): Unit {
        HandOffFixtures().use { f ->
            val witnessed = f.key("witnessed")
            val unrelated = f.key("no witness")
            val reportRule = rule("NNF demand")
            val first = f.pattern(x)
            val unwitnessed = f.pattern(y, tail = Tail.ANY)
            val builder = RulePredicate.Builder(f.cancellation)
            val predicate = builder.build(builder.or(listOf(builder.leaf(first), builder.leaf(unwitnessed))))
            val id = RuleOccurrenceId(FormKey(FormOwner(), witnessed.method, null, f.inst(2)), RuleKind.EXIT_SINK, 0)
            val sink = SinkRule(reportRule, 0, predicate.leaves, emptyList(), id, predicate)
            f.finite.exitRules(2, ExitRules(rules(listOf(sink)), false, emptySet()))
            val support = SinkWitness(0, witnessed,
                listOf(SinkEdge(f.manager.zero, Layer.DEMAND, f.fact(x, layer = Layer.DEMAND))), f.index,
                proof = ProofKey(id, listOf(predicate.leaves.indexOf(first))))
            f.core(witnessed)
            f.vulnerabilities.add(VulnerabilityKey(reportRule, witnessed.method, f.inst(2)), support)
            val result = f.result()
            f.report.add(f.config, result)
            val handOff = HandOff.of(f.config, result, f.report, f.shared)
            assertEquals(listOf(Seed.PredicateSink(id, witnessed, f.inst(2))), handOff.seeds.at(witnessed, f.inst(2)))
            assertEquals(1, handOff.seeds.size)
            val handle = handOff.seeds.at(witnessed, f.inst(2)).single() as Seed.PredicateSink
            val resolved = f.shared.contexts.forms(handle.method).exitRules(Direction.FORWARD, handle.statement)
                .rules.sinks.single { it.occurrenceId == handle.occurrence }
            assertEquals(setOf(first, unwitnessed), resolved.seedPatterns().toSet())
            assertEquals(setOf(witnessed), handOff.seeds.sinkMethods())
            assertTrue(handOff.seeds.at(unrelated, f.inst(2)).isEmpty())
        }
    }

    @Test
    fun `a call witness reads cached call sinks rather than same rule alternatives at another boundary`(): Unit {
        HandOffFixtures().use { f ->
            val key = f.key()
            val rule = rule("same rule at two places")
            val called = f.pattern(x, listOf(f.field("call")))
            val elsewhere = f.pattern(x, listOf(f.field("exit")))
            val callSink = SinkRule(rule, 0, listOf(called), emptyList())
            val exitSink = SinkRule(rule, 1, listOf(elsewhere), emptyList())
            f.finite.call(1, CallPlan(emptySet(), listOf(CallStage.Edges(CallPoint.BEFORE, CallPoint.AFTER,
                StageKind.PASS_OVER, summary(ZERO_KEEP))), listOf(callSink), CallPoint.BEFORE, CallPoint.AFTER))
            f.finite.exitRules(2, ExitRules(rules(listOf(exitSink)), false, emptySet()))
            witness(f, key, rule, 1)
            val result = f.result()
            f.report.add(f.config, result)
            val handOff = HandOff.of(f.config, result, f.report, f.shared)
            assertEquals(listOf(Seed.Sink(rule, key, f.inst(1), called)), handOff.seeds.at(key, f.inst(1)))
            assertTrue(handOff.seeds.at(key, f.inst(2)).isEmpty())
            assertEquals(1, handOff.seeds.size)
        }
    }

    @Test
    fun `unconditional native entry call and exit sink witnesses add no requirement seed`(): Unit {
        HandOffFixtures().use { f ->
            val key = f.key("unconditional context")
            val entry = SinkRule(rule("entry"), 0, listOf(ZERO_PATTERN), emptyList())
            val call = SinkRule(rule("call"), 0, listOf(ZERO_PATTERN), emptyList())
            val exit = SinkRule(rule("exit"), 0, listOf(ZERO_PATTERN), emptyList())
            f.finite.entryRules(rules(listOf(entry), summary(ZERO_KEEP)))
            f.finite.call(1, CallPlan(emptySet(), listOf(CallStage.Edges(CallPoint.BEFORE, CallPoint.AFTER,
                StageKind.PASS_OVER, summary(ZERO_KEEP))), listOf(call), CallPoint.BEFORE, CallPoint.AFTER))
            f.finite.exitRules(2, ExitRules(rules(listOf(exit)), false, emptySet()))
            witness(f, key, entry.rule, 0)
            witness(f, key, call.rule, 1)
            witness(f, key, exit.rule, 2)
            val result = f.result()
            f.report.add(f.config, result)
            assertEquals(3, f.report.demandEntries().size)
            val handOff = HandOff.of(f.config, result, f.report, f.shared)
            assertEquals(0, handOff.seeds.size)
            assertTrue(handOff.seeds.sinkMethods().isEmpty())
            assertTrue((0..2).all { handOff.seeds.at(key, f.inst(it)).isEmpty() })
            assertTrue(handOff.demandEdges.isEmpty())
        }
    }

    @Test
    fun `an incomplete result cannot enter either handoff direction`(): Unit {
        for (index in listOf(1, 2, 3)) for (status in listOf(RunStatus.TIMEOUT, RunStatus.OOM, RunStatus.FAILED)) {
            HandOffFixtures(index).use { f ->
                val key = f.key()
                f.selected(key, f.manager.zero, f.fact(returned, tail = Tail.ANY, layer = Layer.DEMAND))
                assertFails("index=$index status=$status") {
                    HandOff.of(f.config, f.result(status), f.report, f.shared)
                }
            }
        }
    }

    @Test
    fun `exact conjunction handoffs keep backward literal branches demand and exclude them from singleton records`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val aMark = MarkSlot.Concrete(f.mark("A"))
            val bMark = MarkSlot.Concrete(f.mark("B"))
            val resultMark = MarkSlot.Concrete(f.mark("T"))
            val a = f.manager.initial(f.pattern(x, mark = aMark))
            val b = f.manager.initial(f.pattern(y, mark = bMark))
            val target = PathFact(returned, emptyList(), Tail.EXACT, resultMark)
            val targetPattern = Pattern(target, ExclusionSet.Empty)
            val conjunction = ConjunctiveEdge(listOf(a.toPattern(), b.toPattern()), target)
            f.statement(1, StatementSummary(setOf(x, y), emptyList(), listOf(conjunction), emptyMap()))
            val local = MethodLocalServicesFactory(::RunMethodLocalServices)
            val incomingDemand = DemandStore.Builder(f.manager).apply {
                add(f.key, DemandPattern(a.toPattern(), targetPattern))
                add(f.key, DemandPattern(b.toPattern(), targetPattern))
            }.build()
            val forwardConfig = f.config(index = 3, demand = incomingDemand)
            val forward = f.core(forwardConfig, localFactory = local)
            forward.addInitial(a)
            f.drain(forward, quantum = 1)
            assertTrue(forward.summaries.all().none { it.second.base == returned }, "One literal alone cannot complete the conjunction")
            forward.addInitial(b)
            f.drain(forward, quantum = 1)
            forward.freeze()
            val premise = f.manager.premiseOf(listOf(a, b))
            val normalTarget = Conclusion(target, ExclusionSet.Empty, false)
            val forwardExpected = setOf<Pair<PremiseKey, Conclusion>>(premise to normalTarget)
            assertEquals(forwardExpected, decoded(f.ops, forward.summaries.all()))
            assertEquals(forwardExpected, decoded(f.ops, forward.summaries.demandEdges()))
            val report = ReportBuilder()
            val forwardResult = RunResult(RunStatus.COMPLETE, listOf(forward), 3, Direction.FORWARD, f.vulnerabilities)
            val forwardHandOff = HandOff.of(forwardConfig, forwardResult, report, f.shared)
            assertEquals(setOf(DemandPattern(targetPattern, a.toPattern()), DemandPattern(targetPattern, b.toPattern())),
                forwardHandOff.demand.near(f.key, returned, null).toSet())
            assertEquals(mapOf(f.key to 2), forwardHandOff.demandEdges, "One backward guide per conjunction member")
            // Only eligible singleton NORMAL summaries can enter the record store.
            f.records.persist(Direction.FORWARD, sequenceOf(f.key to forward.summaries))
            assertTrue(f.records.byEntry(f.key, a.toPattern()).none())
            assertTrue(f.records.byEntry(f.key, b.toPattern()).none())

            val backwardConfig = f.config(index = 4, demand = forwardHandOff.demand, seeds = forwardHandOff.seeds)
            val backward = f.core(backwardConfig, localFactory = local)
            val requirement = f.manager.initial(targetPattern)
            backward.addInitial(requirement)
            f.drain(backward, quantum = 1)
            backward.freeze()
            val aRequirement = Conclusion(a.toPattern().fact, ExclusionSet.Empty, true)
            val bRequirement = Conclusion(b.toPattern().fact, ExclusionSet.Empty, true)
            val independent = setOf<Pair<PremiseKey, Conclusion>>(requirement to aRequirement, requirement to bRequirement)
            assertEquals(independent + (requirement to normalTarget), decoded(f.ops, backward.summaries.all()))
            assertEquals(independent, decoded(f.ops, backward.summaries.demandEdges()))
            f.records.persist(Direction.BACKWARD, sequenceOf(f.key to backward.summaries))
            assertTrue(f.records.byExit(f.key, a.toPattern()).none(), "A reversed literal is not a normal one-literal record")
            assertTrue(f.records.byExit(f.key, b.toPattern()).none(), "Each literal branch keeps its DEMAND layer")
            val backwardResult = RunResult(RunStatus.COMPLETE, listOf(backward), 4, Direction.BACKWARD, f.vulnerabilities)
            val backwardHandOff = HandOff.of(backwardConfig, backwardResult, report, f.shared)
            assertEquals(setOf(DemandPattern(a.toPattern(), targetPattern)),
                backwardHandOff.demand.near(f.key, x, null).toSet())
            assertEquals(setOf(DemandPattern(b.toPattern(), targetPattern)),
                backwardHandOff.demand.near(f.key, y, null).toSet())
            assertEquals(mapOf(f.key to 2), backwardHandOff.demandEdges)
            assertEquals(0, forwardHandOff.seeds.size)
            assertEquals(0, backwardHandOff.seeds.size)
        }
    }
}
