package org.opentaint.semgrep.pattern.conversion.taint

import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SinkRule
import org.opentaint.semgrep.pattern.GeneratedTaintMark
import org.opentaint.semgrep.pattern.Mark
import org.opentaint.semgrep.pattern.Mark.GeneratedMark
import org.opentaint.semgrep.pattern.Mark.RuleUniqueMarkPrefix
import org.opentaint.semgrep.pattern.ResolvedMetaVarInfo
import org.opentaint.semgrep.pattern.RuleWithMetaVars
import org.opentaint.semgrep.pattern.SemgrepErrorEntry.Reason
import org.opentaint.semgrep.pattern.SemgrepJoinOnOperation
import org.opentaint.semgrep.pattern.SemgrepMatchingRule
import org.opentaint.semgrep.pattern.SemgrepRule
import org.opentaint.semgrep.pattern.SemgrepTaintLabel
import org.opentaint.semgrep.pattern.SemgrepTaintRule
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import org.opentaint.semgrep.pattern.conversion.MetavarAtom

data class TaintAutomataJoinRule(
    val items: Map<String, TaintAutomataJoinRuleItem>,
    val operations: List<TaintAutomataJoinOperation>
)

data class TaintAutomataJoinRuleItem(
    val ruleId: String,
    val rule: SemgrepRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
)

data class TaintAutomataJoinMetaVarRef(
    val itemId: String,
    val metaVar: MetavarAtom
)

data class TaintAutomataJoinOperation(
    val op: SemgrepJoinOnOperation,
    val lhs: TaintAutomataJoinMetaVarRef,
    val rhs: TaintAutomataJoinMetaVarRef
)

fun RuleConversionCtx.convertTaintAutomataJoinToTaintRules(
    rule: TaintAutomataJoinRule
): TaintRuleFromSemgrep? {
    val nonComposeOp = rule.operations.find { it.op !== SemgrepJoinOnOperation.COMPOSE }
    if (nonComposeOp != null) {
        trace.error("Join rule with ${nonComposeOp.op} operation", Reason.NOT_IMPLEMENTED)
        return null
    }

    if (rule.operations.isEmpty()) {
        trace.error("Join rule with no operations", Reason.NOT_IMPLEMENTED)
        return null
    }

    if (!validateNoChainedOperations(rule.operations)) {
        trace.error("Join rule with chained operations", Reason.NOT_IMPLEMENTED)
        return null
    }

    val operationsByRightItem = rule.operations.groupBy { it.rhs }
    if (operationsByRightItem.size > 1) {
        trace.error("Join rule with multiple distinct right items", Reason.NOT_IMPLEMENTED)
        return null
    }

    val (rightItemRef, compositions) = operationsByRightItem.entries.first()
    val leftItemRefs = compositions.map { it.lhs }
    return convertCompositionJoinOperations(rule, rightItemRef, leftItemRefs)
}

private fun validateNoChainedOperations(operations: List<TaintAutomataJoinOperation>): Boolean {
    val leftItems = operations.map { it.lhs.itemId }.toSet()
    val rightItems = operations.map { it.rhs.itemId }.toSet()
    return leftItems.intersect(rightItems).isEmpty()
}

private fun RuleConversionCtx.convertCompositionJoinOperations(
    rule: TaintAutomataJoinRule,
    rightItemRef: TaintAutomataJoinMetaVarRef,
    leftItemRefs: List<TaintAutomataJoinMetaVarRef>,
): TaintRuleFromSemgrep? {
    val allLeftRules = mutableListOf<TaintRuleFromSemgrep.TaintRuleGroup>()
    val allLeftFinalMarks = hashSetOf<GeneratedMark>()

    for (leftItemRef in leftItemRefs) {
        val leftItem = rule.items.getValue(leftItemRef.itemId)
        val leftAutomata = leftItem.rule

        val leftCtx = RuleConversionCtx("$ruleId#${leftItemRef.itemId}", meta, trace)
        val (leftRules, leftFinalMarks) = leftCtx.convertCompositionLeftRule(
            leftAutomata, leftItemRef.metaVar
        ) ?: return null

        allLeftRules.addAll(leftRules)
        allLeftFinalMarks.addAll(leftFinalMarks)
    }

    val rightItem = rule.items.getValue(rightItemRef.itemId)
    val rightAutomata = rightItem.rule
    val rightRules = when (rightAutomata) {
        is SemgrepMatchingRule -> convertCompositionRightMatchingRule(
            rightAutomata, rightItemRef.metaVar, allLeftFinalMarks
        )

        is SemgrepTaintRule -> convertCompositionRightTaintRule(
            rightAutomata, rightItemRef.metaVar, allLeftFinalMarks
        ) ?: return null
    }

    return TaintRuleFromSemgrep(ruleId, allLeftRules + rightRules)
}

private fun RuleConversionCtx.convertCompositionLeftRule(
    automata: SemgrepRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
    finalVar: MetavarAtom,
): Pair<List<TaintRuleFromSemgrep.TaintRuleGroup>, Set<GeneratedMark>>? {
    return when (automata) {
        is SemgrepMatchingRule -> convertCompositionLeftMatchingRule(automata, finalVar)
        is SemgrepTaintRule -> convertCompositionLeftTaintRule(automata, finalVar)
    }
}

private fun RuleConversionCtx.convertCompositionLeftMatchingRule(
    automata: SemgrepMatchingRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
    finalVar: MetavarAtom,
): Pair<List<TaintRuleFromSemgrep.TaintRuleGroup>, Set<GeneratedMark>> {
    val leftEdges = automata.flatMap { r ->
        val automataWithVars = TaintRegisterStateAutomataWithStateVars(
            r.rule, initialStateVars = emptySet(), acceptStateVars = setOf(finalVar)
        )
        val taintEdges = safeConvertToTaintRules {
            generateTaintAutomataEdges(automataWithVars, r.metaVarInfo)
        }
        listOfNotNull(taintEdges)
    }

    val leftCtx = leftEdges.rules.mapIndexed { idx, r ->
        val taintEdgesWithAssign = r.copy(
            edges = r.edges + r.edgesToFinalAccept,
            edgesToFinalAccept = emptyList()
        )
        TaintRuleGenerationCtx(
            RuleUniqueMarkPrefix(ruleId, idx),
            taintEdgesWithAssign, compositionStrategy = null
        )
    }

    val leftRules = leftCtx.mapNotNull {
        safeConvertToTaintRules {
            val generatedRules = it.generateTaintRules(this)
            TaintRuleFromSemgrep.TaintRuleGroup(generatedRules)
        }
    }

    val leftFinalMarks = hashSetOf<GeneratedMark>()
    leftCtx.forEach { ctx ->
        ctx.automata.finalAcceptStates.forEach { s ->
            ctx.stateAssignMark(finalVar, s, PositionBase.Result.base()).forEach { assign ->
                leftFinalMarks.add(Mark.parseMark(assign.kind))
            }
        }
    }

    return leftRules to leftFinalMarks
}

private fun RuleConversionCtx.convertCompositionRightMatchingRule(
    automata: SemgrepMatchingRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
    initialVar: MetavarAtom,
    leftFinalMarks: Set<GeneratedMark>,
): List<TaintRuleFromSemgrep.TaintRuleGroup> {
    val rightEdges = automata.flatMap { r ->
        val automataWithVars = TaintRegisterStateAutomataWithStateVars(
            r.rule, initialStateVars = setOf(initialVar), acceptStateVars = emptySet()
        )
        val taintEdges = safeConvertToTaintRules {
            generateTaintAutomataEdges(automataWithVars, r.metaVarInfo)
        }
        listOfNotNull(taintEdges)
    }

    val rightCtx = rightEdges.rules.mapIndexed { idx, r ->
        val composition = object : TaintRuleGenerationCtx.CompositionStrategy {
            private val initialStateId = r.automata.stateId(r.automata.initial)

            override fun stateContains(
                state: TaintRegisterStateAutomata.State,
                varName: MetavarAtom, pos: PositionBaseWithModifiers
            ): SerializedCondition? {
                if (varName != initialVar) return null
                val value = state.register.assignedVars[varName]
                if (value != initialStateId) return null

                return serializedConditionOr(
                    leftFinalMarks.map {
                        it.mkContainsMark(pos)
                    }
                )
            }

            override fun stateAccessedMarks(
                state: TaintRegisterStateAutomata.State,
                varName: MetavarAtom
            ): Set<GeneratedMark>? {
                if (varName != initialVar) return null
                val value = state.register.assignedVars[varName]
                if (value != initialStateId) return null
                return leftFinalMarks
            }
        }

        TaintRuleGenerationCtx(RuleUniqueMarkPrefix(ruleId, idx), r, composition)
    }

    val rightRules = rightCtx.mapNotNull {
        safeConvertToTaintRules {
            val generatedRules = it.generateTaintRules(this)
            val filteredRules = generatedRules.filter { r ->
                if (r !is SinkRule) return@filter true
                if (r.condition != null && r.condition !is SerializedCondition.True) return@filter true

                trace.error("Join rule match anything", Reason.WARNING)
                false
            }
            TaintRuleFromSemgrep.TaintRuleGroup(filteredRules)
        }
    }

    return rightRules
}

private fun RuleConversionCtx.convertCompositionRightTaintRule(
    automata: SemgrepTaintRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
    @Suppress("UNUSED_PARAMETER") initialVar: MetavarAtom,
    leftFinalMarks: Set<GeneratedMark>,
): List<TaintRuleFromSemgrep.TaintRuleGroup>? {
    if (automata.sources.isNotEmpty()) {
        trace.error("Join on taint rule with non-empty sources", Reason.NOT_IMPLEMENTED)
        return null
    }

    // note: we always treat initial var as taint source
    return convertCompositionRightTaintRule(automata, leftFinalMarks)
}

private fun RuleConversionCtx.convertCompositionRightTaintRule(
    automata: SemgrepTaintRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
    sourceMarks: Set<GeneratedMark>,
): List<TaintRuleFromSemgrep.TaintRuleGroup> {
    val preparedRules = prepareTaintNonSourceRules(
        automata,
        sources = emptyList(),
        taintMarks = sourceMarks.mapTo(hashSetOf()) { GeneratedTaintMark(it) }
    )
    return convertTaintRuleToTaintRules(preparedRules, ignoreEmptySources = true).taintRules
}

private fun RuleConversionCtx.convertCompositionLeftTaintRule(
    automata: SemgrepTaintRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
    finalVar: MetavarAtom,
): Pair<List<TaintRuleFromSemgrep.TaintRuleGroup>, Set<GeneratedMark>>? {
    if (automata.sinks.isNotEmpty()) {
        trace.error("Left taint rule in join should not have sinks", Reason.ERROR)
        return null
    }

    if (automata.sources.isEmpty()) {
        trace.error("Left taint rule in join must have sources", Reason.ERROR)
        return null
    }

    if (finalVar !is MetavarAtom.Basic) {
        trace.error("Complex metavar in join", Reason.ERROR)
        return null
    }

    val finalLabels = automata.sources
        .mapNotNull { it.label }
        .filter { it.label == finalVar.name }

    if (finalLabels.isEmpty()) {
        trace.error("Join is impossible: no label ${finalVar.name} found", Reason.ERROR)
        return null
    }

    return convertCompositionLeftTaintRule(automata, finalLabels)
}

private fun RuleConversionCtx.convertCompositionLeftTaintRule(
    automata: SemgrepTaintRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
    finalLabels: List<SemgrepTaintLabel>,
): Pair<List<TaintRuleFromSemgrep.TaintRuleGroup>, Set<GeneratedMark>> {
    val (sources, taintMarks) = prepareTaintSourceRules(automata)

    val preparedRules = prepareTaintNonSourceRules(
        automata,
        sources = sources,
        taintMarks = taintMarks
    )

    val result = convertTaintRuleToTaintRules(preparedRules, ignoreEmptySources = false)

    val finalMarks = finalLabels.mapTo(hashSetOf()) { taintMark(it) }
    return result.taintRules to finalMarks
}
