package org.opentaint.jvm.sast.dataflow.rules

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.jvm.Action
import org.opentaint.dataflow.configuration.jvm.Argument
import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.configuration.jvm.ClassStatic
import org.opentaint.dataflow.configuration.jvm.Condition
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
import org.opentaint.dataflow.configuration.jvm.TaintMark
import org.opentaint.dataflow.configuration.jvm.TaintMethodEntrySink
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSink
import org.opentaint.dataflow.configuration.jvm.TaintMethodSink
import org.opentaint.dataflow.configuration.jvm.TaintMethodSource
import org.opentaint.dataflow.configuration.jvm.TaintPassThrough
import org.opentaint.dataflow.configuration.jvm.TaintSinkMeta
import org.opentaint.dataflow.configuration.jvm.TaintStaticFieldSource
import org.opentaint.dataflow.configuration.jvm.This
import org.opentaint.dataflow.configuration.jvm.TypeMatchesPattern
import org.opentaint.dataflow.configuration.jvm.isFalse
import org.opentaint.dataflow.configuration.jvm.mkAnd
import org.opentaint.dataflow.configuration.jvm.mkFalse
import org.opentaint.dataflow.configuration.jvm.mkOr
import org.opentaint.dataflow.configuration.jvm.mkTrue
import org.opentaint.dataflow.configuration.jvm.serialized.*
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.AnnotationConstraint
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.AnnotationParamMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedNameMatcher.ClassPattern
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedNameMatcher.Pattern
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedNameMatcher.Simple
import org.opentaint.dataflow.configuration.jvm.simplify
import org.opentaint.dataflow.jvm.util.JIRHierarchyInfo
import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.PredefinedPrimitives
import org.opentaint.ir.impl.util.adjustEmptyList
import java.util.concurrent.atomic.AtomicInteger

class TaintConfiguration(cp: JIRClasspath) {
    private val patternManager = PatternManager()
    private val hierarchyInfo = JIRHierarchyInfo(cp)

    private val entryPointConfig = TaintRulesStorage<SerializedRule.EntryPoint, TaintEntryPointSource>()
    private val sourceConfig = TaintRulesStorage<SerializedRule.Source, TaintMethodSource>()
    private val sinkConfig = TaintRulesStorage<SerializedRule.Sink, TaintMethodSink>()
    private val passThroughConfig = TaintRulesStorage<SerializedRule.PassThrough, TaintPassThrough>()
    private val cleanerConfig = TaintRulesStorage<SerializedRule.Cleaner, TaintCleaner>()
    private val methodExitSinkConfig = TaintRulesStorage<SerializedRule.MethodExitSink, TaintMethodExitSink>()
    private val analysisEndSinkConfig = TaintRulesStorage<SerializedRule.MethodExitSink, TaintMethodExitSink>()
    private val methodEntrySinkConfig = TaintRulesStorage<SerializedRule.MethodEntrySink, TaintMethodEntrySink>()

    private val staticFieldSourceConfig = TaintFieldRulesStorage<SerializedFieldRule.SerializedStaticFieldSource, TaintStaticFieldSource>()

    private val taintMarks = hashMapOf<String, TaintMark>()

    fun loadConfig(config: SerializedTaintConfig) {
        config.entryPoint?.let { entryPointConfig.addRules(it) }
        config.source?.let { sourceConfig.addRules(it) }
        config.sink?.let { sinkConfig.addRules(it) }
        config.passThrough?.let { passThroughConfig.addRules(it) }
        config.cleaner?.let { cleanerConfig.addRules(it) }
        config.methodExitSink?.let { methodExitSinkConfig.addRules(it) }
        config.methodEntrySink?.let { methodEntrySinkConfig.addRules(it) }
        config.staticFieldSource?.let { staticFieldSourceConfig.addRules(it) }
        config.analysisEndSink?.let { r -> analysisEndSinkConfig.addRules(r.map { it.asMethodExitSink() }) }
    }

    private val anyFunction by lazy {
        SerializedFunctionNameMatcher.Complex(
            anyNameMatcher(), anyNameMatcher(), anyNameMatcher()
        )
    }

    private fun AnalysisEndSink.asMethodExitSink(): SerializedRule.MethodExitSink {
        return SerializedRule.MethodExitSink(anyFunction, overrides = false, condition = condition, id = id, meta = meta)
    }

    fun entryPointForMethod(method: JIRMethod): List<TaintEntryPointSource> = entryPointConfig.getConfigForMethod(method)
    fun sourceForMethod(method: JIRMethod): List<TaintMethodSource> = sourceConfig.getConfigForMethod(method)
    fun sinkForMethod(method: JIRMethod): List<TaintMethodSink> = sinkConfig.getConfigForMethod(method)
    fun passThroughForMethod(method: JIRMethod): List<TaintPassThrough> = passThroughConfig.getConfigForMethod(method)
    fun cleanerForMethod(method: JIRMethod): List<TaintCleaner> = cleanerConfig.getConfigForMethod(method)
    fun methodExitSinkForMethod(method: JIRMethod): List<TaintMethodExitSink> = methodExitSinkConfig.getConfigForMethod(method)
    fun analysisEndSinkForMethod(method: JIRMethod): List<TaintMethodExitSink> = analysisEndSinkConfig.getConfigForMethod(method)
    fun methodEntrySinkForMethod(method: JIRMethod): List<TaintMethodEntrySink> = methodEntrySinkConfig.getConfigForMethod(method)

    fun sourceForStaticField(field: JIRField): List<TaintStaticFieldSource> {
        check(field.isStatic)
        return staticFieldSourceConfig.getConfigForField(field)
    }

    private inner class TaintFieldRulesStorage<S : SerializedFieldRule, T : TaintConfigurationItem> {
        private val fieldRules = hashMapOf<String, MutableList<S>>()
        private val fieldItems = hashMapOf<JIRField, List<T>>()

        fun addRules(rules: List<S>) {
            for (rule in rules) {
                fieldRules.getOrPut(rule.fieldName, ::mutableListOf).add(rule)
            }

            // invalidate rules cache
            fieldItems.clear()
        }

        @Synchronized
        fun getConfigForField(field: JIRField): List<T> = fieldItems.getOrPut(field) {
            resolveFieldItems(field).adjustEmptyList()
        }

        private fun resolveFieldItems(field: JIRField): List<T> {
            val rules = fieldRules[field.name]?.toMutableList() ?: return emptyList()
            rules.removeAll { !it.className.match(field.enclosingClass.name) }
            return rules.flatMap { resolveFieldRule(it, field) }
        }

        @Suppress("UNCHECKED_CAST")
        private fun resolveFieldRule(rule: S, field: JIRField): List<T> =
            rule.resolveFieldRule(field) as List<T>
    }

    private inner class TaintRulesStorage<S : SerializedRule, T : TaintConfigurationItem> {
        private var builder: MethodTaintRulesStorage.Builder<S>? = MethodTaintRulesStorage.Builder(patternManager, hierarchyInfo)
        private var storage: MethodTaintRulesStorage<S>? = null

        private fun storage(): MethodTaintRulesStorage<S> {
            storage?.let { return it }

            storage = builder?.build()
            builder = null

            return storage ?: error("Storage initialization failed")
        }

        fun addRules(rules: List<S>) {
            val builder = this.builder ?: error("Storage rule set closed")
            builder.addRules(rules)
        }

        private val methodItems = hashMapOf<JIRMethod, List<T>>()

        @Synchronized
        fun getConfigForMethod(method: JIRMethod): List<T> = methodItems.getOrPut(method) {
            resolveMethodItems(method).adjustEmptyList()
        }

        private fun resolveMethodItems(method: JIRMethod): List<T> {
            val rules = mutableListOf<S>()
            storage().findRules(rules, method)

            rules.removeAll { it.signature?.matchFunctionSignature(method) == false }

            return rules.flatMap { resolveMethodRule(it, method) }
        }

        @Suppress("UNCHECKED_CAST")
        private fun resolveMethodRule(rule: S, method: JIRMethod): List<T> =
            rule.resolveMethodRule(method) as List<T>
    }

    private fun SerializedNameMatcher.match(name: String): Boolean = when (this) {
        is Simple -> if (value == "*") true else value == name
        is Pattern -> {
            isAny() || patternManager.matchPattern(pattern, name)
        }
        is ClassPattern -> {
            val (pkgName, clsName) = splitClassName(name)
            `package`.match(pkgName) && `class`.match(clsName)
        }
    }

    private fun SerializedSignatureMatcher.matchFunctionSignature(method: JIRMethod): Boolean {
        when (this) {
            is SerializedSignatureMatcher.Simple -> {
                if (method.parameters.size != args.size) return false

                if (!`return`.match(method.returnType.typeName)) return false

                return args.zip(method.parameters).all { (matcher, param) ->
                    matcher.match(param.type.typeName)
                }
            }

            is SerializedSignatureMatcher.Partial -> {
                val ret = `return`
                if (ret != null && !ret.match(method.returnType.typeName)) return false

                val params = params
                if (params != null) {
                    for (param in params) {
                        val methodParam = method.parameters.getOrNull(param.index) ?: return false
                        if (!param.type.match(methodParam.type.typeName)) return false
                    }
                }

                return true
            }
        }
    }

    private fun SerializedFieldRule.resolveFieldRule(field: JIRField): List<TaintConfigurationItem> {
        when (this) {
            is SerializedFieldRule.SerializedStaticFieldSource -> {
                if (condition != null && condition !is SerializedCondition.True) {
                    TODO("Complex field rule condition")
                }

                val actions = mutableListOf<AssignMark>()
                for (action in taint) {
                    if (action.pos !is PositionBaseWithModifiers.BaseOnly || action.pos.base !is PositionBase.Result) {
                        TODO("Complex field action position")
                    }
                    actions += AssignMark(taintMark(action.kind), Result)
                }
                return listOf(TaintStaticFieldSource(field, ConstantTrue, actions))
            }
        }
    }

    private fun SerializedRule.resolveMethodRule(method: JIRMethod): List<TaintConfigurationItem> {
        val serializedCondition = when (this) {
            is SinkRule -> condition
            is SourceRule -> condition
            is SerializedRule.Cleaner -> condition
            is SerializedRule.PassThrough -> condition
        }

        val actions = when (this) {
            is SerializedRule.Source -> taint
            is SerializedRule.EntryPoint -> taint
            is SerializedRule.Cleaner -> cleans
            is SerializedRule.PassThrough -> copy
            is SerializedRule.MethodEntrySink,
            is SerializedRule.MethodExitSink,
            is SerializedRule.Sink -> emptyList()
        }

        val contexts = anyArgSpecializationContexts(method, serializedCondition, actions)
        return contexts.mapNotNull { resolveMethodRule(method, serializedCondition, it) }
    }

    private fun SerializedRule.resolveMethodRule(
        method: JIRMethod,
        serializedCondition: SerializedCondition?,
        ctx: AnyArgSpecializationCtx,
    ): TaintConfigurationItem? {
        val condition = serializedCondition.resolve(method, ctx).simplify()
        if (condition.isFalse()) return null

        return when (this) {
            is SerializedRule.EntryPoint -> {
                TaintEntryPointSource(method, condition, taint.flatMap { it.resolve(method, ctx) })
            }

            is SerializedRule.Source -> {
                TaintMethodSource(method, condition, taint.flatMap { it.resolve(method, ctx) })
            }

            is SerializedRule.Sink -> {
                TaintMethodSink(method, condition, ruleId(), meta())
            }

            is SerializedRule.MethodExitSink -> {
                TaintMethodExitSink(method, condition, ruleId(), meta())
            }

            is SerializedRule.MethodEntrySink -> {
                TaintMethodEntrySink(method, condition, ruleId(), meta())
            }

            is SerializedRule.PassThrough -> {
                TaintPassThrough(method, condition, copy.flatMap { it.resolve(method, ctx) })
            }

            is SerializedRule.Cleaner -> {
                TaintCleaner(method, condition, cleans.flatMap { it.resolve(method, ctx) })
            }
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

    private fun taintMark(name: String): TaintMark = taintMarks.getOrPut(name) { TaintMark(name) }

    data class AnyArgSpecializationCtx(val positions: Map<String, Argument>) {
        fun resolve(anyArg: PositionBase.AnyArgument): Argument =
            positions[anyArg.classifier]
                ?: error("Unresolved anyarg classifier")
    }

    private fun anyArgSpecializationContexts(
        method: JIRMethod, condition: SerializedCondition?, actions: List<SerializedAction>
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
        is SerializedCondition.IsType -> pos.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.ParamAnnotated -> pos.collectAnyArgumentClassifiers(classifiers)
        is SerializedCondition.ClassAnnotated,
        is SerializedCondition.MethodAnnotated,
        is SerializedCondition.MethodNameMatches,
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

    private fun SerializedCondition?.resolve(
        method: JIRMethod,
        ctx: AnyArgSpecializationCtx,
    ): Condition = when (this) {
        null -> ConstantTrue
        is SerializedCondition.Not -> Not(not.resolve(method, ctx))
        is SerializedCondition.And -> mkAnd(allOf.map { it.resolve(method, ctx) })
        is SerializedCondition.Or -> mkOr(anyOf.map { it.resolve(method, ctx) })
        SerializedCondition.True -> ConstantTrue
        is SerializedCondition.AnnotationType -> {
            val containsAnnotation = pos.resolveWithAnnotationConstraint(
                method, ctx,
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

            pos.resolve(method, ctx).map {
                when (cmp) {
                    SerializedCondition.ConstantCmpType.Eq -> ConstantEq(it, value)
                    SerializedCondition.ConstantCmpType.Lt -> ConstantLt(it, value)
                    SerializedCondition.ConstantCmpType.Gt -> ConstantGt(it, value)
                }
            }.let { mkOr(it) }
        }

        is SerializedCondition.ConstantEq -> mkOr(
            pos.resolve(method, ctx).map { ConstantEq(it, ConstantStringValue(constantEq)) })

        is SerializedCondition.ConstantGt -> mkOr(
            pos.resolve(method, ctx).map { ConstantGt(it, ConstantStringValue(constantGt)) })

        is SerializedCondition.ConstantLt -> mkOr(
            pos.resolve(method, ctx).map { ConstantLt(it, ConstantStringValue(constantLt)) })

        is SerializedCondition.ConstantMatches -> mkOr(
            pos.resolve(method, ctx).map { ConstantMatches(it, patternManager.compilePattern(constantMatches)) })

        is SerializedCondition.IsConstant -> mkOr(isConstant.resolve(method, ctx).map { IsConstant(it) })

        is SerializedCondition.ContainsMark -> mkOr(
            pos.resolvePosition(method, ctx).map { ContainsMark(it, taintMark(tainted)) })

        is SerializedCondition.IsType -> resolveIsType(method, ctx)

        is SerializedCondition.NumberOfArgs -> {
            (method.parameters.size == numberOfArgs).asCondition()
        }

        is SerializedCondition.ClassAnnotated -> {
            method.enclosingClass.annotations.matched(annotation).asCondition()
        }

        is SerializedCondition.MethodAnnotated -> {
            method.annotations.matched(annotation).asCondition()
        }

        is SerializedCondition.ParamAnnotated -> {
            val containsAnnotation = pos.resolveWithAnnotationConstraint(method, ctx, annotation).any()
            containsAnnotation.asCondition()
        }

        is SerializedCondition.MethodNameMatches -> {
            patternManager.matchPattern(nameMatches, method.name).asCondition()
        }
    }

    private fun Boolean.asCondition(): Condition = if (this) mkTrue() else mkFalse()

    private fun SerializedCondition.IsType.resolveIsType(method: JIRMethod, ctx: AnyArgSpecializationCtx): Condition {
        val position = pos.resolve(method, ctx)
        if (position.isEmpty()) return mkFalse()

        val normalizedTypeIs = typeIs.normalizeAnyName()
        for (pos in position) {
            val posTypeName = when (pos) {
                is Argument -> method.parameters[pos.index].type.typeName
                Result -> method.returnType.typeName
                This -> method.enclosingClass.name
                is PositionWithAccess,
                is ClassStatic -> continue
            }

            if (normalizedTypeIs.match(posTypeName)) return mkTrue()
        }

        val matcher = normalizedTypeIs.toConditionNameMatcher(patternManager)
            ?: return mkTrue()

        return mkOr(position.map { TypeMatchesPattern(it, matcher) })
    }

    private fun SerializedTaintAssignAction.resolve(method: JIRMethod, ctx: AnyArgSpecializationCtx): List<AssignMark> =
        pos.resolvePositionWithAnnotationConstraint(method, ctx, annotatedWith?.asAnnotationConstraint())
            .map { AssignMark(taintMark(kind), it) }

    private fun SerializedTaintPassAction.resolve(method: JIRMethod, ctx: AnyArgSpecializationCtx): List<Action> =
        from.resolvePosition(method, ctx).flatMap { fromPos ->
            to.resolvePosition(method, ctx).map { toPos ->
                val taintKind = taintKind
                if (taintKind == null) {
                    CopyAllMarks(fromPos, toPos)
                } else {
                    CopyMark(taintMark(taintKind), fromPos, toPos)
                }
            }
        }

    private fun SerializedTaintCleanAction.resolve(method: JIRMethod, ctx: AnyArgSpecializationCtx): List<Action> =
        pos.resolvePosition(method, ctx).map { pos ->
            val taintKind = taintKind
            if (taintKind == null) {
                RemoveAllMarks(pos)
            } else {
                RemoveMark(taintMark(taintKind), pos)
            }
        }

    private fun PositionBaseWithModifiers.resolvePosition(
        method: JIRMethod,
        ctx: AnyArgSpecializationCtx,
    ): List<Position> = resolvePositionWithModifiers { it.resolve(method, ctx) }

    private fun PositionBaseWithModifiers.resolvePositionWithAnnotationConstraint(
        method: JIRMethod,
        ctx: AnyArgSpecializationCtx,
        annotation: AnnotationConstraint?
    ): List<Position> {
        if (annotation == null) return resolvePosition(method, ctx)
        return resolvePositionWithModifiers {
            it.resolveWithAnnotationConstraint(method, ctx, annotation)
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

    private fun PositionBase.resolve(method: JIRMethod, ctx: AnyArgSpecializationCtx): List<Position> {
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
        method: JIRMethod,
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
            if (!param.annotations.matched(annotation)) return@mapNotNull null

            arg
        }
    }

    private fun SerializedNameMatcher.asAnnotationConstraint(): AnnotationConstraint =
        AnnotationConstraint(this, params = null)

    private fun List<JIRAnnotation>.matched(constraint: AnnotationConstraint): Boolean = any { it.matched(constraint) }

    private fun JIRAnnotation.matched(constraint: AnnotationConstraint): Boolean {
        if (!constraint.type.match(name)) return false

        val paramMatchers = constraint.params ?: return true
        return paramMatchers.all { matched(it) }
    }

    private fun JIRAnnotation.matched(param: AnnotationParamMatcher): Boolean {
        val paramValue = this.values[param.name] ?: return false
        val paramValueStr = paramValue.toString()

        return when (param) {
            is SerializedCondition.AnnotationParamPatternMatcher -> {
                patternManager.matchPattern(param.pattern, paramValueStr)
            }

            is SerializedCondition.AnnotationParamStringMatcher -> {
                paramValueStr == param.value
            }
        }
    }
}
