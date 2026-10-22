package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.*

/** Only immutable invocation state is retained, never a cached full call plan. */
data class CallResumeState(val callerLayer: Layer, val identityCandidate: Boolean? = null,
                           val pendingDemotion: Boolean = false)

data class OperationInput(val site: ResumeSite, val premise: PremiseKey, val facts: Facts,
                          val callState: CallResumeState? = null)

/** Actor-owned resume directory. Service replies name these rows; they never carry callbacks. */
internal class Run1PointResume(private val core: RunMethodAnalyzer) {
    private var next = 0L
    private data class PointKey(val site: ResumeSite, val initial: InitialAp, val base: AccessPathBase,
                                val tail: Tail, val fieldExclusion: ExclusionSet, val layer: Layer,
                                val state: CallResumeState?)
    private val pointKeys = HashMap<PointKey, Long>()
    private class PendingFlowImages(private var root: FlowNode) {
        fun addAll(incoming: FlowNode, ap: ApManager) {
            root = FlowAlgebra.mergeAdd(root, incoming, ap.cancellation)
        }
        fun snapshotRoot(): FlowNode = root
    }
    private class PointRow(val id: Long, val key: PointKey) {
        val pending = HashMap<GroupId, PendingFlowImages>()
    }
    private data class DetachedAnswer(val group: GroupId, val images: FlowNode)
    private val points = HashMap<Long, PointRow>()
    private data class ReadKey(val site: ResumeSite, val operation: StaticOperation)
    private val readKeys = HashMap<ReadKey, Long>()
    private val reads = HashMap<Long, Pair<OperationInput, StaticOperation>>()
    private data class WriterKey(val premise: PremiseKey, val output: Pattern, val layer: Layer)
    private val writerKeys = HashMap<WriterKey, Long>()
    private val writers = HashMap<Long, WriterKey>()
    private class DirectRow(val key: ReadKey, val payload: OperationInput, var waiting: GroupSet)
    private val direct = HashMap<Long, DirectRow>()
    private val directKeys = HashMap<ReadKey, Long>()
    private class WriterInput(val writer: WriterKey, var waiting: GroupSet)
    private val writerInputs = HashMap<Long, WriterInput>()
    private class ConditionalJoin(val writer: WriterKey) {
        val inputs = arrayOfNulls<RegionGroups>(writer.premise.size)
    }
    private data class JoinInput(val join: Long, val member: Int)
    private val conditional = HashMap<Long, ConditionalJoin>()
    private val joinInputs = HashMap<Long, JoinInput>()
    private data class CallKey(val callee: MethodKey, val ref: org.opentaint.dataflow.bidi.store.CallerRef,
                               val linkLayer: Layer, val premise: PremiseKey, val result: Pattern,
                               val layer: Layer, val member: Int)
    private class CallContinuation(val key: CallKey, val part: Subscription, val publication: Publication, val member: Int) { var waiting = GroupSet.EMPTY }
    private val callKeys = HashMap<CallKey, Long>()
    private val calls = HashMap<Long, CallContinuation>()

    fun callInterest(part: Subscription, pub: Publication, member: Int) {
        val initial = pub.premise.member(member)
        val atom = (initial.mark as? MarkSlot.Concrete)?.mark ?: return
        val statistics = core.context.shared.n2Statistics.takeIf { core.mode.markMode == MarkMode.GROUP }
        for (leaf in core.ops.leaves(pub.conclusion)) {
            statistics?.staticGroupCallOffered()
            val piece = pub.copy(conclusion = core.ap.factsOf(leaf.fact, leaf.exclusion, pub.layer))
            val key = CallKey(part.callee, part.ref, part.linkLayer, pub.premise, leaf, pub.layer, member)
            val id = callKeys.getOrPut(key) {
                (++next).also { calls[it] = CallContinuation(key, part, piece, member) }
            }
            val row = calls.getValue(id)
            val fresh = GroupSet.of(core.ap.marks.groupOf(atom)) - row.waiting
            if (fresh.isEmpty) continue
            statistics?.staticGroupCallFallbackAdmitted()
            row.waiting += fresh
            core.staticBatch.interests += StaticInterest(core.key, id, initial.toPattern(), fresh)
        }
        core.requestWork()
    }

    fun reached(summary: StatementSummary, at: OperationInput) {
        if (at.facts !is Reach || !at.premise.isZero) return
        for (operation in summary.staticOperations) {
            if (core.config.disableRun1Statics && when (operation) {
                    is StaticOperation.Read -> true
                    is StaticOperation.Write -> core.dropsStaticField(operation.target.fact)
                    is StaticOperation.MarkInput -> operation.position.fact.path.size != 1 ||
                        (operation.position.fact.tail != Tail.EXACT && !operation.includeBase)
                }) continue
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
                        val exact = operation.position.fact.tail == Tail.EXACT
                        // A wildcard is a field-query descriptor, never a class
                        // FLOW initial. Its empty-suffix branch is an exact
                        // global marker; BELOW cleaners omit that branch.
                        if (!exact && !core.config.disableRun1Statics)
                            core.staticBatch.reads += StaticReadRegistration(core.key, id, operation.position)
                        if (exact || operation.includeBase) {
                            val directId = directKeys.getOrPut(key) {
                                (++next).also { direct[it] = DirectRow(key, at, GroupSet.EMPTY) }
                            }
                            val row = direct.getValue(directId)
                            val fresh = groups - row.waiting
                            if (!fresh.isEmpty) {
                                row.waiting += fresh
                                val point = operation.position.copy(fact = operation.position.fact.copy(tail = Tail.EXACT),
                                    exclusion = ExclusionSet.Empty)
                                core.staticBatch.interests += StaticInterest(core.key, directId, point, fresh)
                            }
                        }
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
        if (groups.isEmpty) return
        val key = PointKey(at.site, original, flow.base, flow.tail, flow.exclusion, flow.layer, at.callState)
        val id = pointKeys.getOrPut(key) {
            (++next).also { points[it] = PointRow(it, key) }
        }
        val row = points.getValue(id)
        var subscribe = GroupSet.EMPTY
        groups.forEach { group ->
            core.ap.cancellation.checkpoint()
            val images = row.pending[group]
            if (images == null) {
                row.pending[group] = PendingFlowImages(flow.root)
                subscribe += group
            } else images.addAll(flow.root, core.ap)
        }
        if (!subscribe.isEmpty) {
            core.staticBatch.interests += StaticInterest(core.key, id, original.toPattern(), subscribe)
        }
        core.requestWork()
    }

    /** Only an executed local producer reaches this boundary. Its bounded
     * scalar relation belongs to the writer, independently of summary deltas. */
    fun localWrite(premise: PremiseKey, value: Facts) {
        check(value.base == STATIC)
        for (leaf in core.ops.leaves(value)) {
            if (leaf.fact.path.size < 2) continue // exact class carriers keep their computed-supply route
            val region = Run1StaticService.region(leaf)
            core.staticBatch.writes += region
            if (value !is FlowTree) continue
            check(premise is InitialAp && !premise.isZero && premise.mark is MarkSlot.Star)
            val key = WriterKey(premise, leaf, value.layer)
            if (key in writerKeys) continue
            val id = ++next
            writerKeys[key] = id
            writers[id] = key
            if (premise.base == STATIC)
                core.staticBatch.writers += StaticWriterRegistration(core.key, id, premise, region)
            else core.staticBatch.flowWriters += StaticFlowWriter(core.key, id, region)
        }
        core.requestWork()
    }

    /** Any retained summary can produce a computed group supply. Promotion
     * never admits a new write point or FLOW writer. */
    fun computedGroups(premise: PremiseKey, delta: Facts) {
        check(delta.base == STATIC)
        if (delta !is TaintTree) return
        if (core.hasKnownStaticGroupInput(premise)) {
            val initial = premise as InitialAp
            val actual = core.knownStaticInput(initial)
            for (leaf in core.ops.leaves(delta)) {
                core.ap.cancellation.checkpoint()
                core.context.shared.n2Statistics?.staticGroupSupplyDirect()
                val scalar = core.ap.factsOf(leaf.fact, leaf.exclusion, delta.layer)
                core.composeStaticSummaryWithActualInput(initial, scalar, actual) { value ->
                    core.staticBatch.supplies += core.staticSlices(value)
                }
            }
        } else if (!premise.isZero && (0 until premise.size).all { premise.member(it).base == STATIC }) {
            for (leaf in core.ops.leaves(delta)) {
                if (core.mode.markMode == MarkMode.GROUP)
                    core.context.shared.n2Statistics?.staticGroupJoinFallback()
                val key = WriterKey(premise, leaf, delta.layer)
                // A fixed-group conditional is a pending complete binding, not a
                // permanent group-valued writer or a service-side Cartesian join.
                val joinId = ++next
                conditional[joinId] = ConditionalJoin(key)
                repeat(premise.size) { member ->
                    val initial = premise.member(member)
                    val atom = (initial.mark as MarkSlot.Concrete).mark
                    val inputId = ++next
                    joinInputs[inputId] = JoinInput(joinId, member)
                    core.staticBatch.interests += StaticInterest(core.key, inputId, initial.toPattern(),
                        GroupSet.of(core.ap.marks.groupOf(atom)))
                }
            }
        }
        if (premise.isZero)
            core.staticBatch.supplies += core.staticSlices(delta)
        core.requestWork()
    }

    /** A semantic replacement gets its own fresh descriptor. An obsolete selector
     * can stutter; it never keeps an old cumulative Facts root alive. */
    private fun currentValues(writer: WriterKey): Sequence<Facts> = sequence {
        val path = writer.output.fact.path.toIntArray()
        for (value in core.summaries.borrow(writer.premise)) {
            if (value.base != STATIC || value.layer != writer.layer) continue
            if (value is FlowTree) {
                val selector = writer.output.fact.mark as? MarkSlot.Star ?: continue
                if (value.markExclusion != selector.excluded) continue
            }
            for (leaf in core.ops.leavesNear(value, path)) {
                if (leaf != writer.output) continue
                yield(core.ap.factsOf(leaf.fact, leaf.exclusion, value.layer))
            }
        }
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
                    call.waiting -= effect.supply.groups
                    if (call.waiting.isEmpty) { callKeys.remove(call.key); calls.remove(effect.id) }
                    core.staticCallSupply(call.part, call.publication, call.member, effect.supply)
                    return
                }
                writerInputs[effect.id]?.let { input ->
                    input.waiting -= effect.supply.groups
                    if (input.waiting.isEmpty) writerInputs.remove(effect.id)
                    publishMatched(input.writer, listOf(effect.supply))
                    core.requestWork()
                    return
                }
                joinInputs.remove(effect.id)?.let { input ->
                    val join = conditional.getValue(input.join)
                    join.inputs[input.member] = effect.supply
                    if (join.inputs.all { it != null }) {
                        conditional.remove(input.join)
                        publishMatched(join.writer, join.inputs.map { checkNotNull(it) })
                    }
                    core.requestWork()
                    return
                }
                val directRow = direct[effect.id]
                if (directRow != null) {
                    val at = directRow.payload
                    directRow.waiting -= effect.supply.groups
                    if (directRow.waiting.isEmpty) { directKeys.remove(directRow.key); direct.remove(effect.id) }
                    val actual = core.staticFacts(effect.supply)
                    for (leaf in core.ops.leaves(actual)) {
                        val premise = concreteInitial(leaf)
                        core.installPoint(premise, actual, at.site, at.callState)
                    }
                    return
                }
                answerPackedPoint(effect)
            }
            is StaticOwnerEffect.WriterInterest -> {
                val writer = checkNotNull(writers[effect.id]) { "Unknown static writer interest" }
                val original = writer.premise as? InitialAp ?: return
                check(!original.isZero && original.mark is MarkSlot.Star && writer.output.fact.mark is MarkSlot.Star)
                if (original.base == STATIC) {
                    val inputId = ++next
                    writerInputs[inputId] = WriterInput(writer, effect.groups)
                    core.staticBatch.interests += StaticInterest(core.key, inputId, original.toPattern(), effect.groups)
                } else {
                    for (atom in core.ap.marks.atomsFor(effect.groups).ids)
                        core.addMarkRequest(original, TaintMark(atom))
                }
            }
        }
        core.requestWork()
    }

    private fun answerPackedPoint(effect: StaticOwnerEffect.PointReady) {
        val row = checkNotNull(points[effect.id]) { "Unknown static point reply" }
        effect.supply.groups.forEach { group -> check(row.pending.containsKey(group)) }
        val detached = ArrayList<DetachedAnswer>()
        effect.supply.groups.forEach { group ->
            detached += DetachedAnswer(group, checkNotNull(row.pending.remove(group)).snapshotRoot())
        }
        // Detach every supplied group before any callback. Later requests must
        // subscribe again; an immutable detached root cannot acquire new images.
        if (row.pending.isEmpty()) {
            check(pointKeys.remove(row.key) == row.id)
            check(points.remove(row.id) === row)
        }
        for (answer in detached) {
            core.ap.cancellation.checkpoint()
            materializeScalarImages(row.key, answer.images,
                RegionGroups(effect.supply.region, GroupSet.of(answer.group)))
        }
    }

    private fun materializeScalarImages(key: PointKey, images: FlowNode, supply: RegionGroups) {
        val actual = core.staticFacts(supply)
        FlowAlgebra.forEachLeaf(images, EMPTY_PATH, core.ap.cancellation) { path, _ ->
            val scalarRoot = FlowAlgebra.prepend(core.ap, core.ap.path(path), FlowAlgebra.LEAF_NODE)
            val relation = core.ap.flowTree(key.base, key.layer, key.fieldExclusion,
                MarkExclusions.EMPTY, scalarRoot, key.tail)
            for (leaf in core.ops.leaves(actual)) {
                core.ap.cancellation.checkpoint()
                val premise = concreteInitial(leaf)
                core.ops.applySummary(core.ap.factsOf(leaf.fact, leaf.exclusion, Layer.DEMAND), key.initial, relation,
                    core.mode, object : ApOut {
                        override fun result(f: Facts) = core.installPoint(premise, f, key.site, key.state)
                        override fun markRequest(mark: TaintMark) = Unit // materialization clears mx only
                    })
            }
        }
    }

    private fun publishMatched(writer: WriterKey, inputs: List<RegionGroups>) {
        // Local FLOW writers own this immutable bounded scalar relation. A
        // merged summary can subsume its exact selector without invalidating it.
        val values = if (writer.output.fact.mark is MarkSlot.Star)
            sequenceOf(core.ap.factsOf(writer.output.fact, writer.output.exclusion, writer.layer))
        else currentValues(writer) // Keep complete fixed-group supply behavior.
        for (current in values) {
            core.composeStaticSummary(writer.premise, current, inputs) { value ->
                core.staticBatch.supplies += core.staticSlices(value)
            }
        }
    }

    private fun concreteInitial(leaf: Pattern): InitialAp {
        val path = leaf.fact.path
        check(path.size != 1 || leaf.fact.tail == Tail.EXACT) {
            "A global class-marker reply must remain exact"
        }
        val exact = leaf.fact.tail == Tail.EXACT
        val originalPath = if (path.size > 2) path.take(2) else path
        return core.ap.initial(Pattern(leaf.fact.copy(path = originalPath,
            tail = if (exact && path.size <= 2) Tail.EXACT else Tail.STAR), ExclusionSet.Empty))
    }

    fun clear() { pointKeys.clear(); points.clear(); readKeys.clear(); reads.clear(); writerKeys.clear(); writers.clear(); direct.clear(); directKeys.clear(); callKeys.clear(); calls.clear(); writerInputs.clear(); conditional.clear(); joinInputs.clear() }
}
