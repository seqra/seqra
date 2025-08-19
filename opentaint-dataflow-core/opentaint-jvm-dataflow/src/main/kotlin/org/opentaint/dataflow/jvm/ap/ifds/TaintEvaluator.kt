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
import org.opentaint.dataflow.jvm.util.JIRTraits

class FactReader(val fact: Fact.FinalFact) {
    private var refinement: ExclusionSet = ExclusionSet.Empty
    val hasRefinement: Boolean get() = refinement !is ExclusionSet.Empty

    fun containsFinalPosition(position: PositionAccess): Boolean =
        containsPosition(PositionAccess.Complex(position, FinalAccessor))

    fun containsPosition(position: PositionAccess): Boolean {
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
                        return false
                    }
                    break
                }
            }
        }

        var node = fact.ap
        while (accessors.isNotEmpty()) {
            val accessor = accessors.removeLast()

            if (node.startsWithAccessor(accessor)) {
                if (accessor is FinalAccessor) return true
                node = node.readAccessor(accessor) ?: error("Impossible")
                continue
            }

            if (node.isAbstract() && !fact.ap.exclusions.contains(accessor)) {
                refinement = refinement.add(accessor)
            }

            return false
        }

        return true
    }

    fun refineFact(fact: Fact.InitialFact): Fact.InitialFact {
        if (!hasRefinement) return fact
        val refinedAp = fact.ap.replaceExclusions(fact.ap.exclusions.union(refinement))
        return fact.changeAP(refinedAp)
    }

    fun refineFact(fact: Fact.FinalFact): Fact.FinalFact {
        if (!hasRefinement) return fact
        val refinedAp = fact.ap.replaceExclusions(fact.ap.exclusions.union(refinement))
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
    private val apManager: ApManager,
    private val method: JIRMethod,
    private val positionResolver: PositionResolver<Maybe<List<PositionAccess>>>,
    private val factTypeChecker: FactTypeChecker,
    private val factReader: FactReader
) {
    private val positionTypeResolver = MethodPositionBaseTypeResolver(method)

    fun evaluate(action: CopyAllMarks): Maybe<List<Fact.FinalFact>> =
        positionResolver.resolve(action.from).flatMap { from ->
            positionResolver.resolve(action.to).flatMap { to ->
                copyAllFacts(action.from, action.to, from, to)
            }
        }

    fun evaluate(action: CopyMark): Maybe<List<Fact.FinalFact>> {
        if (factReader.fact.mark != action.mark) return Maybe.none()
        return positionResolver.resolve(action.from).flatMap { from ->
            positionResolver.resolve(action.to).flatMap { to ->
                copyFinalFact(action.to, from, to)
            }
        }
    }

    private fun copyAllFacts(
        fromPos: Position,
        toPos: Position,
        fromPosAccess: PositionAccess,
        toPosAccess: PositionAccess
    ): Maybe<List<Fact.FinalFact>> {
        if (!factReader.containsPosition(fromPosAccess)) return Maybe.none()

        val fromPositionBaseType = positionTypeResolver.resolve(fromPos)
        val toPositionBaseType = positionTypeResolver.resolve(toPos)

        val fact = factTypeChecker.filterFactByLocalType(fromPositionBaseType, factReader.fact)
            ?: return Maybe.some(emptyList())

        val factApDelta = readPosition(fact.ap, fromPosAccess)

        val ap = mkAccessPath(toPosAccess, factApDelta, fact.ap.exclusions)
        val copiedFact = fact.changeAP(ap)

        val wellTypedCopy = factTypeChecker.filterFactByLocalType(toPositionBaseType, copiedFact)
        if (wellTypedCopy == null) {
            return Maybe.none()
        }

        return Maybe.some(listOf(factReader.fact) + wellTypedCopy)
    }

    private fun copyFinalFact(
        toPos: Position,
        fromPosAccess: PositionAccess,
        toPosAccess: PositionAccess
    ): Maybe<List<Fact.FinalFact>> {
        if (!factReader.containsFinalPosition(fromPosAccess)) return Maybe.none()

        val toFinalAp = apManager.mkAccessPath(toPosAccess, factReader.fact.ap.exclusions)
        val copiedFact = factReader.fact.changeAP(toFinalAp)

        val toPositionBaseType = positionTypeResolver.resolve(toPos)
        val wellTypedCopy = factTypeChecker.filterFactByLocalType(toPositionBaseType, copiedFact)
        if (wellTypedCopy == null) {
            return Maybe.none()
        }

        return Maybe.some(listOf(factReader.fact) + wellTypedCopy)
    }


    fun evaluate(action: RemoveAllMarks): Maybe<List<Fact.FinalFact>> =
        positionResolver.resolve(action.position).flatMap { variable ->
            removeAllFacts(variable)
        }

    fun evaluate(action: RemoveMark): Maybe<List<Fact.FinalFact>> {
        if (factReader.fact.mark != action.mark) return Maybe.none()
        return positionResolver.resolve(action.position).flatMap { variable ->
            removeFinalFact(variable)
        }
    }

    private fun removeAllFacts(from: PositionAccess): Maybe<List<Fact.FinalFact>> {
        if (!factReader.containsPosition(from)) return Maybe.none()

        if (from !is PositionAccess.Simple) {
            TODO("Remove from complex: $from")
        }

        return Maybe.some(emptyList())
    }

    private fun removeFinalFact(from: PositionAccess): Maybe<List<Fact.FinalFact>> {
        if (!factReader.containsFinalPosition(from)) return Maybe.none()

        if (from !is PositionAccess.Simple) {
            TODO("Remove from complex: $from")
        }

        val apWithoutFinal = factReader.fact.ap.clearAccessor(FinalAccessor)
        val factWithoutFinal = apWithoutFinal?.let { factReader.fact.changeAP(it) }

        return Maybe.some(listOfNotNull(factWithoutFinal))
    }

    private fun readPosition(ap: FinalFactAp, position: PositionAccess): FinalFactAp {
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
            check(result.startsWithAccessor(accessor))
            result = if (accessor is FinalAccessor) {
                apManager.createFinalAp(ap.base, ap.exclusions)
            } else {
                result.readAccessor(accessor) ?: error("Impossible")
            }
        }

        return result
    }
}

class TaintSourceActionEvaluator(
    private val apManager: ApManager,
    private val positionResolver: PositionResolver<Maybe<List<PositionAccess>>>
) {
    fun evaluate(action: AssignMark): Maybe<List<Fact.FinalFact>> =
        positionResolver.resolve(action.position).flatFmap { variable ->
            val ap = apManager.mkAccessPath(variable, ExclusionSet.Universe)
            listOf(Fact.FinalFact(action.mark, ap))
        }
}

private fun ApManager.mkAccessPath(
    position: PositionAccess,
    exclusionSet: ExclusionSet
): FinalFactAp = mkAccessPath(
    position,
    // we use stub base and exclusion here
    createFinalAp(AccessPathBase.This, ExclusionSet.Universe),
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
