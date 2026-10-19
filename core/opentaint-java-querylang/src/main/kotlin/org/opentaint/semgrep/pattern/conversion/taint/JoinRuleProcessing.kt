package org.opentaint.semgrep.pattern.conversion.taint

import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.semgrep.pattern.ComplexMetavarInJoin
import org.opentaint.semgrep.pattern.GeneratedTaintMark
import org.opentaint.semgrep.pattern.JoinIsImpossibleNoLabelFound
import org.opentaint.semgrep.pattern.JoinMetavarNotReferenced
import org.opentaint.semgrep.pattern.JoinOnTaintRuleWithNonEmptySources
import org.opentaint.semgrep.pattern.JoinRuleWithChainedOperations
import org.opentaint.semgrep.pattern.JoinRuleWithNoOperations
import org.opentaint.semgrep.pattern.JoinRuleWithUnsupportedOperation
import org.opentaint.semgrep.pattern.JoinSinkMetavarConflict
import org.opentaint.semgrep.pattern.LeftTaintRuleMustHaveSources
import org.opentaint.semgrep.pattern.LeftTaintRuleShouldNotHaveSinks
import org.opentaint.semgrep.pattern.Mark.GeneratedMark
import org.opentaint.semgrep.pattern.ResolvedMetaVarInfo
import org.opentaint.semgrep.pattern.RuleWithMetaVars
import org.opentaint.semgrep.pattern.SemgrepJoinOnOperation
import org.opentaint.semgrep.pattern.SemgrepMatchingRule
import org.opentaint.semgrep.pattern.SemgrepRule
import org.opentaint.semgrep.pattern.SemgrepSinkTaintRequirement
import org.opentaint.semgrep.pattern.SemgrepTaintLabel
import org.opentaint.semgrep.pattern.SemgrepTaintRule
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import org.opentaint.semgrep.pattern.conversion.LanguageStrategy.SinkDiscardMode
import org.opentaint.semgrep.pattern.conversion.MetavarAtom
import org.opentaint.semgrep.pattern.conversion.TaintRuleStrategy
import org.opentaint.semgrep.pattern.conversion.taint.composition.JoinRightCompositionStrategy

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

fun <Item, Cond, Assign, Clean> RuleConversionCtx.convertTaintAutomataJoinToTaintRules(
    strategy: TaintRuleStrategy<Item, Cond, Assign, Clean>,
    rule: TaintAutomataJoinRule
): TaintRuleFromSemgrep<Item>? {
    val nonComposeOp = rule.operations.find { it.op !== SemgrepJoinOnOperation.COMPOSE }
    if (nonComposeOp != null) {
        trace.error(JoinRuleWithUnsupportedOperation(nonComposeOp.op))
        return null
    }

    if (rule.operations.isEmpty()) {
        trace.error(JoinRuleWithNoOperations())
        return null
    }

    if (!validateNoChainedOperations(rule.operations)) {
        trace.error(JoinRuleWithChainedOperations())
        return null
    }

    val operationsBySink = rule.operations.groupBy { it.rhs.itemId }

    val branches = mutableListOf<TaintRuleFromSemgrep.JoinBranch<Item>>()
    for ((sinkItemId, sinkOps) in operationsBySink) {
        val rightItemRef = sinkOps.mapTo(linkedSetOf()) { it.rhs }.singleOrNull()
        if (rightItemRef == null) {
            trace.error(JoinSinkMetavarConflict(sinkItemId))
            return null
        }

        // Each sink converts under its own rule id so the marks generated for one sink cannot
        // collide with another's; a single-sink join keeps the original id.
        val sinkCtx =
            if (operationsBySink.size == 1) this
            else child("$ruleId#$sinkItemId")

        val converted = sinkCtx.convertCompositionJoinOperations(strategy, rule, rightItemRef, sinkOps.map { it.lhs })
            ?: return null
        branches += converted
    }

    return TaintRuleFromSemgrep(ruleId, TaintRuleFromSemgrep.Structure.Join(branches), markOwners())
}

private fun validateNoChainedOperations(operations: List<TaintAutomataJoinOperation>): Boolean {
    val leftItems = operations.map { it.lhs.itemId }.toSet()
    val rightItems = operations.map { it.rhs.itemId }.toSet()
    return leftItems.intersect(rightItems).isEmpty()
}

private fun <Item, Cond, Assign, Clean> RuleConversionCtx.convertCompositionJoinOperations(
    strategy: TaintRuleStrategy<Item, Cond, Assign, Clean>,
    rule: TaintAutomataJoinRule,
    rightItemRef: TaintAutomataJoinMetaVarRef,
    leftItemRefs: List<TaintAutomataJoinMetaVarRef>,
): TaintRuleFromSemgrep.JoinBranch<Item>? {
    val leftOperands = mutableListOf<TaintRuleFromSemgrep.JoinOperand<Item>>()
    val allLeftFinalMarks = hashSetOf<GeneratedMark>()

    for (leftItemRef in leftItemRefs) {
        val leftItem = rule.items.getValue(leftItemRef.itemId)
        val leftAutomata = leftItem.rule

        val leftCtx = child("$ruleId#${leftItemRef.itemId}")
        val (leftStructure, leftFinalMarks) = leftCtx.convertCompositionLeftRule(
            leftItemRef.itemId, strategy, leftAutomata, leftItemRef.metaVar
        ) ?: return null

        leftOperands += TaintRuleFromSemgrep.JoinOperand(
            leftItemRef.itemId, leftItem.ruleId, leftItemRef.metaVar.toString(), leftStructure
        )
        allLeftFinalMarks.addAll(leftFinalMarks)
    }

    val rightItem = rule.items.getValue(rightItemRef.itemId)
    val rightAutomata = rightItem.rule
    val rightRules = when (rightAutomata) {
        is SemgrepMatchingRule -> convertCompositionRightMatchingRule(
            rightItemRef.itemId, strategy, rightAutomata, rightItemRef.metaVar, allLeftFinalMarks
        )

        is SemgrepTaintRule -> convertCompositionRightTaintRule(
            rightItemRef.itemId, strategy, rightAutomata, rightItemRef.metaVar, allLeftFinalMarks
        ) ?: return null
    }

    val rightOperand = TaintRuleFromSemgrep.JoinOperand(
        rightItemRef.itemId, rightItem.ruleId, rightItemRef.metaVar.toString(), rightRules
    )
    return TaintRuleFromSemgrep.JoinBranch(leftOperands, rightOperand)
}

private fun <Item, Cond, Assign, Clean> RuleConversionCtx.convertCompositionLeftRule(
    itemId: String,
    strategy: TaintRuleStrategy<Item, Cond, Assign, Clean>,
    automata: SemgrepRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
    finalVar: MetavarAtom,
): Pair<TaintRuleFromSemgrep.Structure<Item>, Set<GeneratedMark>>? {
    return when (automata) {
        is SemgrepMatchingRule -> convertCompositionLeftMatchingRule(itemId, strategy, automata, finalVar)
        is SemgrepTaintRule -> convertCompositionLeftTaintRule(itemId, strategy, automata, finalVar)
    }
}

private fun <Item, Cond, Assign, Clean> RuleConversionCtx.convertCompositionLeftMatchingRule(
    itemId: String,
    strategy: TaintRuleStrategy<Item, Cond, Assign, Clean>,
    automata: SemgrepMatchingRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
    finalVar: MetavarAtom,
): Pair<TaintRuleFromSemgrep.Structure<Item>, Set<GeneratedMark>>? {
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
            markPrefix(idx),
            taintEdgesWithAssign,
            compositionStrategy = null,
            strategy,
        )
    }

    val leftRules = leftCtx.mapNotNull {
        safeConvertToTaintRules {
            val generatedRules = strategy.generateTaintRules(it, this, SinkDiscardMode.NONE)
            it.createRuleGroup(generatedRules)
        }
    }

    val leftFinalMarks = hashSetOf<GeneratedMark>()
    leftCtx.forEach { ctx ->
        ctx.automata.finalAcceptStates.forEach { s ->
            ctx.stateAssignMark(finalVar, s, PositionBase.Result.base()).forEach { assign ->
                leftFinalMarks.add(strategy.assignedMark(assign))
            }
        }
    }

    if (leftFinalMarks.isEmpty()) {
        trace.error(JoinMetavarNotReferenced(itemId, finalVar.toString()))
        return null
    }

    return TaintRuleFromSemgrep.Structure.Matching(leftRules) to leftFinalMarks
}

private fun <Item, Cond, Assign, Clean> RuleConversionCtx.convertCompositionRightMatchingRule(
    itemId: String,
    strategy: TaintRuleStrategy<Item, Cond, Assign, Clean>,
    automata: SemgrepMatchingRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
    initialVar: MetavarAtom,
    leftFinalMarks: Set<GeneratedMark>,
): TaintRuleFromSemgrep.Structure<Item> {
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
        composeRuleJoinRight(r, initialVar, leftFinalMarks, strategy, idx)
    }

    val rightRules = rightCtx.mapNotNull {
        safeConvertToTaintRules {
            val generatedRules = strategy.generateTaintRules(it, this, SinkDiscardMode.TRIVIAL_CONDITION)

            val joinVarReferenced = (it.compositionStrategy as JoinRightCompositionStrategy).joinVarReferenced
            if (!joinVarReferenced) {
                trace.error(JoinMetavarNotReferenced(itemId, initialVar.toString()))
                return@safeConvertToTaintRules null
            }

            it.createRuleGroup(generatedRules)
        }
    }

    return TaintRuleFromSemgrep.Structure.Matching(rightRules)
}

private fun <Item, Cond, Assign, Clean> RuleConversionCtx.composeRuleJoinRight(
    r: TaintAutomataEdges,
    initialVar: MetavarAtom,
    leftFinalMarks: Set<GeneratedMark>,
    taintStrategy: TaintRuleStrategy<Item, Cond, Assign, Clean>,
    idx: Int
): TaintRuleGenerationCtx<Item, Cond, Assign, Clean> {
    val composition = JoinRightCompositionStrategy(r, initialVar, leftFinalMarks, taintStrategy)
    return TaintRuleGenerationCtx(markPrefix(idx), r, composition, taintStrategy)
}

private fun <Item, Cond, Assign, Clean> RuleConversionCtx.convertCompositionRightTaintRule(
    itemId: String,
    strategy: TaintRuleStrategy<Item, Cond, Assign, Clean>,
    automata: SemgrepTaintRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
    initialVar: MetavarAtom,
    leftFinalMarks: Set<GeneratedMark>,
): TaintRuleFromSemgrep.Structure<Item>? {
    if (automata.sources.isNotEmpty()) {
        trace.error(JoinOnTaintRuleWithNonEmptySources())
        return null
    }

    val initialVarName = initialVar.toString()
    for (sink in automata.sinks) {
        val requirement = (sink.requires as? SemgrepSinkTaintRequirement.Simple)?.requirement
        if (requirement is SemgrepTaintLabel && requirement.label == initialVarName) continue

        val focus = sink.pattern.metaVarInfo.focusMetaVars
        if (focus.any { it == initialVarName }) continue

        trace.error(JoinMetavarNotReferenced(itemId, initialVarName))
        return null
    }

    // note: we always treat initial var as taint source
    return convertCompositionRightTaintRule(strategy, automata, leftFinalMarks)
}

private fun <Item, Cond, Assign, Clean> RuleConversionCtx.convertCompositionRightTaintRule(
    strategy: TaintRuleStrategy<Item, Cond, Assign, Clean>,
    automata: SemgrepTaintRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
    sourceMarks: Set<GeneratedMark>,
): TaintRuleFromSemgrep.Structure<Item> {
    val preparedRules = prepareTaintNonSourceRules(
        automata,
        sources = emptyList(),
        taintMarks = sourceMarks.mapTo(hashSetOf()) { GeneratedTaintMark(it) }
    )
    return convertTaintRuleToTaintRules(strategy, preparedRules, ignoreEmptySources = true).root
}

private fun <Item, Cond, Assign, Clean> RuleConversionCtx.convertCompositionLeftTaintRule(
    itemId: String,
    strategy: TaintRuleStrategy<Item, Cond, Assign, Clean>,
    automata: SemgrepTaintRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
    finalVar: MetavarAtom,
): Pair<TaintRuleFromSemgrep.Structure<Item>, Set<GeneratedMark>>? {
    if (automata.sinks.isNotEmpty()) {
        trace.error(LeftTaintRuleShouldNotHaveSinks())
        return null
    }

    if (automata.sources.isEmpty()) {
        trace.error(LeftTaintRuleMustHaveSources())
        return null
    }

    if (finalVar !is MetavarAtom.Basic) {
        trace.error(ComplexMetavarInJoin())
        return null
    }

    val finalLabels = automata.sources
        .mapNotNull { it.label }
        .filter { it.label == finalVar.name }

    if (finalLabels.isEmpty()) {
        trace.error(JoinIsImpossibleNoLabelFound(itemId, finalVar.name))
        return null
    }

    return convertCompositionLeftTaintRule(strategy, automata, finalLabels)
}

private fun <Item, Cond, Assign, Clean> RuleConversionCtx.convertCompositionLeftTaintRule(
    strategy: TaintRuleStrategy<Item, Cond, Assign, Clean>,
    automata: SemgrepTaintRule<RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>>,
    finalLabels: List<SemgrepTaintLabel>,
): Pair<TaintRuleFromSemgrep.Structure<Item>, Set<GeneratedMark>> {
    val (sources, taintMarks) = prepareTaintSourceRules(automata)

    val preparedRules = prepareTaintNonSourceRules(
        automata,
        sources = sources,
        taintMarks = taintMarks
    )

    val result = convertTaintRuleToTaintRules(strategy, preparedRules, ignoreEmptySources = false)

    val finalMarks = finalLabels.mapTo(hashSetOf()) { taintMark(it) }
    return result.root to finalMarks
}
