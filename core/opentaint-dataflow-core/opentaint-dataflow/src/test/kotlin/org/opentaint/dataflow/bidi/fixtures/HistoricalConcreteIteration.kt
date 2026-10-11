package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.driver.SeedIndex
import org.opentaint.dataflow.bidi.engine.RunConfig
import org.opentaint.dataflow.bidi.interp.SinkRule
import org.opentaint.dataflow.bidi.interp.ZERO_PATTERN
import org.opentaint.dataflow.bidi.store.DemandStore
import org.opentaint.dataflow.bidi.store.PersistentRecordStore
import org.opentaint.dataflow.bidi.store.RuleId
import org.opentaint.ir.api.common.cfg.CommonInst

enum class HistoricalState { DEMAND, CONFIRMED }
data class HistoricalFinding(val rule: RuleId, val statement: CommonInst)
data class HistoricalRun(val index: Int, val inputs: ProgramRunInputs, val observation: RunObservation,
                         val handOff: ProgramRunInputs)
data class HistoricalIterationResult(val findings: Map<HistoricalFinding, HistoricalState>, val runs: List<HistoricalRun>)

/**
 * F71's finite concrete iteration, with direct raw barriers and least support. No current HandOff,
 * ReportBuilder, Support, DemandStore lookup, record index or summary selector computes the answer.
 * A RunConfig is only an ApMode carrier; raw excluded-mark demands remain separate from its empty
 * typed store. The differential domain is validated by HistoricalF71, not widened silently.
 * Opt-in END rules are argued interpreter semantics, not a theorem of the historical Lean closure.
 */
class HistoricalConcreteIteration(private val program: FiniteProgramFixtures, private val roots: List<MethodKey>,
                                  private val limits: List<Int>,
                                  private val allowEndFacts: Boolean = false,
                                  private val allowMustSources: Boolean = false) {
    init {
        require(limits.isNotEmpty() && limits.all { it >= 1 })
        require(limits.zipWithNext().all { (a, b) -> b >= a })
    }

    fun run(): HistoricalIterationResult {
        var demand = emptyList<Pair<MethodKey, DemandPattern>>()
        var seeds = emptyList<Seed>()
        val records = LinkedHashSet<RawRecord>()
        val confirmed = LinkedHashMap<HistoricalFinding, HistoricalState>()
        var currentDemand = LinkedHashMap<HistoricalFinding, HistoricalState>()
        val log = ArrayList<HistoricalRun>()
        for ((offset, limit) in limits.withIndex()) {
            program.cancellation.checkpoint()
            val index = offset + 1
            val config = RunConfig(index, limit,
                if (index == 1) null else DemandStore.Builder(program.ap).build(),
                PersistentRecordStore(program.ap), SeedIndex.of(seeds), roots)
            val inputs = ProgramRunInputs(roots.toList(), demand.toList(), records.toList(), seeds.toList())
            var observation = HistoricalConcreteClosure(program, config, inputs, allowEndFacts, allowMustSources).run()
            if (config.direction == Direction.FORWARD) {
                observation = confirm(observation, roots, config.restricted)
                currentDemand = LinkedHashMap()
                for (witness in observation.witnesses) {
                    val key = HistoricalFinding(witness.rule, witness.statement)
                    if (witness.confirmed) confirmed[key] = HistoricalState.CONFIRMED
                    else if (key !in confirmed) currentDemand[key] = HistoricalState.DEMAND
                }
                // A later witness of the same key can confirm it; remove the earlier demand row.
                confirmed.keys.forEach(currentDemand::remove)
            }
            // Historical R1, independently scanned before hand-off. Raw native singleton facts only.
            for (edge in observation.rawSummaries) if (!edge.conclusion.demand && edge.premise.size == 1 &&
                !(config.direction == Direction.BACKWARD && isZero(edge.premise))) {
                records += RawRecord(edge.method, config.direction, edge.premise.single(), edge.conclusion)
            }
            demand = handOff(config.direction, observation)
            seeds = if (config.direction == Direction.FORWARD) sinkSeeds(observation, currentDemand.keys)
                else observation.sourceHits.map { Seed.Source(it.method, it.statement, it.forward) }
            log += HistoricalRun(index, inputs, observation,
                ProgramRunInputs(roots.toList(), demand.toList(), records.toList(), seeds.toList()))
            if (config.direction == Direction.FORWARD && (currentDemand.isEmpty() ||
                (observation.processed.none { it.conclusion.demand } &&
                    observation.rawSummaries.none { it.conclusion.demand } &&
                    observation.links.none { it.linkLayer == Layer.DEMAND }))) break
        }
        return HistoricalIterationResult(LinkedHashMap<HistoricalFinding, HistoricalState>().also {
            it.putAll(confirmed); it.putAll(currentDemand)
        }, log.toList())
    }

    /** HandoffDefs.lean handF/demOfN: selected publications swap; F71 keeps abstract mark exclusions. */
    private fun handOff(direction: Direction, observation: RunObservation): List<Pair<MethodKey, DemandPattern>> {
        val result = LinkedHashSet<Pair<MethodKey, DemandPattern>>()
        for (edge in observation.selectedDemand) {
            program.cancellation.checkpoint()
            fun forgetMust(p: Pattern): Pattern = if (p.fact.tail == Tail.ANY_TAINT)
                Pattern(p.fact.copy(tail = Tail.ANY), ExclusionSet.Empty) else p
            val entry = forgetMust(Pattern(edge.conclusion.fact.copy(path = edge.conclusion.fact.path.toList()), edge.conclusion.exclusion))
            if (direction == Direction.FORWARD) for (member in edge.premise) result += edge.method to DemandPattern(entry, forgetMust(member))
            else {
                check(edge.premise.size == 1)
                result += edge.method to DemandPattern(entry, edge.premise.single().takeUnless { it.fact.base == AccessPathBase.Zero })
            }
        }
        return result.toList()
    }

    private fun sinkSeeds(observation: RunObservation, wanted: Set<HistoricalFinding>): List<Seed> {
        val result = LinkedHashSet<Seed>()
        for (w in observation.witnesses) if (HistoricalFinding(w.rule, w.statement) in wanted) {
            program.cancellation.checkpoint()
            val forms = program.forms(w.methodKey)
            val sinks: List<SinkRule> = program.callExpression(w.methodKey, w.statement.location.index)?.let {
                forms.call(Direction.FORWARD, w.statement, it).sinks
            } ?: forms.exitRules(Direction.FORWARD, w.statement).rules.sinks
            for (s in sinks) if (s.rule == w.rule) for (p in s.patterns) if (p.fact.base != AccessPathBase.Zero)
                result += Seed.Sink(w.rule, w.methodKey, w.statement, p)
        }
        return result.toList()
    }

    /** Direct least joint support at one site, including AnyTaintExDefs.SupLinkX when opted in. */
    private fun confirm(observation: RunObservation, roots: List<MethodKey>, restricted: Boolean): RunObservation {
        data class Question(val method: MethodKey, val premise: Set<Pattern>)
        data class Site(val callee: MethodKey, val caller: MethodKey, val call: CommonInst)
        val sites = observation.links.filter { it.linkLayer == Layer.NORMAL && it.callerLayer == Layer.NORMAL }
            .groupBy { Site(it.callee, it.caller, it.call) }
        val questions = LinkedHashSet<Question>()
        for (link in observation.links) questions += Question(link.caller, link.premise)
        for (w in observation.witnesses) questions += Question(w.methodKey, union(w.edges.map { it.premise }))
        val supported = roots.mapTo(LinkedHashSet()) { Question(it, setOf(ZERO_PATTERN)) }
        fun suppliedAt(site: Site, rows: List<ObservedLink>, members: Set<Pattern>): Boolean = members.all { member ->
            rows.any { link ->
                val a = link.added
                val supplied = when {
                    member.fact.base == AccessPathBase.Zero -> a.fact.base == AccessPathBase.Zero
                    member.fact.mark !is MarkSlot.Concrete -> false
                    a.fact.tail == Tail.EXACT -> member.fact.tail == Tail.EXACT && member == a
                    allowMustSources && restricted && a.fact.tail == Tail.ANY_TAINT ->
                        member.fact.mark == a.fact.mark && (member.fact.tail == Tail.EXACT || member.fact.tail == Tail.ANY_TAINT) && inside(member, a)
                    else -> false
                }
                supplied && Question(site.caller, link.premise) in supported
            }
        }
        var changed: Boolean
        do {
            changed = false
            for (question in questions) {
                program.cancellation.checkpoint()
                if (question in supported) continue
                if (sites.any { (site, rows) -> site.callee == question.method && suppliedAt(site, rows, question.premise) }) {
                    supported += question; changed = true
                }
            }
        } while (changed)
        fun supports(method: MethodKey, premise: Set<Pattern>): Boolean =
            (method in roots && isZero(premise)) ||
                sites.any { (site, rows) -> site.callee == method && suppliedAt(site, rows, premise) }
        return observation.copy(witnesses = observation.witnesses.mapTo(LinkedHashSet()) { w ->
            w.copy(confirmed = w.edges.all { it.layer == Layer.NORMAL } && supports(w.methodKey, union(w.edges.map { it.premise })))
        })
    }

    private fun union(values: List<Set<Pattern>>): Set<Pattern> {
        val members = values.flatten().filterTo(LinkedHashSet()) { it.fact.base != AccessPathBase.Zero }
        return if (members.isEmpty()) setOf(ZERO_PATTERN) else members
    }
    private fun isZero(values: Set<Pattern>): Boolean = values.size == 1 && values.single().fact.base == AccessPathBase.Zero
}
