package org.opentaint.semgrep.pattern.conversion.taint

import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SinkMetaData
import org.opentaint.dataflow.configuration.jvm.serialized.SinkRule
import org.opentaint.semgrep.pattern.ResolvedMetaVarInfo
import org.opentaint.semgrep.pattern.RuleWithMetaVars
import org.opentaint.semgrep.pattern.SemgrepErrorEntry.Reason
import org.opentaint.semgrep.pattern.SemgrepJoinOnOperation
import org.opentaint.semgrep.pattern.SemgrepMatchingRule
import org.opentaint.semgrep.pattern.SemgrepRule
import org.opentaint.semgrep.pattern.SemgrepRuleLoadStepTrace
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import org.opentaint.semgrep.pattern.conversion.MetavarAtom

data class TaintAutomataJoinRule(
    val items: Map<String, TaintAutomataJoinRuleItem>,
    val operations: List<TaintAutomataJoinOperation>
)

data class TaintAutomataJoinRuleItem(
    val ruleId: String,
    val rule: SemgrepRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
    val metaVarRename: List<TaintAutomataJoinRuleMetaVarRename>
)

data class TaintAutomataJoinRuleMetaVarRename(
    val from: MetavarAtom,
    val to: MetavarAtom
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

fun convertTaintAutomataJoinToTaintRules(
    rule: TaintAutomataJoinRule,
    ruleId: String,
    meta: SinkMetaData,
    trace: SemgrepRuleLoadStepTrace
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

    return RuleConversionCtx(ruleId, meta, trace).convertSingleCompositionJoinRule(rule, operation)
}

private fun RuleConversionCtx.convertSingleCompositionJoinRule(
    rule: TaintAutomataJoinRule,
    composition: TaintAutomataJoinOperation,
): TaintRuleFromSemgrep? {
    val leftItem = rule.items.getValue(composition.lhs.itemId)
    val rightItem = rule.items.getValue(composition.rhs.itemId)

    if (leftItem.metaVarRename.isNotEmpty() || rightItem.metaVarRename.isNotEmpty()) {
        trace.error("Join rule with metavar rename", Reason.NOT_IMPLEMENTED)
        return null
    }

    val leftAutomata = leftItem.rule
    val rightAutomata = rightItem.rule
    if (leftAutomata !is SemgrepMatchingRule || rightAutomata !is SemgrepMatchingRule) {
        trace.error("Join non-matching rules", Reason.NOT_IMPLEMENTED)
        return null
    }

    return convertMatchingRulesComposition(
        leftAutomata, leftItem.ruleId, composition.lhs.metaVar,
        rightAutomata, composition.rhs.metaVar
    )
}

private fun RuleConversionCtx.convertMatchingRulesComposition(
    leftAutomata: SemgrepMatchingRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
    leftId: String,
    leftFinalVar: MetavarAtom,
    rightAutomata: SemgrepMatchingRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
    rightInitialVar: MetavarAtom
): TaintRuleFromSemgrep {
    val leftEdges = leftAutomata.flatMap { r ->
        val automataWithVars = TaintRegisterStateAutomataWithStateVars(
            r.rule, initialStateVars = emptySet(), acceptStateVars = setOf(leftFinalVar)
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
        TaintRuleGenerationCtx("$leftId#$idx", taintEdgesWithAssign, compositionStrategy = null)
    }

    val leftRules = leftCtx.mapNotNull {
        safeConvertToTaintRules {
            val generatedRules = it.generateTaintRules(ruleId, meta, trace)
            TaintRuleFromSemgrep.TaintRuleGroup(generatedRules)
        }
    }

    val leftFinalMarks = hashSetOf<String>()
    leftCtx.forEach { ctx ->
        ctx.automata.finalAcceptStates.forEach { s ->
            ctx.stateAssignMark(leftFinalVar, s, PositionBase.Result.base()).forEach { assign ->
                leftFinalMarks.add(assign.kind)
            }
        }
    }

    val rightEdges = rightAutomata.flatMap { r ->
        val automataWithVars = TaintRegisterStateAutomataWithStateVars(
            r.rule, initialStateVars = setOf(rightInitialVar), acceptStateVars = emptySet()
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
                if (varName != rightInitialVar) return null
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
                if (varName != rightInitialVar) return null
                val value = state.register.assignedVars[varName]
                if (value != initialStateId) return null
                return leftFinalMarks
            }
        }

        TaintRuleGenerationCtx("$ruleId#$idx", r, composition)
    }

    val rightRules = rightCtx.mapNotNull {
        safeConvertToTaintRules {
            val generatedRules = it.generateTaintRules(ruleId, meta, trace)
            val filteredRules = generatedRules.filter { r ->
                if (r !is SinkRule) return@filter true
                if (r.condition != null && r.condition !is SerializedCondition.True) return@filter true

                trace.error("Join rule match anything", Reason.WARNING)
                false
            }
            TaintRuleFromSemgrep.TaintRuleGroup(filteredRules)
        }
    }

    return TaintRuleFromSemgrep(ruleId, leftRules + rightRules)
}
