package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.*

/** Only immutable invocation state is retained, never a cached full call plan. */
data class CallResumeState(val callerLayer: Layer, val identityCandidate: Boolean? = null,
                           val pendingDemotion: Boolean = false)

data class OperationInput(val site: ResumeSite, val premise: PremiseKey, val facts: Facts,
                          val callState: CallResumeState? = null, val support: GroundingSupport? = null)

data class PointContinuation(val owner: MethodKey, val id: Long, val site: ResumeSite,
                             val initial: InitialAp, val current: FlowTree,
                             val callState: CallResumeState?)

/** Actor-owned resume directory. Service replies name these rows; they never carry callbacks. */
internal class Run1PointResume(private val core: RunMethodAnalyzer) {
    private var next = 0L
    private data class PointKey(val site: ResumeSite, val initial: InitialAp, val image: Pattern, val layer: Layer,
                                val state: CallResumeState?)
    private val pointKeys = HashMap<PointKey, Long>()
    private val points = HashMap<Long, PointContinuation>()
    private data class ReadKey(val site: ResumeSite, val operation: StaticOperation)
    private val readKeys = HashMap<ReadKey, Long>()
    private val reads = HashMap<Long, Pair<OperationInput, StaticOperation>>()
    private data class WriterKey(val operation: OperationId, val premise: PremiseKey, val value: Pattern, val layer: Layer)
    private val writerKeys = HashMap<WriterKey, Long>()
    private class Writer(val premise: PremiseKey, val value: Facts, var support: GroundingSupport)
    private val writers = HashMap<Long, Writer>()
    private val writerInputs = HashMap<Long, MutableMap<List<Pattern>, List<GroupSet>>>()
    private val direct = HashMap<Long, OperationInput>()
    private data class CallKey(val callee: MethodKey, val ref: org.opentaint.dataflow.bidi.store.CallerRef,
                               val linkLayer: Layer, val premise: PremiseKey, val result: Pattern,
                               val layer: Layer, val member: Int)
    private class CallContinuation(var part: Subscription, var publication: Publication, val member: Int) {
        val supplies = LinkedHashMap<Pattern, GroupSet>()
    }
    private val callKeys = HashMap<CallKey, Long>()
    private val calls = HashMap<Long, CallContinuation>()

    fun callInterest(part: Subscription, pub: Publication, member: Int) {
        val initial = pub.premise.member(member)
        val atom = (initial.mark as? MarkSlot.Concrete)?.mark ?: return
        for (leaf in core.ops.leaves(pub.conclusion)) {
            val piece = pub.copy(conclusion = core.ap.factsOf(leaf.fact, leaf.exclusion, pub.layer))
            val id = callKeys.getOrPut(CallKey(part.callee, part.ref, part.linkLayer, pub.premise, leaf, pub.layer, member)) {
                (++next).also { calls[it] = CallContinuation(part, piece, member) }
            }
            val call = checkNotNull(calls[id])
            val support = call.publication.grounding.or(piece.grounding)
            val inputSupport = call.part.grounding.or(part.grounding)
            if (support != call.publication.grounding || inputSupport != call.part.grounding) {
                call.publication = piece.copy(grounding = support)
                call.part = part.copy(grounding = inputSupport)
                for ((region, supplied) in call.supplies)
                    core.staticCallSupply(call.part, call.publication, member, RegionGroups(region, supplied))
            }
            core.staticBatch.interests += StaticInterest(core.key, id, initial.toPattern(),
                GroupSet.of(core.ap.marks.groupOf(atom)))
        }
        core.requestWork()
    }

    fun reached(summary: StatementSummary, at: OperationInput) {
        if (at.facts !is Reach || !at.premise.isZero) return
        for (operation in summary.staticOperations) {
            val key = ReadKey(at.site, operation)
            val id = readKeys.getOrPut(key) {
                (++next).also { reads[it] = at to operation }
            }
            when (operation) {
                is StaticOperation.Read -> core.staticBatch.reads += StaticReadRegistration(core.key, id,
                    Pattern(operation.transfer.from, operation.transfer.exclusion))
                is StaticOperation.MarkInput -> {
                    val mark = (operation.position.fact.mark as? MarkSlot.Concrete)?.mark ?: continue
                    val groups = GroupSet.of(core.ap.marks.groupOf(mark))
                    if (operation.position.fact.path.size == 1) {
                        // A class point has no legal abstract initial. Match actual groups directly.
                        direct[id] = at
                        core.staticBatch.interests += StaticInterest(core.key, id, operation.position, groups)
                    } else core.staticBatch.reads += StaticReadRegistration(core.key, id, operation.position)
                }
                is StaticOperation.Write -> Unit
            }
        }
    }

    fun request(at: OperationInput, groups: GroupSet) {
        val original = at.premise as? InitialAp ?: return
        val flow = at.facts as? FlowTree ?: return
        if (original.base != STATIC || original.mark !is MarkSlot.Star) return
        for (image in core.ops.leaves(flow)) {
            val id = pointKeys.getOrPut(PointKey(at.site, original, image, flow.layer, at.callState)) {
                (++next).also { points[it] = PointContinuation(core.key, it, at.site, original,
                    core.ap.factsOf(image.fact, image.exclusion, flow.layer) as FlowTree, at.callState) }
            }
            core.staticBatch.interests += StaticInterest(core.key, id, original.toPattern(), groups)
        }
        core.requestWork()
    }

    fun writer(operation: OperationId, premise: PremiseKey, value: Facts, support: GroundingSupport) {
        for (leaf in core.ops.leaves(value)) {
            val id = writerKeys.getOrPut(WriterKey(operation, premise, leaf, value.layer)) {
                (++next).also { writers[it] = Writer(premise,
                    core.ap.factsOf(leaf.fact, leaf.exclusion, value.layer), support) }
            }
            val writer = checkNotNull(writers[id])
            writer.support = writer.support.or(support)
            core.staticBatch.writers += StaticWriterRegistration(core.key, id, premise)
            for ((regions, marks) in writerInputs[id].orEmpty())
                groundWriter(id, regions.indices.map { RegionGroups(regions[it], marks[it]) })
            core.staticBatch.writes += Run1StaticService.region(leaf)
        }
        core.requestWork()
    }

    fun onEffect(effect: StaticOwnerEffect) {
        when (effect) {
            is StaticOwnerEffect.ReadReady -> {
                val (at, operation) = checkNotNull(reads[effect.id]) { "Unknown static read reply" }
                val position = effect.region.fact.path
                if (position.size < 2) return // class-point MarkInput uses the direct group path
                val initial = core.ap.initial(Pattern(PathFact(STATIC, position.take(2), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty))
                val input = core.ap.factsOf(effect.region.fact.copy(mark = MarkSlot.STAR,
                    tail = if (effect.region.fact.tail == Tail.EXACT || effect.region.fact.tail == Tail.ANY_TAINT) Tail.STAR else effect.region.fact.tail),
                    ExclusionSet.Empty, Layer.DEMAND)
                core.installPoint(initial, input, at.site, at.callState)
            }
            is StaticOwnerEffect.PointReady -> {
                calls[effect.id]?.let { call ->
                    call.supplies[effect.supply.region] = (call.supplies[effect.supply.region] ?: GroupSet.EMPTY) + effect.supply.groups
                    core.staticCallSupply(call.part, call.publication, call.member, effect.supply)
                    return
                }
                val at = direct[effect.id]
                if (at != null) {
                    val actual = core.staticFacts(effect.supply)
                    for (leaf in core.ops.leaves(actual)) {
                        val premise = concreteInitial(leaf)
                        core.installPoint(premise, actual, at.site, at.callState)
                    }
                    return
                }
                val point = checkNotNull(points[effect.id]) { "Unknown static point reply" }
                val actual = core.staticFacts(effect.supply)
                val relation = core.ap.flowTree(point.current.base, point.current.layer, point.current.exclusion,
                    MarkExclusions.EMPTY, point.current.root, point.current.tail)
                for (leaf in core.ops.leaves(actual)) {
                    val premise = concreteInitial(leaf)
                    core.ops.applySummary(core.ap.factsOf(leaf.fact, leaf.exclusion, Layer.DEMAND), point.initial, relation,
                        core.mode, object : ApOut {
                            override fun result(f: Facts) = core.installPoint(premise, f, point.site, point.callState)
                            override fun markRequest(mark: TaintMark) = Unit // materialization clears mx only
                        })
                }
            }
            is StaticOwnerEffect.WriterReady -> {
                val rows = writerInputs.getOrPut(effect.id) { LinkedHashMap() }
                val image = effect.inputs.map { it.region }
                val previous = rows[image]
                val merged = effect.inputs.mapIndexed { index, part -> part.groups + (previous?.get(index) ?: GroupSet.EMPTY) }
                rows[image] = merged
                groundWriter(effect.id, image.indices.map { RegionGroups(image[it], merged[it]) })
            }
            is StaticOwnerEffect.InputReady -> {
                // Borrow current qualified cofactors from the owner. A guard on one accepted
                // leaf must never be attached to the whole, possibly broader, N2 union.
                for (part in core.runnerPort.borrowN2Cofactors(effect.row)) {
                    if (!core.groundingPermits(part.ref.premise, part.added, effect.inputs, part.grounding)) continue
                    core.composeGrounded(part.ref.premise, part.added, effect.inputs) { grounded ->
                        core.staticBatch.groundedInputs += GroundedInputGrowth(effect.row, core.staticSlices(grounded))
                    }
                }
            }
        }
        core.requestWork()
    }

    private fun concreteInitial(leaf: Pattern): InitialAp {
        val path = leaf.fact.path
        val exact = leaf.fact.tail == Tail.EXACT
        val originalPath = if (path.size > 2) path.take(2) else path
        return core.ap.initial(Pattern(leaf.fact.copy(path = originalPath,
            tail = if (exact && path.size <= 2) Tail.EXACT else Tail.STAR), ExclusionSet.Empty))
    }

    private fun groundWriter(id: Long, inputs: List<RegionGroups>) {
        val writer = checkNotNull(writers[id]) { "Unknown static writer reply" }
        val premise = writer.premise; val value = writer.value
        if (!core.groundingPermits(premise, value, inputs, writer.support)) return
        core.composeGrounded(premise, value, inputs) { grounded ->
            for (slice in core.staticSlices(grounded)) core.staticBatch.supplies += slice
        }
    }

    fun clear() { pointKeys.clear(); points.clear(); readKeys.clear(); reads.clear(); writerKeys.clear(); writers.clear(); writerInputs.clear(); direct.clear(); callKeys.clear(); calls.clear() }
}
