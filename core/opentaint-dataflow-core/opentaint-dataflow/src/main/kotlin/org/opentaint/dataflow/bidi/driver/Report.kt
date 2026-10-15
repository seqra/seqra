package org.opentaint.dataflow.bidi.driver

import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.engine.RunActor
import org.opentaint.dataflow.bidi.engine.RunConfig
import org.opentaint.dataflow.bidi.engine.RunResult
import org.opentaint.dataflow.bidi.engine.RunStatus
import org.opentaint.dataflow.bidi.store.SinkWitness
import org.opentaint.dataflow.bidi.store.VulnerabilityKey
import java.util.Collections

enum class ReportState { CONFIRMED, DEMAND }
enum class EndReason { STOP_RULE, NO_DEMAND_EDGE, POLICY, ABNORMAL }

data class AnalysisEnd(val status: RunStatus, val run: Int, val direction: Direction, val reason: EndReason)

/** A report owns its lists and retains each witness's complete support shape. */
class Report(entries: List<Entry>, val end: AnalysisEnd) {
    val entries: List<Entry> = owned(entries)

    class Entry(val key: VulnerabilityKey, val state: ReportState, val run: Int, witnesses: List<SinkWitness>) {
        val witnesses: List<SinkWitness> = owned(witnesses)
    }
}

private fun <T> owned(values: Collection<T>): List<T> = Collections.unmodifiableList(ArrayList(values))

/** One driver barrier owns these updates. Only complete forward runs replace the demand set.
 * A NORMAL edge is not a confirmation certificate. Unproved witnesses stay DEMAND; an explicitly
 * proved confirmation remains sticky across later complete and incomplete runs.
 */
class ReportBuilder {
    private class State(val confirmed: Map<VulnerabilityKey, Report.Entry>,
                        val demand: Map<VulnerabilityKey, Report.Entry>)
    private var state = State(emptyMap(), emptyMap())

    fun <A : RunActor> add(config: RunConfig, result: RunResult<A>) {
        if (result.status != RunStatus.COMPLETE || result.direction != Direction.FORWARD ||
            config.direction != Direction.FORWARD) return
        val confirmed = LinkedHashMap(state.confirmed)
        val demand = LinkedHashMap<VulnerabilityKey, Report.Entry>()
        for ((key, witnesses) in result.witnessesByKey()) {
            if (witnesses.any { it.confirmed }) {
                confirmed.putIfAbsent(key, Report.Entry(key, ReportState.CONFIRMED, config.index, witnesses))
            } else if (key !in confirmed) {
                demand[key] = Report.Entry(key, ReportState.DEMAND, config.index, witnesses)
            }
        }
        // Finish all enumeration and allocation before committing either report set.
        state = State(confirmed, demand)
    }

    fun demandEntries(): List<Report.Entry> = owned(state.demand.values)
    fun hasDemandVulnerability(): Boolean = state.demand.isNotEmpty()
    val confirmedCount: Int get() = state.confirmed.size
    fun build(end: AnalysisEnd): Report = Report(state.confirmed.values + state.demand.values, end)
}
