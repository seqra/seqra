package org.opentaint.semgrep.pattern.conversion.taint

import org.opentaint.dataflow.util.PersistentBitSet
import org.opentaint.dataflow.util.PersistentBitSet.Companion.emptyPersistentBitSet
import org.opentaint.dataflow.util.forEach
import org.opentaint.semgrep.pattern.ResolvedMetaVarInfo
import org.opentaint.semgrep.pattern.SemgrepErrorEntry.Reason
import org.opentaint.semgrep.pattern.conversion.IsMetavar
import org.opentaint.semgrep.pattern.conversion.ParamCondition
import org.opentaint.semgrep.pattern.conversion.ParamCondition.StringValueMetaVar
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureModifier
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureModifierValue
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureName
import org.opentaint.semgrep.pattern.conversion.SpecificBoolValue
import org.opentaint.semgrep.pattern.conversion.SpecificStringValue
import org.opentaint.semgrep.pattern.conversion.TypeNamePattern
import org.opentaint.semgrep.pattern.conversion.automata.ClassModifierConstraint
import org.opentaint.semgrep.pattern.conversion.automata.MethodConstraint
import org.opentaint.semgrep.pattern.conversion.automata.MethodModifierConstraint
import org.opentaint.semgrep.pattern.conversion.automata.MethodSignature
import org.opentaint.semgrep.pattern.conversion.automata.NumberOfArgsConstraint
import org.opentaint.semgrep.pattern.conversion.automata.ParamConstraint
import org.opentaint.semgrep.pattern.conversion.automata.Predicate
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.Edge
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeCondition
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeEffect
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.State
import java.util.BitSet

fun RuleConversionCtx.generateTaintEdges(
    automata: TaintRegisterStateAutomata,
    metaVarInfo: ResolvedMetaVarInfo
): TaintAutomataEdges {
    val globalStateAssignStates = hashSetOf<State>()
    val taintRuleEdges = mutableListOf<TaintRuleEdge>()
    val finalAcceptEdges = mutableListOf<TaintRuleEdge>()
    val finalDeadEdges = mutableListOf<TaintRuleEdge>()

    val predecessors = automataPredecessors(automata)

    val outDegree = automata.successors.mapValuesTo(hashMapOf()) { it.value.size }

    val unprocessed = ArrayDeque<State>()

    fun enqueueState(state: State) {
        val current = outDegree[state]
        check(current != null && current > 0) { "Unexpected state degree: $current" }

        val next = current - 1
        outDegree[state] = next

        if (next == 0) {
            unprocessed.add(state)
        }
    }

    val allFinalStates = automata.finalAcceptStates + automata.finalDeadStates
    for (state in allFinalStates) {
        val current = outDegree[state]
        if (current == null || current == 0) {
            unprocessed.add(state)
        }
    }

    while (unprocessed.isNotEmpty()) {
        val dstState = unprocessed.removeFirst()

        val isFinal = dstState in automata.finalAcceptStates || dstState in automata.finalDeadStates

        for ((edge, state) in predecessors[dstState].orEmpty()) {
            enqueueState(state)

            val stateId = automata.stateId(state)
            val stateVars = state.register.assignedVars.filter { it.value == stateId }

            val globalVarRequired = when {
                state == automata.initial -> false
                stateVars.isEmpty() -> true
                else -> {
                    val readVars = when (edge) {
                        is Edge.EdgeWithCondition -> edge.condition.readMetaVar.keys
                        is Edge.AnalysisEnd -> emptySet()
                    }
                    stateVars.all { it.key !in readVars }
                }
            }

            if (isFinal) {
                val edgeDescriptor = edgeDescriptor(edge)
                    ?: continue

                if (globalVarRequired) {
                    globalStateAssignStates.add(state)
                }

                val edgeCollection = if (dstState in automata.finalAcceptStates) finalAcceptEdges else finalDeadEdges
                edgeCollection += TaintRuleEdge(
                    state, dstState,
                    checkGlobalState = globalVarRequired,
                    edgeDescriptor.condition, edgeDescriptor.effect, edgeDescriptor.kind
                )

                continue
            }

            val edgeRequired = state.register != dstState.register
                    || (dstState in globalStateAssignStates && dstState != state && edge.canAssignStateVar())

            if (!edgeRequired) continue

            if (globalVarRequired) {
                globalStateAssignStates.add(state)
            }

            val edgeDescriptor = edgeDescriptor(edge)
                ?: continue

            taintRuleEdges += TaintRuleEdge(
                state, dstState,
                checkGlobalState = globalVarRequired,
                edgeDescriptor.condition, edgeDescriptor.effect, edgeDescriptor.kind
            )
        }
    }

    check(outDegree.all { it.value == 0 }) { "Some states were not visited" }

    val initialStateWithGlobalAssign = hashSetOf<State>()
    for (state in globalStateAssignStates) {
        if (taintRuleEdges.any { it.stateTo == state }) continue
        if (finalAcceptEdges.any { it.stateTo == state }) continue
        if (finalDeadEdges.any { it.stateTo == state }) continue

        initialStateWithGlobalAssign.add(state)
    }

    if (initialStateWithGlobalAssign.isNotEmpty()) {
        globalStateAssignStates.removeAll(initialStateWithGlobalAssign)

        fun MutableList<TaintRuleEdge>.removeGlobalStateCheck() {
            for ((i, edge) in this.withIndex()) {
                if (edge.stateFrom in initialStateWithGlobalAssign) {
                    this[i] = edge.copy(checkGlobalState = false)
                }
            }
        }

        taintRuleEdges.removeGlobalStateCheck()
        finalAcceptEdges.removeGlobalStateCheck()
        finalDeadEdges.removeGlobalStateCheck()
    }

    val metVarConstraints = hashMapOf<String, MetaVarConstraintOrPlaceHolder>()

    val placeHolders = computePlaceHolders(taintRuleEdges, finalAcceptEdges, finalDeadEdges)
    placeHolders.placeHolderRequiredMetaVars.forEach {
        metVarConstraints[it] = MetaVarConstraintOrPlaceHolder.PlaceHolder(metaVarInfo.metaVarConstraints[it])
    }

    metaVarInfo.metaVarConstraints.forEach { (mv, c) ->
        if (mv !in metVarConstraints) {
            metVarConstraints[mv] = MetaVarConstraintOrPlaceHolder.Constraint(c)
        }
    }

    return TaintAutomataEdges(
        automata, TaintRuleGenerationMetaVarInfo(metVarConstraints),
        globalStateAssignStates, taintRuleEdges, finalAcceptEdges, finalDeadEdges
    )
}

private fun Edge.canAssignStateVar(): Boolean = when (this) {
    is Edge.AnalysisEnd -> false
    is Edge.MethodCall -> true
    is Edge.MethodEnter -> true
    is Edge.MethodExit -> true
}

private data class TaintEdgeDescriptor(
    val kind: TaintRuleEdge.Kind,
    val condition: EdgeCondition,
    val effect: EdgeEffect,
)

private fun RuleConversionCtx.edgeDescriptor(edge: Edge): TaintEdgeDescriptor? = when (edge) {
    is Edge.AnalysisEnd -> {
        trace.error("Unexpected analysis end edge", Reason.ERROR)
        null
    }

    is Edge.MethodCall -> TaintEdgeDescriptor(
        TaintRuleEdge.Kind.MethodCall,
        edge.condition,
        edge.effect
    )

    is Edge.MethodEnter -> TaintEdgeDescriptor(
        TaintRuleEdge.Kind.MethodEnter,
        edge.condition,
        edge.effect
    )

    is Edge.MethodExit -> TaintEdgeDescriptor(
        TaintRuleEdge.Kind.MethodExit,
        edge.condition,
        edge.effect
    )
}


private class MetaVarCtx {
    val metaVarIdx = hashMapOf<String, Int>()
    val metaVars = mutableListOf<String>()

    fun String.idx() = metaVarIdx.getOrPut(this) {
        metaVars.add(this)
        metaVarIdx.size
    }
}

private data class MetaVarPlaceHolders(
    val placeHolderRequiredMetaVars: Set<String>,
)

private fun computePlaceHolders(
    taintRuleEdges: List<TaintRuleEdge>,
    finalAcceptEdges: List<TaintRuleEdge>,
    finalDeadEdges: List<TaintRuleEdge>,
): MetaVarPlaceHolders {
    val predecessors = hashMapOf<State, MutableList<TaintRuleEdge>>()
    taintRuleEdges.forEach { predecessors.getOrPut(it.stateTo, ::mutableListOf).add(it) }
    finalAcceptEdges.forEach { predecessors.getOrPut(it.stateTo, ::mutableListOf).add(it) }
    finalDeadEdges.forEach { predecessors.getOrPut(it.stateTo, ::mutableListOf).add(it) }

    val metaVarCtx = MetaVarCtx()

    val resultPlaceHolders = BitSet()
    val unprocessed = mutableListOf<Pair<State, PersistentBitSet>>()
    val visited = hashSetOf<Pair<State, PersistentBitSet>>()
    finalAcceptEdges.mapTo(unprocessed) { it.stateTo to emptyPersistentBitSet() }
    finalDeadEdges.mapTo(unprocessed) { it.stateTo to emptyPersistentBitSet() }

    while (unprocessed.isNotEmpty()) {
        val entry = unprocessed.removeLast()
        if (!visited.add(entry)) continue

        val (state, statePlaceholders) = entry

        for (edge in predecessors[state].orEmpty()) {
            val edgeMetaVars = BitSet()
            metaVarCtx.edgeConditionSignatureMetaVars(edge.edgeCondition, edgeMetaVars)
            metaVarCtx.edgeEffectSignatureMetaVars(edge.edgeEffect, edgeMetaVars)

            val nextMetaVars = statePlaceholders.persistentAddAll(edgeMetaVars)

            // metavar has multiple usages
            edgeMetaVars.and(statePlaceholders)
            resultPlaceHolders.or(edgeMetaVars)

            unprocessed.add(edge.stateFrom to nextMetaVars)
        }
    }

    if (resultPlaceHolders.isEmpty) {
        return MetaVarPlaceHolders(emptySet())
    }

    val placeHolders = hashSetOf<String>()
    resultPlaceHolders.forEach { placeHolders.add(metaVarCtx.metaVars[it]) }
    return MetaVarPlaceHolders(placeHolders)
}

private fun MetaVarCtx.edgeConditionSignatureMetaVars(condition: EdgeCondition, metaVars: BitSet) {
    condition.readMetaVar.values.forEach { predicates ->
        predicates.forEach { predicateSignatureMetaVars(it.predicate, metaVars) }
    }

    condition.other.forEach { predicateSignatureMetaVars(it.predicate, metaVars) }
}

private fun MetaVarCtx.edgeEffectSignatureMetaVars(effect: EdgeEffect, metaVars: BitSet) {
    effect.assignMetaVar.values.forEach { predicates ->
        predicates.forEach { predicateSignatureMetaVars(it.predicate, metaVars) }
    }
}

private fun MetaVarCtx.predicateSignatureMetaVars(predicate: Predicate, metaVars: BitSet) {
    methodSignatureMetaVars(predicate.signature, metaVars)
    predicate.constraint?.let { methodConstraintMetaVars(it, metaVars) }
}

private fun MetaVarCtx.methodSignatureMetaVars(signature: MethodSignature, metaVars: BitSet) {
    typeNameMetaVars(signature.enclosingClassName.name, metaVars)

    val name = signature.methodName.name
    if (name is SignatureName.MetaVar) {
        metaVars.set(name.metaVar.idx())
    }
}

private fun MetaVarCtx.methodConstraintMetaVars(signature: MethodConstraint, metaVars: BitSet) {
    when (signature) {
        is ClassModifierConstraint -> signatureModifierMetaVars(signature.modifier, metaVars)
        is MethodModifierConstraint -> signatureModifierMetaVars(signature.modifier, metaVars)
        is NumberOfArgsConstraint -> {}
        is ParamConstraint -> paramConditionMetaVars(signature.condition, metaVars)
    }
}

private fun MetaVarCtx.signatureModifierMetaVars(sm: SignatureModifier, metaVars: BitSet) {
    typeNameMetaVars(sm.type, metaVars)

    val value = sm.value
    if (value is SignatureModifierValue.MetaVar) {
        metaVars.set(value.metaVar.idx())
    }
}

private fun MetaVarCtx.paramConditionMetaVars(pc: ParamCondition.Atom, metaVars: BitSet) {
    when (pc) {
        is IsMetavar -> {} // handled semantically with taint engine
        is ParamCondition.ParamModifier -> signatureModifierMetaVars(pc.modifier, metaVars)

        is StringValueMetaVar -> {
            /**
             *  todo: for now we ignore metavar substitution
             *  "$A"; "$A" will trigger for different A values
             *  */
        }

        is ParamCondition.TypeIs -> {
            typeNameMetaVars(pc.typeName, metaVars)
        }

        is ParamCondition.SpecificStaticFieldValue -> {
            typeNameMetaVars(pc.fieldClass, metaVars)
        }

        ParamCondition.AnyStringLiteral,
        is SpecificBoolValue,
        is SpecificStringValue -> {
            // do nothing, no metavars
        }
    }
}

private fun MetaVarCtx.typeNameMetaVars(typeName: TypeNamePattern, metaVars: BitSet) {
    when (typeName) {
        is TypeNamePattern.MetaVar -> {
            metaVars.set(typeName.metaVar.idx())
        }

        is TypeNamePattern.ArrayType -> {
            typeNameMetaVars(typeName.element, metaVars)
        }

        TypeNamePattern.AnyType,
        is TypeNamePattern.ClassName,
        is TypeNamePattern.PrimitiveName,
        is TypeNamePattern.FullyQualified -> {
            // no metavars
        }
    }
}
