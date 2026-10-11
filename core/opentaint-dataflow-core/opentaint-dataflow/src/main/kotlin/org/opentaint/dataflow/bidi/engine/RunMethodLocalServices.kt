package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.interp.SinkRule
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.ir.api.common.cfg.CommonInst

/** One method owns its standing requests, forward literal joins and seed triggers. */
class RunMethodLocalServices(core: RunMethodAnalyzer) : MethodLocalServices {
    private var owner: RunMethodAnalyzer? = core
    private var requests: RequestStore? = if (core.config.run1) RequestStore(core.ap.cancellation) else null
    private var conjunctionStore: ConjunctionStore? = if (core.forward) ConjunctionStore(core.ap) else null
    override val conjunctions: ConjunctionStore? get() = conjunctionStore
    private var triggered: HashSet<Pair<CommonInst, SinkRule>>? = HashSet()
    private var requestJoin: StandingJoin<Pair<InitialAp, RequestKind>, Link>? =
        if (!core.config.run1) null else StandingJoin(
            nearB = { (initial, kind) ->
                when (kind) {
                    is RequestKind.Mark -> core.links.overlapping(initial.base, initial.path)
                    is RequestKind.Position -> core.links.overlapping(STATIC, kind.path)
                }
            },
            nearA = { link -> checkNotNull(requests).overlapping(link.addedFact) },
            meet = { (initial, kind), link -> act(initial, kind, link.addedFact, link.caller) },
        )

    private fun activeCore(): RunMethodAnalyzer =
        checkNotNull(owner) { "The local services were frozen" }.also { it.ap.cancellation.checkpoint() }

    override fun addRequest(premise: InitialAp, kind: RequestKind) {
        val core = activeCore()
        check(core.config.run1) { "Requests exist only in run 1" }
        if (checkNotNull(requests).add(premise, kind)) {
            core.ap.cancellation.checkpoint()
            checkNotNull(requestJoin).newA(premise to kind)
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

    private fun act(initial: InitialAp, kind: RequestKind, added: Pattern, caller: CallerRef) {
        val core = activeCore()
        when (val action = core.ops.requestAction(initial, kind, added, caller)) {
            RequestAction.None -> Unit
            is RequestAction.Answer -> core.addInitial(action.initial)
            is RequestAction.Climb ->
                core.runnerPort.send(RunEvent.RequestIn(caller.caller, action.premise, action.request))
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

    override fun checkSinks(premise: PremiseKey, node: CommonInst, facts: Facts, sinks: List<SinkRule>): Triggers {
        val core = activeCore()
        if (!core.forward || sinks.isEmpty()) return Triggers.NONE
        val fired = ArrayList<Pair<SinkRule, Layer>>()
        val parts = ArrayList<Facts>()
        for (sink in sinks) {
            core.ap.cancellation.checkpoint()
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
                            val combinations = checkNotNull(conjunctionStore).add(sink, node, sink.patterns.size, index,
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

    private fun witness(core: RunMethodAnalyzer, sink: SinkRule, node: CommonInst, edges: List<SinkEdge>) {
        core.ap.cancellation.checkpoint()
        val endFacts = sink.endFacts.map { edge -> core.ap.cancellation.checkpoint(); edge.edge.to }
        core.context.vulnerabilities.add(VulnerabilityKey(sink.rule, core.key.method, node),
            SinkWitness(sink.alternative, core.key, edges, core.config.index, endFacts))
    }

    override fun fireSinkSeeds(node: CommonInst, emit: (Facts) -> Unit) {
        val core = activeCore()
        for (seed in core.config.seeds.at(core.key, node)) {
            core.ap.cancellation.checkpoint()
            if (seed is Seed.Sink) seedFact(core, seed.requirement, emit)
        }
    }

    override fun fireTriggerSeeds(node: CommonInst, sink: SinkRule, emit: (Facts) -> Unit) {
        val core = activeCore()
        if (!checkNotNull(triggered).add(node to sink)) return
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
        requests = null
        conjunctionStore = null
        triggered = null
        owner = null
    }
}
