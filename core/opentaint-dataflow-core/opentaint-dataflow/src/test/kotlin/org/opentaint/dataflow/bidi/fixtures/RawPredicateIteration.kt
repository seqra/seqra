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

/** Independent finite NNF iteration with raw barriers and unverified candidate reports.
 *  It uses RawCurrentClosure and no production driver, demand/record index, Support or HandOff.
 *  Inputs admit positive bound source/sink conditions; END actions are ignored; aliases and ordered field actions
 *  remain outside this explicit test domain. This is a current oracle, not an F71 instance. */
class RawPredicateIteration(private val program: FiniteProgramFixtures, private val roots: List<MethodKey>,
                                  private val limits: List<Int>) {
    init {
        require(limits.isNotEmpty() && limits.first() == 1 && limits.all { it >= 1 })
        require(limits.zipWithNext().all { (a, b) -> b >= a })
    }

    private fun validateInputs(inputs: ProgramRunInputs) {
        for (call in program.callInputs) for (stage in call.plan.stages) when (stage) {
            is org.opentaint.dataflow.bidi.interp.CallStage.Actions ->
                check(stage.steps.isEmpty()) { "The NNF iteration oracle excludes ordered field/alias actions" }
            is org.opentaint.dataflow.bidi.interp.CallStage.Edges -> {
                check(stage.kind != org.opentaint.dataflow.bidi.interp.StageKind.ALIASES)
                check(stage.guard == null && stage.trigger == null)
            }
            is org.opentaint.dataflow.bidi.interp.CallStage.Callees -> Unit
        }
        check(inputs.records.all { !it.conclusion.demand })
        val summaries = program.statementInputs.map { it.summary } + program.entryInputs.map { it.rules.summary } +
            program.exitInputs.map { it.rules.rules.summary }
        check(summaries.flatMap { it.sources }.all { !it.backward })
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
            validateInputs(inputs)
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
            // Every phase persists eligible ordinary normal singleton facts; grouped F1 taint is D.
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
            for (s in sinks) if (s.rule == w.rule) {
                if (s.predicate != null) {
                    if (!s.unconditional) result += Seed.PredicateSink(checkNotNull(s.occurrenceId), w.methodKey, w.statement)
                } else for (p in s.patterns) if (p.fact.base != AccessPathBase.Zero)
                    result += Seed.Sink(w.rule, w.methodKey, w.statement, p)
            }
        }
        return result.toList()
    }

    private fun isZero(values: Set<Pattern>): Boolean = values.size == 1 && values.single().fact.base == AccessPathBase.Zero
}
