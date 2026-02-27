package org.opentaint.semgrep.pattern.conversion.automata

import org.opentaint.semgrep.pattern.ActionListSemgrepRule
import org.opentaint.semgrep.pattern.ResolvedMetaVarInfo
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternActionList
import org.opentaint.semgrep.pattern.conversion.automata.operations.acceptIfCurrentAutomataAcceptsPrefix
import org.opentaint.semgrep.pattern.conversion.automata.operations.acceptIfCurrentAutomataAcceptsSuffix
import org.opentaint.semgrep.pattern.conversion.automata.operations.addDummyMethodEnter
import org.opentaint.semgrep.pattern.conversion.automata.operations.addEndEdges
import org.opentaint.semgrep.pattern.conversion.automata.operations.addMethodEntryLoop
import org.opentaint.semgrep.pattern.conversion.automata.operations.addPatternStartAndEnd
import org.opentaint.semgrep.pattern.conversion.automata.operations.addPatternStartAndEndOnEveryNode
import org.opentaint.semgrep.pattern.conversion.automata.operations.complement
import org.opentaint.semgrep.pattern.conversion.automata.operations.hopcroftAlgorithhm
import org.opentaint.semgrep.pattern.conversion.automata.operations.intersection
import org.opentaint.semgrep.pattern.conversion.automata.operations.removePatternStartAndEnd
import org.opentaint.semgrep.pattern.conversion.automata.operations.totalizeAutomata
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

    val resultAutomata = hopcroftAlgorithhm(resultNfa)
    totalizeAutomata(resultAutomata)

    return resultAutomata
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
    initialAutomata: SemgrepRuleAutomata
): SemgrepRuleAutomata {
    var curAutomata = rule.patterns.fold(initialAutomata) { automata, pattern ->
        addPositivePattern(automata, pattern)
    }

    curAutomata = rule.patternNots.fold(curAutomata) { automata, pattern ->
        addNegativePattern(automata, pattern)
    }

    if (rule.patternNotInsides.isEmpty() && rule.patternInsides.isEmpty()) {
        return curAutomata
    }

    if (curAutomata.params.hasMethodEnter) {
        return addInsidePatternsWithMethodEnter(curAutomata, rule.patternInsides, rule.patternNotInsides)
    }

    check(!curAutomata.params.hasEndEdges) {
        "Automata without method enter contains end edges"
    }

    return addInsidePatterns(curAutomata, rule.patternInsides, rule.patternNotInsides)
}

private fun AutomataBuilderCtx.addPositivePattern(
    curAutomata: SemgrepRuleAutomata,
    actionList: SemgrepPatternActionList,
): SemgrepRuleAutomata {
    val actionListAutomata = convertActionListToAutomata(formulaManager, actionList)
    return addPositiveAutomata(curAutomata, actionListAutomata)
}

private fun AutomataBuilderCtx.addPositiveAutomata(
    curAutomata: SemgrepRuleAutomata,
    actionListAutomata: SemgrepRuleAutomata,
): SemgrepRuleAutomata {
    val automataIntersection = intersection(curAutomata, actionListAutomata)
    return hopcroftAlgorithhm(automataIntersection)
}

private fun AutomataBuilderCtx.addNegativePattern(
    curAutomata: SemgrepRuleAutomata,
    actionList: SemgrepPatternActionList,
): SemgrepRuleAutomata {
    val actionListAutomata = convertActionListToAutomata(formulaManager, actionList)
    return addNegativeAutomata(curAutomata, actionListAutomata)
}

private fun AutomataBuilderCtx.addNegativeAutomata(
    curAutomata: SemgrepRuleAutomata,
    actionListAutomata: SemgrepRuleAutomata,
): SemgrepRuleAutomata {
    if (curAutomata.params.hasMethodEnter && !actionListAutomata.params.hasMethodEnter) {
        // they can never be matched simultaneously
        return curAutomata
    }

    totalizeAutomata(actionListAutomata, keepTrivialEdges = true)

    if (actionListAutomata.params.hasMethodEnter) {
        /**
         * If automata has method enter then we have signature pattern like
         * $RET $FUN($ARGS) {
         *    ...
         * }
         * Due to the closing `}` we must add end edge
         */

        addEndEdges(actionListAutomata)
        addEndEdges(curAutomata)
    }

    complement(actionListAutomata)

    val intersect = intersection(curAutomata, actionListAutomata)
    return hopcroftAlgorithhm(intersect)
}

private fun AutomataBuilderCtx.addInsidePatternsWithMethodEnter(
    initialAutomata: SemgrepRuleAutomata,
    patternInsides: List<SemgrepPatternActionList>,
    patternNotInsides: List<SemgrepPatternActionList>
): SemgrepRuleAutomata {
    val patternInsideAutomatas = patternInsides.map { convertActionListToAutomata(formulaManager, it) }
    val patternNotInsideAutomatas = patternNotInsides.map { convertActionListToAutomata(formulaManager, it) }

    var curAutomata = patternInsideAutomatas.fold(initialAutomata) { automata, patternAutomata ->
        var nextAutomata = automata
        if (automata.params.hasMethodEnter != patternAutomata.params.hasMethodEnter) {
            if (patternAutomata.params.hasMethodEnter) {
                // pattern without method enter INSIDE pattern with method enter
                nextAutomata = addDummyMethodEnter(automata)
            }
        }
        addPositiveAutomata(nextAutomata, patternAutomata)
    }

    curAutomata = patternNotInsideAutomatas.fold(curAutomata) { automata, patternAutomata ->
        var nextAutomata = automata
        if (automata.params.hasMethodEnter != patternAutomata.params.hasMethodEnter) {
            if (patternAutomata.params.hasMethodEnter) {
                // pattern without method enter INSIDE pattern with method enter
                nextAutomata = addDummyMethodEnter(automata)
            }
        }
        addNegativeAutomata(nextAutomata, patternAutomata)
    }

    return curAutomata
}

private fun AutomataBuilderCtx.addInsidePatterns(
    curAutomata: SemgrepRuleAutomata,
    patternInsides: List<SemgrepPatternActionList>,
    patternNotInsides: List<SemgrepPatternActionList>
): SemgrepRuleAutomata {
    val curAutomataWithBorders = addPatternStartAndEnd(curAutomata)

    val automatasWithPatternInsides = patternInsides.map { pattern ->
        addPatternInside(curAutomataWithBorders.deepCopy(), pattern)
    }

    val automatasWithPatternNotInsides = patternNotInsides.map { pattern ->
        addPatternNotInside(curAutomataWithBorders.deepCopy(), pattern)
    }

    val automatas = automatasWithPatternInsides + automatasWithPatternNotInsides

    automatas.forEach {
        if (!it.params.hasMethodEnter) {
            acceptIfCurrentAutomataAcceptsSuffix(it)
        }

        acceptIfCurrentAutomataAcceptsPrefix(it)
        if (!it.params.hasEndEdges) {
            addEndEdges(it)
        }
    }

    val result = automatas.reduce { acc, automata ->
        var a1 = acc
        var a2 = automata

        if (a1.params.hasMethodEnter && !a2.params.hasMethodEnter) {
            a2 = addDummyMethodEnter(a2)
        }

        if (!a1.params.hasMethodEnter && a2.params.hasMethodEnter) {
            a1 = addDummyMethodEnter(a1)
        }

        val a1a2 = intersection(a1, a2)
        hopcroftAlgorithhm(a1a2)
    }

    removePatternStartAndEnd(result)

    return result
}

private fun AutomataBuilderCtx.addPatternInside(
    initialCurAutomata: SemgrepRuleAutomata,
    actionList: SemgrepPatternActionList,
): SemgrepRuleAutomata {
    var curAutomata = initialCurAutomata
    check(!curAutomata.params.hasMethodEnter) {
        "Pattern with method enter is not expected here"
    }

    val patternContainsMethodSignature = actionList.actions.firstOrNull() is SemgrepPatternAction.MethodSignature

    val addPrefixEllipsis = patternContainsMethodSignature || actionList.hasEllipsisInTheEnd || !actionList.hasEllipsisInTheBeginning
    val addSuffixEllipsis = patternContainsMethodSignature || actionList.hasEllipsisInTheBeginning || !actionList.hasEllipsisInTheEnd

    if (addSuffixEllipsis) {
        acceptIfCurrentAutomataAcceptsPrefix(curAutomata)
    }

    if (addPrefixEllipsis) {
        acceptIfCurrentAutomataAcceptsSuffix(curAutomata)
        curAutomata = addMethodEntryLoop(curAutomata)
    }

    val actionListAutomata = convertActionListToAutomata(formulaManager, actionList)
    addPatternStartAndEndOnEveryNode(actionListAutomata)

    val automataIntersection = intersection(actionListAutomata, curAutomata)
    return hopcroftAlgorithhm(automataIntersection)
}

private fun AutomataBuilderCtx.addPatternNotInside(
    initialCurAutomata: SemgrepRuleAutomata,
    actionList: SemgrepPatternActionList,
): SemgrepRuleAutomata {
    var curAutomata = initialCurAutomata
    check(!curAutomata.params.hasMethodEnter) {
        "Pattern with method enter is not expected here"
    }

    val patternContainsMethodSignature = actionList.actions.firstOrNull() is SemgrepPatternAction.MethodSignature

    val addPrefixEllipsis = patternContainsMethodSignature || actionList.hasEllipsisInTheEnd || !actionList.hasEllipsisInTheBeginning
    val addSuffixEllipsis = patternContainsMethodSignature || actionList.hasEllipsisInTheBeginning || !actionList.hasEllipsisInTheEnd

    var actionListForAutomata = actionList
    if (!patternContainsMethodSignature && addPrefixEllipsis) {
        // because we will add MethodEnter. Do this here to avoid extra determinization
        actionListForAutomata = addEllipsisInTheBeginning(actionListForAutomata)
    }
    var actionListAutomata = convertActionListToAutomata(formulaManager, actionListForAutomata)

    if (addPrefixEllipsis) {
        acceptIfCurrentAutomataAcceptsSuffix(curAutomata)
        curAutomata = addMethodEntryLoop(curAutomata)

        if (!actionListAutomata.params.hasMethodEnter) {
            actionListAutomata = addMethodEntryLoop(actionListAutomata)
        }
    }

    if (addSuffixEllipsis) {
        acceptIfCurrentAutomataAcceptsPrefix(curAutomata)
        addEndEdges(curAutomata)

        acceptIfCurrentAutomataAcceptsPrefix(actionListAutomata)
        addEndEdges(actionListAutomata)
    }

    totalizeAutomata(actionListAutomata, keepTrivialEdges = true)

    addPatternStartAndEndOnEveryNode(actionListAutomata)

    complement(actionListAutomata)

    val resultAutomata = intersection(curAutomata, actionListAutomata)
    return hopcroftAlgorithhm(resultAutomata)
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
