package org.opentaint.dataflow.bidi.driver

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.RunConfig
import org.opentaint.dataflow.bidi.engine.RunMethodAnalyzer
import org.opentaint.dataflow.bidi.engine.RunResult
import org.opentaint.dataflow.bidi.engine.RunStatus
import org.opentaint.dataflow.bidi.engine.SharedObjects
import org.opentaint.dataflow.bidi.interp.SinkRule
import org.opentaint.dataflow.bidi.store.DemandStore
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.Collections

/** Immutable outgoing demand, seeds and accepted explicit-pattern counts. */
class HandOff(val demand: DemandStore, val seeds: SeedIndex, demandEdges: Map<MethodKey, Int>) {
    val demandEdges: Map<MethodKey, Int> = Collections.unmodifiableMap(LinkedHashMap(demandEdges))

    companion object {
        fun of(config: RunConfig, result: RunResult<RunMethodAnalyzer>, report: ReportBuilder,
               shared: SharedObjects): HandOff {
            check(result.status == RunStatus.COMPLETE) { "A hand-off needs a complete run" }
            shared.cancellation.checkpoint()
            val counts = LinkedHashMap<MethodKey, Int>()
            val (demand, seeds) = if (config.direction == Direction.FORWARD)
                toBackward(result, report, shared, counts, config.run1) else toForward(result, shared, counts)
            return HandOff(demand, seeds, counts)
        }

        private inline fun forEachDemandPiece(result: RunResult<RunMethodAnalyzer>, shared: SharedObjects,
                                             body: (MethodKey, PremiseKey, Pattern) -> Unit) {
            for (actor in result.analyzers) {
                shared.cancellation.checkpoint()
                for ((premise, facts) in actor.summaries.demandEdges()) {
                    shared.cancellation.checkpoint()
                    for (leaf in shared.ops.leaves(facts)) {
                        shared.cancellation.checkpoint()
                        body(actor.key, premise, leaf)
                    }
                }
            }
        }

        private fun toBackward(result: RunResult<RunMethodAnalyzer>, report: ReportBuilder, shared: SharedObjects,
                               counts: MutableMap<MethodKey, Int>, groupedStatics: Boolean): Pair<DemandStore, SeedIndex> {
            val demand = DemandStore.Builder(shared.ap)
            if (groupedStatics) demand.includeStaticGroups()
            forEachDemandPiece(result, shared) { method, premise, piece ->
                val entry = normalizeDemandMark(located(piece).let { p ->
                    if (p.fact.tail == Tail.STAR) p.copy(fact = p.fact.copy(tail = Tail.ANY), exclusion = ExclusionSet.Empty) else p
                })
                premise.forEachMember { member ->
                    shared.cancellation.checkpoint()
                    val pattern = DemandPattern(entry, normalizeDemandMark(located(member.toPattern())))
                    if (demand.add(method, pattern)) counts.merge(method, 1, Int::plus)
                }
            }
            val seeds = LinkedHashSet<Seed>()
            for (entry in report.demandEntries()) {
                shared.cancellation.checkpoint()
                val methods = LinkedHashSet<MethodKey>()
                for (witness in entry.witnesses) {
                    shared.cancellation.checkpoint()
                    methods += witness.methodKey
                }
                for (method in methods) {
                    shared.cancellation.checkpoint()
                    withSinksAt(shared, method, entry.key.statement) { sinks ->
                        for (sink in sinks) {
                            shared.cancellation.checkpoint()
                            if (sink.rule != entry.key.rule) continue
                            if (sink.predicate != null) {
                                if (!sink.unconditional)
                                    seeds += Seed.PredicateSink(checkNotNull(sink.occurrenceId), method, entry.key.statement)
                                continue
                            }
                            for (literal in sink.seedPatterns()) {
                                shared.cancellation.checkpoint()
                                seeds += Seed.Sink(entry.key.rule, method, entry.key.statement, literal)
                            }
                        }
                    }
                }
            }
            return demand.build() to SeedIndex.of(seeds.toList())
        }

        /** Must carries locations in backward demand, without its guarantee or exclusion. */
        private fun located(pattern: Pattern): Pattern = if (pattern.fact.tail != Tail.ANY_TAINT) pattern
            else Pattern(pattern.fact.copy(tail = Tail.ANY), ExclusionSet.Empty)

        private fun normalizeDemandMark(pattern: Pattern): Pattern =
            if (pattern.fact.mark !is MarkSlot.Star || pattern.fact.mark.excluded.isEmpty) pattern
            else pattern.copy(fact = pattern.fact.copy(mark = MarkSlot.STAR))

        private fun withSinksAt(shared: SharedObjects, key: MethodKey, statement: CommonInst,
                                use: (List<SinkRule>) -> Unit) {
            val forms = shared.contexts.forms(key)
            val call = shared.language.getCallExpr(statement)
            if (call != null) {
                forms.withCall(Direction.FORWARD, statement, call) { use(it.sinks) }
            } else if (shared.interpreter.exitNodes(key).any { shared.cancellation.checkpoint(); it.node == statement }) {
                forms.withExitRules(Direction.FORWARD, statement) { use(it.rules.sinks) }
            }
        }

        private fun toForward(result: RunResult<RunMethodAnalyzer>, shared: SharedObjects,
                              counts: MutableMap<MethodKey, Int>): Pair<DemandStore, SeedIndex> {
            val demand = DemandStore.Builder(shared.ap)
            forEachDemandPiece(result, shared) { method, premise, piece ->
                val pattern = when (premise) {
                    is InitialAp -> DemandPattern(normalizeDemandMark(piece),
                        if (premise.isZero) null else normalizeDemandMark(premise.toPattern()))
                    is PremiseSet -> error("The backward run has no multi-premise summary (ap.md §9.2)")
                }
                if (demand.add(method, pattern)) counts.merge(method, 1, Int::plus)
            }
            val seeds = ArrayList<Seed>()
            for (actor in result.analyzers) {
                shared.cancellation.checkpoint()
                for ((method, statement, edge, action) in checkNotNull(actor.sourceHits).occurrences()) {
                    shared.cancellation.checkpoint()
                    seeds += Seed.Source(method, statement, edge, action)
                }
            }
            return demand.build() to SeedIndex.of(seeds)
        }
    }
}
