package org.opentaint.jvm.sast.dataflow

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.RunStatus
import org.opentaint.dataflow.bidi.interp.ActionId
import org.opentaint.ir.api.common.cfg.CommonInst

/** Decode while the real joined run owns the AP tables; retain no analyzer or mutable store. */
data class JvmObservedPath(
    val base: AccessPathBase,
    val path: List<String> = emptyList(),
    val tail: Tail,
    val mark: String,
    val exclusion: Set<String> = emptySet(),
    val excludedMarks: Set<String> = emptySet(),
)
data class JvmObservedConclusion(val value: JvmObservedPath, val layer: Layer)
data class JvmObservedInitial(val method: MethodKey, val value: JvmObservedPath)
data class JvmObservedEdge(val method: MethodKey, val premise: Set<JvmObservedPath>,
                           val conclusion: JvmObservedConclusion, val node: CommonInst? = null)
data class JvmObservedRecord(val method: MethodKey, val direction: Direction,
                             val premise: JvmObservedPath, val conclusion: JvmObservedConclusion)
data class JvmObservedDemand(val method: MethodKey, val entry: JvmObservedPath, val exit: JvmObservedPath?)
data class JvmObservedLink(val callee: MethodKey, val caller: MethodKey, val call: CommonInst,
                           val premise: Set<JvmObservedPath>, val callerLayer: Layer,
                           val linkLayer: Layer, val added: JvmObservedPath)
data class JvmObservedRequest(val method: MethodKey, val originalPremise: JvmObservedPath,
                              val mark: String? = null, val position: List<String>? = null)
data class JvmObservedSourceHit(val method: MethodKey, val statement: CommonInst,
                                val forwardFrom: JvmObservedPath, val forwardTo: JvmObservedPath,
                                val sharedExclusion: Set<String> = emptySet(),
                                val action: ActionId? = null)

/** These scalars are copied from the actual frozen actor, never counted from decoded leaves. */
data class JvmObservedMethodCounters(
    val steps: Long,
    val edgeDeltas: Long,
    val nonZeroInitial: Boolean,
    val demandLayerEdges: Long,
    val recordCrossings: Long,
    val triggerSeeds: Long,
)

/** Owned snapshots only. Full MethodKeys and ordered literal support stay available in Report. */
data class CompleteJvmRun(
    val index: Int,
    val fieldLimit: Int,
    val direction: Direction,
    val status: RunStatus,
    val processed: Set<JvmObservedEdge>,
    val initials: Set<JvmObservedInitial>,
    val rawSummaries: Set<JvmObservedEdge>,
    val selected: Set<JvmObservedEdge>,
    val links: Set<JvmObservedLink>,
    val requests: Set<JvmObservedRequest>,
    val sourceHits: Set<JvmObservedSourceHit>,
    val records: Set<JvmObservedRecord>,
    /** The stopped Next.End does not expose its hand-off payload; its actual counts are in Frontier. */
    val outgoingDemand: Set<JvmObservedDemand>?,
    val incomingSeeds: Set<Seed>,
    val outgoingSeeds: Set<Seed>?,
    val methodCounters: Map<MethodKey, JvmObservedMethodCounters>,
    val steps: Long,
    val edgeDeltas: Long,
    val demandLayerEdges: Long,
    val recordCrossings: Long,
    val triggerSeeds: Long,
    val nonZeroInitials: Set<MethodKey>,
    /** Actual ownership catalog: full name to its run-1 group atom label. */
    val markGroups: Map<String, String> = emptyMap(),
)
data class BidiAcceptanceResult(val output: List<VulnerabilityWithTrace>,
                               val report: Report, val runs: List<CompleteJvmRun>, val frontiers: List<Frontier>,
                               val witnesses: List<J03ObservedWitness> = emptyList()) {
    fun method(name: String): MethodKey = runs.asSequence().flatMap { run ->
        run.initials.asSequence().map { it.method }
    }.distinct().single { it.method.name == name }
    fun raw(run: Int, method: MethodKey, base: AccessPathBase): Set<JvmObservedConclusion> =
        runs.single { it.index == run }.rawSummaries.filter { it.method == method &&
            it.conclusion.value.base == base }.mapTo(linkedSetOf()) { it.conclusion }
}
