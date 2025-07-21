package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.ext.JAVA_OBJECT
import org.opentaint.ir.api.jvm.ext.ifArrayGetElementType
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

    fun containsFinalPosition(position: PositionAccess): Boolean =
        containsPosition(PositionAccess.Complex(position, FinalAccessor))

    fun containsPosition(position: PositionAccess): Boolean =
        factAtPosition(position) != null

    private fun factAtPosition(position: PositionAccess): AccessNode? {
        val accessors = mutableListOf<Accessor>()
        var currentPosition = position

        while (true) {
            when (currentPosition) {
                is PositionAccess.Complex -> {
                    accessors.add(currentPosition.accessor)
                    currentPosition = currentPosition.base
                }

                is PositionAccess.Simple -> {
                    if (fact.ap.base != currentPosition.base) {
                        return null
                    }
                    break
                }
            }
        }

        var node = fact.ap.access
        while (accessors.isNotEmpty()) {
            val accessor = accessors.removeLast()

            if (node.contains(accessor)) {
                if (accessor is FinalAccessor) return AccessNode.create(isFinal = true)
                node = node.getChild(accessor) ?: error("Impossible")
                continue
            }

            if (node.isAbstract) {
                refinement = refinement.add(accessor)
            }

            return null
        }

        return node
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
    traits: JIRTraits,
    private val facts: Iterable<FactReader>,
    private val accessPathResolver: PositionResolver<Maybe<List<PositionAccess>>>,
    positionResolver: PositionResolver<Maybe<JIRValue>>,
) : BasicConditionEvaluator(positionResolver, traits) {
    constructor(
        traits: JIRTraits,
        fact: FactReader,
        accessPathResolver: PositionResolver<Maybe<List<PositionAccess>>>,
        positionResolver: PositionResolver<Maybe<JIRValue>>
    ) : this(traits, listOf(fact), accessPathResolver, positionResolver)

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

        if (factReader.containsFinalPosition(variable)) {
            hasEvaluatedContainsMark = true
            return true
        }

        return false
    }
}

class FactIgnoreConditionEvaluator(
    traits: JIRTraits,
    positionResolver: PositionResolver<Maybe<JIRValue>>
) : BasicConditionEvaluator(positionResolver, traits) {
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
    private val positionTypeResolver = MethodPositionBaseTypeResolver(method)

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
        if (!factReader.containsPosition(fromPosAccess)) return Maybe.none()

        val fromPositionBaseType = positionTypeResolver.resolve(fromPos)
        val toPositionBaseType = positionTypeResolver.resolve(toPos)

        val fact = factTypeChecker.filterFactByLocalType(fromPositionBaseType, factReader.fact)
            ?: return Maybe.some(emptyList())

        val factApDelta = readPosition(fact.ap.access, fromPosAccess)

        val copyFacts = mutableListOf<Fact.TaintedTree>()

        if (fromPos is This) {
            check(toPos !is This)
            check(fromPosAccess is PositionAccess.Simple)
            check(fromPositionBaseType != null) { "Method instance has no type: $method" }

            if (factReader.containsFinalPosition(fromPosAccess)) {
                val ap = mkAccessPath(toPosAccess, AccessNode.create(isFinal = true), fact.ap.exclusions)
                copyFacts += fact.changeAP(ap)
            }

            val ruleStoragePosition = PositionAccess.Complex(fromPosAccess, ruleStorageField)
            if (factReader.containsPosition(ruleStoragePosition)) {
                val ruleStorageAccess = readPosition(fact.ap.access, ruleStoragePosition)
                val ap = mkAccessPath(toPosAccess, ruleStorageAccess, fact.ap.exclusions)
                copyFacts += fact.changeAP(ap)
            }

            val remainingThisAccess = fact.ap.access.clearChild(FinalAccessor).clearChild(ruleStorageField)
            if (!remainingThisAccess.isEmpty) {
                val toPositionType = resolvePositionType(toPositionBaseType, toPosAccess)
                if (toPositionType == null || fromPositionBaseType.isAssignable(toPositionType)) {
                    val ap = mkAccessPath(toPosAccess, remainingThisAccess, fact.ap.exclusions)
                    copyFacts += fact.changeAP(ap)
                }
            }
        } else if (toPos is This) {
            check(fromPos !is This)
            check(toPosAccess is PositionAccess.Simple) { "Unexpected copy-to base: $toPosAccess" }
            check(toPositionBaseType != null) { "Method instance has no type: $method" }

            if (factReader.containsFinalPosition(fromPosAccess)) {
                val thisAp = mkAccessPath(toPosAccess, AccessNode.create(isFinal = true), fact.ap.exclusions)
                copyFacts += fact.changeAP(thisAp)
            }

            val nonFinalAp = factApDelta.clearChild(FinalAccessor)
            if (!nonFinalAp.isEmpty) {
                val fromPositionType = resolvePositionType(fromPositionBaseType, fromPosAccess)
                val apAccess = if (fromPositionType != null && fromPositionType.isAssignable(toPositionBaseType)) {
                    nonFinalAp
                } else {
                    nonFinalAp.addParent(ruleStorageField)
                }

                val thisStorageAp = mkAccessPath(toPosAccess, apAccess, fact.ap.exclusions)
                copyFacts += fact.changeAP(thisStorageAp)
            }
        } else {
            val ap = mkAccessPath(toPosAccess, factApDelta, fact.ap.exclusions)
            copyFacts += fact.changeAP(ap)
        }

        val wellTypedCopies = copyFacts.mapNotNull {
            factTypeChecker.filterFactByLocalType(toPositionBaseType, it)
        }

        if (wellTypedCopies.isEmpty()) {
            return Maybe.none()
        }

        return Maybe.some(listOf(factReader.fact) + wellTypedCopies)
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
        if (!factReader.containsPosition(from)) return Maybe.none()

        if (from !is PositionAccess.Simple) {
            TODO("Remove from complex: $from")
        }

        return Maybe.some(emptyList())
    }

    private fun resolvePositionType(baseType: JIRType?, position: PositionAccess): JIRType? =
        when (position) {
            is PositionAccess.Complex -> when (val access = position.accessor) {
                ElementAccessor -> resolvePositionType(baseType, position.base)?.ifArrayGetElementType
                is FieldAccessor -> positionTypeResolver.cp.findTypeOrNull(access.fieldType)
                FinalAccessor -> null
            }

            is PositionAccess.Simple -> baseType
        }

    private fun readPosition(ap: AccessNode, position: PositionAccess): AccessNode {
        val accessors = mutableListOf<Accessor>()
        var currentPosition = position
        while (true) {
            when (currentPosition) {
                is PositionAccess.Complex -> {
                    accessors.add(currentPosition.accessor)
                    currentPosition = currentPosition.base
                }

                is PositionAccess.Simple -> break
            }
        }

        var result = ap
        while (accessors.isNotEmpty()) {
            val accessor = accessors.removeLast()
            check(result.contains(accessor))
            result = if (accessor is FinalAccessor) {
                AccessNode.create(isFinal = true)
            } else {
                result.getChild(accessor) ?: error("Impossible")
            }
        }

        return result
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
            val ap = mkAccessPath(variable, AccessNode.create(isFinal = true), ExclusionSet.Universe)
            listOf(Fact.TaintedTree(action.mark, ap))
        }
}

private fun mkAccessPath(position: PositionAccess, basicNode: AccessNode, exclusionSet: ExclusionSet): AccessTree {
    var currentPosition = position
    var result = basicNode
    while (true) {
        when (currentPosition) {
            is PositionAccess.Complex -> {
                result = result.addParent(currentPosition.accessor)
                currentPosition = currentPosition.base
            }

            is PositionAccess.Simple -> {
                return AccessTree(currentPosition.base, result, exclusionSet)
            }
        }
    }
}
