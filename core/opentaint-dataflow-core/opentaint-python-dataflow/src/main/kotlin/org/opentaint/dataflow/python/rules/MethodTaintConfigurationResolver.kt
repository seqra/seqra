package org.opentaint.dataflow.python.rules

import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.isFalse
import org.opentaint.dataflow.configuration.mkFalse
import org.opentaint.dataflow.configuration.mkOr
import org.opentaint.dataflow.configuration.mkTrue
import org.opentaint.dataflow.configuration.python.AnyArgument
import org.opentaint.dataflow.configuration.python.Argument
import org.opentaint.dataflow.configuration.python.BoolConstantValue
import org.opentaint.dataflow.configuration.python.ClassRef
import org.opentaint.dataflow.configuration.python.ConstantCmp
import org.opentaint.dataflow.configuration.python.ConstantCmpType
import org.opentaint.dataflow.configuration.python.ConstantMatches
import org.opentaint.dataflow.configuration.python.ConstantValue
import org.opentaint.dataflow.configuration.python.ContainsMark
import org.opentaint.dataflow.configuration.python.ContainsMarkOnAnyAccessor
import org.opentaint.dataflow.configuration.python.IntConstantValue
import org.opentaint.dataflow.configuration.python.KwArgument
import org.opentaint.dataflow.configuration.python.NumberOfArgs
import org.opentaint.dataflow.configuration.python.PIRCondition
import org.opentaint.dataflow.configuration.python.Position
import org.opentaint.dataflow.configuration.python.PositionAccessor
import org.opentaint.dataflow.configuration.python.PositionWithAccess
import org.opentaint.dataflow.configuration.python.PythonRuleCondition
import org.opentaint.dataflow.configuration.python.Result
import org.opentaint.dataflow.configuration.python.StrConstantValue
import org.opentaint.dataflow.configuration.python.TaintAssignAction
import org.opentaint.dataflow.configuration.python.TaintCleanAction
import org.opentaint.dataflow.configuration.python.TaintCleaner
import org.opentaint.dataflow.configuration.python.TaintConfigurationItem
import org.opentaint.dataflow.configuration.python.TaintEntryPointSource
import org.opentaint.dataflow.configuration.python.TaintExitSink
import org.opentaint.dataflow.configuration.python.TaintMark
import org.opentaint.dataflow.configuration.python.TaintPassAction
import org.opentaint.dataflow.configuration.python.TaintPassThrough
import org.opentaint.dataflow.configuration.python.TaintSink
import org.opentaint.dataflow.configuration.python.TaintSinkMeta
import org.opentaint.dataflow.configuration.python.TaintSource
import org.opentaint.dataflow.configuration.python.Target
import org.opentaint.dataflow.configuration.python.This
import org.opentaint.dataflow.configuration.python.serialized.ItemInfo
import org.opentaint.dataflow.configuration.python.serialized.PythonPosition
import org.opentaint.dataflow.configuration.python.serialized.PythonPositionBase
import org.opentaint.dataflow.configuration.python.serialized.PythonPositionModifier
import org.opentaint.dataflow.configuration.python.serialized.PythonTarget
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonCleaner
import org.opentaint.dataflow.configuration.python.serialized.PythonSinkMetaData
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonCondition
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonEntryPointSource
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonExitSink
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonPassThrough
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonRule
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonSignatureMatcher
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonSink
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonSource
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonSourceRule
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonTaintAssignAction
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonTaintCleanAction
import org.opentaint.dataflow.configuration.python.serialized.SerializedPythonTaintPassAction
import org.opentaint.dataflow.configuration.simplify
import org.opentaint.dataflow.python.graph.PIRSimpleNameUnknownFunction
import org.opentaint.ir.api.python.PIRClassType
import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.ir.api.python.PIRParameter
import org.opentaint.ir.api.python.PIRType
import org.opentaint.ir.api.python.PythonNames
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

internal class MethodTaintConfigurationResolver(private val method: PIRFunction?, val bySimpleName: Boolean = false) {

    // region Function-targeted resolution

    fun resolveEntryPoints(
        serialized: List<SerializedPythonEntryPointSource>,
    ): List<TaintEntryPointSource> = resolveFunctionTargeted(serialized) { rule, _, method ->
        TaintEntryPointSource(
            target = Target.Function(method),
            condition = resolveCondition(rule.condition),
            taint = rule.taint.flatMap(::convertEntryPointAssignActions),
            info = rule.info,
        )
    }

    fun resolveSources(
        serialized: List<SerializedPythonSource>,
    ): List<TaintSource> = resolveFunctionTargeted(serialized) { rule, _, method ->
        TaintSource(
            target = Target.Function(method),
            condition = resolveCondition(rule.condition),
            taint = rule.taint.flatMap(::convertAssignActions),
            info = rule.info,
        )
    }

    fun resolveSinks(
        serialized: List<SerializedPythonSink>,
    ): List<TaintSink> = resolveFunctionTargeted(serialized) { rule, fn, method ->
        TaintSink(
            target = Target.Function(method),
            condition = resolveCondition(rule.condition),
            trackFactsReachAnalysisEnd = resolveTrackedEndFacts(rule.trackFactsReachAnalysisEnd),
            id = "function:${fn.function}",
            meta = sinkMeta(rule.meta),
        )
    }

    fun resolveExitSinks(
        serialized: List<SerializedPythonExitSink>,
    ): List<TaintExitSink> = resolveFunctionTargeted(serialized) { rule, fn, method ->
        TaintExitSink(
            target = Target.Function(method),
            condition = resolveCondition(rule.condition),
            trackFactsReachAnalysisEnd = resolveTrackedEndFacts(rule.trackFactsReachAnalysisEnd),
            id = "exit:${fn.function}",
            meta = sinkMeta(rule.meta),
        )
    }

    fun resolvePassThrough(
        serialized: List<SerializedPythonPassThrough>,
    ): List<TaintPassThrough> = resolveFunctionTargeted(serialized) { rule, _, method ->
        TaintPassThrough(
            target = Target.Function(method),
            condition = resolveCondition(rule.condition),
            copy = rule.copy.flatMap { convertPassActions(it) },
        )
    }

    fun resolveCleaners(
        serialized: List<SerializedPythonCleaner>,
    ): List<TaintCleaner> = resolveFunctionTargeted(serialized) { rule, _, method ->
        TaintCleaner(
            target = Target.Function(method),
            condition = resolveCondition(rule.condition),
            cleans = rule.cleans.flatMap { convertCleanActions(it) },
            forCategory = rule.`for`,
            info = rule.info,
        )
    }

    // endregion

    // region Attribute-targeted resolution

    fun resolveAttributeSources(
        serialized: List<SerializedPythonSource>,
        name: String,
    ): List<TaintSource> = resolveAttributeTargeted(serialized, name) { rule ->
        TaintSource(
            target = Target.Attribute(name),
            condition = resolveCondition(rule.condition),
            taint = rule.taint.flatMap { convertAssignActions(it) },
            info = rule.info,
        )
    }

    fun resolveAttributeSinks(
        serialized: List<SerializedPythonSink>,
        name: String,
    ): List<TaintSink> = resolveAttributeTargeted(serialized, name) { rule ->
        TaintSink(
            target = Target.Attribute(name),
            condition = resolveCondition(rule.condition),
            trackFactsReachAnalysisEnd = resolveTrackedEndFacts(rule.trackFactsReachAnalysisEnd),
            id = "attribute:$name",
            meta = sinkMeta(rule.meta),
        )
    }

    fun resolveAttributePassThrough(
        serialized: List<SerializedPythonPassThrough>,
        name: String,
    ): List<TaintPassThrough> = resolveAttributeTargeted(serialized, name) { rule ->
        TaintPassThrough(
            target = Target.Attribute(name),
            condition = resolveCondition(rule.condition),
            copy = rule.copy.flatMap { convertPassActions(it) },
        )
    }

    fun resolveAttributeCleaners(
        serialized: List<SerializedPythonCleaner>,
        name: String,
    ): List<TaintCleaner> = resolveAttributeTargeted(serialized, name) { rule ->
        TaintCleaner(
            target = Target.Attribute(name),
            condition = resolveCondition(rule.condition),
            cleans = rule.cleans.flatMap { convertCleanActions(it) },
            forCategory = rule.`for`,
            info = rule.info,
        )
    }

    // endregion

    private inline fun <S : SerializedPythonRule, T : TaintConfigurationItem> resolveFunctionTargeted(
        serialized: List<S>,
        build: (S, PythonTarget.Function, PIRFunction) -> T,
    ): List<T> {
        requireMethod(method)

        return serialized.mapNotNull { rule ->
            val fn = rule.target as? PythonTarget.Function ?: return@mapNotNull null
            if (!fn.matches(method)) return@mapNotNull null

            build(rule, fn, method).takeUnless { it.condition.isFalse() }
        }
    }

    private inline fun <S : SerializedPythonRule, T : TaintConfigurationItem> resolveAttributeTargeted(
        serialized: List<S>,
        name: String,
        build: (S) -> T,
    ): List<T> = serialized.mapNotNull { rule ->
        val attr = rule.target as? PythonTarget.Attribute ?: return@mapNotNull null
        if (!matchesName(attr, name)) return@mapNotNull null

        build(rule).takeUnless { it.condition.isFalse() }
    }

    // region Matching

    private fun PythonTarget.Function.matches(method: PIRFunction): Boolean {
        if (!matchesName(this, method)) return false
        signature?.let { if (!matchesSignature(it, method)) return false }
        return true
    }

    private fun matchesName(target: PythonTarget.Attribute, attribute: String): Boolean =
        if ("." in target.attribute && "." in attribute && !bySimpleName) {
            target.attribute == attribute
        } else {
            target.attribute.substringAfterLast('.') == attribute.substringAfterLast('.')
        }

    private fun matchesName(target: PythonTarget.Function, method: PIRFunction): Boolean {
        val targetName = target.function
        val targetSimpleName = targetName.substringAfterLast(".")

        val qn = method.qualifiedName.let {
            if (method.enclosingClass != null) it.removeSuffix(".${PythonNames.INIT_METHOD}") else it
        }
        val simpleName = qn.substringAfterLast('.')

        return when {
            hasRegexMetaChar(targetName) -> {
                val rx = Regex(targetName)
                rx.matches(qn)
            }
            method is PIRSimpleNameUnknownFunction -> targetSimpleName == method.name
            '.' !in targetName || bySimpleName -> simpleName == targetSimpleName
            else -> qn == targetName
        }
    }

    private fun hasRegexMetaChar(name: String): Boolean = name.any { it in REGEX_META_CHARS }

    private fun matchesSignature(sig: SerializedPythonSignatureMatcher, method: PIRFunction): Boolean {
        val params = method.declaredParameters()
        if (params.size != sig.args.size) return false
        if (!matchesType(sig.`return`, method.returnType)) return false
        return params.zip(sig.args).all { (p, m) -> matchesType(m, p.type) }
    }

    private fun matchesType(matcher: String, type: PIRType): Boolean =
        matcher == "*" || (type as? PIRClassType)?.qualifiedName == matcher

    private fun PIRFunction.declaredParameters(): List<PIRParameter> = when {
        enclosingClass == null || isStaticMethod -> parameters
        else -> if (parameters.isEmpty()) parameters else parameters.drop(1)
    }

    private fun PIRFunction.argumentIndices(): List<Int> = declaredParameters().indices.toList()

    private fun PIRFunction.hasDecorator(name: String): Boolean = decorators.any {
        if ('.' in name) it.qualifiedName == name || it.qualifiedName.endsWith(".$name") else it.name == name
    }

    private fun PIRFunction.hasBaseClass(fqn: String): Boolean =
        enclosingClass?.let { fqn in it.mro } == true

    // endregion

    // region Position + condition + action conversion

    private fun resolveTrackedEndFacts(
        actions: List<SerializedPythonTaintAssignAction>?,
    ): List<TaintAssignAction> = actions.orEmpty().flatMap(::convertAssignActions)

    private fun convertAssignActions(a: SerializedPythonTaintAssignAction): List<TaintAssignAction> =
        expandPositions(a.pos).map { TaintAssignAction(mark = TaintMark(a.kind), pos = it) }

    private fun convertEntryPointAssignActions(a: SerializedPythonTaintAssignAction): List<TaintAssignAction> =
        expandEntryPointPositions(a.pos).map { TaintAssignAction(mark = TaintMark(a.kind), pos = it) }

    private fun convertCleanActions(a: SerializedPythonTaintCleanAction): List<TaintCleanAction> =
        expandPositions(a.pos).map { TaintCleanAction(mark = TaintMark(a.taintKind), pos = it) }

    private fun convertPassActions(a: SerializedPythonTaintPassAction): List<TaintPassAction> {
        val mark = a.taintKind?.let(::TaintMark)
        val froms = expandPositions(a.from)
        val tos = expandPositions(a.to)
        return froms.flatMap { from -> tos.map { to -> TaintPassAction(mark, from, to) } }
    }

    private fun expandPositions(p: PythonPosition): List<Position> = listOfNotNull(convertPosition(p))

    private fun expandEntryPointPositions(p: PythonPosition): List<Position> {
        val base = p.base

        // TODO kw-params
        if (base is PythonPositionBase.Argument) {
            val idx = base.idx
            val argIndices = method?.argumentIndices().orEmpty()

            if (idx == null) return argIndices.mapNotNull { convertPosition(p.withBase(PythonPositionBase.Argument(it))) }
            if (idx !in argIndices) return emptyList()
        }

        return listOfNotNull(convertPosition(p))
    }

    private fun PythonPosition.withBase(newBase: PythonPositionBase): PythonPosition = when (this) {
        is PythonPosition.BaseOnly -> PythonPosition.BaseOnly(newBase)
        is PythonPosition.WithModifiers -> PythonPosition.WithModifiers(newBase, modifiers)
    }

    private fun convertPosition(p: PythonPosition): Position? {
        val base = convertBase(p.base) ?: return null
        return when (p) {
            is PythonPosition.BaseOnly -> base
            is PythonPosition.WithModifiers -> p.modifiers.fold(base) { acc, mod ->
                PositionWithAccess(acc, convertAccessor(mod))
            }
        }
    }

    private fun convertBase(base: PythonPositionBase): Position? = when (base) {
        is PythonPositionBase.Argument -> base.idx?.let { Argument(it) } ?: AnyArgument
        is PythonPositionBase.KwArgument -> KwArgument(base.name)
        PythonPositionBase.This -> This
        PythonPositionBase.Result -> Result
        is PythonPositionBase.ClassRef -> ClassRef(base.fqn)
    }

    private fun convertAccessor(m: PythonPositionModifier): PositionAccessor = when (m) {
        PythonPositionModifier.ArrayElement -> PositionAccessor.ElementAccessor
        is PythonPositionModifier.Field -> PositionAccessor.FieldAccessor(m.name)
    }

    private fun resolveCondition(c: SerializedPythonCondition?): PIRCondition =
        convertCondition(c).simplify()

    private fun convertCondition(c: SerializedPythonCondition?): PIRCondition = when (c) {
        null -> mkTrue()
        is SerializedPythonCondition.True -> mkTrue()
        is SerializedPythonCondition.Or -> CommonCondition.Or(c.anyOf.map { convertCondition(it) })
        is SerializedPythonCondition.And -> CommonCondition.And(c.allOf.map { convertCondition(it) })
        is SerializedPythonCondition.Not -> CommonCondition.Not(convertCondition(c.not))
        is SerializedPythonCondition.ContainsMark -> containsMarkCondition(c)
        is SerializedPythonCondition.ContainsMarkOnAnyAccessor -> containsMarkOnAnyAccessorCondition(c)
        is SerializedPythonCondition.NumberOfArgs -> atom(NumberOfArgs(c.n))
        is SerializedPythonCondition.ConstantCmp ->
            mkOr(expandPositions(c.pos).map { atom(ConstantCmp(it, c.value.toEngineValue(), c.cmp.toEngineCmp())) })
        is SerializedPythonCondition.ConstantMatches ->
            mkOr(expandPositions(c.pos).map { atom(ConstantMatches(it, Regex(c.pattern))) })
        is SerializedPythonCondition.MethodDecorated -> (method?.hasDecorator(c.decorator) == true).asCondition()
        is SerializedPythonCondition.ClassExtends -> (method?.hasBaseClass(c.baseClass) == true).asCondition()
    }

    private fun Boolean.asCondition(): PIRCondition = if (this) mkTrue() else mkFalse()

    private fun atom(a: PythonRuleCondition): PIRCondition = CommonCondition.Atom(a)

    private fun SerializedPythonCondition.ConstantValue.toEngineValue(): ConstantValue = when (type) {
        SerializedPythonCondition.ConstantType.Str -> StrConstantValue(value)
        SerializedPythonCondition.ConstantType.Int -> IntConstantValue(value.toLong())
        SerializedPythonCondition.ConstantType.Bool -> BoolConstantValue(value.toBooleanStrict())
    }

    private fun SerializedPythonCondition.ConstantCmpType.toEngineCmp(): ConstantCmpType = when (this) {
        SerializedPythonCondition.ConstantCmpType.Eq -> ConstantCmpType.Eq
        SerializedPythonCondition.ConstantCmpType.Lt -> ConstantCmpType.Lt
        SerializedPythonCondition.ConstantCmpType.Gt -> ConstantCmpType.Gt
    }

    private fun containsMarkCondition(c: SerializedPythonCondition.ContainsMark): PIRCondition =
        mkOr(expandPositions(c.pos).map { atom(ContainsMark(mark = TaintMark(c.tainted), pos = it)) })

    private fun containsMarkOnAnyAccessorCondition(c: SerializedPythonCondition.ContainsMarkOnAnyAccessor): PIRCondition =
        mkOr(expandPositions(c.pos).map { atom(ContainsMarkOnAnyAccessor(mark = TaintMark(c.tainted), pos = it)) })

    // endregion

    private fun sinkMeta(meta: PythonSinkMetaData?): TaintSinkMeta = TaintSinkMeta(
        message = meta?.note ?: DEFAULT_SINK_MESSAGE,
        severity = CommonTaintConfigurationSinkMeta.Severity.Warning,
        cwe = meta?.cwe,
        note = meta?.note,
    )

    @OptIn(ExperimentalContracts::class)
    private fun requireMethod(method: PIRFunction?) {
        contract { returns() implies (method != null) }
        requireNotNull(method) { "function-targeted resolution requires a method" }
    }

    companion object {
        private const val DEFAULT_SINK_MESSAGE = "taint reaches sink"
        private val REGEX_META_CHARS = setOf('*', '+', '?', '[', ']', '(', ')', '|', '^', '$', '\\', '{', '}')
    }
}
