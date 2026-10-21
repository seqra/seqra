package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import kotlin.test.*

/** Exercise the real actor services, AP operations, persistent witnesses and managed form payload. */
class RulePredicateEngineTest {
    private val owner = FormOwner()
    private val reportRule = object : CommonTaintConfigurationSink {
        override val id = "predicate sink"
        override val meta = object : CommonTaintConfigurationSinkMeta {
            override val message = "predicate sink"
            override val severity = CommonTaintConfigurationSinkMeta.Severity.Error
        }
    }
    private fun id(f: MethodAnalysisFixtures, kind: RuleKind = RuleKind.CALL_SOURCE, ordinal: Int = 0) =
        RuleOccurrenceId(FormKey(owner, f.key.method, f.key, f.inst(2)), kind, ordinal)
    private fun predicate(f: MethodAnalysisFixtures, root: (RulePredicate.Builder, List<Int>) -> Int): RulePredicate {
        val builder = RulePredicate.Builder(f.cancellation)
        val leaves = List(3) { builder.leaf(f.pattern(AccessPathBase.Argument(it))) }
        return builder.build(root(builder, leaves))
    }
    private fun core(f: MethodAnalysisFixtures, run: Int = 1, seeds: SeedIndex = SeedIndex.EMPTY): Pair<RunMethodAnalyzer, RunMethodLocalServices> {
        lateinit var service: RunMethodLocalServices
        val core = f.core(index = run, seeds = seeds,
            localFactory = MethodLocalServicesFactory { actor -> RunMethodLocalServices(actor).also { service = it } })
        return core to service
    }
    private fun source(f: MethodAnalysisFixtures, predicate: RulePredicate, target: PathFact = f.pattern(AccessPathBase.Return).fact): SourceRuleOccurrence {
        val id = id(f)
        return SourceRuleOccurrence(id, predicate, listOf(SourceAction(ActionId(id, ActionRole.SOURCE, 0), target)))
    }
    private fun sink(f: MethodAnalysisFixtures, predicate: RulePredicate, ordinal: Int = 0): SinkRule =
        SinkRule(reportRule, 0, predicate.leaves, emptyList(), id(f, RuleKind.CALL_SINK, ordinal), predicate)

    @Test fun `run1 source conjunction selects one original after every leaf and keeps fixed target`() {
        for (order in listOf(listOf(0, 1), listOf(1, 0))) MethodAnalysisFixtures().use { f ->
            val (_, services) = core(f)
            val predicate = predicate(f) { b, l -> b.and(listOf(l[0], l[1])) }
            val source = source(f, predicate, f.pattern(AccessPathBase.Return, tail = Tail.ANY_TAINT).fact)
            val result = ArrayList<Pair<PremiseKey, Facts>>()
            for ((position, leaf) in order.withIndex()) {
                val base = AccessPathBase.Argument(leaf)
                services.sourcePredicate(source, f.manager.initial(f.pattern(base)),
                    f.fact(base, layer = if (leaf == 1) Layer.DEMAND else Layer.NORMAL), f.inst(2), false) { pr, facts ->
                    result += pr to facts
                }
                assertEquals(if (position == 0) 0 else 1, result.size)
            }
            assertEquals(setOf(0), result.single().first.members.map { (it.base as AccessPathBase.Argument).idx }.toSet())
            assertIs<InitialAp>(result.single().first)
            assertEquals(Layer.DEMAND, result.single().second.layer)
            assertEquals(Tail.ANY_TAINT, f.ops.leaves(result.single().second).single().fact.tail)
        }
    }


    @Test fun `later FULL source conjunction keeps all original premises`() {
        MethodAnalysisFixtures().use { f ->
            val predicate = predicate(f) { b, l -> b.and(listOf(l[0], l[1])) }
            val source = source(f, predicate)
            val action = source.actions.single()
            val seeds = SeedIndex.of(predicate.leaves.map { Seed.Source(f.key, f.inst(2), source.transfer(it, action, multi = true).forward, action.id) })
            val (_, services) = core(f, run = 3, seeds = seeds)
            val results = ArrayList<Pair<PremiseKey, Facts>>()
            for (leaf in listOf(1, 0)) {
                val base = AccessPathBase.Argument(leaf)
                services.sourcePredicate(source, f.manager.initial(f.pattern(base)), f.fact(base), f.inst(2), false) { pr, facts ->
                    results += pr to facts
                }
            }
            assertIs<PremiseSet>(results.single().first)
            assertEquals(setOf(0, 1), results.single().first.members.map { (it.base as AccessPathBase.Argument).idx }.toSet())
        }
    }

    @Test fun `run1 executable boundaries reject conjunctive origins`() {
        MethodAnalysisFixtures().use { f ->
            val (actor, _) = core(f)
            val left = f.manager.initial(f.run1Pattern(AccessPathBase.Argument(0)))
            val right = f.manager.initial(f.run1Pattern(AccessPathBase.Argument(1)))
            val invalid = f.manager.union(left, right)
            val value = f.run1Fact(AccessPathBase.Return)
            assertFailsWith<IllegalStateException> { actor.summaryDelta(invalid, value) }
            assertFailsWith<IllegalStateException> { actor.emitAfter(invalid, f.inst(1), value) }
            assertTrue(actor.summaries.all().none())
            assertTrue(f.publications.isEmpty())
        }
    }

    @Test fun `a repeated source leaf emits unary outputs with each original premise and no synthetic pair`() {
        MethodAnalysisFixtures().use { f ->
            val (_, services) = core(f)
            val predicate = predicate(f) { b, l -> b.and(listOf(l[0], l[0])) }
            val source = source(f, predicate)
            val result = ArrayList<Pair<PremiseKey, Facts>>()
            val premises = List(2) { f.manager.initial(f.pattern(AccessPathBase.Argument(it))) }
            for (premise in premises) services.sourcePredicate(source, premise, f.fact(), f.inst(2), false) { pr, facts ->
                result += pr to facts
            }
            assertEquals(premises, result.map { it.first })
            assertTrue(result.all { it.first is InitialAp && it.second.base == AccessPathBase.Return })
        }
    }

    @Test fun `one source leaf can have ordinary and forced demand backward transfers`() {
        MethodAnalysisFixtures().use { f ->
            val (_, services) = core(f, run = 2)
            val predicate = predicate(f) { b, l -> b.or(listOf(l[0], b.and(listOf(l[0], l[1])))) }
            val source = source(f, predicate).reversed()
            val result = ArrayList<Facts>()
            services.sourcePredicate(source, f.manager.initial(f.pattern(AccessPathBase.Return)),
                f.fact(AccessPathBase.Return), f.inst(2), false) { _, facts -> result += facts }
            assertEquals(setOf(AccessPathBase.Argument(0) to Layer.NORMAL, AccessPathBase.Argument(0) to Layer.DEMAND,
                AccessPathBase.Argument(1) to Layer.DEMAND), result.map { it.base to it.layer }.toSet())
            assertEquals(3, result.size)
            assertTrue(result.flatMap { f.ops.leaves(it).toList() }.none { it.fact.tail == Tail.ANY_TAINT })
        }
    }

    @Test fun `sink supports do not cross rule occurrences and witnesses keep observed proof scopes`() {
        MethodAnalysisFixtures().use { f ->
            val (_, services) = core(f)
            val predicate = predicate(f) { b, l -> b.and(listOf(l[0], l[1])) }
            val first = sink(f, predicate)
            val second = sink(f, predicate, ordinal = 1)
            assertTrue(services.checkSinks(f.manager.zero, f.inst(2), f.fact(), listOf(first)).fired.isEmpty())
            assertTrue(services.checkSinks(f.manager.zero, f.inst(2), f.fact(AccessPathBase.Argument(1)), listOf(second)).fired.isEmpty())
            assertEquals(1, services.checkSinks(f.manager.zero, f.inst(2), f.fact(AccessPathBase.Argument(1)), listOf(first)).fired.size)
            assertEquals(1, services.checkSinks(f.manager.zero, f.inst(2), f.fact(), listOf(second)).fired.size)
            val witnesses = f.vulnerabilities.witnessesOf(1).map { it.second }.toList()
            assertEquals(2, witnesses.size)
            assertNotEquals(witnesses[0].proof, witnesses[1].proof)
            assertTrue(witnesses.all { it.edges.size == 2 })
        }
    }

    @Test fun `form cleanup between source inputs changes build count without losing standing support`() {
        MethodAnalysisFixtures().use { f ->
            val (_, services) = core(f)
            val refs = f.refManager.softRefManager("bidi-method-forms")
            val cache = FormsCache<SourceRuleOccurrence>(1, refs) { it.reversed() }
            var builds = 0
            fun build(): SourceRuleOccurrence {
                builds++
                return source(f, predicate(f) { b, l -> b.and(listOf(l[0], l[1])) })
            }
            val result = ArrayList<Pair<PremiseKey, Facts>>()
            cache.withForm(0, Direction.FORWARD, ::build) { source ->
                services.sourcePredicate(source, f.manager.zero, f.fact(), f.inst(2), false) { pr, facts -> result += pr to facts }
                assertEquals(1, refs.cleanup())
            }
            refs.enable()
            cache.withForm(0, Direction.FORWARD, ::build) { source ->
                services.sourcePredicate(source, f.manager.zero, f.fact(AccessPathBase.Argument(1)), f.inst(2), false) { pr, facts -> result += pr to facts }
            }
            assertEquals(2, builds)
            assertEquals(1, result.size)
            assertSame(f.manager.zero, result.single().first)
            assertEquals(AccessPathBase.Return, result.single().second.base)
        }
    }

    @Test fun `full predicate sink handles decode all branches and rebuilt end triggers fire once`() {
        MethodAnalysisFixtures().use { f ->
            val predicate = predicate(f) { b, l -> b.or(listOf(l[0], l[1])) }
            val sink = sink(f, predicate)
            f.exitRules(2, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, listOf(sink)), false, emptySet()))
            val seeds = SeedIndex.of(listOf(Seed.PredicateSink(checkNotNull(sink.occurrenceId), f.key, f.inst(2))))
            assertEquals(1, seeds.size)
            assertEquals(setOf(f.key), seeds.sinkMethods())
            val (actor, services) = core(f, run = 2, seeds = seeds)
            val decoded = ArrayList<Facts>()
            services.fireSinkSeeds(f.inst(2), decoded::add)
            assertEquals(setOf(AccessPathBase.Argument(0), AccessPathBase.Argument(1)), decoded.map { it.base }.toSet())
            services.fireTriggerSeeds(f.inst(2), sink, decoded::add)
            val rebuilt = sink(f, predicate(f) { b, l -> b.or(listOf(l[0], l[1])) })
            services.fireTriggerSeeds(f.inst(2), rebuilt, decoded::add)
            assertEquals(4, decoded.size)
            assertEquals(2, actor.triggerSeeds)
        }
    }
}
