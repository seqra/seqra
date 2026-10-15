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
    // Callback multiplicity is reported as ExternalMethodRecord.callSites. Keep the existing
    // exact duplicate contract until that observation contract has a separate design. No index
    // or immutable input view is needed for this gate, and it is never used by emission/replay.
    private var observed: AddedFactAccumulator? = if (core.config.run1) AddedFactAccumulator(core.ap) else null

    private fun active(): RunMethodAnalyzer = checkNotNull(owner) { "The call services were released" }.also {
        it.ap.cancellation.checkpoint()
    }

    private class PlanFact(val premise: PremiseKey, val facts: Facts, val identityCandidate: Boolean?)
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
                else -> flow(ctx, ctx.plan.entry, listOf(PlanFact(item.premise, item.facts, null)))
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
                        frame.triggers += core.localServices.checkSinks(fact.premise, ctx.node, fact.facts, ctx.plan.sinks)
                    }
                }
                if (core.config.run1 && frame.point == CallPoint.ADDED) observeUnresolved(ctx, frame.facts)
            }
            val stages = ctx.plan.stagesAt(frame.point)
            if (frame.next >= stages.size) { stack.removeLast(); continue }
            val stage = stages[frame.next++]
            val out = applyStage(ctx, stage, frame.facts, frame.triggers)
            if (out.isNotEmpty()) stack.addLast(Frame(stage.to, out, frame.pendingDemotion))
        }
    }

    private fun applyStage(ctx: CallContext, stage: CallStage, facts: List<PlanFact>, triggers: Triggers): List<PlanFact> {
        val core = active()
        val out = ArrayList<PlanFact>()
        when (stage) {
            is CallStage.Edges -> {
                val guard = if (core.forward) stage.guard else null
                if (guard is Guard.SinkTriggered) {
                    for ((sink, layer) in triggers.fired) {
                        core.ap.cancellation.checkpoint()
                        if (sink.joinKey != guard.sink.joinKey) continue
                        core.applier.stage(stage.summary, core.ap.zero, Reach.of(layer),
                            Place(ctx.node, statementEdge = false, sources = false)) { premise, result, edge ->
                            core.ap.cancellation.checkpoint()
                            out += PlanFact(premise, result, stage.kind.identityCandidateOf(edge, null))
                        }
                    }
                } else {
                    for (fact in facts) {
                        core.ap.cancellation.checkpoint()
                        if (guard == Guard.MemoryEffect && !Guard.MemoryEffect.admits(
                                checkNotNull(fact.identityCandidate), fact.facts.layer)) continue
                        val place = Place(ctx.node, core.config.run1 && stage.kind.statementEdges,
                            sources = stage.kind == StageKind.SOURCES)
                        core.applier.stage(stage.summary, fact.premise, fact.facts, place) { premise, result, edge ->
                            core.ap.cancellation.checkpoint()
                            out += PlanFact(premise, result, stage.kind.identityCandidateOf(edge, fact.identityCandidate))
                        }
                    }
                }
                if (stage.trigger != null && out.isNotEmpty()) {
                    core.localServices.fireTriggerSeeds(ctx.node, stage.trigger) { result ->
                        core.ap.cancellation.checkpoint()
                        out += PlanFact(core.ap.zero, result, null)
                    }
                }
            }
            is CallStage.Actions -> for (fact in facts) {
                core.ap.cancellation.checkpoint()
                cleanChain(stage.steps, fact, ctx.node) { out += it }
            }
            is CallStage.Callees -> for (fact in facts) {
                core.ap.cancellation.checkpoint()
                enterCallees(ctx, stage, fact)
            }
        }
        core.ap.cancellation.checkpoint()
        return out
    }

    private fun cleanChain(steps: List<CleanStep>, fact: PlanFact, node: CommonInst, emit: (PlanFact) -> Unit) {
        val core = active()
        fun statement(summary: StatementSummary, input: PlanFact): List<PlanFact> {
            val out = ArrayList<PlanFact>()
            core.applier.statement(summary, input.premise, input.facts, Place(node, core.config.run1, sources = false),
                sink = { premise, result ->
                    core.cut(Cut.STATEMENT, premise, result) { cutPremise, cut ->
                        core.ap.cancellation.checkpoint()
                        out += PlanFact(cutPremise, cut, input.identityCandidate)
                    }
                }, untouched = { premise, result -> out += PlanFact(premise, result, input.identityCandidate) })
            return out
        }
        var current = listOf(fact)
        for (step in steps) {
            core.ap.cancellation.checkpoint()
            val next = ArrayList<PlanFact>()
            for (input in current) {
                core.ap.cancellation.checkpoint()
                when (step) {
                    is CleanStep.Clean -> core.ops.clean(input.facts, input.premise, step.cleaner, core.mode,
                        core.collect(input.premise, input.facts) { premise, result ->
                            next += PlanFact(premise, result, input.identityCandidate)
                        })
                    is CleanStep.Kill -> core.applier.statement(step.keepEdges, input.premise, input.facts,
                        Place(node, core.config.run1, sources = false),
                        sink = { premise, result -> next += PlanFact(premise, result, input.identityCandidate) })
                    is CleanStep.Field -> for (read in statement(step.read, input)) {
                        core.ap.cancellation.checkpoint()
                        core.ops.clean(read.facts, read.premise, step.cleaner, core.mode,
                            core.collect(read.premise, read.facts) { premise, result ->
                                for (written in statement(step.write, PlanFact(premise, result, read.identityCandidate))) {
                                    core.ap.cancellation.checkpoint()
                                    if (written.facts.base != step.temporary) next += written
                                }
                            })
                    }
                }
            }
            current = next
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
        val ref = CallerRef(core.key, fact.premise, ctx.callerLayer, ctx.node)
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
            flow(ctx, ctx.plan.entry, listOf(PlanFact(item.premise, item.facts, null)))
            return
        }
        val seeds = ArrayList<PlanFact>()
        core.localServices.fireSinkSeeds(item.node) { seeds += PlanFact(core.ap.zero, it, null) }
        if (seeds.isNotEmpty()) flow(ctx, CallPoint.BOUND, seeds)
        val ref = CallerRef(core.key, core.ap.zero, ctx.callerLayer, item.node)
        for (callee in ctx.plan.callees?.callees.orEmpty()) {
            core.ap.cancellation.checkpoint()
            core.runnerPort.subscribe(Subscription(callee, ref, item.facts.layer, item.facts, zeroOnly = true))
            core.ap.cancellation.checkpoint()
            core.runnerPort.send(RunEvent.ZeroIn(callee))
        }
    }

    override fun applySummary(part: Subscription, pub: Publication, member: Int) {
        active()
        when (val premise = pub.premise) {
            is PremiseSet -> ndMatch(part, pub, premise, member)
            is InitialAp -> {
                val results = ArrayList<PlanFact>()
                if (part.zeroOnly) results += PlanFact(part.ref.premise, pub.conclusion, false)
                else applyParts(part, premise, pub.conclusion, results)
                resumeCallees(part.ref, results)
            }
        }
    }

    override fun applyRecord(part: Subscription, record: Record) {
        val core = active()
        val premise = record.premise
        val native = ArrayList<PlanFact>()
        val pending = ArrayList<PlanFact>()
        if (premise.tail != Tail.ANY_TAINT) applyParts(part, premise, record.conclusion, native)
        else {
            check(core.forward) { "A native must record is only forward" }
            val inside = core.ops.satisfying(part.added, premise, core.mode)
            if (inside != null) applyParts(part.copy(added = inside), premise, record.conclusion, native)
            val only = if (inside == null) part.added else core.ops.without(part.added, inside)
            if (only != null) applyParts(part.copy(added = only), premise, record.conclusion, pending)
        }
        if (native.isNotEmpty() || pending.isNotEmpty()) core.countRecordCrossing()
        resumeCallees(part.ref, native)
        val tagged = ArrayList<PlanFact>(pending.size)
        for (fact in pending) { core.ap.cancellation.checkpoint(); tagged += PlanFact(fact.premise, fact.facts, false) }
        resumeCallees(part.ref, tagged, pendingDemotion = true)
    }

    override fun applyRecordView(part: Subscription, view: RecordLeafView) {
        val core = active()
        val results = ArrayList<PlanFact>()
        val identity = recordLeafIdentity(view)
        core.ops.applyRecordLeaf(part.added, view, core.mode, core.collect(part.ref.premise, part.added) { premise, result ->
            results += PlanFact(premise, result, identity)
        })
        if (results.isNotEmpty()) core.countRecordCrossing()
        resumeCallees(part.ref, results)
    }

    private fun toDemand(facts: Facts): Facts {
        val core = active()
        return when (facts) {
            is TaintTree -> if (facts.layer == Layer.DEMAND) facts else core.ap.taintTree(facts.base, Layer.DEMAND, facts.root)
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
            val stage = checkNotNull(ctx.plan.callees) { "A resumed result needs a callee stage" }
            flow(ctx, stage.to, results, pendingDemotion)
        }
    }

    private fun applyParts(part: Subscription, premise: InitialAp, conclusion: Facts, results: MutableList<PlanFact>) {
        val core = active()
        for ((group, identity) in summaryParts(premise, conclusion)) {
            core.ap.cancellation.checkpoint()
            core.ops.applySummary(part.added, premise, group, core.mode, core.collect(part.ref.premise, part.added) { pr, result ->
                results += PlanFact(pr, result, identity)
            })
        }
    }

    private fun summaryParts(premise: InitialAp, conclusion: Facts): List<Pair<Facts, Boolean>> {
        val core = active()
        val start = core.ops.startFact(premise)
        if (premise.isZero || conclusion.layer != Layer.NORMAL || conclusion.groupKey != start.groupKey)
            return listOf(conclusion to false)
        val effect = core.ops.without(conclusion, start)
        val identity = if (effect == null) conclusion else core.ops.without(conclusion, effect)
        return listOfNotNull(effect?.let { it to false }, identity?.let { it to true })
    }

    private fun ndMatch(part: Subscription, pub: Publication, premise: PremiseSet, member: Int) {
        val core = active()
        check(core.forward) { "Backward transfer has no conjunctive summaries" }
        check(!part.zeroOnly && part.added !is Reach && !premise.member(member).isZero)
        val conclusion = checkNotNull(pub.conclusion as? TaintTree) { "A multi-premise publication is TAINT" }
        val matched = matches(part, pub, member, core.ops, core.mode) ?: return
        check(matched is TaintTree) { "An E6 member input is TAINT" }
        check(part.linkLayer == matched.layer) { "The matched fact and link have the same layer" }
        val store = checkNotNull(core.localServices.conjunctions) { "Forward calls need their local conjunction store" }
        val join = store.ndJoin<NdBinding>(ConjunctionStore.NdKey(part.callee, premise, pub.layer, part.ref.call))
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
        val results = ArrayList<PlanFact>()
        core.ops.applyMatchedCombination(layers, conclusion, core.collect(union, conclusion) { pr, result ->
            results += PlanFact(pr, result, false)
        })
        resumeCallees(parts[0].ref, results)
    }

    override fun freeze() {
        observed = null
        owner = null
    }
}
