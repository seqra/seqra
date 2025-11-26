package org.opentaint.org.opentaint.semgrep.pattern.conversion.automata

import org.opentaint.org.opentaint.semgrep.pattern.ActionListSemgrepRule
import org.opentaint.org.opentaint.semgrep.pattern.ResolvedMetaVarInfo
import org.opentaint.org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction
import org.opentaint.org.opentaint.semgrep.pattern.conversion.SemgrepPatternActionList
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.operations.acceptIfCurrentAutomataAcceptsPrefix
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.operations.acceptIfCurrentAutomataAcceptsSuffix
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.operations.addDummyMethodEnter
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.operations.addEndEdges
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.operations.addPatternStartAndEnd
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.operations.addPatternStartAndEndOnEveryNode
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.operations.brzozowskiAlgorithm
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.operations.complement
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.operations.intersection
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.operations.removePatternStartAndEnd
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.operations.totalizeMethodCalls
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.operations.totalizeMethodEnters

fun transformSemgrepRuleToAutomata(rule: ActionListSemgrepRule, metaVarInfo: ResolvedMetaVarInfo): SemgrepRuleAutomata {
    val formulaManager = MethodFormulaManager()
    return transformSemgrepRuleToAutomata(formulaManager, metaVarInfo, rule)
}

private fun transformSemgrepRuleToAutomata(
    formulaManager: MethodFormulaManager,
    metaVarInfo: ResolvedMetaVarInfo,
    rule: ActionListSemgrepRule
): SemgrepRuleAutomata {
    val (newRule, startingAutomata) = buildStartingAutomata(formulaManager, rule)

    val resultNfa = transformSemgrepRuleToAutomata(formulaManager, metaVarInfo, newRule, startingAutomata)

    val resultDfa = brzozowskiAlgorithm(metaVarInfo, resultNfa)
    acceptIfCurrentAutomataAcceptsPrefix(resultDfa)

    val deadNode = totalizeMethodCalls(metaVarInfo, resultDfa)
    if (resultDfa.hasMethodEnter) {
        totalizeMethodEnters(metaVarInfo, resultDfa, deadNode)
    }

    return resultDfa
}

private fun buildStartingAutomata(
    formulaManager: MethodFormulaManager,
    rule: ActionListSemgrepRule,
): Pair<ActionListSemgrepRule, SemgrepRuleAutomata> {
    val startingPattern = rule.patterns.lastOrNull()
        ?: error("At least one positive pattern must be given")

    val automata = convertActionListToAutomata(formulaManager, startingPattern)
    val newRule = rule.modify(patterns = rule.patterns.dropLast(1))
    return newRule to automata
}

private fun transformSemgrepRuleToAutomata(
    formulaManager: MethodFormulaManager,
    metaVarInfo: ResolvedMetaVarInfo,
    rule: ActionListSemgrepRule,
    curAutomata: SemgrepRuleAutomata
): SemgrepRuleAutomata {
    if (rule.patterns.isNotEmpty()) {
        val newRule = rule.modify(patterns = rule.patterns.dropLast(1))
        val newAutomata = addPositivePattern(formulaManager, metaVarInfo, curAutomata, rule.patterns.last())
        return transformSemgrepRuleToAutomata(formulaManager, metaVarInfo, newRule, newAutomata)

    } else if (rule.patternNots.isNotEmpty()) {
        val newRule = rule.modify(patternNots = rule.patternNots.dropLast(1))
        val newAutomata = addNegativePattern(formulaManager, metaVarInfo, curAutomata, rule.patternNots.last())
        return transformSemgrepRuleToAutomata(formulaManager, metaVarInfo, newRule, newAutomata)

    } else if (rule.patternNotInsides.isNotEmpty() || rule.patternInsides.isNotEmpty()) {
        if (curAutomata.hasMethodEnter) {
            val newRule = ActionListSemgrepRule(
                patterns = rule.patternInsides,
                patternNots = rule.patternNotInsides,
                patternInsides = emptyList(),
                patternNotInsides = emptyList()
            )
            return transformSemgrepRuleToAutomata(formulaManager, metaVarInfo, newRule, curAutomata)
        }

        check(!curAutomata.hasEndEdges)

        val curAutomataWithBorders = addPatternStartAndEnd(curAutomata, metaVarInfo)
        val automatasWithPatternInsides = addPatternInsides(formulaManager, metaVarInfo, rule, curAutomataWithBorders)
        val automatasWithPatternNotInsides = addPatternNotInsides(formulaManager, metaVarInfo, rule, curAutomataWithBorders)
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
            brzozowskiAlgorithm(metaVarInfo, intersection(a1, a2, metaVarInfo))
        }

        removePatternStartAndEnd(result)

        return result

    } else {
        return curAutomata
    }
}

private fun addPatternNotInsides(
    formulaManager: MethodFormulaManager,
    metaVarInfo: ResolvedMetaVarInfo,
    rule: ActionListSemgrepRule,
    curAutomata: SemgrepRuleAutomata,
): List<SemgrepRuleAutomata> {
    if (rule.patternNotInsides.isEmpty()) {
        return emptyList()
    }

    val newRule = rule.modify(patternNotInsides = rule.patternNotInsides.dropLast(1))
    val newAutomata = addPatternNotInside(
        formulaManager, metaVarInfo, curAutomata.deepCopy(),
        rule.patternNotInsides.last()
    )

    return addPatternNotInsides(formulaManager, metaVarInfo, newRule, curAutomata) + newAutomata
}

private fun addPatternInsides(
    formulaManager: MethodFormulaManager,
    metaVarInfo: ResolvedMetaVarInfo,
    rule: ActionListSemgrepRule,
    curAutomata: SemgrepRuleAutomata,
): List<SemgrepRuleAutomata> {
    if (rule.patternInsides.isEmpty()) {
        return emptyList()
    }

    val newRule = rule.modify(patternInsides = rule.patternInsides.dropLast(1))
    val newAutomata = addPatternInside(
        formulaManager, metaVarInfo, curAutomata.deepCopy(),
        rule.patternInsides.last()
    )

    return addPatternInsides(formulaManager, metaVarInfo, newRule, curAutomata) + newAutomata
}

private fun addPositivePattern(
    formulaManager: MethodFormulaManager,
    metaVarInfo: ResolvedMetaVarInfo,
    curAutomata: SemgrepRuleAutomata,
    actionList: SemgrepPatternActionList,
): SemgrepRuleAutomata {
    val actionListAutomata = convertActionListToAutomata(formulaManager, actionList)
    return brzozowskiAlgorithm(metaVarInfo, intersection(curAutomata, actionListAutomata, metaVarInfo))
}

private fun addNegativePattern(
    formulaManager: MethodFormulaManager,
    metaVarInfo: ResolvedMetaVarInfo,
    curAutomata: SemgrepRuleAutomata,
    actionList: SemgrepPatternActionList,
): SemgrepRuleAutomata {
    val actionListAutomata = convertActionListToAutomata(formulaManager, actionList)
    if (actionListAutomata.hasMethodEnter != curAutomata.hasMethodEnter) {
        // they can never be matched simultaneously
        return curAutomata
    }

    val deadNode = totalizeMethodCalls(metaVarInfo, actionListAutomata)
    if (actionListAutomata.hasMethodEnter) {
        totalizeMethodEnters(metaVarInfo, actionListAutomata, deadNode)
        addEndEdges(actionListAutomata)
        addEndEdges(curAutomata)
    }
    complement(actionListAutomata)
    return brzozowskiAlgorithm(metaVarInfo, intersection(curAutomata, actionListAutomata, metaVarInfo))
}

private fun addPatternInside(
    formulaManager: MethodFormulaManager,
    metaVarInfo: ResolvedMetaVarInfo,
    curAutomata: SemgrepRuleAutomata,
    actionList: SemgrepPatternActionList,
): SemgrepRuleAutomata {
    if (curAutomata.hasMethodEnter) {
        return addPositivePattern(formulaManager, metaVarInfo, curAutomata, actionList)
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
    }

    val actionListAutomata = convertActionListToAutomata(formulaManager, actionList)
    addPatternStartAndEndOnEveryNode(actionListAutomata)
    return brzozowskiAlgorithm(metaVarInfo, intersection(actionListAutomata, curAutomata, metaVarInfo))
}

private fun addEllipsisInTheBeginning(actionList: SemgrepPatternActionList): SemgrepPatternActionList {
    check(actionList.actions.firstOrNull() !is SemgrepPatternAction.MethodSignature) {
        "Cannot add ellipsis in the beginning of action list with signature"
    }

    return SemgrepPatternActionList(
        actionList.actions,
        hasEllipsisInTheBeginning = true,
        hasEllipsisInTheEnd = actionList.hasEllipsisInTheEnd,
    )
}

private fun addPatternNotInside(
    formulaManager: MethodFormulaManager,
    metaVarInfo: ResolvedMetaVarInfo,
    curAutomata: SemgrepRuleAutomata,
    actionList: SemgrepPatternActionList,
): SemgrepRuleAutomata {
    if (curAutomata.hasMethodEnter) {
        return addNegativePattern(formulaManager, metaVarInfo, curAutomata, actionList)
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

    val deadNode = totalizeMethodCalls(metaVarInfo, automataNotInside)
    if (automataNotInside.hasMethodEnter) {
        totalizeMethodEnters(metaVarInfo, automataNotInside, deadNode)
    }
    complement(automataNotInside)

    return brzozowskiAlgorithm(metaVarInfo, intersection(mainAutomata, automataNotInside, metaVarInfo))
}
