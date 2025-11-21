package org.opentaint.org.opentaint.semgrep.pattern.conversion.automata

sealed interface AutomataEdgeType {
    sealed interface AutomataEdgeTypeWithFormula : AutomataEdgeType {
        val formula: MethodFormula
    }

    data class MethodEnter(override val formula: MethodFormula) : AutomataEdgeTypeWithFormula
    data class MethodCall(override val formula: MethodFormula) : AutomataEdgeTypeWithFormula
    data object End : AutomataEdgeType
    data object PatternStart : AutomataEdgeType
    data object PatternEnd : AutomataEdgeType
}
