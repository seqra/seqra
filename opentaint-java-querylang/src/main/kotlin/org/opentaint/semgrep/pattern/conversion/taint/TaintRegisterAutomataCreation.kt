package org.opentaint.semgrep.pattern.conversion.taint

import org.opentaint.dataflow.util.forEach
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.OperationCancelation
import org.opentaint.semgrep.pattern.ResolvedMetaVarInfo
import org.opentaint.semgrep.pattern.RuleWithMetaVars
import org.opentaint.semgrep.pattern.SemgrepErrorEntry
import org.opentaint.semgrep.pattern.SemgrepRule
import org.opentaint.semgrep.pattern.SemgrepRuleLoadStepTrace
import org.opentaint.semgrep.pattern.conversion.MetavarAtom
import org.opentaint.semgrep.pattern.conversion.automata.AutomataEdgeType
import org.opentaint.semgrep.pattern.conversion.automata.AutomataNode
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormula.Cube
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaManager
import org.opentaint.semgrep.pattern.conversion.automata.SemgrepRuleAutomata
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.Edge
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeCondition
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeEffect
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.MethodPredicate
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.State
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class TaintAutomataConversionCtx(
    val semgrepRuleTrace: SemgrepRuleLoadStepTrace,
    val operationTimeout: Duration
)

private val automataCreationTimeout = 2.seconds

fun createTaintAutomata(
    rule: SemgrepRule<RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>>,
    semgrepRuleTrace: SemgrepRuleLoadStepTrace
): SemgrepRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>> {
    val ctx = TaintAutomataConversionCtx(semgrepRuleTrace, automataCreationTimeout)
    return rule.flatMap {
        val taintAutomata = ctx.createAutomataWithEdgeElimination(
            it.rule.formulaManager, it.metaVarInfo, it.rule.initialNode
        )
        if (taintAutomata == null) return@flatMap emptyList()

        listOf(RuleWithMetaVars(taintAutomata, it.metaVarInfo))
    }
}

private fun TaintAutomataConversionCtx.createAutomataWithEdgeElimination(
    formulaManager: MethodFormulaManager,
    metaVarInfo: ResolvedMetaVarInfo,
    initialNode: AutomataNode,
): TaintRegisterStateAutomata? =
    runCatching {
        createAutomataWithEdgeEliminationUnsafe(formulaManager, metaVarInfo, initialNode)
    }.onFailure { ex ->
        semgrepRuleTrace.error("Taint automata creation failure: ${ex.message}", SemgrepErrorEntry.Reason.ERROR)
    }.getOrNull()

private fun TaintAutomataConversionCtx.createAutomataWithEdgeEliminationUnsafe(
    formulaManager: MethodFormulaManager,
    metaVarInfo: ResolvedMetaVarInfo,
    initialNode: AutomataNode,
): TaintRegisterStateAutomata? {
    val automata = createAutomata(formulaManager, metaVarInfo, initialNode, operationTimeout)

    val anyValueGeneratorEdgeEliminator = edgeTypePreservingEdgeEliminator(::eliminateAnyValueGenerator)
    val automataWithoutGeneratedEdges = eliminateEdges(
        automata,
        anyValueGeneratorEdgeEliminator,
        ValueGeneratorCtx.EMPTY
    )

    val stringConcatEdgeEliminator = edgeTypePreservingEdgeEliminator(::eliminateStringConcat)
    val result = eliminateEdges(
        automataWithoutGeneratedEdges,
        stringConcatEdgeEliminator,
        StringConcatCtx.EMPTY
    )

    if (result.successors[result.initial].isNullOrEmpty()) {
        semgrepRuleTrace.error("Empty automata after generated edge elimination", SemgrepErrorEntry.Reason.WARNING)
        return null
    }

    return result
}

private fun createAutomata(
    formulaManager: MethodFormulaManager,
    metaVarInfo: ResolvedMetaVarInfo,
    initialNode: AutomataNode,
    automataCreationTimeout: Duration,
): TaintRegisterStateAutomata {
    val cancelation = OperationCancelation(automataCreationTimeout)

    val result = TaintRegisterStateAutomataBuilder(startStateId = 0)

    val startState = result.newState(initialNode)
    val initialState = startState

    val processedStates = hashSetOf<State>()
    val unprocessed = mutableListOf(startState to initialNode)

    while (unprocessed.isNotEmpty()) {
        val (state, node) = unprocessed.removeLast()
        if (!processedStates.add(state)) continue

        if (node.accept) {
            result.acceptStates.add(state)
            // note: no need transitions from final state
            continue
        }

        for ((edgeCondition, dstNode) in node.outEdges) {
            for (simplifiedEdge in simplifyEdgeCondition(formulaManager, metaVarInfo, cancelation, edgeCondition)) {
                val nextState = result.newState(dstNode)
                result.successors.getOrPut(state, ::hashSetOf).add(simplifiedEdge to nextState)
                unprocessed.add(nextState to dstNode)
            }
        }
    }

    check(result.acceptStates.isNotEmpty()) { "Automata has no accept state" }

    result.collapseEpsilonTransitions(initialState)

    return result.build(formulaManager, initialState)
}

private fun simplifyEdgeCondition(
    formulaManager: MethodFormulaManager,
    metaVarInfo: ResolvedMetaVarInfo,
    cancelation: OperationCancelation,
    edge: AutomataEdgeType
) = when (edge) {
    is AutomataEdgeType.AutomataEdgeTypeWithFormula -> {
        simplifyMethodFormula(
            formulaManager, edge.formula, metaVarInfo, cancelation, applyNotEquivalentTransformations = true
        ).map {
            val (effect, cond) = edgeEffectAndCondition(it, formulaManager)

            when (edge) {
                is AutomataEdgeType.MethodCall -> Edge.MethodCall(cond, effect)
                is AutomataEdgeType.MethodEnter -> Edge.MethodEnter(cond, effect)
                is AutomataEdgeType.MethodExit -> Edge.MethodExit(cond, effect)
            }
        }
    }

    AutomataEdgeType.End -> listOf(Edge.AnalysisEnd)

    AutomataEdgeType.PatternEnd, AutomataEdgeType.PatternStart -> error("unexpected edge type: $edge")
}

private fun Cube.predicates(manager: MethodFormulaManager): List<MethodPredicate> {
    check(!negated) { "Negated cube" }

    val result = mutableListOf<MethodPredicate>()
    cube.positiveLiterals.forEach {
        result += MethodPredicate(manager.predicate(it), negated = false)
    }
    cube.negativeLiterals.forEach {
        result += MethodPredicate(manager.predicate(it), negated = true)
    }
    return result
}

private fun edgeEffectAndCondition(cube: Cube, formulaManager: MethodFormulaManager): Pair<EdgeEffect, EdgeCondition> {
    val predicates = cube.predicates(formulaManager)

    val metaVarWrite = hashMapOf<MetavarAtom, MutableList<MethodPredicate>>()
    val metaVarRead = hashMapOf<MetavarAtom, MutableList<MethodPredicate>>()
    val other = mutableListOf<MethodPredicate>()

    for (mp in predicates) {
        val metaVar = mp.findMetaVarConstraint()

        if (!mp.negated && metaVar != null) {
            metaVarWrite.getOrPut(metaVar, ::mutableListOf).add(mp)
        }

        if (metaVar != null) {
            metaVarRead.getOrPut(metaVar, ::mutableListOf).add(mp)
        } else {
            other.add(mp)
        }
    }

    return EdgeEffect(metaVarWrite) to EdgeCondition(metaVarRead, other)
}

private fun TaintRegisterStateAutomataBuilder.collapseEpsilonTransitions(initial: State) {
    val unprocessed = mutableListOf(initial)
    val visited = hashSetOf<State>()

    while (unprocessed.isNotEmpty()) {
        val state = unprocessed.removeLast()
        if (!visited.add(state)) continue

        val epsilonClosure = computeEpsilonClosure(state)

        val stateSuccessors = successors.getOrPut(state, ::hashSetOf)
        stateSuccessors.removeAll { it.first.isEpsilonTransition() }

        for (s in epsilonClosure) {
            if (s == state) continue

            for ((edge, next) in successors[s].orEmpty()) {
                if (edge.isEpsilonTransition()) continue

                val dst = if (next in epsilonClosure) state else next
                stateSuccessors.add(edge to dst)
            }
        }

        if (epsilonClosure.any { it in acceptStates }) {
            acceptStates.add(state)
        }

        if (epsilonClosure.any { it in deadStates }) {
            deadStates.add(state)
        }

        successors[state]?.forEach { unprocessed.add(it.second) }
    }
}

private fun TaintRegisterStateAutomataBuilder.computeEpsilonClosure(startState: State): Set<State> {
    val unprocessed = mutableListOf(startState)
    val visitedStates = hashSetOf<State>()

    while (unprocessed.isNotEmpty()) {
        val state = unprocessed.removeLast()
        if (!visitedStates.add(state)) continue

        successors[state]?.forEach { (edge, next) ->
            if (edge.isEpsilonTransition()){
                unprocessed.add(next)
            }
        }
    }

    return visitedStates
}

private fun Edge.isEpsilonTransition(): Boolean = when (this) {
    is Edge.AnalysisEnd -> false
    is Edge.MethodCall -> condition.isTrue() && effect.hasNoEffect()
    is Edge.MethodEnter -> condition.isTrue() && effect.hasNoEffect()
    is Edge.MethodExit -> condition.isTrue()
}
