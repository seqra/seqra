package org.opentaint.org.opentaint.semgrep.pattern.conversion.automata

import org.opentaint.org.opentaint.semgrep.pattern.ActionListSemgrepRule
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

fun transformSemgrepRuleToAutomata(rule: ActionListSemgrepRule): SemgrepRuleAutomata {
    val formulaManager = MethodFormulaManager()

    val (newRule, startingAutomata) = buildStartingAutomata(formulaManager, rule)

    val resultNfa = transformSemgrepRuleToAutomata(formulaManager, newRule, startingAutomata)

    val resultDfa = brzozowskiAlgorithm(resultNfa)
    acceptIfCurrentAutomataAcceptsPrefix(resultDfa)

    val deadNode = totalizeMethodCalls(resultDfa)
    if (resultDfa.hasMethodEnter) {
        totalizeMethodEnters(resultDfa, deadNode)
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
    rule: ActionListSemgrepRule,
    curAutomata: SemgrepRuleAutomata
): SemgrepRuleAutomata {
    if (rule.patterns.isNotEmpty()) {
        val newRule = rule.modify(patterns = rule.patterns.dropLast(1))
        val newAutomata = addPositivePattern(formulaManager, curAutomata, rule.patterns.last())
        return transformSemgrepRuleToAutomata(formulaManager, newRule, newAutomata)

    } else if (rule.patternNots.isNotEmpty()) {
        val newRule = rule.modify(patternNots = rule.patternNots.dropLast(1))
        val newAutomata = addNegativePattern(formulaManager, curAutomata, rule.patternNots.last())
        return transformSemgrepRuleToAutomata(formulaManager, newRule, newAutomata)

    } else if (rule.patternNotInsides.isNotEmpty() || rule.patternInsides.isNotEmpty()) {
        if (curAutomata.hasMethodEnter) {
            val newRule = ActionListSemgrepRule(
                patterns = rule.patternInsides,
                patternNots = rule.patternNotInsides,
                patternInsides = emptyList(),
                patternNotInsides = emptyList()
            )
            return transformSemgrepRuleToAutomata(formulaManager, newRule, curAutomata)
        }

        check(!curAutomata.hasEndEdges)

        val curAutomataWithBorders = addPatternStartAndEnd(curAutomata)
        val automatasWithPatternInsides = addPatternInsides(formulaManager, rule, curAutomataWithBorders)
        val automatasWithPatternNotInsides = addPatternNotInsides(formulaManager, rule, curAutomataWithBorders)
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
            brzozowskiAlgorithm(intersection(a1, a2))
        }

        removePatternStartAndEnd(result)

        return result

    } else {
        return curAutomata
    }
}

private fun addPatternNotInsides(
    formulaManager: MethodFormulaManager,
    rule: ActionListSemgrepRule,
    curAutomata: SemgrepRuleAutomata,
): List<SemgrepRuleAutomata> {
    if (rule.patternNotInsides.isEmpty()) {
        return emptyList()
    }

    val newRule = rule.modify(patternNotInsides = rule.patternNotInsides.dropLast(1))
    val newAutomata = addPatternNotInside(formulaManager, curAutomata.deepCopy(), rule.patternNotInsides.last())

    return addPatternNotInsides(formulaManager, newRule, curAutomata) + newAutomata
}

private fun addPatternInsides(
    formulaManager: MethodFormulaManager,
    rule: ActionListSemgrepRule,
    curAutomata: SemgrepRuleAutomata,
): List<SemgrepRuleAutomata> {
    if (rule.patternInsides.isEmpty()) {
        return emptyList()
    }

    val newRule = rule.modify(patternInsides = rule.patternInsides.dropLast(1))
    val newAutomata = addPatternInside(formulaManager, curAutomata.deepCopy(), rule.patternInsides.last())

    return addPatternInsides(formulaManager, newRule, curAutomata) + newAutomata
}

private fun addPositivePattern(
    formulaManager: MethodFormulaManager,
    curAutomata: SemgrepRuleAutomata,
    actionList: SemgrepPatternActionList,
): SemgrepRuleAutomata {
    val actionListAutomata = convertActionListToAutomata(formulaManager, actionList)
    return brzozowskiAlgorithm(intersection(curAutomata, actionListAutomata))
}

private fun addNegativePattern(
    formulaManager: MethodFormulaManager,
    curAutomata: SemgrepRuleAutomata,
    actionList: SemgrepPatternActionList,
): SemgrepRuleAutomata {
    val actionListAutomata = convertActionListToAutomata(formulaManager, actionList)
    if (actionListAutomata.hasMethodEnter != curAutomata.hasMethodEnter) {
        // they can never be matched simultaneously
        return curAutomata
    }

    val deadNode = totalizeMethodCalls(actionListAutomata)
    if (actionListAutomata.hasMethodEnter) {
        totalizeMethodEnters(actionListAutomata, deadNode)
        addEndEdges(actionListAutomata)
        addEndEdges(curAutomata)
    }
    complement(actionListAutomata)
    return brzozowskiAlgorithm(intersection(curAutomata, actionListAutomata))
}

private fun addPatternInside(
    formulaManager: MethodFormulaManager,
    curAutomata: SemgrepRuleAutomata,
    actionList: SemgrepPatternActionList,
): SemgrepRuleAutomata {
    if (curAutomata.hasMethodEnter) {
        return addPositivePattern(formulaManager, curAutomata, actionList)
    }

    val addPrefixEllipsis = actionList.actions.first() is SemgrepPatternAction.MethodSignature ||
            actionList.hasEllipsisInTheEnd || !actionList.hasEllipsisInTheBeginning
    val addSuffixEllipsis = actionList.actions.first() is SemgrepPatternAction.MethodSignature ||
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
    return brzozowskiAlgorithm(intersection(actionListAutomata, curAutomata))
}

private fun addEllipsisInTheBeginning(actionList: SemgrepPatternActionList): SemgrepPatternActionList {
    check(actionList.actions.first() !is SemgrepPatternAction.MethodSignature) {
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
    curAutomata: SemgrepRuleAutomata,
    actionList: SemgrepPatternActionList,
): SemgrepRuleAutomata {
    if (curAutomata.hasMethodEnter) {
        return addNegativePattern(formulaManager, curAutomata, actionList)
    }

    val addPrefixEllipsis = actionList.actions.first() is SemgrepPatternAction.MethodSignature ||
        actionList.hasEllipsisInTheEnd || !actionList.hasEllipsisInTheBeginning
    val addSuffixEllipsis = actionList.actions.first() is SemgrepPatternAction.MethodSignature ||
        actionList.hasEllipsisInTheBeginning || !actionList.hasEllipsisInTheEnd

    val actionListForAutomata = if (actionList.actions.first() is SemgrepPatternAction.MethodSignature || !addPrefixEllipsis) {
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

    val deadNode = totalizeMethodCalls(automataNotInside)
    if (automataNotInside.hasMethodEnter) {
        totalizeMethodEnters(automataNotInside, deadNode)
    }
    complement(automataNotInside)

    return brzozowskiAlgorithm(intersection(mainAutomata, automataNotInside))
}
