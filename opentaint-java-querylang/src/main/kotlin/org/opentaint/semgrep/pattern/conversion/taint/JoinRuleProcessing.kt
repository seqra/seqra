package org.opentaint.semgrep.pattern.conversion.taint

import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SinkRule
import org.opentaint.org.opentaint.semgrep.pattern.Mark.Companion.markNamePrefix
import org.opentaint.semgrep.pattern.GeneratedTaintMark
import org.opentaint.semgrep.pattern.ResolvedMetaVarInfo
import org.opentaint.semgrep.pattern.RuleWithMetaVars
import org.opentaint.semgrep.pattern.SemgrepErrorEntry.Reason
import org.opentaint.semgrep.pattern.SemgrepJoinOnOperation
import org.opentaint.semgrep.pattern.SemgrepMatchingRule
import org.opentaint.semgrep.pattern.SemgrepRule
import org.opentaint.semgrep.pattern.SemgrepTaintRule
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import org.opentaint.semgrep.pattern.conversion.MetavarAtom

data class TaintAutomataJoinRule(
    val items: Map<String, TaintAutomataJoinRuleItem>,
    val operations: List<TaintAutomataJoinOperation>
)

data class TaintAutomataJoinRuleItem(
    val ruleFullId: String,
    val ruleShortId: String,
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
    if (rule.operations.size > 1) {
        trace.error("Join rule with multiple operations", Reason.NOT_IMPLEMENTED)
        return null
    }

    val operation = rule.operations.first()
    if (operation.op !== SemgrepJoinOnOperation.COMPOSE) {
        trace.error("Join rule with ${operation.op} operation", Reason.NOT_IMPLEMENTED)
        return null
    }

    return convertSingleCompositionJoinRule(rule, operation)
}

private fun RuleConversionCtx.convertSingleCompositionJoinRule(
    rule: TaintAutomataJoinRule,
    composition: TaintAutomataJoinOperation,
): TaintRuleFromSemgrep? {
    val leftItem = rule.items.getValue(composition.lhs.itemId)
    val leftAutomata = leftItem.rule

    if (leftAutomata !is SemgrepMatchingRule) {
        trace.error("Join on non-matching rule left", Reason.NOT_IMPLEMENTED)
        return null
    }

    val (leftRules, leftFinalMarks) = convertCompositionLeftMatchingRule(
        leftAutomata, leftItem, composition.lhs.metaVar
    )

    val rightItem = rule.items.getValue(composition.rhs.itemId)
    val rightAutomata = rightItem.rule
    val rightRules = when (rightAutomata) {
        is SemgrepMatchingRule -> convertCompositionRightMatchingRule(
            rightAutomata, composition.rhs.metaVar, leftFinalMarks
        )

        is SemgrepTaintRule -> convertCompositionRightTaintRule(
            rightAutomata, composition.rhs.metaVar, leftFinalMarks
        ) ?: return null
    }

    return TaintRuleFromSemgrep(fullRuleId, leftRules + rightRules)
}

private fun RuleConversionCtx.convertCompositionLeftMatchingRule(
    automata: SemgrepMatchingRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
    item: TaintAutomataJoinRuleItem, finalVar: MetavarAtom,
): Pair<List<TaintRuleFromSemgrep.TaintRuleGroup>, Set<String>> {
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
            markNamePrefix(item.ruleShortId, "$idx"),
            taintEdgesWithAssign, compositionStrategy = null
        )
    }

    val leftRules = leftCtx.mapNotNull {
        safeConvertToTaintRules {
            val generatedRules = it.generateTaintRules(this)
            TaintRuleFromSemgrep.TaintRuleGroup(generatedRules)
        }
    }

    val leftFinalMarks = hashSetOf<String>()
    leftCtx.forEach { ctx ->
        ctx.automata.finalAcceptStates.forEach { s ->
            ctx.stateAssignMark(finalVar, s, PositionBase.Result.base()).forEach { assign ->
                leftFinalMarks.add(assign.kind)
            }
        }
    }

    return leftRules to leftFinalMarks
}

private fun RuleConversionCtx.convertCompositionRightMatchingRule(
    automata: SemgrepMatchingRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
    initialVar: MetavarAtom,
    leftFinalMarks: Set<String>,
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
                    leftFinalMarks.map { SerializedCondition.ContainsMark(it, pos) }
                )
            }

            override fun stateAccessedMarks(
                state: TaintRegisterStateAutomata.State,
                varName: MetavarAtom
            ): Set<String>? {
                if (varName != initialVar) return null
                val value = state.register.assignedVars[varName]
                if (value != initialStateId) return null
                return leftFinalMarks
            }
        }

        TaintRuleGenerationCtx(markNamePrefix(shortRuleId, "$idx"), r, composition)
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
    leftFinalMarks: Set<String>,
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
    sourceMarks: Set<String>,
): List<TaintRuleFromSemgrep.TaintRuleGroup> {
    val preparedRules = prepareTaintNonSourceRules(
        automata,
        sources = emptyList(),
        taintMarks = sourceMarks.mapTo(hashSetOf()) { GeneratedTaintMark(it) }
    )
    return convertTaintRuleToTaintRules(preparedRules, ignoreEmptySources = true).taintRules
}
