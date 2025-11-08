package org.opentaint.dataflow.ap.ifds.taint

import org.opentaint.ir.api.common.CommonType
import org.opentaint.ir.taint.configuration.AssignMark
import org.opentaint.ir.taint.configuration.Condition
import org.opentaint.ir.taint.configuration.ConditionVisitor
import org.opentaint.ir.taint.configuration.CopyAllMarks
import org.opentaint.ir.taint.configuration.CopyMark
import org.opentaint.ir.taint.configuration.Position
import org.opentaint.ir.taint.configuration.PositionResolver
import org.opentaint.ir.taint.configuration.RemoveAllMarks
import org.opentaint.ir.taint.configuration.RemoveMark
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.ir.taint.configuration.TaintMark
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactReader
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.FinalFactReader
import org.opentaint.dataflow.ap.ifds.InitialFactReader
import org.opentaint.dataflow.ap.ifds.PositionAccess
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.mkAccessPath
import org.opentaint.dataflow.ap.ifds.readPosition
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker.FactAssumption
import org.opentaint.dataflow.ap.ifds.trace.TaintRulePrecondition
import org.opentaint.util.Maybe
import org.opentaint.util.flatFmap
import org.opentaint.util.flatMap
import org.opentaint.util.fmap

interface FactAwareConditionEvaluator : ConditionVisitor<Boolean> {
    fun evalWithAssumptionsCheck(condition: Condition): Boolean
    fun assumptionsPossible(): Boolean
    fun facts(): List<InitialFactAp>
    fun evalContainsMark(factReader: FactReader, mark: TaintMark, variable: PositionAccess): Boolean
}

data class ResultWithFactAssumptions(
    val result: Boolean,
    val assumptions: Set<FactAssumption>,
)

interface FactAwareConditionEvaluatorWithAssumptions : ConditionVisitor<List<ResultWithFactAssumptions>> {
    fun evalWithAssumptions(condition: Condition): List<ResultWithFactAssumptions>
}

interface PassActionEvaluator<T> {
    fun evaluate(rule: TaintConfigurationItem, action: CopyAllMarks): Maybe<List<T>>
    fun evaluate(rule: TaintConfigurationItem, action: CopyMark): Maybe<List<T>>
    fun evaluate(rule: TaintConfigurationItem, action: RemoveAllMarks): Maybe<List<T>>
    fun evaluate(rule: TaintConfigurationItem, action: RemoveMark): Maybe<List<T>>
}

class TaintPassActionEvaluator(
    private val apManager: ApManager,
    private val positionResolver: PositionResolver<Maybe<List<PositionAccess>>>,
    private val factTypeChecker: FactTypeChecker,
    private val factReader: FinalFactReader,
    private val positionTypeResolver: PositionResolver<CommonType?>
) : PassActionEvaluator<FinalFactAp> {
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
                // Position can be filtered out by the type checker
                return Maybe.none()
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
) : PassActionEvaluator<TaintRulePrecondition> {
    override fun evaluate(rule: TaintConfigurationItem, action: CopyAllMarks): Maybe<List<TaintRulePrecondition>> =
        positionResolver.resolve(action.from).flatMap { from ->
            positionResolver.resolve(action.to).flatMap { to ->
                copyAllFactsPrecondition(from, to).fmap { facts ->
                    facts.map { TaintRulePrecondition.Pass(rule, action, it) }
                }
            }
        }

    override fun evaluate(rule: TaintConfigurationItem, action: CopyMark): Maybe<List<TaintRulePrecondition>> =
        positionResolver.resolve(action.from).flatMap { from ->
            positionResolver.resolve(action.to).flatMap { to ->
                copyFinalFactPrecondition(from, to, action.mark).fmap { facts ->
                    facts.map { TaintRulePrecondition.Pass(rule, action, it) }
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

    override fun evaluate(rule: TaintConfigurationItem, action: RemoveAllMarks): Maybe<List<TaintRulePrecondition>> =
        Maybe.none()

    override fun evaluate(rule: TaintConfigurationItem, action: RemoveMark): Maybe<List<TaintRulePrecondition>> =
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