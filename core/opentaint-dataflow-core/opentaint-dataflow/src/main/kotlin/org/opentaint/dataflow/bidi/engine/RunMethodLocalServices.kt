package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.interp.SinkRule
import org.opentaint.dataflow.bidi.interp.ProofKey
import org.opentaint.dataflow.bidi.interp.SourceRuleOccurrence
import org.opentaint.dataflow.bidi.interp.RulePredicate
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.ir.api.common.cfg.CommonInst

/** One method owns its standing requests, forward literal joins and seed triggers. */
class RunMethodLocalServices(core: RunMethodAnalyzer) : MethodLocalServices {
    private var owner: RunMethodAnalyzer? = core
    private val requestStatistics = core.context.shared.n1Statistics
    private var requests: RequestStore? = if (core.config.run1) RequestStore(core.ap.cancellation, requestStatistics) else null
    private var conjunctionStore: ConjunctionStore? = if (core.forward) ConjunctionStore(core.ap) else null
    private var predicateStore: RulePredicateStore? =
        if (core.forward) RulePredicateStore(core.ap, core.context.shared.formStatistics) else null
    override val conjunctions: ConjunctionStore? get() = conjunctionStore
    private var triggered: HashSet<Pair<CommonInst, Any>>? = HashSet()
    private var requestJoin: RequestJoin? = if (core.config.run1) RequestJoin() else null

    private inner class RequestJoin {
        fun newA(initial: InitialAp, kind: RequestKind) {
            val core = activeCore()
            val query = checkNotNull(requests).query(initial, kind)
            val links = when (kind) {
                is RequestKind.Mark -> core.links.overlapping(initial.base, initial.path)
                is RequestKind.Position -> core.links.overlapping(STATIC, kind.path)
            }
            val counts = if (requestStatistics == null) null else N1Statistics.Actions()
            try {
                for (link in links) {
                    core.ap.cancellation.checkpoint()
                    if (counts != null) counts.existingLinks++
                    if (kind is RequestKind.Mark) when (val mark = link.addedFact.fact.mark) {
                        is MarkSlot.Concrete -> if (mark.mark != kind.mark) continue
                        is MarkSlot.Star -> if (kind.mark in mark.excluded) continue
                    }
                    if (!overlap(link.addedFact, query)) continue
                    if (counts != null) counts.selectedLinks++
                    act(initial, kind, link.addedFact, link.caller, counts)
                }
            } finally {
                if (counts != null) requestStatistics?.actions(counts)
            }
        }
        fun newB(link: Link) {
            activeCore()
            val counts = if (requestStatistics == null) null else N1Statistics.Actions()
            try {
                checkNotNull(requests).visitActionCandidates(link.addedFact) { initial, kind ->
                    act(initial, kind, link.addedFact, link.caller, counts)
                }
            } finally {
                if (counts != null) requestStatistics?.actions(counts)
            }
        }
    }

    private fun activeCore(): RunMethodAnalyzer =
        checkNotNull(owner) { "The local services were frozen" }.also { it.ap.cancellation.checkpoint() }

    override fun addRequest(premise: InitialAp, kind: RequestKind) {
        val core = activeCore()
        check(core.config.run1) { "Requests exist only in run 1" }
        if (checkNotNull(requests).add(premise, kind)) {
            core.ap.cancellation.checkpoint()
            checkNotNull(requestJoin).newA(premise, kind)
        }
    }

    override fun onAddedLink(ref: CallerRef, linkLayer: Layer, delta: Facts) {
        val core = activeCore()
        val join = requestJoin ?: return
        for (leaf in core.ops.leaves(delta)) {
            core.ap.cancellation.checkpoint()
            join.newB(Link(leaf, linkLayer, ref))
        }
    }

    private fun act(initial: InitialAp, kind: RequestKind, added: Pattern, caller: CallerRef,
                    counts: N1Statistics.Actions?) {
        val core = activeCore()
        when (val action = core.ops.requestAction(initial, kind, added, caller)) {
            RequestAction.None -> if (counts != null) counts.none++
            is RequestAction.Answer -> {
                if (counts != null) counts.answers++
                core.addInitial(action.initial)
            }
            is RequestAction.Climb -> {
                if (counts != null) counts.climbs++
                core.runnerPort.send(RunEvent.RequestIn(caller.caller, action.premise, action.request))
            }
        }
    }

    override fun conjunction(edge: ConjunctiveEdge, premise: PremiseKey, facts: Facts, node: CommonInst,
                             sink: (PremiseKey, Facts) -> Unit) {
        val core = activeCore()
        val store = checkNotNull(conjunctionStore) { "A backward form has independent conjunction branches" }
        for ((index, literal) in edge.literals.withIndex()) {
            core.ap.cancellation.checkpoint()
            if (literal.fact.base != facts.base) continue
            when (val checked = core.ops.checkMark(facts, literal, core.mode)) {
                MarkCheck.None -> Unit
                is MarkCheck.Request -> core.raise(premise, facts, RequestKind.Mark(checked.mark))
                is MarkCheck.Holds -> {
                    val combinations = store.add(edge, node, edge.literals.size, index,
                        ConjunctionStore.Input(premise, checked.facts.layer))
                    for (combination in combinations) {
                        core.ap.cancellation.checkpoint()
                        sink(combination.premise, core.ops.targetTree(edge.target, combination.layer))
                        core.ap.cancellation.checkpoint()
                    }
                }
            }
        }
    }

    override fun sourcePredicate(source: SourceRuleOccurrence, premise: PremiseKey, facts: Facts, node: CommonInst,
                                 statementEdge: Boolean, sink: (PremiseKey, Facts) -> Unit) {
        val core = activeCore()
        if (source.actions.isEmpty()) return
        val predicate = source.predicate
        fun transfer(leaf: Int, multi: Boolean) {
            for (action in source.actions) {
                core.ap.cancellation.checkpoint()
                val edge = source.transfer(predicate.leaves[leaf], action, multi)
                if (edge.edge.from.base != facts.base) continue
                core.ops.applyEdge(facts, premise, edge.edge, statementEdge, core.mode,
                    core.collect(premise, facts, sink), may = edge.may)
            }
        }
        if (source.backward) {
            check(!core.forward)
            for (leaf in predicate.leaves.indices) {
                core.ap.cancellation.checkpoint()
                if (predicate.isUnary(leaf)) transfer(leaf, multi = false)
                if (predicate.isMulti(leaf)) transfer(leaf, multi = true)
            }
            return
        }
        check(core.forward)
        matchingLeaves(core, predicate, facts) { leaf, pattern ->
            core.ap.cancellation.checkpoint()
            // Unary transfer needs the original Facts for suffix, exclusion and POSITION behavior.
            if (predicate.isUnary(leaf)) transfer(leaf, multi = false)
            if (predicate.isMulti(leaf)) when (val checked = core.ops.checkMark(facts, pattern, core.mode)) {
                MarkCheck.None -> Unit
                is MarkCheck.Request -> core.raise(premise, facts, RequestKind.Mark(checked.mark))
                is MarkCheck.Holds -> checkNotNull(predicateStore).add(source.id, node, predicate, leaf,
                    ConjunctionStore.Input(premise, checked.facts.layer)) { combination ->
                    if (combination.leaves.size >= 2) for (action in source.actions) {
                        core.ap.cancellation.checkpoint()
                        sink(combination.premise, core.ops.targetTree(action.target, combination.layer))
                    }
                }
            }
        }
    }

    override fun checkSinks(premise: PremiseKey, node: CommonInst, facts: Facts, sinks: List<SinkRule>): Triggers {
        val core = activeCore()
        if (!core.forward || sinks.isEmpty()) return Triggers.NONE
        val fired = ArrayList<Pair<SinkRule, Layer>>()
        val parts = ArrayList<Facts>()
        for (sink in sinks) {
            core.ap.cancellation.checkpoint()
            val predicate = sink.predicate
            if (predicate != null && !predicate.isTrue) {
                matchingLeaves(core, predicate, facts) { index, literal ->
                    core.ap.cancellation.checkpoint()
                    when (val checked = core.ops.checkMark(facts, literal, core.mode)) {
                        MarkCheck.None -> Unit
                        is MarkCheck.Request -> core.raise(premise, facts, RequestKind.Mark(checked.mark))
                        is MarkCheck.Holds -> {
                            parts += checked.facts
                            checkNotNull(predicateStore).add(checkNotNull(sink.occurrenceId), node, predicate, index,
                                ConjunctionStore.Input(premise, checked.facts.layer, checked.facts)) { combination ->
                                val edges = combination.inputs.map {
                                    SinkEdge(it.premise, it.layer, checkNotNull(it.facts))
                                }
                                witness(core, sink, node, edges, combination.proof)
                                fired += sink to combination.layer
                            }
                        }
                    }
                }
                continue
            }
            for ((index, literal) in sink.patterns.withIndex()) {
                core.ap.cancellation.checkpoint()
                when (val checked = core.ops.checkMark(facts, literal, core.mode)) {
                    MarkCheck.None -> Unit
                    is MarkCheck.Request -> core.raise(premise, facts, RequestKind.Mark(checked.mark))
                    is MarkCheck.Holds -> {
                        parts += checked.facts
                        if (!sink.conjunctive) {
                            witness(core, sink, node, listOf(SinkEdge(premise, checked.facts.layer, checked.facts)))
                            fired += sink to checked.facts.layer
                        } else {
                            val combinations = checkNotNull(conjunctionStore).add(sink.joinKey, node, sink.patterns.size, index,
                                ConjunctionStore.Input(premise, checked.facts.layer, checked.facts))
                            for (combination in combinations) {
                                core.ap.cancellation.checkpoint()
                                val edges = combination.inputs.map { input ->
                                    core.ap.cancellation.checkpoint()
                                    SinkEdge(input.premise, input.layer, checkNotNull(input.facts))
                                }
                                witness(core, sink, node, edges)
                                fired += sink to combination.layer
                            }
                        }
                    }
                }
            }
        }
        return Triggers(fired.distinct(), parts)
    }

    private fun matchingLeaves(core: RunMethodAnalyzer, predicate: RulePredicate, facts: Facts,
                               visit: (Int, Pattern) -> Unit) {
        when (facts) {
            is FlowTree -> predicate.matchingLeaves(facts.base, mark = null, visit)
            is Reach -> predicate.matchingLeaves(facts.base, TaintMark.ZERO, visit)
            is TaintTree -> {
                val leaves = core.ap.taintAlg.foldAll(facts.root, core.ap.cancellation)
                val marks = leaves.exact + leaves.any
                for (mark in marks.data) {
                    core.ap.cancellation.checkpoint()
                    predicate.matchingLeaves(facts.base, TaintMark(mark), visit)
                }
            }
        }
    }

    private fun witness(core: RunMethodAnalyzer, sink: SinkRule, node: CommonInst, edges: List<SinkEdge>, proof: ProofKey? = null) {
        core.ap.cancellation.checkpoint()
        val endFacts = sink.endFacts.map { edge -> core.ap.cancellation.checkpoint(); edge.edge.to }
        val scopedProof = proof ?: sink.occurrenceId?.let { ProofKey(it, emptyList()) }
        core.context.vulnerabilities.add(VulnerabilityKey(sink.rule, core.key.method, node),
            SinkWitness(sink.alternative, core.key, edges, core.config.index, endFacts, scopedProof))
    }

    override fun fireSinkSeeds(node: CommonInst, emit: (Facts) -> Unit) {
        val core = activeCore()
        for (seed in core.config.seeds.at(core.key, node)) {
            core.ap.cancellation.checkpoint()
            if (seed is Seed.Sink) seedFact(core, seed.requirement, emit)
            if (seed is Seed.PredicateSink) {
                fun visit(sinks: List<SinkRule>) {
                    val sink = sinks.singleOrNull { it.occurrenceId == seed.occurrence }
                        ?: error("A predicate seed must resolve to its frozen rule occurrence")
                    for (requirement in sink.seedPatterns()) seedFact(core, requirement, emit)
                }
                val call = core.context.callAt(node)
                if (call != null) core.forms.withCall(core.key, node, call) { visit(it.sinks) }
                else core.forms.withStartRules(core.key, node) { visit(it.sinks) }
            }
        }
    }

    override fun fireTriggerSeeds(node: CommonInst, sink: SinkRule, emit: (Facts) -> Unit) {
        val core = activeCore()
        if (!checkNotNull(triggered).add(node to sink.joinKey)) return
        for (literal in sink.seedPatterns()) {
            core.ap.cancellation.checkpoint()
            core.countTriggerSeed()
            seedFact(core, literal, emit)
        }
    }

    private fun seedFact(core: RunMethodAnalyzer, requirement: Pattern, emit: (Facts) -> Unit) {
        core.ap.cancellation.checkpoint()
        core.cut(Cut.SEED, core.ap.zero, core.ops.targetTree(requirement.fact, Layer.NORMAL)) { _, facts ->
            emit(facts)
            core.ap.cancellation.checkpoint()
        }
    }

    override fun freeze() {
        requestJoin = null
        requests?.release()
        requests = null
        conjunctionStore = null
        predicateStore = null
        triggered = null
        owner = null
    }
}
