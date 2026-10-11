package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.driver.Support
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.FiniteProgramFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.Link
import org.opentaint.dataflow.bidi.store.SinkWitness
import org.opentaint.dataflow.bidi.store.VulnerabilityKey
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/** Confirmation reads the actual forward links retained after all runner jobs have joined. */
class SupportIntegrationTest {
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Argument(1)

    private fun summary(vararg edges: PathEdge): StatementSummary =
        StatementSummary(edges.map { it.from.base }.toSet(), edges.map(MicroEdge::of), emptyList(), emptyMap())

    private fun rule(id: String): CommonTaintConfigurationSink = object : CommonTaintConfigurationSink {
        override val id = id
        override val meta = object : CommonTaintConfigurationSinkMeta {
            override val message = id
            override val severity = CommonTaintConfigurationSinkMeta.Severity.Warning
        }
    }

    private fun source(fact: PathFact): PathEdge = PathEdge(ZERO_FACT, fact, ExclusionSet.Empty)

    private fun rules(vararg sinks: SinkRule): RuleStatement =
        RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, sinks.toList())

    private fun call(callee: MethodKey): CallPlan = CallPlan(setOf(x, y), listOf(
        CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(x), keepEdge(y), ZERO_KEEP)),
        CallStage.Actions(BOUND, ADDED, emptyList()),
        CallStage.Callees(ADDED, RETURNED, listOf(callee)),
        CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
        CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(keepEdge(x), keepEdge(y), ZERO_KEEP)),
    ), emptyList(), BEFORE, AFTER)

    /** These are primitive AP cleaners, outside the JVM's lowered field-action mapping. */
    private fun unresolved(actions: List<CleanStep> = emptyList()): CallPlan = CallPlan(setOf(x), listOf(
        CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(x), ZERO_KEEP)),
        CallStage.Actions(BOUND, ADDED, actions),
        CallStage.Edges(ADDED, RETURNED, UNRESOLVED, summary(keepEdge(x), ZERO_KEEP)),
        CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
        CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(keepEdge(x), ZERO_KEEP)),
    ), emptyList(), BEFORE, AFTER)

    private fun joined(f: FiniteProgramFixtures, config: RunConfig): RunResult<RunMethodAnalyzer> {
        val handlers = RunMethodHandlers(MethodLocalServicesFactory(::RunMethodLocalServices),
            MethodCallServicesFactory(::RunMethodCallServices))
        val manager = RunManager(config, f.shared, handlers, quantum = 1)
        val result = manager.run(10.seconds)
        assertEquals(RunStatus.COMPLETE, result.status, f.describe())
        assertEquals(Direction.FORWARD, result.direction)
        assertEquals(config.index, result.runIndex)
        assertTrue(manager.ended)
        assertEquals(0L, manager.inFlight.pending)
        assertTrue(result.analyzers.isNotEmpty())
        for (actor in result.analyzers) {
            assertFailsWith<IllegalStateException> { actor.edges }
            actor.links.links().toList() // Forward links remain available after the successful join and freeze.
        }
        return result
    }

    private fun decoded(f: FiniteProgramFixtures, facts: Facts): Set<Conclusion> =
        f.ops.leaves(facts).mapTo(LinkedHashSet()) {
            Conclusion(it.fact, it.exclusion, facts.layer == Layer.DEMAND)
        }

    private fun checkWitness(f: FiniteProgramFixtures, witness: SinkWitness, method: MethodKey,
                             run: Int, premises: List<PremiseKey>, conclusions: List<Conclusion>) {
        assertEquals(method, witness.methodKey)
        assertEquals(run, witness.run)
        assertEquals(premises, witness.edges.map { it.premise }, "Keep the ordered literal support headers")
        assertEquals(conclusions.map { if (it.demand) Layer.DEMAND else Layer.NORMAL },
            witness.edges.map { it.layer })
        assertEquals(conclusions.map { setOf(it) }, witness.edges.map { decoded(f, it.facts) })
        assertTrue(witness.endFacts.isEmpty())
        assertFalse(witness.confirmed, "Only the complete-run confirmation barrier changes this flag")
    }

    @Test fun `a joined root zero normal exact sink confirms without any fabricated caller link`(): Unit {
        FiniteProgramFixtures().use { f ->
            val method = f.method("rootExact", listOf(listOf(1), emptyList()), 1)
            val key = method.key("root context")
            val t = MarkSlot.Concrete(f.mark("T"))
            val field = f.field("one field")
            val target = PathFact(x, listOf(field), Tail.EXACT, t)
            val forward = source(target)
            val sink = SinkRule(rule("root-exact"), 0, listOf(Pattern(target, ExclusionSet.Empty)), emptyList())
            f.entry(key, RuleStatement(summary(ZERO_KEEP, forward), StatementSummary.EMPTY, emptyList()))
            f.exit(key, 1, ExitRules(rules(sink), false, emptySet()))
            val config = f.config(L = 1, roots = listOf(key))
            val result = joined(f, config)
            assertEquals(listOf(key), result.analyzers.map { it.key })
            assertTrue(result.analyzers.single().links.links().none())
            val (vulnerability, witnesses) = result.witnessesByKey().entries.single()
            assertEquals(VulnerabilityKey(sink.rule, key.method, method.inst(1)), vulnerability)
            val witness = witnesses.single()
            checkWitness(f, witness, key, 1, listOf(f.ap.zero),
                listOf(Conclusion(target, ExclusionSet.Empty, false)))

            val support = Support(result, config.roots, config.mode, f.shared)
            assertTrue(support.isSupported(key, listOf(f.ap.zero)))
            assertFalse(support.isSupported(key, listOf(f.ap.initial(sink.patterns.single()))),
                "A root's nonzero member has no root base case")
            support.confirm()
            assertTrue(witness.confirmed)
        }
    }

    @Test fun `a primitive exact cleaner demotes a root must sink to demand despite supported zero`(): Unit {
        FiniteProgramFixtures().use { f ->
            val method = f.method("rootPartialCleaner", listOf(listOf(1), listOf(2), emptyList()), 2)
            val key = method.key()
            val t = MarkSlot.Concrete(f.mark("T"))
            val target = PathFact(x, emptyList(), Tail.ANY_TAINT, t)
            val sink = SinkRule(rule("root-demand"), 0, listOf(f.pattern(x, mark = t)), emptyList())
            f.entry(key, RuleStatement(summary(ZERO_KEEP, source(target)), StatementSummary.EMPTY, emptyList()))
            f.call(key, 1, unresolved(listOf(CleanStep.Clean(Cleaner(x, null, CleanReach.EXACT, t.mark)))))
            f.exit(key, 2, ExitRules(rules(sink), false, emptySet()))
            val config = f.config(L = 1, roots = listOf(key))
            val result = joined(f, config)
            assertTrue(result.analyzers.single().links.links().none())
            val witness = result.witnessesByKey().values.single().single()
            val demand = Conclusion(target.copy(tail = Tail.ANY), ExclusionSet.Empty, true)
            checkWitness(f, witness, key, 1, listOf(f.ap.zero), listOf(demand))
            assertTrue(result.demandLayerEdges > 0L)

            val support = Support(result, config.roots, config.mode, f.shared)
            assertTrue(support.isSupported(key, listOf(f.ap.zero)))
            support.confirm()
            assertFalse(witness.confirmed, "Supported premises do not change a DEMAND sink edge into NORMAL")
        }
    }

    @Test fun `seeded restricted concrete inputs at one real cross unit call jointly confirm the complete sink`(): Unit {
        FiniteProgramFixtures(threads = 2).use { f ->
            val main = f.method("jointCaller", listOf(listOf(1), listOf(2), emptyList()), 2)
            val target = f.method("jointCallee", listOf(listOf(1), emptyList()), 1)
            val caller = main.key("caller context")
            val callee = target.key("callee context")
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val px = f.pattern(x, mark = t)
            val py = f.pattern(y, mark = u)
            val sourceX = source(px.fact)
            val sourceY = source(py.fact)
            val sink = SinkRule(rule("joint-positive"), 3, listOf(px, py), emptyList())
            f.entry(caller, RuleStatement(summary(ZERO_KEEP, sourceX, sourceY), StatementSummary.EMPTY, emptyList()))
            f.call(caller, 1, call(callee))
            f.exit(callee, 1, ExitRules(rules(sink), false, emptySet()))
            val seeds = listOf(Seed.Source(caller, main.inst(0), sourceX), Seed.Source(caller, main.inst(0), sourceY))
            val config = f.config(index = 3, L = 1, roots = listOf(caller),
                demand = listOf(callee to DemandPattern(px, null), callee to DemandPattern(py, null)), seeds = seeds)
            assertTrue(config.seeds.allowsSource(caller, main.inst(0), sourceX))
            assertTrue(config.seeds.allowsSource(caller, main.inst(0), sourceY))
            val result = joined(f, config)
            assertEquals(setOf(caller, callee), result.analyzers.map { it.key }.toSet())
            val calleeActor = result.analyzers.single { it.key == callee }
            val ref = CallerRef(caller, f.ap.zero, Layer.NORMAL, main.inst(1))
            assertEquals(setOf(Link(ZERO_PATTERN, Layer.NORMAL, ref), Link(px, Layer.NORMAL, ref),
                Link(py, Layer.NORMAL, ref)), calleeActor.links.links().toSet())
            val (key, witnesses) = result.witnessesByKey().entries.single()
            assertEquals(VulnerabilityKey(sink.rule, callee.method, target.inst(1)), key)
            val witness = witnesses.single()
            val jx = f.ap.initial(px)
            val jy = f.ap.initial(py)
            checkWitness(f, witness, callee, 3, listOf(jx, jy),
                listOf(Conclusion(px.fact, px.exclusion, false), Conclusion(py.fact, py.exclusion, false)))
            assertEquals(3, witness.alternative)
            assertSame(f.ap.union(jx, jy), witness.supportPremise(f.ap))

            val support = Support(result, config.roots, config.mode, f.shared)
            assertTrue(support.isSupported(callee, listOf(jx, jy)),
                "Different caller edges at one call jointly supply every literal")
            support.confirm()
            assertTrue(witness.confirmed)
        }
    }

    @Test fun `normal literals arriving from different callers or different sites cannot confirm one joint sink`(): Unit {
        for (differentCallers in listOf(true, false)) FiniteProgramFixtures(threads = 3).use { f ->
            val target = f.method("splitCallee", listOf(listOf(1), emptyList()), 1)
            val callee = target.key("shared callee context")
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val px = f.pattern(x, mark = t)
            val py = f.pattern(y, mark = u)
            val sourceX = source(px.fact)
            val sourceY = source(py.fact)
            val sink = SinkRule(rule("joint-negative"), 0, listOf(px, py), emptyList())
            f.exit(callee, 1, ExitRules(rules(sink), false, emptySet()))
            val roots: List<MethodKey>
            val seeds: List<Seed>
            val refX: CallerRef
            val refY: CallerRef
            if (differentCallers) {
                val left = f.method("leftCaller", listOf(listOf(1), listOf(2), emptyList()), 2)
                val right = f.method("rightCaller", listOf(listOf(1), listOf(2), emptyList()), 2)
                val callerX = left.key("left context")
                val callerY = right.key("right context")
                f.entry(callerX, RuleStatement(summary(ZERO_KEEP, sourceX), StatementSummary.EMPTY, emptyList()))
                f.entry(callerY, RuleStatement(summary(ZERO_KEEP, sourceY), StatementSummary.EMPTY, emptyList()))
                f.call(callerX, 1, call(callee))
                f.call(callerY, 1, call(callee))
                roots = listOf(callerX, callerY)
                seeds = listOf(Seed.Source(callerX, left.inst(0), sourceX), Seed.Source(callerY, right.inst(0), sourceY))
                refX = CallerRef(callerX, f.ap.zero, Layer.NORMAL, left.inst(1))
                refY = CallerRef(callerY, f.ap.zero, Layer.NORMAL, right.inst(1))
            } else {
                val main = f.method("twoSites", listOf(listOf(1, 3), listOf(2), listOf(5),
                    listOf(4), listOf(5), emptyList()), 5)
                val caller = main.key("one caller context")
                f.statement(caller, 1, summary(ZERO_KEEP, sourceX))
                f.statement(caller, 3, summary(ZERO_KEEP, sourceY))
                f.call(caller, 2, call(callee))
                f.call(caller, 4, call(callee))
                roots = listOf(caller)
                seeds = listOf(Seed.Source(caller, main.inst(1), sourceX), Seed.Source(caller, main.inst(3), sourceY))
                refX = CallerRef(caller, f.ap.zero, Layer.NORMAL, main.inst(2))
                refY = CallerRef(caller, f.ap.zero, Layer.NORMAL, main.inst(4))
            }
            val config = f.config(index = 3, L = 1, roots = roots,
                demand = listOf(callee to DemandPattern(px, null), callee to DemandPattern(py, null)), seeds = seeds)
            val result = joined(f, config)
            val calleeActor = result.analyzers.single { it.key == callee }
            assertEquals(setOf(Link(ZERO_PATTERN, Layer.NORMAL, refX), Link(ZERO_PATTERN, Layer.NORMAL, refY),
                Link(px, Layer.NORMAL, refX), Link(py, Layer.NORMAL, refY)), calleeActor.links.links().toSet())
            assertNotEquals(refX, refY)
            assertEquals(differentCallers, refX.caller != refY.caller)
            assertNotEquals(refX.call, refY.call)
            val witness = result.witnessesByKey().values.single().single()
            val jx = f.ap.initial(px)
            val jy = f.ap.initial(py)
            checkWitness(f, witness, callee, 3, listOf(jx, jy),
                listOf(Conclusion(px.fact, px.exclusion, false), Conclusion(py.fact, py.exclusion, false)))
            assertSame(f.ap.union(jx, jy), witness.supportPremise(f.ap))

            val support = Support(result, config.roots, config.mode, f.shared)
            assertTrue(support.isSupported(callee, listOf(jx)))
            assertTrue(support.isSupported(callee, listOf(jy)))
            assertFalse(support.isSupported(callee, listOf(jx, jy)),
                "The witness retains the whole premise set; singleton support at different sites is insufficient")
            support.confirm()
            assertFalse(witness.confirmed)
        }
    }

    @Test fun `confirmation preserves full witness contexts alternatives and distinct vulnerability rule keys`(): Unit {
        FiniteProgramFixtures().use { f ->
            val method = f.method("contextualRoot", listOf(listOf(1), listOf(2), emptyList()), 2)
            val exactContext = method.key("normal exact context")
            val demandContext = method.key("partial must context")
            val t = MarkSlot.Concrete(f.mark("T"))
            val exact = f.pattern(x, mark = t)
            val must = exact.fact.copy(tail = Tail.ANY_TAINT)
            val primary = rule("context alternatives")
            val secondary = rule("separate vulnerability")
            val sinks = listOf(SinkRule(primary, 0, listOf(exact), emptyList()),
                SinkRule(primary, 1, listOf(exact.copy(fact = exact.fact.copy(tail = Tail.ANY))), emptyList()),
                SinkRule(secondary, 0, listOf(exact), emptyList()))
            f.entry(exactContext, RuleStatement(summary(ZERO_KEEP, source(exact.fact)), StatementSummary.EMPTY, emptyList()))
            f.entry(demandContext, RuleStatement(summary(ZERO_KEEP, source(must)), StatementSummary.EMPTY, emptyList()))
            f.call(exactContext, 1, unresolved())
            f.call(demandContext, 1, unresolved(listOf(CleanStep.Clean(Cleaner(x, null, CleanReach.EXACT, t.mark)))))
            for (key in listOf(exactContext, demandContext)) f.exit(key, 2, ExitRules(rules(*sinks.toTypedArray()), false, emptySet()))
            val config = f.config(L = 1, roots = listOf(exactContext, demandContext))
            val result = joined(f, config)
            assertEquals(setOf(exactContext, demandContext), result.analyzers.map { it.key }.toSet())
            assertTrue(result.analyzers.all { it.links.links().none() })
            val primaryKey = VulnerabilityKey(primary, method.method, method.inst(2))
            val secondaryKey = VulnerabilityKey(secondary, method.method, method.inst(2))
            val witnesses = result.witnessesByKey()
            assertEquals(setOf(primaryKey, secondaryKey), witnesses.keys)
            assertEquals(setOf(exactContext to 0, exactContext to 1, demandContext to 0, demandContext to 1),
                witnesses.getValue(primaryKey).map { it.methodKey to it.alternative }.toSet())
            assertEquals(setOf(exactContext to 0, demandContext to 0),
                witnesses.getValue(secondaryKey).map { it.methodKey to it.alternative }.toSet())
            assertEquals(6, witnesses.values.sumOf { it.size })
            for (witness in witnesses.values.flatten()) {
                val demand = witness.methodKey == demandContext
                val conclusion = Conclusion(if (demand) exact.fact.copy(tail = Tail.ANY) else exact.fact,
                    ExclusionSet.Empty, demand)
                checkWitness(f, witness, witness.methodKey, 1, listOf(f.ap.zero), listOf(conclusion))
            }

            val support = Support(result, config.roots, config.mode, f.shared)
            support.confirm()
            for (witness in witnesses.values.flatten())
                assertEquals(witness.methodKey == exactContext, witness.confirmed,
                    "Confirmation is per full witness; context-free vulnerability keys do not merge alternatives or layers")
        }
    }
}
