package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.mayReadField
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.readFieldTo
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.ext.JAVA_OBJECT
import org.opentaint.ir.api.jvm.ext.isAssignable
import org.opentaint.ir.taint.configuration.And
import org.opentaint.ir.taint.configuration.AssignMark
import org.opentaint.ir.taint.configuration.Condition
import org.opentaint.ir.taint.configuration.ContainsMark
import org.opentaint.ir.taint.configuration.CopyAllMarks
import org.opentaint.ir.taint.configuration.CopyMark
import org.opentaint.ir.taint.configuration.Or
import org.opentaint.ir.taint.configuration.Position
import org.opentaint.ir.taint.configuration.PositionResolver
import org.opentaint.ir.taint.configuration.RemoveAllMarks
import org.opentaint.ir.taint.configuration.RemoveMark
import org.opentaint.ir.taint.configuration.TaintMark
import org.opentaint.ir.taint.configuration.This
import org.opentaint.dataflow.config.BasicConditionEvaluator
import org.opentaint.dataflow.ifds.Maybe
import org.opentaint.dataflow.ifds.flatFmap
import org.opentaint.dataflow.ifds.flatMap
import org.opentaint.dataflow.ifds.onSome
import org.opentaint.dataflow.jvm.ap.ifds.AccessTree.AccessNode
import org.opentaint.dataflow.jvm.util.JIRTraits

class FactReader(val fact: Fact.TaintedTree) {
    private var refinement: ExclusionSet = ExclusionSet.Empty
    val hasRefinement: Boolean get() = refinement !is ExclusionSet.Empty

    fun containsPosition(position: PositionAccess): Boolean {
        when (position) {
            is PositionAccess.Base -> {
                if (!fact.ap.mayReadField(position.base, FinalAccessor)) return false

                if (!fact.ap.access.contains(FinalAccessor)) {
                    if (fact.ap.access.isAbstract) {
                        refinement = refinement.add(FinalAccessor)
                    }
                    return false
                }

                return true
            }

            is PositionAccess.ArrayElement -> {
                if (!fact.ap.mayReadField(position.base, ElementAccessor)) return false

                if (!fact.ap.access.contains(ElementAccessor)) {
                    if (fact.ap.access.isAbstract) {
                        refinement = refinement.add(ElementAccessor)
                    }
                    return false
                }

                val arrayElement = fact.ap.readFieldTo(newBase = fact.ap.base, field = ElementAccessor)

                if (!arrayElement.mayReadField(position.base, FinalAccessor)) return false

                if (!arrayElement.access.contains(FinalAccessor)) {
                    if (arrayElement.access.isAbstract) {
                        refinement = refinement.add(FinalAccessor)
                    }
                    return false
                }

                return true
            }
        }
    }

    fun containsAccessor(accessor: Accessor): Boolean {
        if (!fact.ap.mayReadField(fact.ap.base, accessor)) return false

        if (!fact.ap.access.contains(accessor)) {
            if (fact.ap.access.isAbstract) {
                refinement = refinement.add(accessor)
            }
            return false
        }

        return true
    }

    fun refineFact(fact: Fact.TaintedPath): Fact.TaintedPath = with(fact.ap) {
        if (!hasRefinement) return fact

        val refinedAp = AccessPath(base, access, exclusions.union(refinement))
        fact.changeAP(refinedAp)
    }

    fun refineFact(fact: Fact.TaintedTree): Fact.TaintedTree = with(fact.ap) {
        if (!hasRefinement) return fact

        val refinedAp = AccessTree(base, access, exclusions.union(refinement))
        return fact.changeAP(refinedAp)
    }
}

class FactAwareConditionEvaluator(
    private val facts: Iterable<FactReader>,
    private val accessPathResolver: PositionResolver<Maybe<List<PositionAccess>>>,
    positionResolver: PositionResolver<Maybe<JIRValue>>,
) : BasicConditionEvaluator(positionResolver, JIRTraits) {
    constructor(
        fact: FactReader,
        accessPathResolver: PositionResolver<Maybe<List<PositionAccess>>>,
        positionResolver: PositionResolver<Maybe<JIRValue>>
    ) : this(listOf(fact), accessPathResolver, positionResolver)

    private var hasEvaluatedContainsMark: Boolean = false

    fun evalWithAssumptionsCheck(condition: Condition): Boolean {
        hasEvaluatedContainsMark = false
        return condition.accept(this)
    }

    fun assumptionsPossible(): Boolean = hasEvaluatedContainsMark

    // Force evaluation of all branches
    override fun visit(condition: And): Boolean =
        condition.args.map { it.accept(this) }.all { it }

    override fun visit(condition: Or): Boolean =
        condition.args.map { it.accept(this) }.any { it }

    override fun visit(condition: ContainsMark): Boolean {
        accessPathResolver.resolve(condition.position).onSome { variables ->
            for (variable in variables) {
                for (fact in facts) {
                    if (evalContainsMark(fact, condition.mark, variable)) return true
                }
            }
        }
        return false
    }

    fun evalContainsMark(factReader: FactReader, mark: TaintMark, variable: PositionAccess): Boolean {
        if (factReader.fact.mark != mark) return false

        if (factReader.containsPosition(variable)) {
            hasEvaluatedContainsMark = true
            return true
        }

        return false
    }
}

class FactIgnoreConditionEvaluator(
    positionResolver: PositionResolver<Maybe<JIRValue>>
) : BasicConditionEvaluator(positionResolver, JIRTraits) {
    override fun visit(condition: ContainsMark): Boolean {
        return false
    }
}

class TaintPassActionEvaluator(
    private val method: JIRMethod,
    private val positionResolver: PositionResolver<Maybe<List<PositionAccess>>>,
    private val factTypeChecker: FactTypeChecker,
    private val factReader: FactReader
) {
    private val positionTypeResolver = MethodPositionTypeResolver(method)

    fun evaluate(action: CopyAllMarks): Maybe<List<Fact.TaintedTree>> =
        positionResolver.resolve(action.from).flatMap { from ->
            positionResolver.resolve(action.to).flatMap { to ->
                copyFact(action.from, action.to, from, to)
            }
        }

    fun evaluate(action: CopyMark): Maybe<List<Fact.TaintedTree>> {
        if (factReader.fact.mark != action.mark) return Maybe.none()
        return positionResolver.resolve(action.from).flatMap { from ->
            positionResolver.resolve(action.to).flatMap { to ->
                copyFact(action.from, action.to, from, to)
            }
        }
    }

    private fun copyFact(
        fromPos: Position,
        toPos: Position,
        fromPosAccess: PositionAccess,
        toPosAccess: PositionAccess
    ): Maybe<List<Fact.TaintedTree>> {
        if (fromPosAccess.base != factReader.fact.ap.base) return Maybe.none()

        check(fromPosAccess is PositionAccess.Base) { "Unexpected copy-from base: $fromPosAccess" }

        val fromPositionType = positionTypeResolver.resolve(fromPos)
        val toPositionType = positionTypeResolver.resolve(toPos)

        val fact = factTypeChecker.filterFactByLocalType(fromPositionType, factReader.fact)
            ?: return Maybe.some(emptyList())

        val copyFacts = mutableListOf<Fact.TaintedTree>()

        if (fromPos is This) {
            check(toPos !is This)
            check(fromPositionType != null) { "Method instance has no type: $method" }

            if (factReader.containsAccessor(FinalAccessor)) {
                val toFinalAccess = when (toPosAccess) {
                    is PositionAccess.Base -> AccessNode.create(isFinal = true)
                    is PositionAccess.ArrayElement -> AccessNode.create(isFinal = true).addParent(ElementAccessor)
                }

                val finalAp = AccessTree(
                    toPosAccess.base,
                    toFinalAccess,
                    fact.ap.exclusions
                )
                copyFacts += fact.changeAP(finalAp)
            }

            if (factReader.containsAccessor(ruleStorageField)) {
                val access = fact.ap.access.getChild(ruleStorageField) ?: error("Impossible")

                val toAccess = when (toPosAccess) {
                    is PositionAccess.Base -> access
                    is PositionAccess.ArrayElement -> access.addParent(ElementAccessor)
                }

                val ap = AccessTree(toPosAccess.base, toAccess, fact.ap.exclusions)
                copyFacts += fact.changeAP(ap)
            }

            val remainingThisAccess = fact.ap.access.clearChild(FinalAccessor).clearChild(ruleStorageField)
            if (!remainingThisAccess.isEmpty) {
                if (toPositionType == null || fromPositionType.isAssignable(toPositionType)) {
                    val remainingAp = AccessTree(toPosAccess.base, remainingThisAccess, fact.ap.exclusions)
                    copyFacts += fact.changeAP(remainingAp)
                }
            }
        } else if (toPos is This) {
            check(toPosAccess is PositionAccess.Base) { "Unexpected copy-to base: $toPosAccess" }

            if (factReader.containsAccessor(FinalAccessor)) {
                val thisAp = AccessTree(
                    toPosAccess.base,
                    AccessNode.create(isFinal = true),
                    fact.ap.exclusions
                )
                copyFacts += fact.changeAP(thisAp)
            }

            val nonFinalAp = fact.ap.access.clearChild(FinalAccessor)
            if (!nonFinalAp.isEmpty) {
                check(toPositionType != null) { "Method instance has no type: $method" }

                val apAccess = if (fromPositionType != null && fromPositionType.isAssignable(toPositionType)) {
                    nonFinalAp
                } else {
                    nonFinalAp.addParent(ruleStorageField)
                }

                val thisStorageAp = AccessTree(toPosAccess.base, apAccess, fact.ap.exclusions)
                copyFacts += fact.changeAP(thisStorageAp)
            }
        } else {
            val toAccess = when (toPosAccess) {
                is PositionAccess.Base -> fact.ap.access
                is PositionAccess.ArrayElement -> fact.ap.access.addParent(ElementAccessor)
            }
            val ap = AccessTree(toPosAccess.base, toAccess, fact.ap.exclusions)
            copyFacts += fact.changeAP(ap)
        }

        val wellTypedCopies = copyFacts.mapNotNull {
            factTypeChecker.filterFactByLocalType(toPositionType, it)
        }

        if (wellTypedCopies.isEmpty()) {
            return Maybe.none()
        }

        return Maybe.some(listOf(fact) + wellTypedCopies)
    }

    fun evaluate(action: RemoveAllMarks): Maybe<List<Fact.TaintedTree>> =
        positionResolver.resolve(action.position).flatMap { variable ->
            removeFact(variable)
        }

    fun evaluate(action: RemoveMark): Maybe<List<Fact.TaintedTree>> {
        if (factReader.fact.mark != action.mark) return Maybe.none()
        return positionResolver.resolve(action.position).flatMap { variable ->
            removeFact(variable)
        }
    }

    private fun removeFact(from: PositionAccess): Maybe<List<Fact.TaintedTree>> {
        if (from.base != factReader.fact.ap.base) return Maybe.none()

        when (from) {
            is PositionAccess.Base -> {
                return Maybe.some(emptyList())
            }

            is PositionAccess.ArrayElement -> TODO("Remove from array access")
        }
    }

    companion object {
        private val ruleStorageField = FieldAccessor(
            className = JAVA_OBJECT, // todo: more precise storage
            fieldName = "<rule-storage>",
            fieldType = JAVA_OBJECT
        )
    }
}

class TaintSourceActionEvaluator(
    private val positionResolver: PositionResolver<Maybe<List<PositionAccess>>>
) {
    fun evaluate(action: AssignMark): Maybe<List<Fact.TaintedTree>> =
        positionResolver.resolve(action.position).flatFmap { variable ->
            val ap = mkAccessPath(variable)
            listOf(Fact.TaintedTree(action.mark, ap))
        }

    private fun mkAccessPath(position: PositionAccess): AccessTree {
        val accessor = when (position) {
            is PositionAccess.Base -> AccessNode.create(isFinal = true)
            is PositionAccess.ArrayElement -> AccessNode.create(isFinal = true).addParent(ElementAccessor)
        }

        return AccessTree(position.base, accessor, ExclusionSet.Universe)
    }
}
