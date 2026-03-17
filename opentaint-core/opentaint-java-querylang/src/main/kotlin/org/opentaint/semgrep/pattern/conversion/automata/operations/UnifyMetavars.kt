package org.opentaint.semgrep.pattern.conversion.automata.operations

import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.map
import org.opentaint.dataflow.util.toSet
import org.opentaint.semgrep.pattern.conversion.IsMetavar
import org.opentaint.semgrep.pattern.conversion.MetavarAtom
import org.opentaint.semgrep.pattern.conversion.ParamCondition.StringValueMetaVar
import org.opentaint.semgrep.pattern.conversion.automata.AutomataBuilderCtx
import org.opentaint.semgrep.pattern.conversion.automata.AutomataEdgeType
import org.opentaint.semgrep.pattern.conversion.automata.AutomataNode
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormula
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaCubeCompact
import org.opentaint.semgrep.pattern.conversion.automata.MethodFormulaManager
import org.opentaint.semgrep.pattern.conversion.automata.ParamConstraint
import org.opentaint.semgrep.pattern.conversion.automata.Position
import org.opentaint.semgrep.pattern.conversion.automata.Predicate
import org.opentaint.semgrep.pattern.conversion.automata.PredicateId
import org.opentaint.semgrep.pattern.conversion.automata.SemgrepRuleAutomata
import org.opentaint.semgrep.pattern.conversion.taint.methodFormulaSat
import org.opentaint.semgrep.pattern.conversion.taint.simplifyMethodFormula
import java.util.BitSet
import java.util.LinkedList
import java.util.Queue

private data class MetavarUnificationContext private constructor(
    private val metavarMappings: Map<MetavarAtom.Basic, MetavarAtom>
) {
    fun transform(metavar: MetavarAtom): MetavarAtom {
        if (metavar is MetavarAtom.Basic) {
            return metavarMappings.getOrDefault(metavar, metavar)
        }

        return metavar.basics
            .map { metavarMappings[it] }
            .distinct()
            .singleOrNull()
            ?: error("Ambiguous transform for metavar $metavar")
    }

    fun unifyMetavars(metavars: Collection<MetavarAtom>): MetavarUnificationContext {
        // Note: size == 1 can be interesting because this can be already unified metavar
        if (metavars.isEmpty()) {
            return this
        }

        if (metavars.size == 1 && metavars.single() is MetavarAtom.Basic) {
            return this
        }

        val inputBasics = metavars.flatMap { it.basics }.toSet()
        val basicsToUnify = inputBasics.map { transform(it) }.toSet().flatMap { it.basics }

        val unifiedMetavar = MetavarAtom.create(basicsToUnify)

        if (transform(basicsToUnify.first()) == unifiedMetavar) {
            check(basicsToUnify.all { transform(it) == unifiedMetavar }) {
                "Unexpected different mappings for basics of $unifiedMetavar"
            }
            return this
        }

        val newMetavarMappings = metavarMappings.toMutableMap().apply {
            basicsToUnify.forEach { put(it, unifiedMetavar) }
        }
        return MetavarUnificationContext(newMetavarMappings)
    }

    fun addMetavar(metavar: MetavarAtom): MetavarUnificationContext = unifyMetavars(listOf(metavar))

    fun intersect(other: MetavarUnificationContext): MetavarUnificationContext {
        val resultMetavarMappings = buildMap {
            metavarMappings.forEach { (metavar, thisMapping) ->
                val otherMapping = other.metavarMappings[metavar] ?: return@forEach
                val commonBasics = thisMapping.basics.intersect(otherMapping.basics)
                    .takeIf { it.size > 1 } ?: return@forEach

                put(metavar, MetavarAtom.create(commonBasics))
            }
        }

        return MetavarUnificationContext(resultMetavarMappings)
    }

    companion object {
        val EMPTY: MetavarUnificationContext
            get() = MetavarUnificationContext(emptyMap())
    }
}

fun AutomataBuilderCtx.unifyMetavars(automata: SemgrepRuleAutomata): SemgrepRuleAutomata {
    val newInitialNode = AutomataNode()
    val initialContext = MetavarUnificationContext.EMPTY

    val nodeMapping: MutableMap<Pair<AutomataNode, MetavarUnificationContext>, AutomataNode> = hashMapOf()
    val nodeQueue: Queue<Pair<AutomataNode, MetavarUnificationContext>> = LinkedList()
    nodeMapping[automata.initialNode to initialContext] = newInitialNode
    nodeQueue.add(automata.initialNode to initialContext)

    while (nodeQueue.isNotEmpty()) {
        cancelation.check()

        val (prevNode, ctx) = nodeQueue.poll()
        val newNode = nodeMapping[prevNode to ctx] ?: error("Expected non-null node")

        newNode.accept = prevNode.accept

        var anyEdgeChanged = false

        prevNode.outEdges.forEach { (prevEdge, prevDst) ->
            val (newEdge, newCtx) = transformEdge(prevEdge, ctx) ?: run {
                return@forEach
            }

            if (newEdge != prevEdge) {
                anyEdgeChanged = true
            }

            val newDst = nodeMapping.getOrPut(prevDst to newCtx) {
                nodeQueue.add(prevDst to newCtx)
                AutomataNode()
            }

            newNode.outEdges.add(newEdge to newDst)
        }

        if (anyEdgeChanged) {
            methodCallEdgeToDeadNode(automata, prevNode, keepTrivialEdges = false)?.also {
                newNode.outEdges.add(it to automata.deadNode)
            }

            methodExitEdgeToDeadNode(automata, prevNode, keepTrivialEdges = false)?.also {
                newNode.outEdges.add(it to automata.deadNode)
            }

            if (automata.params.hasMethodEnter) {
                methodEnterEdgeToDeadNode(automata, prevNode, keepTrivialEdges = false)?.also {
                    newNode.outEdges.add(it to automata.deadNode)
                }
            }
        }
    }

    return SemgrepRuleAutomata(
        formulaManager = automata.formulaManager,
        initialNodes = setOf(newInitialNode),
        automata.params
    )
}

private fun AutomataBuilderCtx.transformEdge(
    edge: AutomataEdgeType,
    context: MetavarUnificationContext
): Pair<AutomataEdgeType, MetavarUnificationContext>? {
    if (edge !is AutomataEdgeType.AutomataEdgeTypeWithFormula) {
        return edge to context
    }

    val newContext = context.extendByFormula(edge.formula, this)
        ?: return null

    val newFormula = edge.formula.transform(formulaManager, newContext)

    if (newFormula == edge.formula && newContext == context) {
        return edge to context
    }

    if (!methodFormulaSat(formulaManager, newFormula, metaVarInfo, cancelation)) {
        return null
    }

    val newEdge = when (edge) {
        is AutomataEdgeType.MethodCall -> AutomataEdgeType.MethodCall(newFormula)
        is AutomataEdgeType.MethodEnter -> AutomataEdgeType.MethodEnter(newFormula)
        is AutomataEdgeType.MethodExit -> AutomataEdgeType.MethodExit(newFormula)
    }

    return newEdge to newContext
}

private fun MethodFormula.transform(
    formulaManager: MethodFormulaManager,
    context: MetavarUnificationContext
): MethodFormula {
    // TODO: memory optimizations?
    return when (this) {
        is MethodFormula.And -> MethodFormula.And(
            all.map { it.transform(formulaManager, context) }.toTypedArray()
        )

        is MethodFormula.Or -> MethodFormula.Or(
            any.map { it.transform(formulaManager, context) }.toTypedArray()
        )

        is MethodFormula.Literal -> MethodFormula.Literal(
            predicate = predicate.transform(formulaManager, context),
            negated = negated
        )

        is MethodFormula.Cube -> MethodFormula.Cube(
            cube = MethodFormulaCubeCompact(
                positiveLiterals = cube.positiveLiterals.map { it.transform(formulaManager, context) },
                negativeLiterals = cube.negativeLiterals.map { it.transform(formulaManager, context) }
            ),
            negated = negated
        )

        MethodFormula.False,
        MethodFormula.True -> this
    }
}

private fun PredicateId.transform(
    formulaManager: MethodFormulaManager,
    context: MetavarUnificationContext
): PredicateId {
    val newPredicate = formulaManager.predicate(this).transform(context)
    return formulaManager.predicateId(newPredicate)
}

private fun Predicate.transform(context: MetavarUnificationContext): Predicate {
    if (constraint !is ParamConstraint) {
        return this
    }

    val condition = constraint.condition

    val newCondition = when (condition) {
        is IsMetavar -> IsMetavar(context.transform(condition.metavar))
        is StringValueMetaVar -> StringValueMetaVar(context.transform(condition.metaVar))
        else -> return this
    }

    if (condition == newCondition) {
        return this
    }

    return Predicate(signature, ParamConstraint(constraint.position, newCondition))
}

private fun MetavarUnificationContext.extendByFormula(
    formula: MethodFormula,
    automataCtx: AutomataBuilderCtx
): MetavarUnificationContext? {
    val (positive, negative) = formula.getAllPredicates()
    val allMetavars = (positive + negative)
        .map(automataCtx.formulaManager::predicate)
        .mapNotNull { it.metavarWithPosition()?.first }
        .distinct()

    val extendedBySeenMetavars = allMetavars.fold(initial = this, MetavarUnificationContext::addMetavar)

    val positivesByPosition = positive
        .map(automataCtx.formulaManager::predicate)
        .mapNotNull { it.metavarWithPosition() }
        .groupBy({ it.second }) { it.first }

    if (positivesByPosition.all { it.value.distinct().size == 1 }) {
        // Can skip call to extendByFormulaPositivePredicates
        return extendedBySeenMetavars
    }

    return extendedBySeenMetavars.extendByFormulaPositivePredicates(formula, automataCtx)
}

private fun MetavarUnificationContext.extendByFormulaPositivePredicates(
    formula: MethodFormula,
    automataCtx: AutomataBuilderCtx
): MetavarUnificationContext? {
    val cubes = simplifyMethodFormula(automataCtx.formulaManager, formula, automataCtx.metaVarInfo, automataCtx.cancelation)

    val cubeContexts = cubes.map {
        val positivePredicates = it.cube.positiveLiterals.toSet()
        extendByPositivePredicates(positivePredicates, automataCtx)
    }

    return cubeContexts.reduceOrNull(MetavarUnificationContext::intersect)?.also {
        check(it.intersect(this) == this) {
            "Resulting context expected to at least contain initial context"
        }
    }
}

private fun MetavarUnificationContext.extendByPositivePredicates(
    predicates: Iterable<PredicateId>,
    automataCtx: AutomataBuilderCtx
): MetavarUnificationContext {
    val metavarsToUnify = predicates
        .map(automataCtx.formulaManager::predicate)
        .mapNotNull { it.metavarWithPosition() }
        .groupBy({ it.second }) { it.first }

    return metavarsToUnify
        .values
        .fold(initial = this, MetavarUnificationContext::unifyMetavars)
}

private fun Predicate.metavarWithPosition(): Pair<MetavarAtom, Position>? {
    if (constraint !is ParamConstraint) {
        return null
    }

    val position = constraint.position
    val condition = constraint.condition

    if (condition is IsMetavar) {
        return condition.metavar to position
    } else if (condition is StringValueMetaVar) {
        return condition.metaVar to position
    }
    return null
}

private fun MethodFormula.getAllPredicates(): Pair<Set<PredicateId>, Set<PredicateId>> {
    val positive = BitSet()
    val negative = BitSet()
    collectPredicates(positive, negative)
    return positive.toSet() to negative.toSet()
}

private fun MethodFormula.collectPredicates(positive: BitSet, negative: BitSet) {
    when (this) {
        is MethodFormula.And -> all.forEach { it.collectPredicates(positive, negative) }
        is MethodFormula.Or -> any.forEach { it.collectPredicates(positive, negative) }
        is MethodFormula.Cube -> {
            if (negated) {
                cube.positiveLiterals.forEach { negative.set(it) }
                cube.negativeLiterals.forEach { positive.set(it) }
            } else {
                cube.positiveLiterals.forEach { positive.set(it) }
                cube.negativeLiterals.forEach { negative.set(it) }
            }
        }
        is MethodFormula.Literal -> {
            if (negated) {
                negative.set(predicate)
            } else {
                positive.set(predicate)
            }
        }
        MethodFormula.False,
        MethodFormula.True -> return
    }
}