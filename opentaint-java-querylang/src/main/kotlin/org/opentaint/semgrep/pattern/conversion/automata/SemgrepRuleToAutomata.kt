package org.opentaint.semgrep.pattern.conversion.automata

import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.OperationCancelation
import org.opentaint.semgrep.pattern.ActionListSemgrepRule
import org.opentaint.semgrep.pattern.ResolvedMetaVarInfo
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternActionList
import org.opentaint.semgrep.pattern.conversion.automata.operations.acceptIfCurrentAutomataAcceptsPrefix
import org.opentaint.semgrep.pattern.conversion.automata.operations.acceptIfCurrentAutomataAcceptsSuffix
import org.opentaint.semgrep.pattern.conversion.automata.operations.addDummyMethodEnter
import org.opentaint.semgrep.pattern.conversion.automata.operations.addEndEdges
import org.opentaint.semgrep.pattern.conversion.automata.operations.addPatternStartAndEnd
import org.opentaint.semgrep.pattern.conversion.automata.operations.addPatternStartAndEndOnEveryNode
import org.opentaint.semgrep.pattern.conversion.automata.operations.complement
import org.opentaint.semgrep.pattern.conversion.automata.operations.hopcroftAlgorithhm
import org.opentaint.semgrep.pattern.conversion.automata.operations.intersection
import org.opentaint.semgrep.pattern.conversion.automata.operations.removePatternStartAndEnd
import org.opentaint.semgrep.pattern.conversion.automata.operations.totalizeMethodCalls
import org.opentaint.semgrep.pattern.conversion.automata.operations.totalizeMethodEnters
import kotlin.time.Duration

fun transformSemgrepRuleToAutomata(
    rule: ActionListSemgrepRule,
    metaVarInfo: ResolvedMetaVarInfo,
    timeout: Duration
): SemgrepRuleAutomata {
    val formulaManager = MethodFormulaManager()
    val cancelation = OperationCancelation(timeout)
    val ctx = AutomataBuilderCtx(cancelation, formulaManager, metaVarInfo)
    return ctx.transformSemgrepRuleToAutomata(rule)
}

class AutomataBuilderCtx(
    val cancelation: OperationCancelation,
    val formulaManager: MethodFormulaManager,
    val metaVarInfo: ResolvedMetaVarInfo,
)

private fun AutomataBuilderCtx.transformSemgrepRuleToAutomata(
    rule: ActionListSemgrepRule
): SemgrepRuleAutomata {
    val (newRule, startingAutomata) = buildStartingAutomata(rule)

    val resultNfa = transformSemgrepRuleToAutomata(newRule, startingAutomata)

    val resultDfa = hopcroftAlgorithhm(resultNfa)
    acceptIfCurrentAutomataAcceptsPrefix(resultDfa)

    totalizeMethodCalls(resultDfa)
    if (resultDfa.hasMethodEnter) {
        totalizeMethodEnters(metaVarInfo, resultDfa)
    }

    return resultDfa
}

private fun AutomataBuilderCtx.buildStartingAutomata(
    rule: ActionListSemgrepRule,
): Pair<ActionListSemgrepRule, SemgrepRuleAutomata> {
    val startingPattern = rule.patterns.lastOrNull()
        ?: error("At least one positive pattern must be given")

    val automata = convertActionListToAutomata(formulaManager, startingPattern)
    val newRule = rule.modify(patterns = rule.patterns.dropLast(1))
    return newRule to automata
}

private fun AutomataBuilderCtx.transformSemgrepRuleToAutomata(
    rule: ActionListSemgrepRule,
    curAutomata: SemgrepRuleAutomata
): SemgrepRuleAutomata {
    if (rule.patterns.isNotEmpty()) {
        val newRule = rule.modify(patterns = rule.patterns.dropLast(1))
        val newAutomata = addPositivePattern(curAutomata, rule.patterns.last())
        return transformSemgrepRuleToAutomata(newRule, newAutomata)

    } else if (rule.patternNots.isNotEmpty()) {
        val newRule = rule.modify(patternNots = rule.patternNots.dropLast(1))
        val newAutomata = addNegativePattern(curAutomata, rule.patternNots.last())
        return transformSemgrepRuleToAutomata(newRule, newAutomata)

    } else if (rule.patternNotInsides.isNotEmpty() || rule.patternInsides.isNotEmpty()) {
        if (curAutomata.hasMethodEnter) {
            val newRule = ActionListSemgrepRule(
                patterns = rule.patternInsides,
                patternNots = rule.patternNotInsides,
                patternInsides = emptyList(),
                patternNotInsides = emptyList()
            )
            return transformSemgrepRuleToAutomata(newRule, curAutomata)
        }

        check(!curAutomata.hasEndEdges)

        val curAutomataWithBorders = addPatternStartAndEnd(curAutomata)
        val automatasWithPatternInsides = addPatternInsides(rule, curAutomataWithBorders)
        val automatasWithPatternNotInsides = addPatternNotInsides(rule, curAutomataWithBorders)
        val automatas = automatasWithPatternInsides + automatasWithPatternNotInsides
        automatas.forEach  {
            if (!it.hasMethodEnter) {
                acceptIfCurrentAutomataAcceptsSuffix(it)
            }
            acceptIfCurrentAutomataAcceptsPrefix(it)
            if (!it.hasEndEdges) {
                addEndEdges(it)
            }
        }
        val result = automatas.reduce { acc, automata ->
            var a1 = acc
            var a2 = automata

            if (a1.hasMethodEnter && !a2.hasMethodEnter) {
                a2 = addDummyMethodEnter(a2)
            }

            if (!a1.hasMethodEnter && a2.hasMethodEnter) {
                a1 = addDummyMethodEnter(a1)
            }

            val a1a2 = intersection(a1, a2)
            hopcroftAlgorithhm(a1a2)
        }

        removePatternStartAndEnd(result)

        return result

    } else {
        return curAutomata
    }
}

private fun AutomataBuilderCtx.addPatternNotInsides(
    rule: ActionListSemgrepRule,
    curAutomata: SemgrepRuleAutomata,
): List<SemgrepRuleAutomata> {
    if (rule.patternNotInsides.isEmpty()) {
        return emptyList()
    }

    val newRule = rule.modify(patternNotInsides = rule.patternNotInsides.dropLast(1))
    val newAutomata = addPatternNotInside(
        curAutomata.deepCopy(), rule.patternNotInsides.last()
    )

    return addPatternNotInsides(newRule, curAutomata) + newAutomata
}

private fun AutomataBuilderCtx.addPatternInsides(
    rule: ActionListSemgrepRule,
    curAutomata: SemgrepRuleAutomata,
): List<SemgrepRuleAutomata> {
    if (rule.patternInsides.isEmpty()) {
        return emptyList()
    }

    val newRule = rule.modify(patternInsides = rule.patternInsides.dropLast(1))
    val newAutomata = addPatternInside(
        curAutomata.deepCopy(), rule.patternInsides.last()
    )

    return addPatternInsides(newRule, curAutomata) + newAutomata
}

private fun AutomataBuilderCtx.addPositivePattern(
    curAutomata: SemgrepRuleAutomata,
    actionList: SemgrepPatternActionList,
): SemgrepRuleAutomata {
    val actionListAutomata = convertActionListToAutomata(formulaManager, actionList)
    val automataIntersection = intersection(curAutomata, actionListAutomata)
    return hopcroftAlgorithhm(automataIntersection)
}

private fun AutomataBuilderCtx.addNegativePattern(
    curAutomata: SemgrepRuleAutomata,
    actionList: SemgrepPatternActionList,
): SemgrepRuleAutomata {
    val actionListAutomata = convertActionListToAutomata(formulaManager, actionList)
    if (actionListAutomata.hasMethodEnter != curAutomata.hasMethodEnter) {
        // they can never be matched simultaneously
        return curAutomata
    }

    totalizeMethodCalls(actionListAutomata)
    if (actionListAutomata.hasMethodEnter) {
        totalizeMethodEnters(metaVarInfo, actionListAutomata,)
        addEndEdges(actionListAutomata)
        addEndEdges(curAutomata)
    }
    complement(actionListAutomata)

    val intersect = intersection(
        curAutomata,
        actionListAutomata
    )

    return hopcroftAlgorithhm(intersect)
}

private fun AutomataBuilderCtx.addPatternInside(
    curAutomata: SemgrepRuleAutomata,
    actionList: SemgrepPatternActionList,
): SemgrepRuleAutomata {
    if (curAutomata.hasMethodEnter) {
        return addPositivePattern(curAutomata, actionList)
    }

    val addPrefixEllipsis = actionList.actions.firstOrNull() is SemgrepPatternAction.MethodSignature ||
            actionList.hasEllipsisInTheEnd || !actionList.hasEllipsisInTheBeginning

    val addSuffixEllipsis = actionList.actions.firstOrNull() is SemgrepPatternAction.MethodSignature ||
            actionList.hasEllipsisInTheBeginning || !actionList.hasEllipsisInTheEnd

    if (addSuffixEllipsis) {
        acceptIfCurrentAutomataAcceptsPrefix(curAutomata)
    }

    if (addPrefixEllipsis) {
        acceptIfCurrentAutomataAcceptsSuffix(curAutomata)
        curAutomata.initialNode.outEdges.add(AutomataEdgeType.MethodEnter(MethodFormula.True) to curAutomata.initialNode)
        curAutomata.hasMethodEnter = true
    }

    val actionListAutomata = convertActionListToAutomata(formulaManager, actionList)
    addPatternStartAndEndOnEveryNode(actionListAutomata)
    val automataIntersection = intersection(actionListAutomata, curAutomata)
    return hopcroftAlgorithhm(automataIntersection)
}

private fun AutomataBuilderCtx.addEllipsisInTheBeginning(actionList: SemgrepPatternActionList): SemgrepPatternActionList {
    check(actionList.actions.firstOrNull() !is SemgrepPatternAction.MethodSignature) {
        "Cannot add ellipsis in the beginning of action list with signature"
    }

    return SemgrepPatternActionList(
        actionList.actions,
        hasEllipsisInTheBeginning = true,
        hasEllipsisInTheEnd = actionList.hasEllipsisInTheEnd,
    )
}

private fun AutomataBuilderCtx.addPatternNotInside(
    curAutomata: SemgrepRuleAutomata,
    actionList: SemgrepPatternActionList,
): SemgrepRuleAutomata {
    if (curAutomata.hasMethodEnter) {
        return addNegativePattern(curAutomata, actionList)
    }

    val addPrefixEllipsis = actionList.actions.firstOrNull() is SemgrepPatternAction.MethodSignature ||
        actionList.hasEllipsisInTheEnd || !actionList.hasEllipsisInTheBeginning
    val addSuffixEllipsis = actionList.actions.firstOrNull() is SemgrepPatternAction.MethodSignature ||
        actionList.hasEllipsisInTheBeginning || !actionList.hasEllipsisInTheEnd

    val actionListForAutomata = if (actionList.actions.firstOrNull() is SemgrepPatternAction.MethodSignature || !addPrefixEllipsis) {
        actionList
    } else {
        // because we will add MethodEnter. Do this here to avoid extra determinization
        addEllipsisInTheBeginning(actionList)
    }
    val actionListAutomata = convertActionListToAutomata(formulaManager, actionListForAutomata)
    addPatternStartAndEndOnEveryNode(actionListAutomata)

    var mainAutomata = curAutomata
    var automataNotInside = actionListAutomata

    if (addPrefixEllipsis) {
        acceptIfCurrentAutomataAcceptsSuffix(curAutomata)
        mainAutomata = addDummyMethodEnter(curAutomata)
        if (!actionListAutomata.hasMethodEnter) {
            automataNotInside = addDummyMethodEnter(actionListAutomata)
        }
    }

    if (addSuffixEllipsis) {
        acceptIfCurrentAutomataAcceptsPrefix(curAutomata)
        addEndEdges(curAutomata)

        acceptIfCurrentAutomataAcceptsPrefix(automataNotInside)
        addEndEdges(automataNotInside)
    }

    totalizeMethodCalls(automataNotInside)
    if (automataNotInside.hasMethodEnter) {
        totalizeMethodEnters(metaVarInfo, automataNotInside)
    }
    complement(automataNotInside)

    return hopcroftAlgorithhm(
        intersection(
            mainAutomata,
            automataNotInside
        )
    )
}
