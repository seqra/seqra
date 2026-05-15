package org.opentaint.jvm.sast.dataflow.rules

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.jvm.Action
import org.opentaint.dataflow.configuration.jvm.Argument
import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.configuration.jvm.ClassStatic
import org.opentaint.dataflow.configuration.jvm.Condition
import org.opentaint.dataflow.configuration.jvm.ConditionNameMatcher
import org.opentaint.dataflow.configuration.jvm.ConstantBooleanValue
import org.opentaint.dataflow.configuration.jvm.ConstantEq
import org.opentaint.dataflow.configuration.jvm.ConstantGt
import org.opentaint.dataflow.configuration.jvm.ConstantIntValue
import org.opentaint.dataflow.configuration.jvm.ConstantLt
import org.opentaint.dataflow.configuration.jvm.ConstantMatches
import org.opentaint.dataflow.configuration.jvm.ConstantStringValue
import org.opentaint.dataflow.configuration.jvm.ConstantTrue
import org.opentaint.dataflow.configuration.jvm.ContainsMark
import org.opentaint.dataflow.configuration.jvm.CopyAllMarks
import org.opentaint.dataflow.configuration.jvm.CopyMark
import org.opentaint.dataflow.configuration.jvm.IsConstant
import org.opentaint.dataflow.configuration.jvm.IsNull
import org.opentaint.dataflow.configuration.jvm.IsStaticField
import org.opentaint.dataflow.configuration.jvm.Not
import org.opentaint.dataflow.configuration.jvm.Position
import org.opentaint.dataflow.configuration.jvm.PositionAccessor
import org.opentaint.dataflow.configuration.jvm.PositionWithAccess
import org.opentaint.dataflow.configuration.jvm.RemoveAllMarks
import org.opentaint.dataflow.configuration.jvm.RemoveMark
import org.opentaint.dataflow.configuration.jvm.Result
import org.opentaint.dataflow.configuration.jvm.TaintCleaner
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationItem
import org.opentaint.dataflow.configuration.jvm.TaintEntryPointSource
import org.opentaint.dataflow.configuration.jvm.TaintMethodEntrySink
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSink
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSource
import org.opentaint.dataflow.configuration.jvm.TaintMethodSink
import org.opentaint.dataflow.configuration.jvm.TaintMethodSource
import org.opentaint.dataflow.configuration.jvm.TaintPassThrough
import org.opentaint.dataflow.configuration.jvm.TaintSinkMeta
import org.opentaint.dataflow.configuration.jvm.This
import org.opentaint.dataflow.configuration.jvm.TypeMatchesPattern
import org.opentaint.dataflow.configuration.jvm.isFalse
import org.opentaint.dataflow.configuration.jvm.matchType
import org.opentaint.dataflow.configuration.jvm.mkAnd
import org.opentaint.dataflow.configuration.jvm.mkFalse
import org.opentaint.dataflow.configuration.jvm.mkOr
import org.opentaint.dataflow.configuration.jvm.mkTrue
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.PositionModifier
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.AnnotationConstraint
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.AnnotationParamMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSignatureMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintAssignAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintCleanAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintPassAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTypeNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTypeNameMatcher.ClassPattern
import org.opentaint.dataflow.configuration.jvm.serialized.SinkMetaData
import org.opentaint.dataflow.configuration.jvm.serialized.SinkRule
import org.opentaint.dataflow.configuration.jvm.serialized.SourceRule
import org.opentaint.dataflow.configuration.jvm.simplify
import org.opentaint.ir.api.jvm.JIRAnnotated
import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JIRClassType
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.JIRType
import org.opentaint.ir.api.jvm.JIRTypedMethod
import org.opentaint.ir.api.jvm.PredefinedPrimitives
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.api.jvm.ext.allSuperHierarchySequence
import org.opentaint.ir.impl.cfg.util.isArray
import org.opentaint.jvm.sast.dataflow.matchedAnnotations
import java.util.concurrent.atomic.AtomicInteger

class MethodTaintConfigurationResolver(
    val patternManager: PatternManager,
    val taintMarkManager: TaintMarkManager,
    val cp: JIRClasspath,
    val objectTypeName: TypeName,
    val method: JIRMethod
) {
    private val typedMethod by lazy { resolveTypedMethod() }
    
    fun SerializedSignatureMatcher.matchFunctionSignature(): Boolean {
        when (this) {
            is SerializedSignatureMatcher.Simple -> {
                if (method.parameters.size != args.size) return false
                if (!`return`.matchTypedOrErased(method.returnType.typeName) { typedMethod?.returnType }) return false

                return args.withIndex().all { (idx, matcher) ->
                    matcher.matchTypedOrErased(method.parameters[idx].type.typeName) {
                        typedMethod?.parameters?.getOrNull(idx)?.type
                    }
                }
            }

            is SerializedSignatureMatcher.Partial -> {
                val ret = `return`
                if (ret != null) {
                    if (!ret.matchTypedOrErased(method.returnType.typeName) { typedMethod?.returnType }) return false
                }

                val paramList = params
                if (paramList != null) {
                    for (param in paramList) {
                        val methodParam = method.parameters.getOrNull(param.index) ?: return false
                        val paramTypeMatched = param.type.matchTypedOrErased(methodParam.type.typeName) {
                            typedMethod?.parameters?.getOrNull(param.index)?.type
                        }
                        if (!paramTypeMatched) return false
                    }
                }

                return true
            }
        }
    }

    private fun SerializedTypeNameMatcher.matchTypedOrErased(erased: String, resolveType: () -> JIRType?): Boolean {
        return withTypeResolutionFailureHandling(onFailure = { true }) {
            matchType(erased, { resolveType() ?: throw TypeResolutionFailed() }, { name -> match(patternManager, name) })
        }
    }

    private inline fun <T> withTypeResolutionFailureHandling(onFailure: () -> T, body: () -> T): T = try {
        body()
    } catch (_: TypeResolutionFailed) {
        onFailure()
    }

    private class TypeResolutionFailed : Exception() {
        override fun fillInStackTrace(): Throwable = this
    }

    fun SerializedRule.resolveRelevantRule(): List<TaintConfigurationItem> =
        resolveMethodRuleWithConditionResolver { condition, ctx ->
            condition.resolveRelevant(ctx)
        }

    fun SerializedRule.resolveRule(): List<TaintConfigurationItem> =
        resolveMethodRuleWithConditionResolver { condition, ctx ->
            condition.resolve(ctx)
        }

    private inline fun SerializedRule.resolveMethodRuleWithConditionResolver(
        resolveCondition: (SerializedCondition?, AnyArgSpecializationCtx) -> Condition
    ): List<TaintConfigurationItem> {
        val serializedCondition = when (this) {
            is SinkRule -> condition
            is SourceRule -> condition
            is SerializedRule.Cleaner -> condition
            is SerializedRule.PassThrough -> condition
        }

        val actions = when (this) {
            is SerializedRule.Source -> taint
            is SerializedRule.EntryPoint -> taint
            is SerializedRule.MethodExitSource -> taint
            is SerializedRule.Cleaner -> cleans
            is SerializedRule.PassThrough -> copy
            is SerializedRule.MethodEntrySink,
            is SerializedRule.MethodExitSink,
            is SerializedRule.Sink -> emptyList()
        }

        val contexts = anyArgSpecializationContexts(serializedCondition, actions)
        return contexts.mapNotNull {
            val condition = resolveCondition(serializedCondition, it).simplify()
            if (condition.isFalse()) return@mapNotNull null

            resolveMethodRule(condition, it)
        }
    }

    private fun SerializedRule.resolveMethodRule(
        condition: Condition,
        ctx: AnyArgSpecializationCtx,
    ): TaintConfigurationItem = when (this) {
        is SerializedRule.EntryPoint -> {
            TaintEntryPointSource(method, condition, taint.flatMap { it.resolveWithArray(ctx) }, info)
        }

        is SerializedRule.Source -> {
            TaintMethodSource(method, condition, taint.flatMap { it.resolveWithArray(ctx) }, info)
        }

        is SerializedRule.MethodExitSource -> {
            TaintMethodExitSource(method, condition, taint.flatMap { it.resolveWithArray(ctx) }, info)
        }

        is SerializedRule.Sink -> {
            TaintMethodSink(
                method, condition,
                trackFactsReachAnalysisEnd?.flatMap { it.resolveNoArray(ctx) }.orEmpty(),
                ruleId(), meta(), info
            )
        }

        is SerializedRule.MethodExitSink -> {
            TaintMethodExitSink(
                method, condition,
                trackFactsReachAnalysisEnd?.flatMap { it.resolveNoArray(ctx) }.orEmpty(),
                ruleId(), meta(), info
            )
        }

        is SerializedRule.MethodEntrySink -> {
            TaintMethodEntrySink(
                method, condition,
                trackFactsReachAnalysisEnd?.flatMap { it.resolveNoArray(ctx) }.orEmpty(),
                ruleId(), meta(), info
            )
        }

        is SerializedRule.PassThrough -> {
            TaintPassThrough(method, condition, copy.flatMap { it.resolve(ctx) }, info)
        }

        is SerializedRule.Cleaner -> {
            TaintCleaner(method, condition, cleans.flatMap { it.resolve(ctx) }, info)
        }
    }

    private val ruleIdGen = AtomicInteger()

    private fun SinkRule.ruleId(): String {
        id?.let { return it }
        meta?.cwe?.firstOrNull()?.let { return "CWE-$it" }
        return "generated-id-${ruleIdGen.incrementAndGet()}"
    }

    private fun SinkRule.meta(): TaintSinkMeta = TaintSinkMeta(
        message = meta?.message() ?: "",
        severity = meta?.severity ?: CommonTaintConfigurationSinkMeta.Severity.Warning,
        cwe = meta?.cwe
    )

    private fun SinkMetaData.message(): String? = note

    data class AnyArgSpecializationCtx(val positions: Map<String, Argument>) {
        fun resolve(anyArg: PositionBase.AnyArgument): Argument =
            positions[anyArg.classifier]
                ?: error("Unresolved anyarg classifier")
    }

    private fun anyArgSpecializationContexts(
        condition: SerializedCondition?, actions: List<SerializedAction>
    ): List<AnyArgSpecializationCtx> {
        val classifiers = hashSetOf<String>()
        condition.collectAnyArgumentClassifiers(classifiers)
        actions.forEach {
            when (it) {
                is SerializedTaintAssignAction -> it.pos.collectAnyArgumentClassifiers(classifiers)
                is SerializedTaintCleanAction -> it.pos.collectAnyArgumentClassifiers(classifiers)
                is SerializedTaintPassAction -> {
                    it.from.collectAnyArgumentClassifiers(classifiers)
                    it.to.collectAnyArgumentClassifiers(classifiers)
                }
            }
        }

        if (classifiers.isEmpty()) {
            return listOf(AnyArgSpecializationCtx(emptyMap()))
        }

        val contexts = mutableListOf<AnyArgSpecializationCtx>()
        val allArgs = method.parameters.indices.map { Argument(it) }
        buildAnyArgSpecializationCtx(classifiers.toList(), idx = 0, persistentHashMapOf(), allArgs, contexts)
        return contexts
    }

    private fun buildAnyArgSpecializationCtx(
        classifiers: List<String>,
        idx: Int,
        current: PersistentMap<String, Argument>,
        allArgs: List<Argument>,
        result: MutableList<AnyArgSpecializationCtx>
    ) {
        if (idx == classifiers.size) {
            result.add(AnyArgSpecializationCtx(current))
            return
        }

        val classifier = classifiers[idx]
        for (arg in allArgs) {
            val next = current.put(classifier, arg)
            buildAnyArgSpecializationCtx(classifiers, idx + 1, next, allArgs, result)
        }
    }

    private fun SerializedCondition?.collectAnyArgumentClassifiers(
        classifiers: MutableSet<String>
    ): Unit = when (this) {
        is SerializedCondition.And -> allOf.forEach { it.collectAnyArgumentClassifiers(classifiers) }
        is SerializedCondition.Or -> anyOf.forEach { it.collectAnyArgumentClassifiers(classifiers) }
        is SerializedCondition.Not -> not.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.AnnotationType -> pos.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.ConstantCmp -> pos.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.ConstantEq -> pos.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.ConstantGt -> pos.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.ConstantLt -> pos.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.ConstantMatches -> pos.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.ContainsMark -> pos.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.IsConstant -> isConstant.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.IsNull -> isNull.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.IsType -> pos.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.IsStaticField -> pos.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.ParamAnnotated -> pos.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.ClassAnnotated,
        is SerializedCondition.MethodAnnotated,
        is SerializedCondition.MethodNameMatches,
        is SerializedCondition.ClassNameMatches,
        is SerializedCondition.NumberOfArgs,
        SerializedCondition.True,
        null -> {
            // no positions
        }
    }

    private fun PositionBaseWithModifiers.collectAnyArgumentClassifiers(classifiers: MutableSet<String>) {
        base.collectAnyArgumentClassifiers(classifiers)
    }

    private fun PositionBase.collectAnyArgumentClassifiers(classifiers: MutableSet<String>) {
        if (this !is PositionBase.AnyArgument) return
        classifiers.add(classifier)
    }

    private fun SerializedCondition?.resolveRelevant(
        ctx: AnyArgSpecializationCtx,
    ): Condition {
        val relevantCondition = this?.rewriteNnf(negated = false) { c, negated ->
            if (!negated) return@rewriteNnf c

            val result = when (c) {
                is SerializedCondition.ClassNameMatches,
                is SerializedCondition.MethodNameMatches,
                is SerializedCondition.NumberOfArgs -> return@rewriteNnf SerializedCondition.True

                else -> c
            }

            SerializedCondition.not(result)
        }

        return relevantCondition.resolve(ctx)
    }

    private fun SerializedCondition.rewriteNnf(
        negated: Boolean,
        rewriteLiteral: (SerializedCondition, Boolean) -> SerializedCondition
    ): SerializedCondition = when (this) {
        is SerializedCondition.Not -> not.rewriteNnf(!negated, rewriteLiteral)

        is SerializedCondition.And -> if (!negated) {
            SerializedCondition.and(allOf.map { it.rewriteNnf(negated = false, rewriteLiteral) })
        } else {
            SerializedCondition.or(allOf.map { it.rewriteNnf(negated = true, rewriteLiteral) })
        }

        is SerializedCondition.Or -> if (!negated) {
            SerializedCondition.or(anyOf.map { it.rewriteNnf(negated = false, rewriteLiteral) })
        } else {
            SerializedCondition.and(anyOf.map { it.rewriteNnf(negated = true, rewriteLiteral) })
        }

        else -> rewriteLiteral(this, negated)
    }

    private fun SerializedCondition?.resolve(
        ctx: AnyArgSpecializationCtx,
    ): Condition = when (this) {
        null -> ConstantTrue
        is SerializedCondition.Not -> Not(not.resolve(ctx))
        is SerializedCondition.And -> mkAnd(allOf.map { it.resolve(ctx) })
        is SerializedCondition.Or -> mkOr(anyOf.map { it.resolve(ctx) })
        is SerializedCondition.True -> ConstantTrue
        is SerializedCondition.AnnotationType -> {
            val containsAnnotation = pos.resolveWithAnnotationConstraint(
                ctx,
                annotatedWith.asAnnotationConstraint()
            ).any()
            containsAnnotation.asCondition()
        }

        is SerializedCondition.ConstantCmp -> {
            val value = when (value.type) {
                SerializedCondition.ConstantType.Str -> ConstantStringValue(value.value)
                SerializedCondition.ConstantType.Bool -> ConstantBooleanValue(value.value.toBoolean())
                SerializedCondition.ConstantType.Int -> ConstantIntValue(value.value.toInt())
            }

            pos.resolve(ctx).map {
                when (cmp) {
                    SerializedCondition.ConstantCmpType.Eq -> ConstantEq(it, value)
                    SerializedCondition.ConstantCmpType.Lt -> ConstantLt(it, value)
                    SerializedCondition.ConstantCmpType.Gt -> ConstantGt(it, value)
                }
            }.let { mkOr(it) }
        }

        is SerializedCondition.ConstantEq -> mkOr(
            pos.resolve(ctx).map { ConstantEq(it, ConstantStringValue(constantEq)) })

        is SerializedCondition.ConstantGt -> mkOr(
            pos.resolve(ctx).map { ConstantGt(it, ConstantStringValue(constantGt)) })

        is SerializedCondition.ConstantLt -> mkOr(
            pos.resolve(ctx).map { ConstantLt(it, ConstantStringValue(constantLt)) })

        is SerializedCondition.ConstantMatches -> mkOr(
            pos.resolve(ctx).map { ConstantMatches(it, patternManager.compilePattern(constantMatches)) })

        is SerializedCondition.IsConstant -> mkOr(isConstant.resolve(ctx).map { IsConstant(it) })

        is SerializedCondition.IsNull -> mkOr(isNull.resolve(ctx).map { IsNull(it) })

        is SerializedCondition.IsStaticField -> {
            val className = className.normalizeAnyName()
                .toConditionNameMatcher(patternManager)

            val fieldName = fieldName.normalizeAnyName()
                .toConditionNameMatcher(patternManager)

            if (className == null && fieldName == null) {
                mkTrue()
            } else {
                mkOr(pos.resolve(ctx).map {
                    IsStaticField(
                        it,
                        className ?: ConditionNameMatcher.AnyName,
                        fieldName ?: ConditionNameMatcher.AnyName
                    )
                })
            }
        }

        is SerializedCondition.ContainsMark -> mkOr(
            pos.resolvePosition(ctx)
                .flatMap { it.resolveArrayPosition() }
                .map { ContainsMark(it, taintMarkManager.taintMark(tainted)) }
        )

        is SerializedCondition.IsType -> resolveIsType(ctx)

        is SerializedCondition.NumberOfArgs -> {
            (method.parameters.size == numberOfArgs).asCondition()
        }

        is SerializedCondition.ClassAnnotated -> {
            method.enclosingClass.matched(annotation).asCondition()
        }

        is SerializedCondition.MethodAnnotated -> {
            method.matched(annotation).asCondition()
        }

        is SerializedCondition.ParamAnnotated -> {
            val containsAnnotation = pos.resolveWithAnnotationConstraint(ctx, annotation).any()
            containsAnnotation.asCondition()
        }

        is SerializedCondition.MethodNameMatches -> {
            methodName.match(patternManager, method.name).asCondition()
        }

        is SerializedCondition.ClassNameMatches -> {
            className.match(patternManager, method.enclosingClass.name).asCondition()
        }
    }

    private fun Boolean.asCondition(): Condition = if (this) mkTrue() else mkFalse()

    private fun SerializedCondition.IsType.resolveIsType(ctx: AnyArgSpecializationCtx): Condition {
        val position = pos.resolve(ctx)
        if (position.isEmpty()) return mkFalse()

        val falsePositions = hashSetOf<Position>()

        val normalizedTypeIs = typeIs.normalizeAnyName()

        for (pos in position) {
            val posTypeName = when (pos) {
                is Argument -> method.parameters[pos.index].type.typeName
                is Result -> method.returnType.typeName
                is This -> method.enclosingClass.name
                is PositionWithAccess,
                is ClassStatic -> continue
            }

            if (normalizedTypeIs.matchTypedOrErased(posTypeName) { typedMethod?.positionType(pos) }) {
                return mkTrue()
            }

            if (pos is This) {
                val anySuperTypeMatch = method.enclosingClass.allSuperHierarchySequence.any {
                    normalizedTypeIs.matchTypedOrErased(it.name) { typedMethod?.positionType(This) }
                }
                if (anySuperTypeMatch) return mkTrue()

                if (method.isConstructor || method.isFinal) {
                    falsePositions.add(pos)
                }
            }
        }

        val matcher = normalizedTypeIs.toConditionNameMatcher(patternManager)
            ?: return mkTrue()

        val nonFalsePositions = position.filter { it !in falsePositions }
        val typeArgs = (normalizedTypeIs as? ClassPattern)?.typeArgs
            ?.map { it.toTypeArgMatcher(patternManager) }

        return mkOr(nonFalsePositions.map { TypeMatchesPattern(it, matcher, typeArgs) })
    }

    private fun JIRTypedMethod.positionType(pos: Position): JIRType? = when (pos) {
        is Argument -> parameters.getOrNull(pos.index)?.type
        is Result -> returnType
        is This -> enclosingType
        else -> null
    }

    private fun resolveTypedMethod(): JIRTypedMethod? {
        val classType = cp.typeOf(method.enclosingClass) as? JIRClassType ?: return null
        return classType.declaredMethods.find { it.method == method }
    }

    private fun SerializedTaintAssignAction.resolveWithArray(ctx: AnyArgSpecializationCtx): List<AssignMark> =
        pos.resolvePositionWithAnnotationConstraint(ctx, annotatedWith?.asAnnotationConstraint())
            .flatMap { it.resolveArrayPosition() }
            .map { AssignMark(taintMarkManager.taintMark(kind), it) }

    private fun SerializedTaintAssignAction.resolveNoArray(ctx: AnyArgSpecializationCtx): List<AssignMark> =
        pos.resolvePositionWithAnnotationConstraint(ctx, annotatedWith?.asAnnotationConstraint())
            .flatMap { it.resolveArrayPosition() }
            .map { AssignMark(taintMarkManager.taintMark(kind), it) }

    private fun Position.resolveArrayPosition(): List<Position> = when (this) {
        is ClassStatic -> listOf(this)
        is PositionWithAccess -> base.resolveArrayPosition().map { PositionWithAccess(it, access) }
        is This -> listOf(this)
        is Argument -> resolveArrayPosition(this, method.parameters.getOrNull(index)?.type)
        is Result -> resolveArrayPosition(this, method.returnType)
    }

    private fun resolveArrayPosition(position: Position, positionType: TypeName?): List<Position> {
        if (positionType == null) return listOf(position)

        if (!positionType.isArray && positionType != objectTypeName) {
            return listOf(position)
        }

        return listOf(position, PositionWithAccess(position, PositionAccessor.ElementAccessor))
    }

    private fun SerializedTaintPassAction.resolve(ctx: AnyArgSpecializationCtx): List<Action> =
        from.resolvePosition(ctx).flatMap { fromPos ->
            to.resolvePosition(ctx).map { toPos ->
                val taintKind = taintKind
                if (taintKind == null) {
                    CopyAllMarks(fromPos, toPos)
                } else {
                    CopyMark(taintMarkManager.taintMark(taintKind), fromPos, toPos)
                }
            }
        }

    private fun SerializedTaintCleanAction.resolve(ctx: AnyArgSpecializationCtx): List<Action> =
        pos.resolvePosition(ctx)
            .map { pos ->
                val taintKind = taintKind
                if (taintKind == null) {
                    RemoveAllMarks(pos)
                } else {
                    RemoveMark(taintMarkManager.taintMark(taintKind), pos)
                }
            }

    private fun PositionBaseWithModifiers.resolvePosition(
        ctx: AnyArgSpecializationCtx,
    ): List<Position> = resolvePositionWithModifiers { it.resolve(ctx) }

    private fun PositionBaseWithModifiers.resolvePositionWithAnnotationConstraint(
        ctx: AnyArgSpecializationCtx,
        annotation: AnnotationConstraint?
    ): List<Position> {
        if (annotation == null) return resolvePosition(ctx)
        return resolvePositionWithModifiers {
            it.resolveWithAnnotationConstraint(ctx, annotation)
        }
    }

    private inline fun PositionBaseWithModifiers.resolvePositionWithModifiers(
        resolveBase: (PositionBase) -> List<Position>
    ): List<Position> {
        val resolvedBase = resolveBase(base)
        return when (this) {
            is PositionBaseWithModifiers.BaseOnly -> resolvedBase
            is PositionBaseWithModifiers.WithModifiers -> {
                resolvedBase.map { b ->
                    modifiers.fold(b) { basePos, modifier ->
                        val accessor = when (modifier) {
                            PositionModifier.AnyField -> PositionAccessor.AnyFieldAccessor
                            PositionModifier.ArrayElement -> PositionAccessor.ElementAccessor
                            is PositionModifier.Field -> {
                                PositionAccessor.FieldAccessor(
                                    modifier.className,
                                    modifier.fieldName,
                                    modifier.fieldType
                                )
                            }
                        }
                        PositionWithAccess(basePos, accessor)
                    }
                }
            }
        }
    }

    private fun PositionBase.resolve(ctx: AnyArgSpecializationCtx): List<Position> {
        when (this) {
            is PositionBase.AnyArgument -> return listOf(ctx.resolve(this))

            is PositionBase.Argument -> {
                val idx = idx
                if (idx != null) {
                    if (idx !in method.parameters.indices) return emptyList()
                    return listOf(Argument(idx))
                } else {
                    return method.parameters.map { Argument(it.index) }
                }
            }

            PositionBase.Result -> {
                if (method.returnType.typeName == PredefinedPrimitives.Void) return emptyList()
                return listOf(Result)
            }

            PositionBase.This -> {
                if (method.isStatic) return emptyList()
                return listOf(This)
            }

            is PositionBase.ClassStatic -> return listOf(ClassStatic(className))
        }
    }

    private fun PositionBase.resolveWithAnnotationConstraint(
        ctx: AnyArgSpecializationCtx,
        annotation: AnnotationConstraint
    ): List<Position> {
        val arguments = when (this) {
            is PositionBase.AnyArgument -> listOf(ctx.resolve(this))

            is PositionBase.Argument -> {
                val idx = idx
                if (idx != null) {
                    listOf(Argument(idx))
                } else {
                    method.parameters.map { Argument(it.index) }
                }
            }

            PositionBase.Result,
            PositionBase.This,
            is PositionBase.ClassStatic -> TODO("Annotation constraint on non-argument position")
        }

        return arguments.mapNotNull { arg ->
            val param = method.parameters.getOrNull(arg.index) ?: return@mapNotNull null
            if (!param.matched(annotation)) return@mapNotNull null

            arg
        }
    }

    private fun SerializedTypeNameMatcher.asAnnotationConstraint(): AnnotationConstraint =
        AnnotationConstraint(this, params = null)

    private fun JIRAnnotated.matched(constraint: AnnotationConstraint): Boolean =
        matchedAnnotations { constraint.type.match(patternManager, it) }
            .any { it.paramsMatched(constraint) }

    private fun JIRAnnotation.paramsMatched(constraint: AnnotationConstraint): Boolean {
        val paramMatchers = constraint.params ?: return true
        return paramMatchers.all { matched(it) }
    }

    private fun JIRAnnotation.matched(param: AnnotationParamMatcher): Boolean {
        val matchedParams = values.filter { param.name.match(patternManager, it.key) }
        val rawParamValues = matchedParams.mapNotNull { it.value }
        val flatParamValues = rawParamValues.flatMap { it.flatAnnotationValues() }
        return flatParamValues.any { paramValue ->
            val paramValueStr = paramValue.toString()

            when (param) {
                is SerializedCondition.AnnotationParamStringMatcher -> {
                    param.value.match(patternManager, paramValueStr)
                }
            }
        }
    }

    private fun Any.flatAnnotationValues(): List<Any> =
        if (this !is List<*>) listOf(this) else flatMap { it?.flatAnnotationValues().orEmpty() }
}
