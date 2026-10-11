package org.opentaint.dataflow.bidi.driver

import mu.KLogging
import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.engine.RunConfig
import org.opentaint.dataflow.bidi.engine.RunMethodAnalyzer
import org.opentaint.dataflow.bidi.engine.RunResult
import org.opentaint.dataflow.bidi.engine.RunStatus
import java.util.Collections

enum class DemandCause { FIELD_LIMIT_CUT, MAY_TARGET, CLEANER_ROW, MUST_RECORD, DEMAND_INPUT }

/** A completed run's owned keys and counts. No edge or run store is retained. */
class Frontier(
    val run: Int,
    val direction: Direction,
    analysed: Set<MethodKey>,
    demandEdges: Map<MethodKey, Int>,
    val recordCrossings: Long,
    val demandVulnerabilities: Int,
    val confirmedVulnerabilities: Int,
    val seeds: Int,
    zeroOnly: Set<MethodKey>,
    val zeroOnlyEdges: Long,
    demandByCause: Map<DemandCause, Long>? = null,
) {
    val analysed: Set<MethodKey> = Collections.unmodifiableSet(LinkedHashSet(analysed))
    val demandEdges: Map<MethodKey, Int> = Collections.unmodifiableMap(LinkedHashMap(demandEdges))
    val zeroOnly: Set<MethodKey> = Collections.unmodifiableSet(LinkedHashSet(zeroOnly))
    val demandByCause: Map<DemandCause, Long>? = demandByCause?.let {
        Collections.unmodifiableMap(LinkedHashMap(it))
    }
}

/** The barrier reads counters only after all method owners have joined and frozen. */
fun frontierOf(config: RunConfig, result: RunResult<RunMethodAnalyzer>, handOff: HandOff,
               report: ReportBuilder): Frontier {
    check(result.status == RunStatus.COMPLETE) { "A frontier needs a complete run" }
    val analysed = LinkedHashSet<MethodKey>()
    val zeroOnly = LinkedHashSet<MethodKey>()
    var zeroOnlyEdges = 0L
    var recordCrossings = 0L
    for (actor in result.analyzers) {
        actor.ap.cancellation.checkpoint()
        if (actor.nonZeroInitial) analysed += actor.key
        else {
            zeroOnly += actor.key
            zeroOnlyEdges += actor.edgeDeltas
        }
        recordCrossings += actor.recordCrossings
    }
    val cancellation = result.analyzers.firstOrNull()?.ap?.cancellation
    val counts = LinkedHashMap<MethodKey, Int>()
    for ((key, count) in handOff.demandEdges) {
        cancellation?.checkpoint()
        counts[key] = count
    }
    cancellation?.checkpoint()
    val forward = config.direction == Direction.FORWARD
    return Frontier(config.index, config.direction, analysed, counts, recordCrossings,
        if (forward) report.demandEntries().size else 0,
        if (forward) report.confirmedCount else 0, handOff.seeds.size, zeroOnly, zeroOnlyEdges)
}

private val frontierLogger = object : KLogging() {}.logger

fun Frontier.log() {
    frontierLogger.info {
        "Run $run ($direction) frontier: analysed ${analysed.size}, demand edges ${demandEdges.values.sum()} " +
            "in ${demandEdges.size} method keys, left ${(analysed - demandEdges.keys).size}, " +
            "record crossings $recordCrossings, DEMAND $demandVulnerabilities, CONFIRMED " +
            "$confirmedVulnerabilities, seeds $seeds, zero only ${zeroOnly.size} ($zeroOnlyEdges edges)"
    }
    frontierLogger.debug { "Run $run frontier: analysed $analysed; demand edges $demandEdges" }
    demandByCause?.let { frontierLogger.info { "Run $run demand-layer results per cause: $it" } }
}
