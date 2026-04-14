package org.opentaint.dataflow.jvm.ap.ifds.taint

import mu.KotlinLogging
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.configuration.jvm.Action
import org.opentaint.dataflow.configuration.jvm.Argument
import org.opentaint.dataflow.configuration.jvm.AssignAction
import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.configuration.jvm.AssignMarkAnyField
import org.opentaint.dataflow.configuration.jvm.ClassStatic
import org.opentaint.dataflow.configuration.jvm.Condition
import org.opentaint.dataflow.configuration.jvm.CopyAllMarks
import org.opentaint.dataflow.configuration.jvm.CopyMark
import org.opentaint.dataflow.configuration.jvm.Position
import org.opentaint.dataflow.configuration.jvm.PositionAccessor
import org.opentaint.dataflow.configuration.jvm.PositionResolver
import org.opentaint.dataflow.configuration.jvm.PositionWithAccess
import org.opentaint.dataflow.configuration.jvm.RemoveAllMarks
import org.opentaint.dataflow.configuration.jvm.RemoveMark
import org.opentaint.dataflow.configuration.jvm.Result
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationItem
import org.opentaint.dataflow.configuration.jvm.TaintMark
import org.opentaint.dataflow.configuration.jvm.This
import org.opentaint.dataflow.jvm.ap.ifds.JIRFactTypeChecker
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionExpr
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.util.Maybe
import org.opentaint.util.flatMap
import org.opentaint.util.fmap

interface ConditionEvaluator<T> {
    fun eval(condition: Condition): T
}

interface FactAwareConditionEvaluator {
    fun evalWithAssumptionsCheck(condition: JIRMarkAwareConditionExpr): Boolean
    fun assumptionExpr(): JIRMarkAwareConditionExpr?
    fun facts(): List<InitialFactAp>
}

interface PassActionEvaluator<T> {
    fun evaluate(rule: TaintConfigurationItem, action: CopyAllMarks): Maybe<List<T>>
    fun evaluate(rule: TaintConfigurationItem, action: CopyMark): Maybe<List<T>>
}

data class EvaluatedPass(
    val rule: TaintConfigurationItem,
    val action: Action,
    val fact: FinalFactAp,
)

class TaintPassActionEvaluator(
    private val apManager: ApManager,
    private val factTypeChecker: JIRFactTypeChecker,
    private val factReader: FinalFactReader,
    private val positionTypeResolver: PositionResolver<JIRType?>,
) : PassActionEvaluator<EvaluatedPass> {
    override fun evaluate(rule: TaintConfigurationItem, action: CopyAllMarks): Maybe<List<EvaluatedPass>> =
        copyAllFacts(action.from, action.to, action.from.resolveAp(), action.to.resolveAp()).fmap { facts ->
            facts.map { EvaluatedPass(rule, action, it) }
        }

    override fun evaluate(rule: TaintConfigurationItem, action: CopyMark): Maybe<List<EvaluatedPass>> =
        copyFinalFact(action.to, action.from.resolveAp(), action.to.resolveAp(), action.mark).fmap { facts ->
            facts.map { EvaluatedPass(rule, action, it) }
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

        val toPositionBaseType = positionTypeResolver.resolve(toPos)

        val resultFact = mkAccessPath(toPosAccess, factApDelta, fact.exclusions)
        val wellTypedFact = resultFact.let { factTypeChecker.filterFactByLocalType(toPositionBaseType, it) }
        if (wellTypedFact == null) return Maybe.none()

        return Maybe.some(listOf(factReader.factAp) + wellTypedFact)
    }

    private fun copyFinalFact(
        toPos: Position,
        fromPosAccess: PositionAccess,
        toPosAccess: PositionAccess,
        markRestriction: TaintMark,
    ): Maybe<List<FinalFactAp>> {
        if (!factReader.containsPositionWithTaintMark(fromPosAccess, markRestriction)) return Maybe.none()

        val copiedFact = apManager.mkAccessPath(toPosAccess, factReader.factAp.exclusions, markRestriction.name)

        val toPositionBaseType = positionTypeResolver.resolve(toPos)
        val wellTypedCopy = factTypeChecker.filterFactByLocalType(toPositionBaseType, copiedFact)
            ?: return Maybe.none()

        return Maybe.some(listOf(factReader.factAp) + wellTypedCopy)
    }
}

data class EvaluatedCleanAction(
    val fact: FinalFactReader?,
    val action: ActionInfo?,
    val prev: EvaluatedCleanAction?,
) {
    data class ActionInfo(
        val rule: TaintConfigurationItem,
        val action: Action,
    )

    companion object {
        fun initial(fact: FinalFactReader) = EvaluatedCleanAction(
            action = null, fact = fact, prev = null
        )
    }
}

class TaintCleanActionEvaluator(
    private val positionTypeResolver: PositionResolver<JIRType?>,
) {
    fun evaluate(
        initialFact: EvaluatedCleanAction,
        rule: TaintConfigurationItem,
        action: RemoveAllMarks,
    ): List<EvaluatedCleanAction> {
        val variable = action.position.resolveAp()
        return removeAllFacts(initialFact, variable, rule, action)
    }

    fun evaluate(
        initialFact: EvaluatedCleanAction,
        rule: TaintConfigurationItem,
        action: RemoveMark,
    ): List<EvaluatedCleanAction> {
        val variable = action.position.resolveAp()
        val cleaned = removeFinalFact(initialFact, variable, action.mark, rule, action)

        val positionType = positionTypeResolver.resolve(action.position)
        if (positionType?.typeName != STRING) {
            return cleaned
        }

        val stringBytesVar = PositionWithAccess(action.position, stringBytes).resolveAp()
        return cleaned.flatMap { f ->
            removeFinalFact(f, stringBytesVar, action.mark, rule, action)
        }
    }

    private fun removeAllFacts(
        evc: EvaluatedCleanAction,
        from: PositionAccess,
        rule: TaintConfigurationItem,
        action: RemoveAllMarks,
    ): List<EvaluatedCleanAction> {
        val fact = evc.fact ?: return listOf(evc)

        if (!fact.containsPosition(from)) return listOf(evc)

        if (from is PositionAccess.Simple) {
            val actionInfo = EvaluatedCleanAction.ActionInfo(rule, action)
            return listOf(EvaluatedCleanAction(fact = null, actionInfo, evc))
        }

        val cleanAccessors = from.accessorList()
        return cleanAccessors(cleanAccessors, fact, rule, action, evc)
    }

    private fun removeFinalFact(
        evc: EvaluatedCleanAction,
        from: PositionAccess,
        markRestriction: TaintMark,
        rule: TaintConfigurationItem,
        action: RemoveMark,
    ): List<EvaluatedCleanAction> {
        val fact = evc.fact ?: return listOf(evc)

        if (!fact.containsPositionWithTaintMark(from, markRestriction)) return listOf(evc)

        val cleanAccessors = from.accessorList() + TaintMarkAccessor(markRestriction.name)
        return cleanAccessors(cleanAccessors, fact, rule, action, evc)
    }

    private fun cleanAccessors(
        accessors: List<Accessor>,
        fact: FinalFactReader,
        rule: TaintConfigurationItem,
        action: Action,
        evc: EvaluatedCleanAction
    ): List<EvaluatedCleanAction> {
        val (cleanedFacts, factCleaned) = clearPosition(accessors, fact.factAp)

        val result = mutableListOf<EvaluatedCleanAction>()
        if (factCleaned) {
            val actionInfo = EvaluatedCleanAction.ActionInfo(rule, action)
            result += EvaluatedCleanAction(null, actionInfo, evc)
        }

        return cleanedFacts.mapTo(result) { cleanedFact ->
            val resultFact = fact.replaceFact(cleanedFact)
            val actionInfo = EvaluatedCleanAction.ActionInfo(rule, action)
            EvaluatedCleanAction(resultFact, actionInfo, evc)
        }
    }

    private fun clearPosition(accessors: List<Accessor>, fact: FinalFactAp): Pair<List<FinalFactAp>, Boolean> {
        val head = accessors.first()
        val tail = accessors.drop(1)
        if (tail.isEmpty()) {
            if (fact.startsWithAccessor(AnyAccessor)) {
                val factAfterAny = fact.readAccessor(AnyAccessor)
                    ?: error("Impossible")

                val clearedAfterAny = factAfterAny.clearAccessor(head)
                val restoredAfterAny = clearedAfterAny?.prependAccessor(AnyAccessor)

                val factWithoutAny = fact.clearAccessor(AnyAccessor)
                val cleanedWithoutAny = factWithoutAny?.clearAccessor(head)

                val cleaned = clearedAfterAny != factAfterAny || cleanedWithoutAny != factWithoutAny

                return listOfNotNull(restoredAfterAny, cleanedWithoutAny) to cleaned
            }

            if (!fact.startsWithAccessor(head)) {
                return listOf(fact) to false
            }

            val clearedFact = fact.clearAccessor(head)
            val cleaned = clearedFact != fact

            return listOfNotNull(clearedFact) to cleaned
        }

        val child = fact.readAccessor(head)
            ?: return listOf(fact) to false

        val remaining = listOfNotNull(fact.clearAccessor(head))
        val (cleanChild, childCleaned) = clearPosition(tail, child)
        val cleanChildWithAccessor = cleanChild.map { it.prependAccessor(head) }
        val fullFact = remaining + cleanChildWithAccessor

        return fullFact to childCleaned
    }

    private fun PositionAccess.accessorList(): List<Accessor> = when (this) {
        is PositionAccess.Simple -> emptyList()
        is PositionAccess.Complex -> base.accessorList() + accessor
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        private const val STRING = "java.lang.String"

        // todo: fix in config?
        // string bytes virtual field fully reflects the string content.
        // So, if we clean string, we should clean its byte content
        private val stringBytes = PositionAccessor.FieldAccessor(STRING, "<string-bytes>", "byte[]")
    }
}

class TaintPassActionPreconditionEvaluator(
    private val factReader: InitialFactReader,
) : PassActionEvaluator<Pair<Action, InitialFactAp>> {
    override fun evaluate(rule: TaintConfigurationItem, action: CopyAllMarks): Maybe<List<Pair<Action, InitialFactAp>>> {
        val fromVar = action.from.resolveAp()

        val toVariables = listOf(action.to.resolveAp())
        return Maybe.from(toVariables).flatMap { toVar ->
            copyAllFactsPrecondition(fromVar, toVar).fmap { facts ->
                facts.map { action to it }
            }
        }
    }

    override fun evaluate(rule: TaintConfigurationItem, action: CopyMark): Maybe<List<Pair<Action, InitialFactAp>>> {
        val fromVar = action.from.resolveAp()
        val toVariable = action.to.resolveAp()

        return copyFinalFactPrecondition(fromVar, toVariable, action.mark).fmap { facts ->
            facts.map { action to it }
        }
    }

    private fun copyAllFactsPrecondition(
        fromPosAccess: PositionAccess,
        toPosAccess: PositionAccess,
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
        mark: TaintMark,
    ): Maybe<List<InitialFactAp>> {
        if (!factReader.containsPositionWithTaintMark(toPosAccess, mark)) return Maybe.none()

        val preconditionFact = factReader
            .createInitialFactWithTaintMark(fromPosAccess, mark)
            .replaceExclusions(factReader.fact.exclusions)

        return Maybe.some(listOf(preconditionFact))
    }
}

interface SourceActionEvaluator<T> {
    fun evaluate(rule: TaintConfigurationItem, action: AssignAction): Maybe<List<T>>
}

class TaintSourceActionEvaluator(
    private val apManager: ApManager,
    private val exclusion: ExclusionSet,
) : SourceActionEvaluator<FinalFactAp> {
    override fun evaluate(rule: TaintConfigurationItem, action: AssignAction): Maybe<List<FinalFactAp>> {
        val variable = when (action) {
            is AssignMark -> action.position
            is AssignMarkAnyField -> action.positionWithAny
        }.resolveAp()

        val fact = apManager.mkAccessPath(variable, exclusion, action.mark.name)
        return Maybe.from(listOf(fact))
    }
}

class TaintSourceActionPreconditionEvaluator(
    private val factReader: InitialFactReader,
) : SourceActionEvaluator<Pair<TaintConfigurationItem, AssignAction>> {
    override fun evaluate(
        rule: TaintConfigurationItem,
        action: AssignAction,
    ): Maybe<List<Pair<TaintConfigurationItem, AssignAction>>> {
        when (action) {
            is AssignMark -> {
                val variable = action.position.resolveAp()
                if (!factReader.containsPositionWithTaintMark(variable, action.mark)) return Maybe.none()
                return Maybe.some(listOf(rule to action))
            }
            is AssignMarkAnyField -> {
                val variable = action.barePosition.resolveAp()
                if (!factReader.containsAnyPositionWithTaintMark(variable, action.mark)) return Maybe.none()
                return Maybe.some(listOf(rule to action))
            }
        }
    }
}

fun Position.resolveBaseAp(): AccessPathBase = when (this) {
    is Argument -> AccessPathBase.Argument(index)
    is This -> AccessPathBase.This
    is Result -> AccessPathBase.Return
    is ClassStatic -> AccessPathBase.ClassStatic
    is PositionWithAccess -> base.resolveBaseAp()
}

fun Position.resolveAp(): PositionAccess = resolveAp(resolveBaseAp())

fun Position.resolveAp(baseAp: AccessPathBase): PositionAccess {
    return when (this) {
        is Argument,
        is This,
        is Result -> PositionAccess.Simple(baseAp)

        is ClassStatic -> PositionAccess.Complex(
            PositionAccess.Simple(baseAp),
            ClassStaticAccessor(className)
        )

        is PositionWithAccess -> {
            val resolvedBaseAp = base.resolveAp(baseAp)
            val accessor = access.toApAccessor()

            PositionAccess.Complex(resolvedBaseAp, accessor)
        }
    }
}

fun PositionAccessor.toApAccessor() = when(this) {
    PositionAccessor.AnyFieldAccessor -> AnyAccessor
    PositionAccessor.ElementAccessor -> ElementAccessor
    is PositionAccessor.FieldAccessor -> FieldAccessor(className, fieldName, fieldType)
}

private fun PositionAccess.baseIsResult(): Boolean = when (this) {
    is PositionAccess.Complex -> base.baseIsResult()
    is PositionAccess.Simple -> base is AccessPathBase.Return
}

fun PositionAccess.withPrefix(prefix: Accessor): PositionAccess = when (this) {
    is PositionAccess.Complex -> PositionAccess.Complex(base.withPrefix(prefix), accessor)
    is PositionAccess.Simple -> PositionAccess.Complex(this, prefix)
}

fun PositionAccess.withPrefix(prefix: List<Accessor>): PositionAccess = when (this) {
    is PositionAccess.Complex -> PositionAccess.Complex(base.withPrefix(prefix), accessor)
    is PositionAccess.Simple -> prefix.fold(this as PositionAccess) { res, ac ->
        PositionAccess.Complex(res, ac)
    }
}

fun PositionAccess.withSuffix(suffix: List<Accessor>): PositionAccess =
    suffix.fold(this) { res, ac -> PositionAccess.Complex(res, ac) }

fun PositionAccess.removeSuffix(suffix: List<Accessor>): PositionAccess {
    var result = this
    for (ac in suffix.asReversed()) {
        check(result is PositionAccess.Complex && result.accessor == ac) {
            "Suffix mismatch"
        }
        result = result.base
    }
    return result
}

fun PositionAccess.removePrefix(prefix: Accessor): PositionAccess = when (this) {
    is PositionAccess.Complex -> when (base) {
        is PositionAccess.Complex -> PositionAccess.Complex(base.removePrefix(prefix), accessor)
        is PositionAccess.Simple -> {
            check(accessor == prefix) { "Prefix mismatch" }
            base
        }
    }

    is PositionAccess.Simple -> error("Prefix mismatch")
}
