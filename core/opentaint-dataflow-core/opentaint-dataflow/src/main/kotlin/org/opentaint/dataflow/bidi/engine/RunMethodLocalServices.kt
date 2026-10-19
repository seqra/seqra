package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.interp.SinkRule
import org.opentaint.dataflow.bidi.interp.ProofKey
import org.opentaint.dataflow.bidi.interp.SourceRuleOccurrence
import org.opentaint.dataflow.bidi.interp.RulePredicate
import org.opentaint.dataflow.bidi.interp.ResumeSite
import org.opentaint.dataflow.bidi.interp.StaticOperation
import org.opentaint.dataflow.bidi.interp.OperationId
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.ir.api.common.cfg.CommonInst

/** Rule joins use the method abstraction for completed mark-demand deliveries. */
class RunMethodLocalServices(core: RunMethodAnalyzer) : MethodLocalServices {
    private var owner: RunMethodAnalyzer? = core
    private var conjunctionStore: ConjunctionStore? = if (core.forward) ConjunctionStore(core.ap, core.context.shared.n2Statistics) else null
    private var predicateStore: RulePredicateStore? =
        if (core.forward) RulePredicateStore(core.ap, core.context.shared.formStatistics) else null
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
        core.observeGrounding(at)
        val store = checkNotNull(conjunctionStore) { "A backward form has independent conjunction branches" }
        for ((index, literal) in edge.literals.withIndex()) {
            core.ap.cancellation.checkpoint()
            if (literal.fact.base != facts.base) continue
            when (val checked = core.ops.checkMark(facts, literal, core.mode, core.unchangedStaticInitial(premise))) {
                MarkCheck.None -> Unit
                is MarkCheck.Request -> core.needGroups(at, checked.mark)
                is MarkCheck.Holds -> {
                    val combinations = store.add(edge, node, edge.literals.size, index,
                        ConjunctionStore.Input(premise, checked.facts.layer, checked.facts, literal, index,
                            core.guardGrounding(at, checked.facts, literal, leaf = index)))
                    for (combination in combinations) {
                        core.ap.cancellation.checkpoint()
                        val result = core.ops.targetTree(edge.target, combination.layer, core.mode)
                        val input = at.copy(premise = combination.premise, support = combinationSupport(combination.inputs))
                        val write = if (edge.target.base == STATIC) StaticOperation.Write(at.site.operation,
                            Pattern(edge.target, ExclusionSet.Empty)) else null
                        val value = core.forceRun1Layer(combination.premise, result)
                        val support = if (edge.target.tail != Tail.STAR) core.fixedGrounding(input)
                            else checkNotNull(input.support)
                        core.withGroundingSupport(support) { sink(combination.premise, value) }
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
        core.observeGrounding(at)
        val predicate = source.predicate
        fun transfer(leaf: Int, multi: Boolean) {
            for (action in source.actions) {
                core.ap.cancellation.checkpoint()
                val edge = source.transfer(predicate.leaves[leaf], action, multi)
                if (edge.edge.from.base != facts.base) continue
                if (core.config.seededSources && !core.config.seeds.allowsSource(core.key, node, edge.forward, action.id)) continue
                val checked = if (core.forward && !multi) core.ops.checkMark(facts, predicate.leaves[leaf], core.mode) else null
                val sourceSupport = if (checked is MarkCheck.Holds) {
                    core.guardGrounding(at, checked.facts, predicate.leaves[leaf], ProofKey(source.id, listOf(leaf)), leaf)
                } else at.support
                val sourceAt = at.copy(support = sourceSupport)
                core.ops.applyEdge(facts, premise, edge.edge, statementEdge, core.mode,
                    core.collect(sourceAt) { _, result ->
                        core.sourceHits?.add(core.key, node, edge.forward, action.id)
                        val write = if (action.target.base == STATIC) StaticOperation.Write(
                            OperationId(action.id.occurrence.form, at.site.operation.stageOrdinal, action.id),
                            Pattern(action.target, ExclusionSet.Empty), action = action.id) else null
                        val support = if (action.target.tail != Tail.STAR) core.fixedGrounding(sourceAt)
                            else sourceAt.support ?: core.currentGroundingSupport
                        core.withGroundingSupport(support) { sink(premise, result) }
                    }, may = edge.may)
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
            if (predicate.isMulti(leaf)) when (val checked = core.ops.checkMark(facts, pattern, core.mode, core.unchangedStaticInitial(premise))) {
                MarkCheck.None -> Unit
                is MarkCheck.Request -> core.needGroups(at, checked.mark)
                is MarkCheck.Holds -> checkNotNull(predicateStore).add(source.id, node, predicate, leaf,
                    ConjunctionStore.Input(premise, checked.facts.layer, checked.facts, pattern, leaf,
                        core.guardGrounding(at, checked.facts, pattern, ProofKey(source.id, listOf(leaf)), leaf))) { combination ->
                    if (combination.leaves.size >= 2) for (action in source.actions) {
                        core.ap.cancellation.checkpoint()
                        if (core.config.seededSources && combination.leaves.none { selected ->
                            core.config.seeds.allowsSource(core.key, node,
                                source.transfer(predicate.leaves[selected], action, multi = true).forward, action.id)
                        }) continue
                        val result = core.ops.targetTree(action.target, combination.layer, core.mode)
                        val input = at.copy(premise = combination.premise, support = combinationSupport(combination.inputs, combination.proof))
                        val write = if (action.target.base == STATIC) StaticOperation.Write(
                            OperationId(action.id.occurrence.form, at.site.operation.stageOrdinal, action.id),
                            Pattern(action.target, ExclusionSet.Empty), action = action.id) else null
                        val value = core.forceRun1Layer(combination.premise, result)
                        val support = if (action.target.tail != Tail.STAR) core.fixedGrounding(input)
                            else checkNotNull(input.support)
                        core.withGroundingSupport(support) { sink(combination.premise, value) }
                    }
                }
            }
        }
    }

    override fun checkSinks(premise: PremiseKey, node: CommonInst, facts: Facts, sinks: List<SinkRule>): Triggers {
        return checkSinksAt(premise, node, facts, sinks,
            OperationInput(ResumeSite.StatementInput(activeCore().operation(node)), premise, facts))
    }

    override fun checkSinksAt(premise: PremiseKey, node: CommonInst, facts: Facts, sinks: List<SinkRule>, at: OperationInput): Triggers {
        val core = activeCore()
        if (!core.forward || sinks.isEmpty()) return Triggers.NONE
        core.observeGrounding(at)
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
                when (val checked = core.ops.checkMark(facts, literal, core.mode, core.unchangedStaticInitial(premise))) {
                    MarkCheck.None -> Unit
                    is MarkCheck.Request -> core.needGroups(at, checked.mark)
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

    private fun combinationSupport(inputs: List<ConjunctionStore.Input>, proof: ProofKey? = null): GroundingSupport {
        val core = activeCore()
        var inherited = GroundingSupport.TRUE
        val requirements = ArrayList<GroundingRequirement>()
        for (input in inputs) {
            val facts = input.facts ?: continue
            if (input.grounding != null) { inherited = inherited.and(input.grounding); continue }
            inherited = inherited.and(core.currentGroundingSupport)
            input.requirement?.let { requirements += GroundingRequirement(input.premise, facts, it, proof, input.leafOrdinal) }
        }
        return inherited.and(GroundingSupport.require(requirements))
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
        triggered = null
        owner = null
    }
}
