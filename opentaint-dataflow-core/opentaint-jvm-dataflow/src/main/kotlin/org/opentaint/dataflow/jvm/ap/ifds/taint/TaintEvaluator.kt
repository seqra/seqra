package org.opentaint.dataflow.jvm.ap.ifds.taint

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.configuration.jvm.Action
import org.opentaint.dataflow.configuration.jvm.Argument
import org.opentaint.dataflow.configuration.jvm.AssignMark
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

        val resultFacts = mutableListOf(mkAccessPath(toPosAccess, factApDelta, fact.exclusions))
        resultFacts.hackResultArray(toPosAccess, factTypeChecker, toPositionBaseType)

        val wellTypedFacts = resultFacts.mapNotNull { factTypeChecker.filterFactByLocalType(toPositionBaseType, it) }
        if (wellTypedFacts.isEmpty()) return Maybe.none()

        return Maybe.some(listOf(factReader.factAp) + wellTypedFacts)
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

class TaintCleanActionEvaluator {
    fun evaluate(
        initialFact: EvaluatedCleanAction,
        rule: TaintConfigurationItem,
        action: RemoveAllMarks,
    ): EvaluatedCleanAction {
        val variable = action.position.resolveAp()
        return removeAllFacts(initialFact, variable, rule, action)
    }

    fun evaluate(
        initialFact: EvaluatedCleanAction,
        rule: TaintConfigurationItem,
        action: RemoveMark,
    ): EvaluatedCleanAction {
        val variable = action.position.resolveAp()
        return removeFinalFact(initialFact, variable, action.mark, rule, action)
    }

    private fun removeAllFacts(
        evc: EvaluatedCleanAction,
        from: PositionAccess,
        rule: TaintConfigurationItem,
        action: RemoveAllMarks,
    ): EvaluatedCleanAction {
        val fact = evc.fact ?: return evc

        if (!fact.containsPosition(from)) return evc

        if (from !is PositionAccess.Simple) {
            TODO("Remove from complex: $from")
        }

        val actionInfo = EvaluatedCleanAction.ActionInfo(rule, action)
        return EvaluatedCleanAction(fact = null, actionInfo, evc)
    }

    private fun removeFinalFact(
        evc: EvaluatedCleanAction,
        from: PositionAccess,
        markRestriction: TaintMark,
        rule: TaintConfigurationItem,
        action: RemoveMark,
    ): EvaluatedCleanAction {
        val fact = evc.fact ?: return evc

        if (!fact.containsPositionWithTaintMark(from, markRestriction)) return evc

        if (from !is PositionAccess.Simple) {
            TODO("Remove from complex: $from")
        }

        val factWithoutFinal = fact.factAp.clearAccessor(TaintMarkAccessor(markRestriction.name))
        val resultFact = factWithoutFinal?.let { fact.replaceFact(it) }

        val actionInfo = EvaluatedCleanAction.ActionInfo(rule, action)
        return EvaluatedCleanAction(resultFact, actionInfo, evc)
    }
}

class TaintPassActionPreconditionEvaluator(
    private val factReader: InitialFactReader,
    private val typeChecker: JIRFactTypeChecker,
    private val returnValueType: JIRType?,
) : PassActionEvaluator<Pair<Action, InitialFactAp>> {
    override fun evaluate(rule: TaintConfigurationItem, action: CopyAllMarks): Maybe<List<Pair<Action, InitialFactAp>>> {
        val fromVar = action.from.resolveAp()

        val toVariables = mutableListOf(action.to.resolveAp())
        toVariables.hackResultArray(typeChecker, returnValueType)

        return Maybe.from(toVariables).flatMap { toVar ->
            copyAllFactsPrecondition(fromVar, toVar).fmap { facts ->
                facts.map { action to it }
            }
        }
    }

    override fun evaluate(rule: TaintConfigurationItem, action: CopyMark): Maybe<List<Pair<Action, InitialFactAp>>> {
        val fromVar = action.from.resolveAp()

        val toVariables = mutableListOf(action.to.resolveAp())
        toVariables.hackResultArray(typeChecker, returnValueType)

        return Maybe.from(toVariables).flatMap { toVar ->
            copyFinalFactPrecondition(fromVar, toVar, action.mark).fmap { facts ->
                facts.map { action to it }
            }
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
    fun evaluate(rule: TaintConfigurationItem, action: AssignMark): Maybe<List<T>>
}

class TaintSourceActionEvaluator(
    private val apManager: ApManager,
    private val exclusion: ExclusionSet,
    private val factTypeChecker: JIRFactTypeChecker,
    private val returnValueType: JIRType?,
) : SourceActionEvaluator<FinalFactAp> {
    override fun evaluate(rule: TaintConfigurationItem, action: AssignMark): Maybe<List<FinalFactAp>> {
        val variable = action.position.resolveAp()

        val facts = mutableListOf(apManager.mkAccessPath(variable, exclusion, action.mark.name))
        facts.hackResultArray(variable, factTypeChecker, returnValueType)

        return Maybe.from(facts)
    }
}

class TaintSourceActionPreconditionEvaluator(
    private val factReader: InitialFactReader,
    private val typeChecker: JIRFactTypeChecker,
    private val returnValueType: JIRType?,
) : SourceActionEvaluator<Pair<TaintConfigurationItem, AssignMark>> {
    override fun evaluate(
        rule: TaintConfigurationItem,
        action: AssignMark,
    ): Maybe<List<Pair<TaintConfigurationItem, AssignMark>>> {
        val variables = mutableListOf(action.position.resolveAp())
        variables.hackResultArray(typeChecker, returnValueType)

        return Maybe.from(variables).flatMap { variable ->
            if (!factReader.containsPositionWithTaintMark(variable, action.mark)) return@flatMap Maybe.none()
            Maybe.some(listOf(rule to action))
        }
    }
}

fun Position.resolveBaseAp(): AccessPathBase = when (this) {
    is Argument -> AccessPathBase.Argument(index)
    is This -> AccessPathBase.This
    is Result -> AccessPathBase.Return
    is ClassStatic -> AccessPathBase.ClassStatic(className)
    is PositionWithAccess -> base.resolveBaseAp()
}

fun Position.resolveAp(): PositionAccess = resolveAp(resolveBaseAp())

fun Position.resolveAp(baseAp: AccessPathBase): PositionAccess {
    return when (this) {
        is Argument,
        is This,
        is Result,
        is ClassStatic -> PositionAccess.Simple(baseAp)

        is PositionWithAccess -> {
            val resolvedBaseAp = base.resolveAp(baseAp)
            val accessor = when (val a = access) {
                PositionAccessor.ElementAccessor -> ElementAccessor
                is PositionAccessor.FieldAccessor -> FieldAccessor(a.className, a.fieldName, a.fieldType)
                PositionAccessor.AnyFieldAccessor -> {
                    // force loop in access path
                    val loopedPosition = PositionAccess.Complex(
                        PositionAccess.Complex(resolvedBaseAp, AnyAccessor),
                        AnyAccessor
                    )
                    return loopedPosition
                }
            }

            PositionAccess.Complex(resolvedBaseAp, accessor)
        }
    }
}

private fun MutableList<FinalFactAp>.hackResultArray(
    access: PositionAccess,
    typeChecker: JIRFactTypeChecker,
    resultPositionType: JIRType?,
) {
    if (resultPositionType == null) return
    if (!access.baseIsResult()) return

    if (!with(typeChecker) { resultPositionType.mayBeArray() }) return

    for (fact in this.toList()) {
        this += fact.prependAccessor(ElementAccessor)
    }
}

private fun MutableList<PositionAccess>.hackResultArray(
    typeChecker: JIRFactTypeChecker,
    resultPositionType: JIRType?,
) {
    if (resultPositionType == null) return

    for (access in this.toList()) {
        if (!access.baseIsResult()) continue
        if (!with(typeChecker) { resultPositionType.mayBeArray() }) continue

        this += access.withPrefix(ElementAccessor)
    }
}

private fun PositionAccess.baseIsResult(): Boolean = when (this) {
    is PositionAccess.Complex -> base.baseIsResult()
    is PositionAccess.Simple -> base is AccessPathBase.Return
}

fun PositionAccess.withPrefix(prefix: Accessor): PositionAccess = when (this) {
    is PositionAccess.Complex -> PositionAccess.Complex(base.withPrefix(prefix), accessor)
    is PositionAccess.Simple -> PositionAccess.Complex(this, prefix)
}
