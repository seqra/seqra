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

/**
 * Current finite iteration, with direct raw barriers and unverified candidate reports. No current HandOff,
 * ReportBuilder, historical Support, DemandStore lookup, record index or summary selector computes the answer.
 * A RunConfig is only an ApMode carrier; raw excluded-mark demands remain separate from its empty
 * typed store. The generated domain and explicitly enabled END/must extensions are checked by
 * HistoricalF71 to match the historical comparison scope. The current raw selector and unverified report policy are coded here.
 */
class DirectCurrentIteration(private val program: FiniteProgramFixtures, private val roots: List<MethodKey>,
                                  private val limits: List<Int>,
                                  private val allowEndFacts: Boolean = false,
                                  private val allowMustSources: Boolean = false) {
    init {
        require(limits.isNotEmpty() && limits.first() == 1 && limits.all { it >= 1 })
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
            HistoricalF71.validate(program, inputs, allowEndFacts, allowMustSources)
            var observation = RawCurrentClosure(program, config, inputs).run()
            if (config.direction == Direction.FORWARD) {
                currentDemand = LinkedHashMap()
                for (witness in observation.witnesses) {
                    val key = HistoricalFinding(witness.rule, witness.statement)
                    if (!config.run1 && witness.confirmed) confirmed[key] = HistoricalState.CONFIRMED
                    else if (key !in confirmed) currentDemand[key] = HistoricalState.DEMAND
                }
                // A later witness of the same key can confirm it; remove the earlier demand row.
                confirmed.keys.forEach(currentDemand::remove)
            }
            // Eligible normal singleton facts use the same ordinary writer in every run.
            // Group/static-history D facts are excluded by their layer.
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
                (!config.run1 && observation.processed.none { it.conclusion.demand } &&
                    observation.rawSummaries.none { it.conclusion.demand } &&
                    observation.links.none { it.linkLayer == Layer.DEMAND }))) break
        }
        return HistoricalIterationResult(LinkedHashMap<HistoricalFinding, HistoricalState>().also {
            it.putAll(confirmed); it.putAll(currentDemand)
        }, log.toList())
    }

    /** Current ap.md9.2: structurally selected publications swap; abstract excluded marks normalize to STAR. */
    private fun handOff(direction: Direction, observation: RunObservation): List<Pair<MethodKey, DemandPattern>> {
        val result = LinkedHashSet<Pair<MethodKey, DemandPattern>>()
        for (edge in observation.selectedDemand) {
            program.cancellation.checkpoint()
            fun normalized(p: Pattern, entry: Boolean = false): Pattern {
                val forgotten = if (p.fact.tail == Tail.ANY_TAINT || (entry && p.fact.tail == Tail.STAR)) Pattern(p.fact.copy(tail = Tail.ANY), ExclusionSet.Empty) else p
                return if (forgotten.fact.mark is MarkSlot.Star) forgotten.copy(fact = forgotten.fact.copy(mark = MarkSlot.STAR)) else forgotten
            }
            val entry = normalized(Pattern(edge.conclusion.fact.copy(path = edge.conclusion.fact.path.toList()), edge.conclusion.exclusion), entry = true)
            if (direction == Direction.FORWARD) for (member in edge.premise) result += edge.method to DemandPattern(entry, normalized(member))
            else {
                check(edge.premise.size == 1)
                result += edge.method to DemandPattern(entry, edge.premise.single().takeUnless { it.fact.base == AccessPathBase.Zero }?.let(::normalized))
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

    private fun isZero(values: Set<Pattern>): Boolean = values.size == 1 && values.single().fact.base == AccessPathBase.Zero
}
