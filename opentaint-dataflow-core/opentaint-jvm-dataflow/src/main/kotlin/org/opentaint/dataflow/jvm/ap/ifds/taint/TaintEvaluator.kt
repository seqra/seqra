package org.opentaint.dataflow.jvm.ap.ifds.taint

import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.trace.TaintRulePrecondition
import org.opentaint.dataflow.configuration.jvm.And
import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.configuration.jvm.Condition
import org.opentaint.dataflow.configuration.jvm.ConditionVisitor
import org.opentaint.dataflow.configuration.jvm.ConstantEq
import org.opentaint.dataflow.configuration.jvm.ConstantGt
import org.opentaint.dataflow.configuration.jvm.ConstantLt
import org.opentaint.dataflow.configuration.jvm.ConstantMatches
import org.opentaint.dataflow.configuration.jvm.ConstantTrue
import org.opentaint.dataflow.configuration.jvm.ContainsMark
import org.opentaint.dataflow.configuration.jvm.CopyAllMarks
import org.opentaint.dataflow.configuration.jvm.CopyMark
import org.opentaint.dataflow.configuration.jvm.IsConstant
import org.opentaint.dataflow.configuration.jvm.Not
import org.opentaint.dataflow.configuration.jvm.Or
import org.opentaint.dataflow.configuration.jvm.Position
import org.opentaint.dataflow.configuration.jvm.PositionResolver
import org.opentaint.dataflow.configuration.jvm.RemoveAllMarks
import org.opentaint.dataflow.configuration.jvm.RemoveMark
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationItem
import org.opentaint.dataflow.configuration.jvm.TaintMark
import org.opentaint.dataflow.configuration.jvm.TypeMatches
import org.opentaint.dataflow.configuration.jvm.TypeMatchesPattern
import org.opentaint.util.Maybe
import org.opentaint.util.flatFmap
import org.opentaint.util.flatMap
import org.opentaint.util.fmap
import org.opentaint.util.onNone

interface FactAwareConditionEvaluator : ConditionVisitor<FactAwareConditionEvaluator.EvaluationResult> {
    fun evalWithAssumptionsCheck(condition: Condition): Boolean
    fun assumptionsPossible(): Boolean
    fun facts(): List<InitialFactAp>

    enum class EvaluationResult {
        True, False, Unknown
    }

    fun withoutAssumptions(): ConditionVisitor<Boolean> = object : ConditionVisitor<Boolean> {
        private fun eval(condition: Condition): Boolean =
            this@FactAwareConditionEvaluator.evalWithAssumptionsCheck(condition)

        override fun visit(condition: ConstantTrue): Boolean = eval(condition)
        override fun visit(condition: Not): Boolean = eval(condition)
        override fun visit(condition: And): Boolean = eval(condition)
        override fun visit(condition: Or): Boolean = eval(condition)
        override fun visit(condition: IsConstant): Boolean = eval(condition)
        override fun visit(condition: ConstantEq): Boolean = eval(condition)
        override fun visit(condition: ConstantLt): Boolean = eval(condition)
        override fun visit(condition: ConstantGt): Boolean = eval(condition)
        override fun visit(condition: ConstantMatches): Boolean = eval(condition)
        override fun visit(condition: ContainsMark): Boolean = eval(condition)
        override fun visit(condition: TypeMatches): Boolean = eval(condition)
        override fun visit(condition: TypeMatchesPattern): Boolean = eval(condition)
    }
}

interface PassActionEvaluator<T> {
    fun evaluate(rule: TaintConfigurationItem, action: CopyAllMarks): Maybe<List<T>>
    fun evaluate(rule: TaintConfigurationItem, action: CopyMark): Maybe<List<T>>
}

class TaintPassActionEvaluator(
    private val apManager: ApManager,
    private val positionResolver: PositionResolver<Maybe<List<PositionAccess>>>,
    private val factTypeChecker: FactTypeChecker,
    private val factReader: FinalFactReader,
    private val positionTypeResolver: PositionResolver<JIRType?>
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

        val copiedFact = apManager.mkAccessPath(toPosAccess, factReader.factAp.exclusions, markRestriction.name)

        val toPositionBaseType = positionTypeResolver.resolve(toPos)
        val wellTypedCopy = factTypeChecker.filterFactByLocalType(toPositionBaseType, copiedFact)
            ?: return Maybe.none()

        return Maybe.some(listOf(factReader.factAp) + wellTypedCopy)
    }
}

class TaintCleanActionEvaluator(
    private val positionResolver: PositionResolver<Maybe<List<PositionAccess>>>,
) {
    fun evaluate(initialFact: FinalFactReader?, action: RemoveAllMarks): FinalFactReader? {
        val resolved = positionResolver.resolve(action.position)
        resolved.onNone { return initialFact }
        return resolved.getOrThrow().fold(initialFact) { fact, variable ->
            removeAllFacts(fact, variable)
        }
    }

    fun evaluate(initialFact: FinalFactReader?, action: RemoveMark): FinalFactReader? {
        val resolved = positionResolver.resolve(action.position)
        resolved.onNone { return initialFact }
        return resolved.getOrThrow().fold(initialFact) { fact, variable ->
            removeFinalFact(fact, variable, action.mark)
        }
    }

    private fun removeAllFacts(
        fact: FinalFactReader?,
        from: PositionAccess
    ): FinalFactReader? {
        if (fact == null) return null

        if (!fact.containsPosition(from)) return fact

        if (from !is PositionAccess.Simple) {
            TODO("Remove from complex: $from")
        }

        return null
    }

    private fun removeFinalFact(
        fact: FinalFactReader?,
        from: PositionAccess,
        markRestriction: TaintMark
    ): FinalFactReader? {
        if (fact == null) return null

        if (!fact.containsPositionWithTaintMark(from, markRestriction)) return fact

        if (from !is PositionAccess.Simple) {
            TODO("Remove from complex: $from")
        }

        val factWithoutFinal = fact.factAp.clearAccessor(TaintMarkAccessor(markRestriction.name)) ?: return null
        return fact.replaceFact(factWithoutFinal)
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
}

interface SourceActionEvaluator<T> {
    fun evaluate(rule: TaintConfigurationItem, action: AssignMark): Maybe<List<T>>
}

class TaintSourceActionEvaluator(
    private val apManager: ApManager,
    private val exclusion: ExclusionSet,
    private val positionResolver: PositionResolver<Maybe<List<PositionAccess>>>
) : SourceActionEvaluator<FinalFactAp> {
    override fun evaluate(rule: TaintConfigurationItem, action: AssignMark): Maybe<List<FinalFactAp>> =
        positionResolver.resolve(action.position).flatFmap { variable ->
            val ap = apManager.mkAccessPath(variable, exclusion, action.mark.name)
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