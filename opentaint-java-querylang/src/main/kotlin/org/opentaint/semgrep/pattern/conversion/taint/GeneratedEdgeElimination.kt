package org.opentaint.semgrep.pattern.conversion.taint

import org.opentaint.semgrep.pattern.conversion.IsMetavar
import org.opentaint.semgrep.pattern.conversion.MetavarAtom
import org.opentaint.semgrep.pattern.conversion.ParamCondition
import org.opentaint.semgrep.pattern.conversion.ParamCondition.StringValueMetaVar
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternAction.SignatureName
import org.opentaint.semgrep.pattern.conversion.TypeNamePattern
import org.opentaint.semgrep.pattern.conversion.automata.ClassModifierConstraint
import org.opentaint.semgrep.pattern.conversion.automata.MethodConstraint
import org.opentaint.semgrep.pattern.conversion.automata.MethodEnclosingClassName
import org.opentaint.semgrep.pattern.conversion.automata.MethodModifierConstraint
import org.opentaint.semgrep.pattern.conversion.automata.MethodName
import org.opentaint.semgrep.pattern.conversion.automata.MethodSignature
import org.opentaint.semgrep.pattern.conversion.automata.NumberOfArgsConstraint
import org.opentaint.semgrep.pattern.conversion.automata.ParamConstraint
import org.opentaint.semgrep.pattern.conversion.automata.Position
import org.opentaint.semgrep.pattern.conversion.automata.Predicate
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.Edge
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeCondition
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.EdgeEffect
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.MethodPredicate
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata.State

data class ValueGeneratorCtx(
    val valueConstraint: Map<MetavarAtom, List<ParamCondition.Atom>>
) {
    companion object {
        val EMPTY: ValueGeneratorCtx = ValueGeneratorCtx(emptyMap())
    }
}

fun <CtxT> eliminateEdges(
    automata: TaintRegisterStateAutomata,
    edgeEliminator: EdgeEliminator<CtxT>,
    initialCtx: CtxT
): TaintRegisterStateAutomata {
    val successors = hashMapOf<State, MutableSet<Pair<Edge, State>>>()
    val finalAcceptStates = automata.finalAcceptStates.toHashSet()
    val finalDeadStates = automata.finalDeadStates.toHashSet()
    val removedStates = hashSetOf<State>()

    val unprocessed = mutableListOf(automata.initial to initialCtx)
    val visited = hashSetOf<Pair<State, CtxT>>()
    while (unprocessed.isNotEmpty()) {
        val state = unprocessed.removeLast()
        if (!visited.add(state)) continue

        val stateSuccessors = successors.getOrPut(state.first, ::hashSetOf)
        eliminateEdgesForOneState(
            state.first, state.second, automata.successors,
            finalAcceptStates, finalDeadStates,
            removedStates,
            stateSuccessors, unprocessed, edgeEliminator
        )
    }

    finalAcceptStates.removeAll(removedStates)
    finalDeadStates.removeAll(removedStates)
    removedStates.forEach { successors.remove(it) }
    finalAcceptStates.forEach { successors.remove(it) }
    finalDeadStates.forEach { successors.remove(it) }

    return TaintRegisterStateAutomata(
        automata.formulaManager, automata.initial,
        finalAcceptStates, finalDeadStates,
        successors, automata.nodeIndex
    )
}

private fun <CtxT> eliminateEdgesForOneState(
    state: State,
    ctx: CtxT,
    successors: Map<State, Set<Pair<Edge, State>>>,
    finalAcceptStates: MutableSet<State>,
    finalDeadStates: MutableSet<State>,
    removedStates: MutableSet<State>,
    resultStateSuccessors: MutableSet<Pair<Edge, State>>,
    unprocessed: MutableList<Pair<State, CtxT>>,
    edgeEliminator: EdgeEliminator<CtxT>
) {
    for ((edge, nextState) in successors[state].orEmpty()) {
        val elimResult = edgeEliminator.eliminateEdge(edge, ctx)
        when (elimResult) {
            EdgeEliminationResult.Unchanged -> {
                resultStateSuccessors.add(edge to nextState)
                unprocessed.add(nextState to ctx)
                continue
            }

            is EdgeEliminationResult.Replace -> {
                resultStateSuccessors.add(elimResult.newEdge to nextState)
                unprocessed.add(nextState to elimResult.ctx)
                continue
            }

            is EdgeEliminationResult.Eliminate -> {
                if (nextState in finalAcceptStates) {
                    val nextSuccessors = successors[nextState].orEmpty()
                    check(nextSuccessors.isEmpty())

                    removedStates.add(nextState)
                    finalAcceptStates.add(state)
                }

                if (nextState in finalDeadStates) {
                    val nextSuccessors = successors[nextState].orEmpty()
                    check(nextSuccessors.isEmpty())

                    removedStates.add(nextState)
                    finalDeadStates.add(state)
                }

                if (nextState == state) continue

                eliminateEdgesForOneState(
                    nextState, elimResult.ctx, successors, finalAcceptStates, finalDeadStates, removedStates,
                    resultStateSuccessors, unprocessed, edgeEliminator
                )
            }
        }
    }
}

fun interface EdgeEliminator<CtxT> {
    fun eliminateEdge(edge: Edge, ctx: CtxT): EdgeEliminationResult<CtxT>
}

sealed interface EdgeEliminationResult<out CtxT> {
    data object Unchanged : EdgeEliminationResult<Nothing>
    data class Replace<CtxT>(val newEdge: Edge, val ctx: CtxT) : EdgeEliminationResult<CtxT>
    data class Eliminate<CtxT>(val ctx: CtxT) : EdgeEliminationResult<CtxT>
}

fun <CtxT> edgeTypePreservingEdgeEliminator(
    eliminateEdge: (EdgeEffect, EdgeCondition, CtxT, (EdgeEffect, EdgeCondition) -> Edge) -> EdgeEliminationResult<CtxT>
): EdgeEliminator<CtxT> = EdgeEliminator { edge, ctx ->
    when (edge) {
        is Edge.AnalysisEnd -> EdgeEliminationResult.Unchanged
        is Edge.MethodCall -> eliminateEdge(edge.effect, edge.condition, ctx) { effect, cond ->
            Edge.MethodCall(cond, effect)
        }

        is Edge.MethodEnter -> eliminateEdge(edge.effect, edge.condition, ctx) { effect, cond ->
            Edge.MethodEnter(cond, effect)
        }

        is Edge.MethodExit -> eliminateEdge(edge.effect, edge.condition, ctx) { effect, cond ->
            Edge.MethodExit(cond, effect)
        }
    }
}

fun eliminateAnyValueGenerator(
    effect: EdgeEffect,
    condition: EdgeCondition,
    ctx: ValueGeneratorCtx,
    rebuildEdge: (EdgeEffect, EdgeCondition) -> Edge,
): EdgeEliminationResult<ValueGeneratorCtx> {
    if (effect.anyValueGeneratorUsed()) {
        val metaVar = effect.assignMetaVar.keys.singleOrNull()
            ?: error("Value gen with multiple mata vars")

        val metaVarPred = effect.assignMetaVar.getValue(metaVar).first()
        check((metaVarPred.predicate.constraint as ParamConstraint).position is Position.Result) {
            "Unexpected constraint: $metaVarPred"
        }

        check(condition.readMetaVar.keys.all { it == metaVar }) {
            "Unexpected condition: $condition"
        }

        val metaVarConstraints = mutableListOf<ParamCondition.Atom>()
        for (constraint in condition.other) {
            when (val c = constraint.predicate.constraint) {
                is NumberOfArgsConstraint -> continue

                is ParamConstraint -> {
                    if (c.position !is Position.Result) {
                        error("Unexpected constraint: $c")
                    }

                    if (c.condition is IsMetavar) {
                        error("Unexpected condition: $c")
                    }

                    metaVarConstraints.add(c.condition)
                }

                null -> TODO("any value generator without constraints")
                is ClassModifierConstraint,
                is MethodModifierConstraint -> error("Unexpected any value generator constraint")
            }
        }

        val nextCtx = ValueGeneratorCtx(ctx.valueConstraint + (metaVar to metaVarConstraints))
        return EdgeEliminationResult.Eliminate(nextCtx)
    }

    val valueGenEffect = hashMapOf<MetavarAtom, MutableList<MethodPredicate>>()
    for ((metaVar, preds) in effect.assignMetaVar) {
        for (pred in preds) {
            if (pred.anyValueGeneratorUsed()) {
                valueGenEffect.getOrPut(metaVar, ::mutableListOf).add(pred)
            }
        }
    }

    var resultCondition = condition
    val resultConstraint = ctx.valueConstraint.toMutableMap()
    val constraintIter = resultConstraint.iterator()
    while (constraintIter.hasNext()) {
        val (metaVar, constraint) = constraintIter.next()

        val metaVarEffect = effect.assignMetaVar[metaVar] ?: continue

        val readMetaVar = resultCondition.readMetaVar - metaVar
        val other = resultCondition.other.toMutableList()

        for (mp in metaVarEffect) {
            val paramConstraint = mp.predicate.constraint as? ParamConstraint ?: continue
            check(paramConstraint.condition is IsMetavar && paramConstraint.condition.metavar == metaVar)
            for (atom in constraint) {
                val newParamConstraint = paramConstraint.copy(condition = atom)
                val newPredicate = mp.predicate.copy(constraint = newParamConstraint)
                other += MethodPredicate(newPredicate, negated = false)
            }
        }

        resultCondition = EdgeCondition(readMetaVar, other)
        constraintIter.remove()
    }

    if (resultCondition === condition) return EdgeEliminationResult.Unchanged

    val newEdge = rebuildEdge(effect, resultCondition)
    return EdgeEliminationResult.Replace(newEdge, ValueGeneratorCtx(resultConstraint))
}

data class StringConcatCtx(
    val metavarMapping: Map<MetavarAtom, Set<MetavarAtom>>
) {
    fun transform(condition: EdgeCondition): EdgeCondition {
        val transformedOther = mutableSetOf<MethodPredicate>()
        val transformedReadMetaVar = transform(condition.readMetaVar, ignoreNegatedPreds = false, transformedOther)
        condition.other.forEach { transformedOther.addAll(transform(it)) }
        return EdgeCondition(transformedReadMetaVar, transformedOther.toList())
    }

    fun transform(effect: EdgeEffect): EdgeEffect {
        val transformedAssign = transform(effect.assignMetaVar, ignoreNegatedPreds = true, newOther = hashSetOf())
        return EdgeEffect(transformedAssign)
    }

    private fun transform(
        preds: Map<MetavarAtom, List<MethodPredicate>>,
        ignoreNegatedPreds: Boolean,
        newOther: MutableSet<MethodPredicate>,
    ): Map<MetavarAtom, List<MethodPredicate>> {
        val result = hashMapOf<MetavarAtom, MutableList<MethodPredicate>>()
        preds.forEach { (prevMetavar, prevPreds) ->
            val newMetavars = metavarMapping.getOrElse(prevMetavar) { setOf(prevMetavar) }

            newMetavars.forEach { newMetavar ->
                // Need to concretize context for `prevMetavar`
                val newCtx = StringConcatCtx(metavarMapping + (prevMetavar to setOf(newMetavar)))
                val newPreds = prevPreds.flatMap(newCtx::transform)

                val newMetaVarPreds = result.getOrPut(newMetavar, ::mutableListOf)
                for (p in newPreds) {
                    if (p.negated && ignoreNegatedPreds) continue

                    val metaVar = p.findMetaVarConstraint()
                    if (metaVar != null) {
                        check(metaVar == newMetavar) { "Error: unexpected meta var: $metaVar" }
                        newMetaVarPreds.add(p)
                    } else {
                        newOther.add(p)
                    }
                }
            }
        }
        return result
    }

    private fun transform(predicate: MethodPredicate): List<MethodPredicate> {
        return transform(predicate.predicate).map { newPredicate ->
            MethodPredicate(newPredicate, predicate.negated)
        }
    }

    private fun transform(predicate: Predicate): List<Predicate> {
        if (predicate.signature.isGeneratedStringConcat()) {
            // Replacing with String.concat()
            val newConstraints = predicate.constraint?.let { constraint ->
                transform(constraint) {
                    if (it is Position.Argument) {
                        val index = it.index
                        check(index is Position.ArgumentIndex.Concrete) { "Expected concrete argument index" }
                        check(index.idx in 0 until 2) { "Invalid index for string concat" }
                        if (index.idx == 0) {
                            Position.Object
                        } else {
                            Position.Argument(
                                Position.ArgumentIndex.Concrete(0)
                            )
                        }
                    } else {
                        it
                    }
                }
            }

            return (newConstraints ?: listOf(null)).map { newConstraint ->
                Predicate(stringConcatMethodSignature, newConstraint)
            }
        }

        val newConstraints = predicate.constraint?.let { constraint ->
            transform(constraint) { it }
        }
        return (newConstraints ?: listOf(null)).map { newConstraint ->
            Predicate(predicate.signature, newConstraint)
        }
    }

    private fun transform(
        constraint: MethodConstraint,
        positionTransform: (Position) -> Position
    ): List<MethodConstraint> {
        if (constraint !is ParamConstraint) {
            return listOf(constraint)
        }

        val newPosition = positionTransform(constraint.position)
        val newConditions = transform(constraint.condition)

        return newConditions.map { newCondition ->
            ParamConstraint(newPosition, newCondition)
        }
    }

    private fun transform(condition: ParamCondition.Atom): List<ParamCondition.Atom> {
        return when (condition) {
            is IsMetavar -> {
                val newMetavars = metavarMapping[condition.metavar] ?: return listOf(condition)
                val modified = newMetavars.map(::IsMetavar)

                if (condition.metavar !in newMetavars || newMetavars.size > 1) {
                    return modified + ParamCondition.TypeIs(TypeNamePattern.FullyQualified("java.lang.String"))
                } else {
                    return modified
                }
            }

            is StringValueMetaVar -> {
                val newMetavars = metavarMapping[condition.metaVar] ?: return listOf(condition)
                return newMetavars.map(ParamCondition::StringValueMetaVar)
            }

            else -> listOf(condition)
        }
    }

    companion object {
        val EMPTY: StringConcatCtx = StringConcatCtx(emptyMap())

        val stringConcatMethodSignature by lazy {
            MethodSignature(
                MethodName(SignatureName.Concrete("concat")),
                MethodEnclosingClassName(TypeNamePattern.FullyQualified("java.lang.String"))
            )
        }
    }
}

fun eliminateStringConcat(
    effect: EdgeEffect,
    condition: EdgeCondition,
    ctx: StringConcatCtx,
    rebuildEdge: (EdgeEffect, EdgeCondition) -> Edge,
): EdgeEliminationResult<StringConcatCtx> {
    // TODO: rollback renaming of metavar when necessary (?)
    val generatedByConcatHelperMetavars = effect.assignMetaVar.mapNotNull { (metavar, preds) ->
        val isResultOfConcatHelper = preds.any {
            val predCondition = it.asConditionOnStringConcat<Position.Result>()
                ?: return@any false

            check(predCondition == IsMetavar(metavar)) { "Unexpected condition" }
            !it.negated
        }

        metavar.takeIf { isResultOfConcatHelper }
    }.toSet()

    if (generatedByConcatHelperMetavars.isEmpty()) {
        return ctx.transformEdge(effect, condition, rebuildEdge)
    }

    val metavarArguments = condition.readMetaVar.flatMap { (metavar, preds) ->
        val isArgumentOfConcatHelper = preds.any {
            val predCondition = it.asConditionOnStringConcat<Position.Argument>()
                ?: return@any false

            check(predCondition == IsMetavar(metavar)) { "Unexpected condition" }
            !it.negated
        }

        if (isArgumentOfConcatHelper) {
            ctx.metavarMapping.getOrElse(metavar) { setOf(metavar) }
        } else {
            emptyList()
        }
    }.toSet()

    val otherArguments = condition.other.mapNotNull {
        it.asConditionOnStringConcat<Position.Argument>()
    }

    if (otherArguments.isEmpty() || otherArguments.singleOrNull() == ParamCondition.AnyStringLiteral) {
        val newCtx = if (metavarArguments.size == 1 && metavarArguments == generatedByConcatHelperMetavars) {
            ctx
        } else {
            StringConcatCtx(
                metavarMapping = ctx.metavarMapping + generatedByConcatHelperMetavars.associateWith { metavarArguments }
            )
        }
        return EdgeEliminationResult.Eliminate(newCtx)
    }
    return ctx.transformEdge(effect, condition, rebuildEdge)
}

private fun StringConcatCtx.transformEdge(
    effect: EdgeEffect,
    condition: EdgeCondition,
    rebuildEdge: (EdgeEffect, EdgeCondition) -> Edge
): EdgeEliminationResult<StringConcatCtx> {
    val newEffect = transform(effect)
    val newCondition = transform(condition)

    return if (effect == newEffect && condition == newCondition) {
        EdgeEliminationResult.Unchanged
    } else {
        val newEdge = rebuildEdge(newEffect, newCondition)
        EdgeEliminationResult.Replace(newEdge, this)
    }
}

private inline fun <reified T : Position> MethodPredicate.asConditionOnStringConcat(): ParamCondition.Atom? {
    if (!predicate.signature.isGeneratedStringConcat()) {
        return null
    }

    val constraint = predicate.constraint as? ParamConstraint ?: return null

    if (constraint.position !is T) {
        return null
    }

    return constraint.condition
}
