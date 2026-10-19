package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.ProofKey
import org.opentaint.dataflow.bidi.interp.ResumeSite
import org.opentaint.dataflow.bidi.store.CallerRef

/** A reference to an accepted positive literal, not a new source or an absence test. */
data class GroundingRequirement(val original: PremiseKey, val accepted: Facts, val literal: Pattern,
                                val proof: ProofKey? = null, val leafOrdinal: Int = -1,
                                val relationInitial: InitialAp? = null)

data class GroundingArgument(val initial: InitialAp, val ref: CallerRef, val matched: Facts,
                             val grounding: GroundingSupport = GroundingSupport.TRUE)

/** Structural references to accepted bindings. AND never distributes over OR. */
sealed interface GroundingSupport {
    data object True : GroundingSupport
    data object False : GroundingSupport
    data class Row(val requirements: List<GroundingRequirement>) : GroundingSupport
    data class And(val left: GroundingSupport, val right: GroundingSupport) : GroundingSupport
    data class Or(val left: GroundingSupport, val right: GroundingSupport) : GroundingSupport
    /** Orthogonal current cofactor certificate; it is not another guard or a derivation step. */
    data class WithFixed(override val logical: GroundingSupport,
                         override val fixedImageSupport: GroundingSupport) : GroundingSupport

    val logical: GroundingSupport get() = if (this is WithFixed) this.logical else this
    val fixedImageSupport: GroundingSupport get() = if (this is WithFixed) this.fixedImageSupport else False
    fun certified(): GroundingSupport = withFixed(fixedImageSupport, fixedImageSupport)

    fun and(other: GroundingSupport): GroundingSupport = if (this is WithFixed || other is WithFixed) {
        withFixed(logical.and(other.logical), fixedImageSupport.and(other.logical).or(logical.and(other.fixedImageSupport)))
    } else when {
        this == False || other == False -> False
        this == True -> other
        other == True || containsAnd(other) -> this
        other.containsAnd(this) -> other
        else -> And(this, other)
    }
    fun or(other: GroundingSupport): GroundingSupport = if (this is WithFixed || other is WithFixed) {
        withFixed(logical.or(other.logical), fixedImageSupport.or(other.fixedImageSupport))
    } else when {
        this == True || other == True -> True
        this == False -> other
        other == False -> this
        contains(other) -> this
        other.contains(this) -> other
        else -> Or(this, other)
    }
    private fun contains(other: GroundingSupport): Boolean = this == other ||
        (this is Or && (left.contains(other) || right.contains(other)))
    private fun containsAnd(other: GroundingSupport): Boolean = this == other ||
        (this is And && (left.containsAnd(other) || right.containsAnd(other)))

    companion object {
        val TRUE: GroundingSupport = True
        fun withFixed(logical: GroundingSupport, fixed: GroundingSupport): GroundingSupport =
            if (logical == False) False else if (fixed == False) logical else WithFixed(logical, fixed)
        fun require(requirements: List<GroundingRequirement>): GroundingSupport =
            if (requirements.isEmpty()) TRUE else Row(requirements.distinct())
    }
}

/** Owner-side guard composition. Persistent support lives on existing store/accepted-input rows. */
internal class Run1Grounding(private val core: RunMethodAnalyzer) {
    private var currentSite: ResumeSite? = null
    fun <T> at(site: ResumeSite, block: () -> T): T {
        val previous = currentSite
        currentSite = site
        try { return block() } finally { currentSite = previous }
    }
    private var nextVersion = 0L
    private data class FlowKey(val site: ResumeSite, val original: InitialAp)
    private data class WaitingLeaf(val original: PremiseKey, val pattern: Pattern, val layer: Layer,
                                   val state: CallResumeState?)
    private val flows = HashMap<FlowKey, ConclusionGroup>()
    private val flowVersions = HashMap<FlowKey, Long>()
    private val flowWaiters = HashMap<FlowKey, MutableMap<WaitingLeaf, GroundingSupport>>()
    private val flowInterners = core.ap.newInterners()

    private fun abstract(original: InitialAp): InitialAp = core.ap.initial(original.base, original.path,
        Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR)
    private fun geometry(flow: FlowTree): FlowTree = if (flow.markExclusion.isEmpty) flow else
        core.ap.flowTree(flow.base, flow.layer, flow.exclusion, MarkExclusions.EMPTY, flow.root, flow.tail)

    /** Save only sensitive-operation/call input relations, never a second CFG store. */
    fun observe(at: OperationInput) {
        val original = at.premise as? InitialAp ?: return
        val facts = at.facts as? FlowTree ?: return
        val key = FlowKey(at.site, original)
        if (flows.getOrPut(key) { ConclusionGroup(core.ap, flowInterners) }.add(facts) == null) return
        flowVersions[key] = ++nextVersion
        for ((waiter, support) in flowWaiters[key].orEmpty()) core.installPoint(waiter.original,
            core.ap.factsOf(waiter.pattern.fact, waiter.pattern.exclusion, waiter.layer),
            at.site, waiter.state, support)
    }

    fun relations(site: ResumeSite, original: InitialAp, base: org.opentaint.dataflow.ap.ifds.AccessPathBase): List<FlowTree> =
        flows[FlowKey(site, abstract(original))]?.all()?.filterIsInstance<FlowTree>()?.filter { it.base == base }?.toList().orEmpty()
    fun callRevision(ref: CallerRef): Long = (ref.premise as? InitialAp)?.let { original ->
        if (original.isZero) 0L else flowVersions[FlowKey(
            ResumeSite.CallStageInput(core.operation(ref.call, ref.stageOrdinal)), abstract(original))]
    } ?: 0L

    fun requirement(at: OperationInput, accepted: Facts, literal: Pattern, proof: ProofKey? = null,
                    leaf: Int = -1): GroundingSupport {
        val inherited = at.support ?: core.currentGroundingSupport
        val original = at.premise as? InitialAp
        if (original == null || original.isZero || original.mark !is MarkSlot.Concrete)
            return inherited.and(rows(at.premise, accepted, literal, proof, leaf))
        if (original.tail == Tail.EXACT || (original.base == STATIC && original.pathData.size == 1))
            return inherited.and(rows(original, accepted, literal, proof, leaf))
        val relations = relations(at.site, original, accepted.base)
        if (relations.isEmpty()) {
            val waiters = flowWaiters.getOrPut(FlowKey(at.site, abstract(original))) { LinkedHashMap() }
            for (part in core.ops.leaves(at.facts)) {
                val key = WaitingLeaf(at.premise, part, at.facts.layer, at.callState)
                waiters[key] = waiters[key]?.or(inherited) ?: inherited
            }
            val certified = if (original.base == STATIC || original.tail == Tail.EXACT) inherited else inherited.certified()
            return certified.and(rows(original, accepted, literal, proof, leaf))
        }
        var selected: GroundingSupport = GroundingSupport.False
        for (relation in relations) selected = selected.or(rows(original, geometry(relation), literal, proof, leaf, abstract(original)))
        return inherited.and(selected)
    }

    /** Accepted semantic leaf relations are stable; cumulative AP roots are never support versions. */
    private fun rows(original: PremiseKey, accepted: Facts, literal: Pattern, proof: ProofKey?, leaf: Int,
                     relationInitial: InitialAp? = null): GroundingSupport {
        var result: GroundingSupport = GroundingSupport.False
        for (part in core.ops.leaves(accepted)) result = result.or(GroundingSupport.require(listOf(
            GroundingRequirement(original, core.ap.factsOf(part.fact, part.exclusion, accepted.layer),
                literal, proof, leaf, relationInitial))))
        return result
    }

    /** A producer certificate is transient metadata on its actual accepted result. */
    fun fixed(at: OperationInput): GroundingSupport {
        val support = at.support ?: core.currentGroundingSupport
        if (at.premise.isZero) return support
        return GroundingSupport.withFixed(support.logical, support.logical)
    }

    fun needsCallRelation(support: GroundingSupport, ref: CallerRef, base: org.opentaint.dataflow.ap.ifds.AccessPathBase): Boolean {
        val caller = ref.premise as? InitialAp ?: return false
        if (caller.isZero || caller.base == STATIC || caller.mark !is MarkSlot.Concrete || caller.tail == Tail.EXACT) return false
        fun uses(node: GroundingSupport): Boolean = when (node) {
            is GroundingSupport.Row -> node.requirements.any { it.relationInitial != null }
            is GroundingSupport.And -> uses(node.left) || uses(node.right)
            is GroundingSupport.Or -> uses(node.left) || uses(node.right)
            is GroundingSupport.WithFixed -> uses(node.logical)
            else -> false
        }
        return uses(support) && relations(ResumeSite.CallStageInput(core.operation(ref.call, ref.stageOrdinal)), caller, base).isEmpty()
    }
    /** Re-express callee guard rows through one ordinary matched caller binding, without DNF conversion. */
    fun rebind(support: GroundingSupport, arguments: List<GroundingArgument>): GroundingSupport {
        fun requirement(row: GroundingRequirement): GroundingSupport {
            if (row.original.isZero) return GroundingSupport.Row(listOf(row))
            val needed = row.original.members
            val selected = needed.map { initial -> arguments.firstOrNull { it.initial === initial } ?: return GroundingSupport.False }
            var original: PremiseKey = selected.firstOrNull()?.ref?.premise ?: row.original
            for (argument in selected.drop(1)) original = core.ap.union(original, argument.ref.premise)
            val images = ArrayList<Facts>()
            val out = object : ApOut {
                override fun result(f: Facts) { images += f }
                override fun markRequest(mark: TaintMark) = Unit
            }
            when (val from = row.original) {
                is InitialAp -> if (from.isZero) return GroundingSupport.Row(listOf(row))
                    else {
                        val argument = selected.single()
                        val caller = argument.ref.premise as? InitialAp
                        val relationInitial = row.relationInitial
                        val shadow = if (relationInitial != null && caller != null && !caller.isZero)
                            relations(ResumeSite.CallStageInput(core.operation(argument.ref.call, argument.ref.stageOrdinal)),
                                caller, argument.matched.base) else emptyList()
                        if (shadow.isNotEmpty()) for (flow in shadow)
                            core.ops.applySummary(geometry(flow), checkNotNull(relationInitial), row.accepted, core.mode, out)
                        else core.ops.applySummary(argument.matched, relationInitial ?: from, row.accepted, core.mode, out)
                    }
                is PremiseSet -> if (row.accepted is TaintTree)
                    core.ops.applyCombination(selected.map { it.matched to it.initial }, row.accepted, core.mode, out)
            }
            if (images.isEmpty()) return GroundingSupport.False
            var result: GroundingSupport? = null
            for (image in images) {
                val mapped = rows(original, image, row.literal, row.proof, row.leafOrdinal,
                    if (image is FlowTree && original is InitialAp) abstract(original) else null)
                result = result?.or(mapped) ?: mapped
            }
            return checkNotNull(result)
        }
        val mapped = HashMap<GroundingSupport, GroundingSupport>()
        fun map(node: GroundingSupport): GroundingSupport = mapped.getOrPut(node) { when (node) {
            GroundingSupport.True -> node
            GroundingSupport.False -> node
            is GroundingSupport.Row -> node.requirements.fold(GroundingSupport.TRUE) { out, row -> out.and(requirement(row)) }
            is GroundingSupport.And -> map(node.left).and(map(node.right))
            is GroundingSupport.Or -> map(node.left).or(map(node.right))
            is GroundingSupport.WithFixed -> GroundingSupport.withFixed(map(node.logical), map(node.fixedImageSupport))
        } }
        var bound = map(support)
        for (argument in arguments) bound = bound.and(argument.grounding)
        return bound
    }

    /** Every literal is checked from the same complete incoming binding supplied to this writer. */
    fun permits(premise: PremiseKey, value: Facts, inputs: List<RegionGroups>,
                selected: GroundingSupport? = null): Boolean {
        val support = selected ?: core.currentGroundingSupport
        if (support == GroundingSupport.TRUE) return true
        val actual = premise.members.zip(inputs).associate { (initial, region) -> initial to core.staticFacts(region) }
        fun accepted(requirement: GroundingRequirement): Boolean {
            var holds = false
            val out = object : ApOut {
                override fun result(f: Facts) {
                    if (core.ops.checkMark(f, requirement.literal, core.mode) is MarkCheck.Holds) holds = true
                }
                override fun markRequest(mark: TaintMark) = Unit
            }
            when (val original = requirement.original) {
                is InitialAp -> {
                    if (original.isZero) holds = core.ops.checkMark(requirement.accepted, requirement.literal, core.mode) is MarkCheck.Holds
                    else actual[original]?.let { input ->
                        val matched = core.ops.satisfying(input, original, core.mode)
                        if (matched != null) core.ops.applySummary(matched, requirement.relationInitial ?: original,
                            requirement.accepted, core.mode, out)
                    }
                }
                is PremiseSet -> if (original.members.all { it in actual } && requirement.accepted is TaintTree)
                    core.ops.applyCombination(original.members.map { checkNotNull(actual[it]) to it }, requirement.accepted, core.mode, out)
            }
            return holds
        }
        fun evaluate(support: GroundingSupport): Boolean = when (support) {
            GroundingSupport.True -> true
            GroundingSupport.False -> false
            is GroundingSupport.Row -> support.requirements.all(::accepted)
            is GroundingSupport.And -> evaluate(support.left) && evaluate(support.right)
            is GroundingSupport.Or -> evaluate(support.left) || evaluate(support.right)
            is GroundingSupport.WithFixed -> evaluate(support.logical)
        }
        return evaluate(support)
    }

    fun clear() { flows.clear(); flowVersions.clear(); flowWaiters.clear() }
}
