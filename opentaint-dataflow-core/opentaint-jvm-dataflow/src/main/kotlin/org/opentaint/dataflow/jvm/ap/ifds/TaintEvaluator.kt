package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.taint.configuration.Action
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
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.ir.taint.configuration.TaintMark
import org.opentaint.dataflow.config.BasicConditionEvaluator
import org.opentaint.dataflow.ifds.Maybe
import org.opentaint.dataflow.ifds.flatFmap
import org.opentaint.dataflow.ifds.flatMap
import org.opentaint.dataflow.ifds.fmap
import org.opentaint.dataflow.ifds.onSome
import org.opentaint.dataflow.jvm.ap.ifds.access.ApManager
import org.opentaint.dataflow.jvm.ap.ifds.access.FactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.jvm.util.JIRTraits

interface FactReader {
    fun containsPosition(position: PositionAccess): Boolean
    fun createInitialFactWithTaintMark(position: PositionAccess, mark: TaintMark): InitialFactAp

    fun containsPositionWithTaintMark(position: PositionAccess, mark: TaintMark): Boolean {
        val positionWithMark = PositionAccess.Complex(position, TaintMarkAccessor(mark))
        val finalPositionWithMark = PositionAccess.Complex(positionWithMark, FinalAccessor)
        return containsPosition(finalPositionWithMark)
    }
}

class FinalFactReader(
    val factAp: FinalFactAp,
    val apManager: ApManager
): FactReader {
    private var refinement: ExclusionSet = ExclusionSet.Empty
    val hasRefinement: Boolean get() = refinement !is ExclusionSet.Empty

    override fun createInitialFactWithTaintMark(position: PositionAccess, mark: TaintMark): InitialFactAp {
        val positionWithMark = PositionAccess.Complex(position, TaintMarkAccessor(mark))
        return apManager.mkInitialAccessPath(positionWithMark, ExclusionSet.Universe)
    }

    override fun containsPosition(position: PositionAccess): Boolean =
        readPosition(
            ap = factAp,
            position = position,
            onMismatch = { node, accessor ->
                if (accessor != null && node.isAbstract() && !factAp.exclusions.contains(accessor)) {
                    refinement = refinement.add(accessor)
                }
                false
            },
            matchedNode = { true }
        )

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

class InitialFactReader(val fact: InitialFactAp, val apManager: ApManager): FactReader {
    override fun containsPosition(position: PositionAccess): Boolean =
        readPosition(
            ap = fact,
            position = position,
            onMismatch = { _, _ -> false },
            matchedNode = { true }
        )

    override fun createInitialFactWithTaintMark(position: PositionAccess, mark: TaintMark): InitialFactAp {
        val positionWithMark = PositionAccess.Complex(position, TaintMarkAccessor(mark))
        return apManager.mkInitialAccessPath(positionWithMark, ExclusionSet.Universe)
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
    private val evaluatedFacts = mutableListOf<InitialFactAp>()

    fun evalWithAssumptionsCheck(condition: Condition): Boolean {
        evaluatedFacts.clear()
        hasEvaluatedContainsMark = false
        return condition.accept(this)
    }

    fun assumptionsPossible(): Boolean = hasEvaluatedContainsMark

    fun facts(): List<InitialFactAp> = evaluatedFacts.toList()

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
            evaluatedFacts += factReader.createInitialFactWithTaintMark(variable, mark)
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

interface PassActionEvaluator<T> {
    fun evaluate(rule: TaintConfigurationItem, action: CopyAllMarks): Maybe<List<T>>
    fun evaluate(rule: TaintConfigurationItem, action: CopyMark): Maybe<List<T>>
    fun evaluate(rule: TaintConfigurationItem, action: RemoveAllMarks): Maybe<List<T>>
    fun evaluate(rule: TaintConfigurationItem, action: RemoveMark): Maybe<List<T>>
}

class TaintPassActionEvaluator(
    private val apManager: ApManager,
    method: JIRMethod,
    private val positionResolver: PositionResolver<Maybe<List<PositionAccess>>>,
    private val factTypeChecker: FactTypeChecker,
    private val factReader: FinalFactReader,
) : PassActionEvaluator<FinalFactAp> {
    private val positionTypeResolver = MethodPositionBaseTypeResolver(method)

    override fun evaluate(rule: TaintConfigurationItem, action: CopyAllMarks): Maybe<List<FinalFactAp>> =
        positionResolver.resolve(action.from).flatMap { from ->
            positionResolver.resolve(action.to).flatMap { to ->
                copyAllFacts(action.from, action.to, from, to)
            }
        }

    override fun evaluate(rule: TaintConfigurationItem, action: CopyMark): Maybe<List<FinalFactAp>> {
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

        val factApDelta = readPosition(
            ap = fact,
            position = fromPosAccess,
            onMismatch = { _, _ ->
                error("Failed to read $fromPosAccess from $fact")
            },
            matchedNode = { it }
        )

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

    override fun evaluate(rule: TaintConfigurationItem, action: RemoveAllMarks): Maybe<List<FinalFactAp>> =
        positionResolver.resolve(action.position).flatMap { variable ->
            removeAllFacts(variable)
        }

    override fun evaluate(rule: TaintConfigurationItem, action: RemoveMark): Maybe<List<FinalFactAp>> {
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


class TaintPassActionPreconditionEvaluator(
    private val positionResolver: PositionResolver<Maybe<List<PositionAccess>>>,
    private val factReader: InitialFactReader
) : PassActionEvaluator<TaintPassActionPreconditionEvaluator.Precondition> {
    data class Precondition(val rule: TaintConfigurationItem, val action: Action, val fact: InitialFactAp)

    override fun evaluate(rule: TaintConfigurationItem, action: CopyAllMarks): Maybe<List<Precondition>> =
        positionResolver.resolve(action.from).flatMap { from ->
            positionResolver.resolve(action.to).flatMap { to ->
                copyAllFactsPrecondition(from, to).fmap { facts ->
                    facts.map { Precondition(rule, action, it) }
                }
            }
        }

    override fun evaluate(rule: TaintConfigurationItem, action: CopyMark): Maybe<List<Precondition>> =
        positionResolver.resolve(action.from).flatMap { from ->
            positionResolver.resolve(action.to).flatMap { to ->
                copyFinalFactPrecondition(from, to, action.mark).fmap { facts ->
                    facts.map { Precondition(rule, action, it) }
                }
            }
        }

    private fun copyAllFactsPrecondition(
        fromPosAccess: PositionAccess,
        toPosAccess: PositionAccess
    ): Maybe<List<InitialFactAp>> {
        if (!factReader.containsPosition(toPosAccess)) return Maybe.none()

        val fact = factReader.fact
        val factApDelta = readPosition(
            ap = fact,
            position = toPosAccess,
            onMismatch = { _, _ ->
                error("Failed to read $fromPosAccess from $fact")
            },
            matchedNode = { it }
        )
        val preconditionFact = mkAccessPath(fromPosAccess, factApDelta, fact.exclusions)

        return Maybe.some(listOf(preconditionFact))
    }

    private fun copyFinalFactPrecondition(
        fromPosAccess: PositionAccess,
        toPosAccess: PositionAccess,
        mark: TaintMark
    ): Maybe<List<InitialFactAp>> {
        if (!factReader.containsPositionWithTaintMark(toPosAccess, mark)) return Maybe.none()

        val preconditionFact = factReader
            .createInitialFactWithTaintMark(fromPosAccess, mark)
            .replaceExclusions(factReader.fact.exclusions)

        return Maybe.some(listOf(preconditionFact))
    }

    override fun evaluate(rule: TaintConfigurationItem, action: RemoveAllMarks): Maybe<List<Precondition>> =
        Maybe.none()

    override fun evaluate(rule: TaintConfigurationItem, action: RemoveMark): Maybe<List<Precondition>> =
        Maybe.none()
}

interface SourceActionEvaluator<T> {
    fun evaluate(rule: TaintConfigurationItem, action: AssignMark): Maybe<List<T>>
}


class TaintSourceActionEvaluator(
    private val apManager: ApManager,
    private val positionResolver: PositionResolver<Maybe<List<PositionAccess>>>
) : SourceActionEvaluator<FinalFactAp> {
    override fun evaluate(rule: TaintConfigurationItem, action: AssignMark): Maybe<List<FinalFactAp>> =
        positionResolver.resolve(action.position).flatFmap { variable ->
            val ap = apManager.mkAccessPath(variable, ExclusionSet.Universe, action.mark)
            listOf(ap)
        }
}

class TaintSourceActionPreconditionEvaluator(
    private val positionResolver: PositionResolver<Maybe<List<PositionAccess>>>,
    private val factReader: InitialFactReader
) : SourceActionEvaluator<Pair<TaintConfigurationItem, AssignMark>> {
    override fun evaluate(
        rule: TaintConfigurationItem,
        action: AssignMark
    ): Maybe<List<Pair<TaintConfigurationItem, AssignMark>>> =
        positionResolver.resolve(action.position).flatMap { variable ->
            if (!factReader.containsPositionWithTaintMark(variable, action.mark)) return@flatMap Maybe.none()
            Maybe.some(listOf(rule to action))
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

private fun ApManager.mkInitialAccessPath(
    position: PositionAccess,
    exclusionSet: ExclusionSet
): InitialFactAp = mkAccessPath(
    position,
    // we use stub base and exclusion here
    createFinalInitialAp(AccessPathBase.This, ExclusionSet.Universe),
    exclusionSet
)

private fun mkAccessPath(position: PositionAccess, basicAp: FinalFactAp, exclusionSet: ExclusionSet): FinalFactAp =
    mkAccessPath(
        position = position,
        basicAp = basicAp,
        exclusionSet = exclusionSet,
        prependAccessor = { prependAccessor(it) },
        rebase = { rebase(it) },
        replaceExclusions = { replaceExclusions(it) }
    )

private fun mkAccessPath(position: PositionAccess, basicAp: InitialFactAp, exclusionSet: ExclusionSet): InitialFactAp =
    mkAccessPath(
        position = position,
        basicAp = basicAp,
        exclusionSet = exclusionSet,
        prependAccessor = { prependAccessor(it) },
        rebase = { rebase(it) },
        replaceExclusions = { replaceExclusions(it) }
    )

private fun <F : FactAp> mkAccessPath(
    position: PositionAccess,
    basicAp: F,
    exclusionSet: ExclusionSet,
    prependAccessor: F.(Accessor) -> F,
    rebase: F.(AccessPathBase) -> F,
    replaceExclusions: F.(ExclusionSet) -> F
): F {
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

private inline fun <R> readPosition(
    ap: FinalFactAp,
    position: PositionAccess,
    onMismatch: (FinalFactAp, Accessor?) -> R,
    matchedNode: (FinalFactAp) -> R
): R = readPosition(ap, position, onMismatch, { readAccessor(it) }, matchedNode)

private inline fun <R> readPosition(
    ap: InitialFactAp,
    position: PositionAccess,
    onMismatch: (InitialFactAp, Accessor?) -> R,
    matchedNode: (InitialFactAp) -> R
): R = readPosition(ap, position, onMismatch, { readAccessor(it) }, matchedNode)

private inline fun <F: FactAp, R> readPosition(
    ap: F,
    position: PositionAccess,
    onMismatch: (F, Accessor?) -> R,
    readAccessor: F.(Accessor) -> F?,
    matchedNode: (F) -> R
): R {
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
                    return onMismatch(ap, null)
                }
                break
            }
        }
    }

    var result = ap
    while (accessors.isNotEmpty()) {
        val accessor = accessors.removeLast()

        if (!result.startsWithAccessor(accessor)) {
            return onMismatch(result, accessor)
        }

        result =  result.readAccessor(accessor) ?: error("Impossible")
    }

    return matchedNode(result)
}
