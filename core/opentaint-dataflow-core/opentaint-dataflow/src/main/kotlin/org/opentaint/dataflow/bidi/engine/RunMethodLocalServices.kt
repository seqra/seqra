package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.interp.SinkRule
import org.opentaint.dataflow.bidi.interp.ProofKey
import org.opentaint.dataflow.bidi.interp.SourceRuleOccurrence
import org.opentaint.dataflow.bidi.interp.RulePredicate
import org.opentaint.dataflow.bidi.interp.ResumeSite
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.ir.api.common.cfg.CommonInst

/** Rule joins use the method abstraction for completed mark-demand deliveries. */
class RunMethodLocalServices(core: RunMethodAnalyzer) : MethodLocalServices {
    private var owner: RunMethodAnalyzer? = core
    private var conjunctionStore: ConjunctionStore? = if (core.forward) ConjunctionStore(core.ap, core.context.shared.n2Statistics) else null
    private var predicateStore: RulePredicateStore? =
        if (core.forward) RulePredicateStore(core.ap, core.context.shared.formStatistics) else null
    private var originConjunctionStore: Run1OriginConjunctionStore? =
        if (core.forward && core.config.run1) Run1OriginConjunctionStore(core.ap) else null
    private var originPredicateStore: Run1OriginPredicateStore? =
        if (core.forward && core.config.run1) Run1OriginPredicateStore(core.ap, core.context.shared.formStatistics) else null
    override val conjunctions: ConjunctionStore? get() = conjunctionStore
    private var triggered: HashSet<Pair<CommonInst, Any>>? = HashSet()
    override val hasPendingRequests: Boolean get() = owner?.abstraction?.hasPending == true

    private fun activeCore(): RunMethodAnalyzer =
        checkNotNull(owner) { "The local services were frozen" }.also { it.ap.cancellation.checkpoint() }

    override fun addMarkRequest(premise: InitialAp, mark: TaintMark) {
        val core = activeCore()
        check(core.config.run1) { "Requests exist only in run 1" }
        checkNotNull(core.abstraction).requireMark(premise, mark)
        if (hasPendingRequests) core.requestWork()
    }

    override fun addMarkRequests(premise: InitialAp, marks: RequestMarkSet) {
        val core = activeCore()
        check(core.config.run1) { "Requests exist only in run 1" }
        checkNotNull(core.abstraction).requireMarks(premise, marks)
        if (hasPendingRequests) core.requestWork()
    }

    override fun flushRequests(quantum: Int): Boolean {
        val core = activeCore()
        require(quantum > 0) { "The request-work quantum must be positive" }
        return core.abstraction?.flush(quantum) ?: true
    }

    override fun conjunction(edge: ConjunctiveEdge, premise: PremiseKey, facts: Facts, node: CommonInst,
                             sink: (PremiseKey, Facts) -> Unit) = conjunctionAt(edge, premise, facts, node,
        OperationInput(ResumeSite.StatementInput(activeCore().operation(node)), premise, facts), sink)

    override fun conjunctionAt(edge: ConjunctiveEdge, premise: PremiseKey, facts: Facts, node: CommonInst,
                               at: OperationInput, sink: (PremiseKey, Facts) -> Unit) {
        val core = activeCore()
        val store = checkNotNull(conjunctionStore) { "A backward form has independent conjunction branches" }
        for ((index, literal) in edge.literals.withIndex()) {
            core.ap.cancellation.checkpoint()
            if (literal.fact.base != facts.base) continue
            when (val checked = core.ops.checkMark(facts, literal, core.mode, core.unchangedStaticInitial(premise))) {
                MarkCheck.None -> Unit
                is MarkCheck.Request -> core.needGroups(at, checked.mark)
                is MarkCheck.Holds -> {
                    val input = ConjunctionStore.Input(premise, checked.facts.layer, checked.facts)
                    if (core.config.run1) {
                        val targets = HashMap<Layer, Facts>()
                        checkNotNull(originConjunctionStore).addOrigins(edge, node, edge.literals.size, index, input) { initial, layer ->
                            val result = targets.getOrPut(layer) { core.ops.targetTree(edge.target, layer, core.mode) }
                            sink(initial, core.forceRun1Layer(initial, result))
                        }
                        continue
                    }
                    val combinations = store.add(edge, node, edge.literals.size, index,
                        input)
                    for (combination in combinations) {
                        core.ap.cancellation.checkpoint()
                        val result = core.ops.targetTree(edge.target, combination.layer, core.mode)
                        sink(combination.premise, core.forceRun1Layer(combination.premise, result))
                        core.ap.cancellation.checkpoint()
                    }
                }
            }
        }
    }

    override fun sourcePredicate(source: SourceRuleOccurrence, premise: PremiseKey, facts: Facts, node: CommonInst,
                                 statementEdge: Boolean, sink: (PremiseKey, Facts) -> Unit) =
        sourcePredicateAt(source, premise, facts, node, statementEdge,
            OperationInput(ResumeSite.StatementInput(activeCore().operation(node)), premise, facts), sink)

    override fun sourcePredicateAt(source: SourceRuleOccurrence, premise: PremiseKey, facts: Facts, node: CommonInst,
                                   statementEdge: Boolean, at: OperationInput, sink: (PremiseKey, Facts) -> Unit) {
        val core = activeCore()
        if (source.actions.isEmpty()) return
        val predicate = source.predicate
        fun transfer(pattern: Pattern, multi: Boolean) {
            for (action in source.actions) {
                core.ap.cancellation.checkpoint()
                val edge = source.transfer(pattern, action, multi)
                if (edge.edge.from.base != facts.base) continue
                if (core.config.seededSources && !core.config.seeds.allowsSource(core.key, node, edge.forward, action.id)) continue
                core.ops.applyEdge(facts, premise, edge.edge, statementEdge, core.mode,
                    core.collectAfterRun1Policy(at) { _, result ->
                        core.sourceHits?.add(core.key, node, edge.forward, action.id)
                        sink(premise, result)
                    }, may = edge.may)
            }
        }
        if (source.backward) {
            check(!core.forward)
            for (leaf in predicate.leaves.indices) {
                core.ap.cancellation.checkpoint()
                if (predicate.isUnary(leaf)) transfer(predicate.leaves[leaf], multi = false)
                if (predicate.isMulti(leaf)) transfer(predicate.leaves[leaf], multi = true)
            }
            return
        }
        check(core.forward)
        fun multiLeaf(selected: RulePredicate, leaf: Int, pattern: Pattern, allowSingle: Boolean) {
            when (val checked = core.ops.checkMark(facts, pattern, core.mode, core.unchangedStaticInitial(premise))) {
                MarkCheck.None -> Unit
                is MarkCheck.Request -> core.needGroups(at, checked.mark)
                is MarkCheck.Holds -> if (core.config.run1) {
                    checkNotNull(originPredicateStore).add(source.id, node, selected, leaf,
                        ConjunctionStore.Input(premise, checked.facts.layer, checked.facts)) { cube ->
                        if (allowSingle || cube.leaves.size >= 2) for (action in source.actions) {
                            core.ap.cancellation.checkpoint()
                            if (core.config.seededSources && cube.leaves.none { index ->
                                core.config.seeds.allowsSource(core.key, node,
                                    source.transfer(selected.leaves[index], action, multi = true).forward, action.id)
                            }) continue
                            val result = core.ops.targetTree(action.target, cube.layer, core.mode)
                            cube.emitOrigins(action.id) { initial ->
                                sink(initial, core.forceRun1Layer(initial, result))
                            }
                        }
                    }
                } else checkNotNull(predicateStore).add(source.id, node, selected, leaf,
                    ConjunctionStore.Input(premise, checked.facts.layer, checked.facts)) { combination ->
                    // GROUP folding can leave one accepted leaf in an original MULTI transfer.
                    if (allowSingle || combination.leaves.size >= 2) for (action in source.actions) {
                        core.ap.cancellation.checkpoint()
                        if (core.config.seededSources && combination.leaves.none { index ->
                            core.config.seeds.allowsSource(core.key, node,
                                source.transfer(selected.leaves[index], action, multi = true).forward, action.id)
                        }) continue
                        val result = core.ops.targetTree(action.target, combination.layer, core.mode)
                        sink(combination.premise, core.forceRun1Layer(combination.premise, result))
                    }
                }
            }
        }
        if (core.config.run1 && source.groupTransfers) {
            for (unary in source.unaryTransfers) {
                core.ap.cancellation.checkpoint()
                matchingLeaves(core, unary.predicate, facts) { _, _ ->
                    // Use the declared anchor and original Facts, never the checked AP piece.
                    transfer(unary.anchor, multi = false)
                }
            }
            source.multiPredicate?.let { multi ->
                matchingLeaves(core, multi, facts) { leaf, pattern -> multiLeaf(multi, leaf, pattern, allowSingle = true) }
            }
            return
        }
        matchingLeaves(core, predicate, facts) { leaf, pattern ->
            core.ap.cancellation.checkpoint()
            if (predicate.isUnary(leaf)) transfer(pattern, multi = false)
            if (predicate.isMulti(leaf)) multiLeaf(predicate, leaf, pattern, allowSingle = false)
        }
    }

    override fun checkSinks(premise: PremiseKey, node: CommonInst, facts: Facts, sinks: List<SinkRule>): Triggers {
        return checkSinksAt(premise, node, facts, sinks,
            OperationInput(ResumeSite.StatementInput(activeCore().operation(node)), premise, facts))
    }

    override fun checkSinksAt(premise: PremiseKey, node: CommonInst, facts: Facts, sinks: List<SinkRule>, at: OperationInput): Triggers {
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
                    when (val checked = core.ops.checkMark(facts, literal, core.mode, core.unchangedStaticInitial(premise))) {
                        MarkCheck.None -> Unit
                        is MarkCheck.Request -> core.needGroups(at, checked.mark)
                        is MarkCheck.Holds -> {
                            parts += checked.facts
                            checkNotNull(predicateStore).addBinding(checkNotNull(sink.occurrenceId), node, predicate, index,
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
                when (val checked = core.ops.checkMark(facts, literal, core.mode, core.unchangedStaticInitial(premise))) {
                    MarkCheck.None -> Unit
                    is MarkCheck.Request -> core.needGroups(at, checked.mark)
                    is MarkCheck.Holds -> {
                        parts += checked.facts
                        if (!sink.conjunctive) {
                            witness(core, sink, node, listOf(SinkEdge(premise, checked.facts.layer, checked.facts)))
                            fired += sink to checked.facts.layer
                        } else {
                            val combinations = checkNotNull(conjunctionStore).addBinding(sink.joinKey, node, sink.patterns.size, index,
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
        val endFacts = emptyList<PathFact>() // selected policy ignores sink end actions in every run
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
        conjunctionStore = null
        predicateStore = null
        originConjunctionStore = null
        originPredicateStore = null
        triggered = null
        owner = null
    }
}
