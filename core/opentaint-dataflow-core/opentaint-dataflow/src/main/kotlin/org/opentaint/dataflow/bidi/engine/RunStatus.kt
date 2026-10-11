package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.store.SinkWitness
import org.opentaint.dataflow.bidi.store.VulnerabilityKey
import org.opentaint.dataflow.bidi.store.VulnerabilityStore
import java.util.Collections

enum class RunStatus { COMPLETE, TIMEOUT, OOM, FAILED }

/** Incomplete results provide no run stores. The shared witnesses are read at a complete barrier. */
class RunResult<A : RunActor>(
    val status: RunStatus,
    analyzers: List<A>,
    val runIndex: Int,
    val direction: Direction,
    val vulnerabilities: VulnerabilityStore,
) {
    val analyzers: List<A> = Collections.unmodifiableList(ArrayList(analyzers))

    init {
        check(status == RunStatus.COMPLETE || this.analyzers.isEmpty())
    }

    fun witnessesByKey(): Map<VulnerabilityKey, List<SinkWitness>> =
        if (status == RunStatus.COMPLETE)
            vulnerabilities.witnessesOf(runIndex).groupBy({ it.first }, { it.second })
        else emptyMap()

    val demandLayerEdges: Long get() = analyzers.sumOf { it.demandLayerEdges }
    val recordCrossings: Long get() = analyzers.sumOf { it.recordCrossings }
}
