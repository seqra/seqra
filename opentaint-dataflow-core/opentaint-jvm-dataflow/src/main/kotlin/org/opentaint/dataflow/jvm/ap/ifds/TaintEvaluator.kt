package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.taint.configuration.And
import org.opentaint.ir.taint.configuration.AssignMark
import org.opentaint.ir.taint.configuration.Condition
import org.opentaint.ir.taint.configuration.ContainsMark
import org.opentaint.ir.taint.configuration.CopyAllMarks
import org.opentaint.ir.taint.configuration.CopyMark
import org.opentaint.ir.taint.configuration.Not
import org.opentaint.ir.taint.configuration.Or
import org.opentaint.ir.taint.configuration.Position
import org.opentaint.ir.taint.configuration.PositionResolver
import org.opentaint.ir.taint.configuration.RemoveAllMarks
import org.opentaint.ir.taint.configuration.RemoveMark
import org.opentaint.ir.taint.configuration.TaintMark
import org.opentaint.dataflow.config.BasicConditionEvaluator
import org.opentaint.dataflow.ifds.Maybe
import org.opentaint.dataflow.ifds.flatFmap
import org.opentaint.dataflow.ifds.flatMap
import org.opentaint.dataflow.ifds.onSome
import org.opentaint.dataflow.jvm.ap.ifds.access.ApManager
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.jvm.util.JIRTraits

class FactReader(val factAp: FinalFactAp) {
    private var refinement: ExclusionSet = ExclusionSet.Empty
    val hasRefinement: Boolean get() = refinement !is ExclusionSet.Empty

    fun containsPositionWithTaintMark(position: PositionAccess, mark: TaintMark): Boolean {
        return containsPosition(PositionAccess.Complex(position, TaintMarkAccessor(mark)))
    }

    fun containsPosition(position: PositionAccess): Boolean =
        readPosition(factAp, position) { node, accessor ->
            if (node.isAbstract() && !factAp.exclusions.contains(accessor)) {
                refinement = refinement.add(accessor)
            }
        } != null

    fun refineFact(factAp: InitialFactAp): InitialFactAp {
        if (!hasRefinement) return factAp
        val refinedAp = factAp.replaceExclusions(factAp.exclusions.union(refinement))
        return refinedAp
    }

    fun refineFact(factAp: FinalFactAp): FinalFactAp {
        if (!hasRefinement) return factAp
        val refinedAp = factAp.replaceExclusions(factAp.exclusions.union(refinement))
        return refinedAp
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

    override fun visit(condition: Not): Boolean {
        if (condition.arg is ContainsMark) {
            return true
        }
        return super.visit(condition)
    }

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
        if (factReader.containsPositionWithTaintMark(variable, mark)) {
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
    private val apManager: ApManager,
    method: JIRMethod,
    private val positionResolver: PositionResolver<Maybe<List<PositionAccess>>>,
    private val factTypeChecker: FactTypeChecker,
    private val factReader: FactReader,
) {
    private val positionTypeResolver = MethodPositionBaseTypeResolver(method)

    fun evaluate(action: CopyAllMarks): Maybe<List<FinalFactAp>> =
        positionResolver.resolve(action.from).flatMap { from ->
            positionResolver.resolve(action.to).flatMap { to ->
                copyAllFacts(action.from, action.to, from, to)
            }
        }

    fun evaluate(action: CopyMark): Maybe<List<FinalFactAp>> {
        return positionResolver.resolve(action.from).flatMap { from ->
            positionResolver.resolve(action.to).flatMap { to ->
                copyFinalFact(action.to, from, to, action.mark)
            }
        }
    }

    private fun copyAllFacts(
        fromPos: Position,
        toPos: Position,
        fromPosAccess: PositionAccess,
        toPosAccess: PositionAccess,
    ): Maybe<List<FinalFactAp>> {
        if (!factReader.containsPosition(fromPosAccess)) {
            return Maybe.none()
        }

        val fromPositionBaseType = positionTypeResolver.resolve(fromPos)
        val toPositionBaseType = positionTypeResolver.resolve(toPos)

        val fact = factTypeChecker.filterFactByLocalType(fromPositionBaseType, factReader.factAp)
            ?: return Maybe.some(emptyList())

        val factApDelta = readPosition(fact, fromPosAccess) { _, _ ->
            error("Failed to read $fromPosAccess from $fact")
        }!!

        val copiedFact = mkAccessPath(toPosAccess, factApDelta, fact.exclusions)

        val wellTypedCopy = factTypeChecker.filterFactByLocalType(toPositionBaseType, copiedFact)
            ?: return Maybe.none()

        return Maybe.some(listOf(factReader.factAp) + wellTypedCopy)
    }

    private fun copyFinalFact(
        toPos: Position,
        fromPosAccess: PositionAccess,
        toPosAccess: PositionAccess,
        markRestriction: TaintMark
    ): Maybe<List<FinalFactAp>> {
        if (!factReader.containsPositionWithTaintMark(fromPosAccess, markRestriction)) return Maybe.none()

        val copiedFact = apManager.mkAccessPath(toPosAccess, factReader.factAp.exclusions, markRestriction)

        val toPositionBaseType = positionTypeResolver.resolve(toPos)
        val wellTypedCopy = factTypeChecker.filterFactByLocalType(toPositionBaseType, copiedFact)
            ?: return Maybe.none()

        return Maybe.some(listOf(factReader.factAp) + wellTypedCopy)
    }

    fun evaluate(action: RemoveAllMarks): Maybe<List<FinalFactAp>> =
        positionResolver.resolve(action.position).flatMap { variable ->
            removeAllFacts(variable)
        }

    fun evaluate(action: RemoveMark): Maybe<List<FinalFactAp>> {
        return positionResolver.resolve(action.position).flatMap { variable ->
            removeFinalFact(variable, action.mark)
        }
    }

    private fun removeAllFacts(from: PositionAccess): Maybe<List<FinalFactAp>> {
        if (!factReader.containsPosition(from)) return Maybe.none()

        if (from !is PositionAccess.Simple) {
            TODO("Remove from complex: $from")
        }

        return Maybe.some(emptyList())
    }

    private fun removeFinalFact(from: PositionAccess, markRestriction: TaintMark): Maybe<List<FinalFactAp>> {
        if (!factReader.containsPositionWithTaintMark(from, markRestriction)) return Maybe.none()

        if (from !is PositionAccess.Simple) {
            TODO("Remove from complex: $from")
        }

        val factWithoutFinal = factReader.factAp.clearAccessor(TaintMarkAccessor(markRestriction))

        return Maybe.some(listOfNotNull(factWithoutFinal))
    }
}

class TaintSourceActionEvaluator(
    private val apManager: ApManager,
    private val positionResolver: PositionResolver<Maybe<List<PositionAccess>>>
) {
    fun evaluate(action: AssignMark): Maybe<List<FinalFactAp>> =
        positionResolver.resolve(action.position).flatFmap { variable ->
            val ap = apManager.mkAccessPath(variable, ExclusionSet.Universe, action.mark)
            listOf(ap)
        }
}

private fun ApManager.mkAccessPath(
    position: PositionAccess,
    exclusionSet: ExclusionSet,
    mark: TaintMark,
): FinalFactAp = mkAccessPath(
    position,
    // we use stub base and exclusion here
    createFinalAp(AccessPathBase.This, ExclusionSet.Universe).prependAccessor(TaintMarkAccessor(mark)),
    exclusionSet
)

private fun mkAccessPath(position: PositionAccess, basicAp: FinalFactAp, exclusionSet: ExclusionSet): FinalFactAp {
    var currentPosition = position
    var result = basicAp
    while (true) {
        when (currentPosition) {
            is PositionAccess.Complex -> {
                result = result.prependAccessor(currentPosition.accessor)
                currentPosition = currentPosition.base
            }

            is PositionAccess.Simple -> {
                return result.rebase(currentPosition.base).replaceExclusions(exclusionSet)
            }
        }
    }
}


private inline fun readPosition(
    ap: FinalFactAp,
    position: PositionAccess,
    onMismatch: (FinalFactAp, Accessor) -> Unit
): FinalFactAp? {
    val accessors = mutableListOf<Accessor>()
    var currentPosition = position
    while (true) {
        when (currentPosition) {
            is PositionAccess.Complex -> {
                accessors.add(currentPosition.accessor)
                currentPosition = currentPosition.base
            }

            is PositionAccess.Simple -> {
                if (ap.base != currentPosition.base) {
                    return null
                }
                break
            }
        }
    }

    var result = ap
    while (accessors.isNotEmpty()) {
        val accessor = accessors.removeLast()

        if (!result.startsWithAccessor(accessor)) {
            onMismatch(result, accessor)
            return null
        }

        result =  result.readAccessor(accessor) ?: error("Impossible")
    }

    return result
}