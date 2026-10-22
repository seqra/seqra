package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.AddedFactAccumulator
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.ConjunctionStore
import org.opentaint.dataflow.bidi.store.NdBinding
import org.opentaint.dataflow.bidi.store.Record
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import java.util.ArrayDeque

/** Call walks and suspended return batches belong to one method actor. */
class RunMethodCallServices(core: RunMethodAnalyzer) : MethodCallServices {
    private var owner: RunMethodAnalyzer? = core
    // Keep semantic unresolved observations once per qualified current input.
    private var observed: AddedFactAccumulator? = if (core.config.run1) AddedFactAccumulator(core.ap) else null

    private fun active(): RunMethodAnalyzer = checkNotNull(owner) { "The call services were released" }.also {
        it.ap.cancellation.checkpoint()
    }

    private data class PlanFact(val premise: PremiseKey, val facts: Facts, val identityCandidate: Boolean?)
    private fun planFact(premise: PremiseKey, facts: Facts, identity: Boolean?) =
        PlanFact(premise, facts, identity)
    private class CallContext(val node: CommonInst, val call: CommonCallExpr, val plan: CallPlan, val callerLayer: Layer)
    private class Frame(val point: CallPoint, val facts: List<PlanFact>, val pendingDemotion: Boolean) {
        var entered = false
        var next = 0
        var triggers = Triggers.NONE
    }
    private val CallPlan.callees: CallStage.Callees?
        get() = stages.firstNotNullOfOrNull { it as? CallStage.Callees }

    override fun process(item: EdgeDelta) {
        val core = active()
        val call = checkNotNull(core.context.callAt(item.node)) { "The delta is not at a call" }
        core.forms.withCall(core.key, item.node, call) { plan ->
            val ctx = CallContext(item.node, call, plan, item.facts.layer)
            when {
                item.facts is Reach -> zeroAtCall(item, ctx)
                item.facts.base !in ctx.plan.touched -> core.emitUnchanged(item)
                else -> flow(ctx, ctx.plan.entry, listOf(planFact(item.premise, item.facts, null)))
            }
        }
    }

    /** An explicit frame stack preserves stage order and keeps pending demotion local to this invocation. */
    private fun flow(ctx: CallContext, point: CallPoint, facts: List<PlanFact>, pendingDemotion: Boolean = false) {
        val core = active()
        check(!pendingDemotion || core.forward) { "Native must-record demotion is only forward" }
        val stack = ArrayDeque<Frame>()
        stack.addLast(Frame(point, facts, pendingDemotion))
        while (stack.isNotEmpty()) {
            core.ap.cancellation.checkpoint()
            val frame = stack.peekLast()
            if (!frame.entered) {
                frame.entered = true
                if (frame.point == ctx.plan.exit) {
                    for (fact in frame.facts) {
                        core.ap.cancellation.checkpoint()
                        val result = if (frame.pendingDemotion) toDemand(fact.facts) else fact.facts
                        core.cut(Cut.CALL, fact.premise, result) { premise, cut -> core.emitAfter(premise, ctx.node, cut) }
                    }
                    stack.removeLast()
                    continue
                }
                if (core.forward && frame.point == CallPoint.BOUND) {
                    for (fact in frame.facts) {
                        core.ap.cancellation.checkpoint()
                        val ordinal = ctx.plan.stages.indexOfFirst { it.from == frame.point }.coerceAtLeast(0)
                        frame.triggers += core.localServices.checkSinksAt(fact.premise, ctx.node, fact.facts, ctx.plan.sinks,
                            OperationInput(ResumeSite.CallStageInput(core.operation(ctx.node, ordinal)), fact.premise, fact.facts,
                                CallResumeState(ctx.callerLayer, fact.identityCandidate, frame.pendingDemotion)))
                    }
                }
                if (core.config.run1 && frame.point == CallPoint.ADDED) observeUnresolved(ctx, frame.facts)
            }
            val stages = ctx.plan.stagesAt(frame.point)
            if (frame.next >= stages.size) { stack.removeLast(); continue }
            val stage = stages[frame.next++]
            val out = applyStage(ctx, stage, frame.facts, frame.triggers, frame.pendingDemotion)
            if (out.isNotEmpty()) stack.addLast(Frame(stage.to, out, frame.pendingDemotion))
        }
    }

    private fun applyStage(ctx: CallContext, stage: CallStage, facts: List<PlanFact>, triggers: Triggers,
                           pendingDemotion: Boolean = false): List<PlanFact> {
        val core = active()
        val out = ArrayList<PlanFact>()
        val ordinal = ctx.plan.stages.indexOf(stage)
        when (stage) {
            is CallStage.Edges -> {
                if (stage.kind == StageKind.END_FACTS) return emptyList() // ignored in every run
                val guard = if (core.forward) stage.guard else null
                if (guard is Guard.SinkTriggered) {
                    for ((sink, layer) in triggers.fired) {
                        core.ap.cancellation.checkpoint()
                        if (sink.joinKey != guard.sink.joinKey) continue
                        core.applier.stage(stage.summary, core.ap.zero, Reach.of(layer),
                            Place(ctx.node, statementEdge = false, sources = false)) { premise, result, edge ->
                            core.ap.cancellation.checkpoint()
                            out += planFact(premise, result, stage.kind.identityCandidateOf(edge, null))
                        }
                    }
                } else {
                    for (fact in facts) {
                        core.ap.cancellation.checkpoint()
                        if (guard == Guard.MemoryEffect && !Guard.MemoryEffect.admits(
                                core.config.run1, fact.identityCandidate, fact.facts.layer)) continue
                        val input = fact.facts
                        val place = Place(ctx.node, core.config.run1 && stage.kind.statementEdges,
                            sources = stage.kind == StageKind.SOURCES,
                            site = ResumeSite.CallStageInput(core.operation(ctx.node, ordinal)),
                            callerLayer = ctx.callerLayer, identityCandidate = fact.identityCandidate, pendingDemotion = pendingDemotion)
                        core.applier.stage(stage.summary, fact.premise, input, place) { premise, result, edge ->
                            core.ap.cancellation.checkpoint()
                            out += planFact(premise, result, stage.kind.identityCandidateOf(edge, fact.identityCandidate))
                        }
                    }
                }
                if (stage.trigger != null && out.isNotEmpty()) {
                    core.localServices.fireTriggerSeeds(ctx.node, stage.trigger) { result ->
                        core.ap.cancellation.checkpoint()
                        out += planFact(core.ap.zero, result, null)
                    }
                }
            }
            is CallStage.Actions -> for (fact in facts) {
                core.ap.cancellation.checkpoint()
                cleanChain(stage, fact, ctx, ordinal, pendingDemotion = pendingDemotion) { out += it }
            }
            is CallStage.Callees -> for (fact in facts) {
                core.ap.cancellation.checkpoint()
                enterCallees(ctx, stage, fact)
            }
        }
        core.ap.cancellation.checkpoint()
        return out
    }

    private fun cleanChain(actions: CallStage.Actions, fact: PlanFact, ctx: CallContext, stageOrdinal: Int,
                           firstStep: Int = 0, firstPhase: CleanerPhase = CleanerPhase.INPUT,
                           pendingDemotion: Boolean = false, emit: (PlanFact) -> Unit) {
        val core = active()
        val steps = actions.steps
        val schedule = actions.returnGroupSchedule.takeIf {
            core.config.run1 && core.forward && core.mode.markMode == MarkMode.GROUP
        }
        check(schedule == null || schedule.size == steps.size)
        val node = ctx.node
        fun at(input: PlanFact, step: Int, phase: CleanerPhase) = OperationInput(
            ResumeSite.CleanerInput(core.operation(node, stageOrdinal), step, phase), input.premise, input.facts,
            CallResumeState(ctx.callerLayer, input.identityCandidate, pendingDemotion))
        fun statement(summary: StatementSummary, input: PlanFact, step: Int, phase: CleanerPhase): List<PlanFact> {
            val out = ArrayList<PlanFact>()
            val compiled = if (core.config.run1 && phase == CleanerPhase.READ && summary.staticOperations.isEmpty()) {
                val id = core.operation(node, stageOrdinal).copy(substep = step)
                val operations = summary.edges.filter { it.edge.from.base == STATIC && it.edge.to.base != STATIC }
                    .map { StaticOperation.Read(id, it.forward) }
                if (operations.isEmpty()) summary else summary.withStaticOperations(operations)
            } else if (core.config.run1 && phase == CleanerPhase.WRITE && summary.staticOperations.isEmpty()) {
                val id = core.operation(node, stageOrdinal).copy(substep = step)
                val operations = summary.edges.filter { it.edge.to.base == STATIC && it.edge.from.base != STATIC }
                    .map { StaticOperation.Write(id, Pattern(it.edge.to, ExclusionSet.Empty), it.forward) }
                if (operations.isEmpty()) summary else summary.withStaticOperations(operations)
            } else summary
            core.applier.statement(compiled, input.premise, input.facts,
                Place(node, core.config.run1, sources = false, site = at(input, step, phase).site,
                    callerLayer = ctx.callerLayer, identityCandidate = input.identityCandidate, pendingDemotion = pendingDemotion),
                sink = { premise, result ->
                    core.cut(Cut.STATEMENT, premise, result) { cutPremise, cut ->
                        core.ap.cancellation.checkpoint()
                        out += planFact(cutPremise, cut, input.identityCandidate)
                    }
                }, untouched = { premise, result -> out += planFact(premise, result, input.identityCandidate) })
            return out
        }
        var current = listOf(fact)
        for (stepIndex in firstStep until steps.size) {
            core.ap.cancellation.checkpoint()
            if (schedule != null && !schedule.execute(stepIndex, firstStep)) continue
            val step = steps[stepIndex]
            val phase = if (stepIndex == firstStep) firstPhase else CleanerPhase.INPUT
            // Finish every current-step effect before merging equal inputs to the next step.
            val next: MutableCollection<PlanFact> =
                if (core.config.run1) LinkedHashSet() else ArrayList()
            for (input in current) {
                core.ap.cancellation.checkpoint()
                when (step) {
                    is CleanStep.Clean -> {
                        if (core.config.run1 && step.cleaner.base == STATIC && step.cleaner.mark != null && input.facts is Reach) {
                            val id = core.operation(node, stageOrdinal).copy(substep = stepIndex)
                            val pattern = Pattern(PathFact(STATIC, step.cleaner.pathArray.asList(),
                                if (step.cleaner.reach == CleanReach.EXACT) Tail.EXACT else Tail.ANY,
                                MarkSlot.Concrete(checkNotNull(step.cleaner.mark))), ExclusionSet.Empty)
                            core.reachedOperations(StatementSummary.EMPTY.withStaticOperations(listOf(StaticOperation.MarkInput(id, pattern, includeBase = step.cleaner.reach != CleanReach.BELOW))),
                                at(input, stepIndex, CleanerPhase.CLEAN))
                        }
                        core.ops.clean(input.facts, input.premise, step.cleaner, core.mode,
                        core.collect(at(input, stepIndex, CleanerPhase.CLEAN)) { premise, result ->
                            if (core.config.run1 && result.base == STATIC) {
                                core.promoteStaticSummary(premise, result)
                            } else next += planFact(premise, result, input.identityCandidate)
                        })
                    }
                    is CleanStep.Kill -> core.applier.statement(step.keepEdges, input.premise, input.facts,
                        Place(node, core.config.run1, sources = false, site = at(input, stepIndex, CleanerPhase.INPUT).site,
                            callerLayer = ctx.callerLayer, identityCandidate = input.identityCandidate,
                            pendingDemotion = pendingDemotion),
                        sink = { premise, result -> next += planFact(premise, result, input.identityCandidate) })
                    is CleanStep.Field -> for (read in if (phase == CleanerPhase.CLEAN || phase == CleanerPhase.WRITE) listOf(input)
                            else statement(step.read, input, stepIndex, CleanerPhase.READ)) {
                        core.ap.cancellation.checkpoint()
                        val cleaned: ResultSink = { premise, result ->
                                for (written in statement(step.write, planFact(premise, result, read.identityCandidate), stepIndex, CleanerPhase.WRITE)) {
                                    core.ap.cancellation.checkpoint()
                                    if (written.facts.base != step.temporary) next += written
                                }
                            }
                        if (phase == CleanerPhase.WRITE) cleaned(read.premise, read.facts)
                        else core.ops.clean(read.facts, read.premise, step.cleaner, core.mode,
                            core.collect(at(read, stepIndex, CleanerPhase.CLEAN), cleaned))
                    }
                }
            }
            current = if (core.config.run1) next.toList() else next as ArrayList<PlanFact>
        }
        for (result in current) { core.ap.cancellation.checkpoint(); emit(result) }
    }

    private fun observeUnresolved(ctx: CallContext, facts: List<PlanFact>) {
        val core = active()
        val observer = core.context.shared.unresolvedObserver ?: return
        if (ctx.plan.stagesFrom[CallPoint.ADDED].orEmpty().none {
                it is CallStage.Edges && it.kind == StageKind.UNRESOLVED
            }) return
        val observed = checkNotNull(observed)
        for (fact in facts) {
            core.ap.cancellation.checkpoint()
            if (fact.facts !is TaintTree) continue
            val ref = CallerRef(core.key, fact.premise, ctx.callerLayer, ctx.node)
            if (observed.add(ref, fact.facts.layer, fact.facts) == null) continue
            observer.reached(ctx.call, fact.facts.base, ctx.plan)
            core.ap.cancellation.checkpoint()
        }
    }

    private fun enterCallees(ctx: CallContext, stage: CallStage.Callees, fact: PlanFact) {
        val core = active()
        val ref = CallerRef(core.key, fact.premise, ctx.callerLayer, ctx.node, ctx.plan.stages.indexOf(stage))
        for (callee in stage.callees) {
            core.ap.cancellation.checkpoint()
            core.runnerPort.subscribe(Subscription(callee, ref, fact.facts.layer, fact.facts))
            core.ap.cancellation.checkpoint()
            core.runnerPort.link(callee, ref, fact.facts.layer, fact.facts)
        }
    }

    private fun zeroAtCall(item: EdgeDelta, ctx: CallContext) {
        val core = active()
        core.emitUnchanged(item)
        if (!item.premise.isZero) { check(!core.forward); return }
        if (core.forward) {
            flow(ctx, ctx.plan.entry, listOf(planFact(item.premise, item.facts, null)))
            return
        }
        val seeds = ArrayList<PlanFact>()
        core.localServices.fireSinkSeeds(item.node) { seeds += planFact(core.ap.zero, it, null) }
        if (seeds.isNotEmpty()) flow(ctx, CallPoint.BOUND, seeds)
        val ref = CallerRef(core.key, core.ap.zero, ctx.callerLayer, item.node,
            ctx.plan.stages.indexOfFirst { it is CallStage.Callees }.coerceAtLeast(0))
        for (callee in ctx.plan.callees?.callees.orEmpty()) {
            core.ap.cancellation.checkpoint()
            core.runnerPort.subscribe(Subscription(callee, ref, item.facts.layer, item.facts, zeroOnly = true))
            core.ap.cancellation.checkpoint()
            core.runnerPort.send(RunEvent.ZeroIn(callee))
        }
    }

    override fun applySummary(part: Subscription, pub: Publication, member: Int) {
        val core = active()
        core.checkPremise(part.ref.premise)
        core.checkPremise(pub.premise)
        when (val premise = pub.premise) {
            is PremiseSet -> ndMatch(part, pub, premise, member)
            is InitialAp -> {
                val results = ArrayList<PlanFact>()
                if (part.zeroOnly) {
                    if (core.config.run1 && pub.conclusion.base == STATIC)
                        core.promoteStaticSummary(part.ref.premise, pub.conclusion)
                    else results += planFact(part.ref.premise, pub.conclusion, false)
                }
                else applyParts(part, premise, pub.conclusion, results)
                resumeCallees(part.ref, results)
            }
        }
    }

    override fun applyStaticSummary(part: Subscription, pub: Publication, member: Int) {
        val core = active()
        check(core.config.run1 && part.ref.premise.isZero && part.added is Reach && !part.zeroOnly)
        core.checkPremise(pub.premise)
        val initial = pub.premise.member(member)
        check(initial.base == STATIC)
        if (core.dropsStaticField(initial)) return
        if (initial.mark is MarkSlot.Star) {
            check(pub.premise is InitialAp && pub.conclusion is FlowTree)
            val value = pub.conclusion.withLayer(Layer.DEMAND)
            if (value.base == STATIC) core.promoteStaticSummary(initial, value)
            else resumeCallees(part.ref, listOf(planFact(initial, value, false)))
        } else if (core.hasKnownStaticGroupInput(pub.premise)) {
            val actual = core.knownStaticInput(pub.premise as InitialAp)
            val statistics = core.context.shared.n2Statistics
            for (leaf in core.ops.leaves(pub.conclusion)) {
                core.ap.cancellation.checkpoint()
                statistics?.staticGroupCallOffered()
                statistics?.staticGroupCallDirect()
                val scalar = core.ap.factsOf(leaf.fact, leaf.exclusion, pub.layer)
                applyStaticInput(part, pub, member, actual, scalar)
            }
        } else core.staticCallInterest(part, pub, member)
    }

    override fun staticSupply(part: Subscription, pub: Publication, member: Int, supply: RegionGroups) {
        val core = active()
        core.checkPremise(part.ref.premise)
        core.checkPremise(pub.premise)
        val actual = core.staticFacts(supply)
        applyStaticInput(part, pub, member, actual)
    }

    /** Both pool replies and known-input activation use the same scalar AP application and suffix. */
    private fun applyStaticInput(part: Subscription, pub: Publication, member: Int, actual: Facts,
                                 conclusion: Facts = pub.conclusion) {
        val core = active()
        val initial = pub.premise.member(member)
        val matched = core.ops.satisfying(actual, initial, core.mode) ?: return
        for (leaf in core.ops.leaves(matched)) {
            check(leaf.fact.path.size != 1 || leaf.fact.tail == Tail.EXACT) {
                "A global class-marker call input must remain exact"
            }
            val path = leaf.fact.path.take(2)
            val selected = core.ap.initial(Pattern(leaf.fact.copy(path = path,
                tail = if (leaf.fact.tail == Tail.EXACT && leaf.fact.path.size <= 2) Tail.EXACT else Tail.STAR), ExclusionSet.Empty))
            val ref = CallerRef(core.key, selected, part.ref.callerLayer, part.ref.call, part.ref.stageOrdinal)
            val input = part.copy(ref = ref, linkLayer = matched.layer, added = matched)
            if (pub.premise is PremiseSet) ndMatch(input, pub, pub.premise, member)
            else {
                val statistics = core.context.shared.n2Statistics.takeIf { core.mode.markMode == MarkMode.GROUP }
                val results = ArrayList<PlanFact>()
                statistics?.staticGroupScalarApplied()
                applyParts(input, initial, conclusion, results)
                if (results.isNotEmpty()) statistics?.staticGroupSuffixEntered()
                resumeCallees(ref, results)
            }
        }
    }

    override fun resumePoint(premise: PremiseKey, facts: Facts, site: ResumeSite, state: CallResumeState?) {
        val core = active()
        val node = site.operation.form.statement
        val call = checkNotNull(core.context.callAt(node))
        core.forms.withCall(core.key, node, call) { plan ->
            val ctx = CallContext(node, call, plan, state?.callerLayer ?: facts.layer)
            val stage = checkNotNull(plan.stages.getOrNull(site.operation.stageOrdinal)) { "The resumed call stage changed" }
            val input = planFact(premise, facts, state?.identityCandidate)
            when (site) {
                is ResumeSite.CallStageInput -> {
                    val triggers = if (core.forward && stage.from == CallPoint.BOUND)
                        core.localServices.checkSinksAt(premise, node, facts, plan.sinks,
                            OperationInput(site, premise, facts, state)) else Triggers.NONE
                    val out = applyStage(ctx, stage, listOf(input), triggers, state?.pendingDemotion ?: false)
                    if (out.isNotEmpty()) flow(ctx, stage.to, out, state?.pendingDemotion ?: false)
                }
                is ResumeSite.CleanerInput -> {
                    val actions = checkNotNull(stage as? CallStage.Actions)
                    val out = ArrayList<PlanFact>()
                    cleanChain(actions, input, ctx, site.operation.stageOrdinal, site.step, site.phase,
                        state?.pendingDemotion ?: false) { out += it }
                    if (out.isNotEmpty()) flow(ctx, stage.to, out, state?.pendingDemotion ?: false)
                }
                else -> error("Not a call resume address")
            }
        }
    }

    override fun applyRecord(part: Subscription, record: Record) {
        val core = active()
        val premise = record.premise
        val native = ArrayList<PlanFact>()
        val pending = ArrayList<PlanFact>()
        if (premise.tail != Tail.ANY_TAINT) applyParts(part, premise, record.conclusion, native, run1Summary = false)
        else {
            check(core.forward) { "A native must record is only forward" }
            val inside = core.ops.satisfying(part.added, premise, core.mode)
            if (inside != null) applyParts(part.copy(added = inside), premise, record.conclusion, native, run1Summary = false)
            val only = if (inside == null) part.added else core.ops.without(part.added, inside)
            if (only != null) applyParts(part.copy(added = only), premise, record.conclusion, pending, run1Summary = false)
        }
        if (native.isNotEmpty() || pending.isNotEmpty()) core.countRecordCrossing()
        resumeCallees(part.ref, native)
        val tagged = ArrayList<PlanFact>(pending.size)
        for (fact in pending) { core.ap.cancellation.checkpoint(); tagged += planFact(fact.premise, fact.facts, false) }
        resumeCallees(part.ref, tagged, pendingDemotion = true)
    }

    override fun applyRecordView(part: Subscription, view: RecordLeafView) {
        val core = active()
        val results = ArrayList<PlanFact>()
        val identity = recordLeafIdentity(view)
        core.ops.applyRecordLeaf(part.added, view, core.mode, core.collect(part.ref.premise, part.added) { premise, result ->
            results += planFact(premise, result, identity)
        })
        if (results.isNotEmpty()) core.countRecordCrossing()
        resumeCallees(part.ref, results)
    }

    private fun toDemand(facts: Facts): Facts {
        val core = active()
        return when (facts) {
            is TaintTree -> facts.withLayer(Layer.DEMAND)
            is Reach -> Reach.DEMAND
            is FlowTree -> error("A must record has TAINT results")
        }
    }

    private fun resumeCallees(ref: CallerRef, results: List<PlanFact>, pendingDemotion: Boolean = false) {
        val core = active()
        if (results.isEmpty()) return
        val call = checkNotNull(core.context.callAt(ref.call)) { "The caller reference is not at a call" }
        core.forms.withCall(core.key, ref.call, call) { plan ->
            val ctx = CallContext(ref.call, call, plan, ref.callerLayer)
            val stage = ctx.plan.stages.getOrNull(ref.stageOrdinal) as? CallStage.Callees
                ?: checkNotNull(ctx.plan.callees) { "A resumed result needs a callee stage" }
            flow(ctx, stage.to, results, pendingDemotion)
        }
    }

    private fun applyParts(part: Subscription, premise: InitialAp, conclusion: Facts, results: MutableList<PlanFact>,
                           run1Summary: Boolean = true) {
        val core = active()
        for ((group, identity) in summaryParts(premise, conclusion, run1Summary)) {
            core.ap.cancellation.checkpoint()
            val at = OperationInput(ResumeSite.CallStageInput(core.operation(part.ref.call, part.ref.stageOrdinal)),
                part.ref.premise, part.added, CallResumeState(part.ref.callerLayer, identity))
            core.ops.applySummary(part.added, premise, group, core.mode, core.collect(at) { pr, result ->
                if (core.config.run1 && result.base == STATIC) core.promoteStaticSummary(pr, result)
                else results += planFact(pr, result, identity)
            })
        }
    }

    private fun summaryParts(premise: InitialAp, conclusion: Facts, run1Summary: Boolean): List<Pair<Facts, Boolean>> {
        val core = active()
        if (core.config.run1 && run1Summary) return core.ap.run1SummaryParts(premise, conclusion)
        val start = core.ops.startFact(premise)
        if (premise.isZero || conclusion.layer != Layer.NORMAL || conclusion.groupKey != start.groupKey)
            return listOf(conclusion to false)
        val effect = core.ops.without(conclusion, start)
        val identity = if (effect == null) conclusion else core.ops.without(conclusion, effect)
        return listOfNotNull(effect?.let { it to false }, identity?.let { it to true })
    }

    private fun ndMatch(part: Subscription, pub: Publication, premise: PremiseSet, member: Int) {
        val core = active()
        check(core.forward && !core.config.run1) { "Conjunctive summaries require a later forward run" }
        check(!part.zeroOnly && part.added !is Reach && !premise.member(member).isZero)
        val conclusion = checkNotNull(pub.conclusion as? TaintTree) { "A multi-premise publication is TAINT" }
        val matched = matches(part, pub, member, core.ops, core.mode) ?: return
        check(matched is TaintTree) { "An E6 member input is TAINT" }
        check(part.linkLayer == matched.layer) { "The matched fact and link have the same layer" }
        val store = checkNotNull(core.localServices.conjunctions) { "Forward calls need their local conjunction store" }
        val join = store.ndJoin<NdBinding>(ConjunctionStore.NdKey(part.callee, premise, pub.layer, part.ref.call, part.ref.stageOrdinal))
        val binding = NdBinding(part.ref, matched.layer)
        for ((parts, result) in join.addSubscription(member, binding)) {
            core.ap.cancellation.checkpoint()
            applyCombination(parts, result)
        }
        for ((parts, result) in join.addConclusion(conclusion)) {
            core.ap.cancellation.checkpoint()
            applyCombination(parts, result)
        }
    }

    private fun applyCombination(parts: List<NdBinding>, conclusion: TaintTree) {
        val core = active()
        var union = parts[0].ref.premise
        val layers = ArrayList<Layer>(parts.size)
        for (index in parts.indices) {
            core.ap.cancellation.checkpoint()
            if (index != 0) union = core.ap.union(union, parts[index].ref.premise)
            layers += parts[index].linkLayer
        }
        val ref = parts.first().ref
        val results = ArrayList<PlanFact>()
        val at = OperationInput(ResumeSite.CallStageInput(core.operation(ref.call, ref.stageOrdinal)), union, conclusion,
            CallResumeState(ref.callerLayer))
        core.ops.applyMatchedCombination(layers, conclusion, core.collect(at) { pr, result ->
            if (core.config.run1 && result.base == STATIC) core.promoteStaticSummary(pr, result)
            else results += planFact(pr, result, false)
        })
        resumeCallees(ref, results)
    }

    override fun freeze() {
        observed = null
        owner = null
    }
}
