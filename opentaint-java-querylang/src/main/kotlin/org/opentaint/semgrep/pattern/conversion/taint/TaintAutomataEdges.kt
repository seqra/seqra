package org.opentaint.semgrep.pattern.conversion.taint

import org.opentaint.semgrep.pattern.MetaVarConstraints

data class TaintRuleEdge(
    val stateFrom: TaintRegisterStateAutomata.State,
    val stateTo: TaintRegisterStateAutomata.State,
    val checkGlobalState: Boolean,
    val edgeCondition: TaintRegisterStateAutomata.EdgeCondition,
    val edgeEffect: TaintRegisterStateAutomata.EdgeEffect,
    val edgeKind: Kind,
) {
    enum class Kind {
        MethodEnter,
        MethodCall,
        MethodExit,
    }
}

sealed interface MetaVarConstraintOrPlaceHolder {
    data class Constraint(val constraint: MetaVarConstraints) : MetaVarConstraintOrPlaceHolder
    data class PlaceHolder(val constraint: MetaVarConstraints?) : MetaVarConstraintOrPlaceHolder
}

data class TaintRuleGenerationMetaVarInfo(
    val constraints: Map<String, MetaVarConstraintOrPlaceHolder>
)

data class TaintAutomataEdges(
    val automata: TaintRegisterStateAutomata,
    val metaVarInfo: TaintRuleGenerationMetaVarInfo,
    val globalStateAssignStates: Set<TaintRegisterStateAutomata.State>,
    val edges: List<TaintRuleEdge>,
    val edgesToFinalAccept: List<TaintRuleEdge>,
    val edgesToFinalDead: List<TaintRuleEdge>,
)
