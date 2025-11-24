package org.opentaint.dataflow.configuration.jvm.serialized

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.PredefinedPrimitives
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.api.jvm.ext.allSuperHierarchySequence
import org.opentaint.ir.api.jvm.ext.findTypeOrNull
import org.opentaint.ir.api.jvm.ext.isAssignable
import org.opentaint.ir.impl.types.TypeNameImpl
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.jvm.Action
import org.opentaint.dataflow.configuration.jvm.And
import org.opentaint.dataflow.configuration.jvm.AnyNameMatcher
import org.opentaint.dataflow.configuration.jvm.AnyTypeMatcher
import org.opentaint.dataflow.configuration.jvm.Argument
import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.configuration.jvm.ClassMatcher
import org.opentaint.dataflow.configuration.jvm.ClassStatic
import org.opentaint.dataflow.configuration.jvm.Condition
import org.opentaint.dataflow.configuration.jvm.ConditionSimplifier
import org.opentaint.dataflow.configuration.jvm.ConditionVisitor
import org.opentaint.dataflow.configuration.jvm.ConfigurationTrie
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
import org.opentaint.dataflow.configuration.jvm.JIRTypeNameMatcher
import org.opentaint.dataflow.configuration.jvm.JIRTypeNamePatternMatcher
import org.opentaint.dataflow.configuration.jvm.NameExactMatcher
import org.opentaint.dataflow.configuration.jvm.NameMatcher
import org.opentaint.dataflow.configuration.jvm.NamePatternMatcher
import org.opentaint.dataflow.configuration.jvm.Not
import org.opentaint.dataflow.configuration.jvm.Or
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
import org.opentaint.dataflow.configuration.jvm.This
import org.opentaint.dataflow.configuration.jvm.TypeMatcher
import org.opentaint.dataflow.configuration.jvm.TypeMatches
import org.opentaint.dataflow.configuration.jvm.TypeMatchesPattern
import org.opentaint.dataflow.configuration.jvm.isFalse
import org.opentaint.dataflow.configuration.jvm.match
import org.opentaint.dataflow.configuration.jvm.mkAnd
import org.opentaint.dataflow.configuration.jvm.mkFalse
import org.opentaint.dataflow.configuration.jvm.mkOr
import org.opentaint.dataflow.configuration.jvm.mkTrue
import org.opentaint.dataflow.configuration.jvm.resolveTypeMatcherCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.AnnotationConstraint
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.AnnotationParamMatcher
import org.opentaint.dataflow.configuration.jvm.simplify
import java.util.concurrent.atomic.AtomicInteger

class TaintConfiguration {
    private val entryPointConfig = TaintRulesStorage<SerializedRule.EntryPoint, TaintEntryPointSource>()
    private val sourceConfig = TaintRulesStorage<SerializedRule.Source, TaintMethodSource>()
    private val sinkConfig = TaintRulesStorage<SerializedRule.Sink, TaintMethodSink>()
    private val passThroughConfig = TaintRulesStorage<SerializedRule.PassThrough, TaintPassThrough>()
    private val cleanerConfig = TaintRulesStorage<SerializedRule.Cleaner, TaintCleaner>()
    private val methodExitSinkConfig = TaintRulesStorage<SerializedRule.MethodExitSink, TaintMethodExitSink>()
    private val methodEntrySinkConfig = TaintRulesStorage<SerializedRule.MethodEntrySink, TaintMethodEntrySink>()

    private val taintMarks = hashMapOf<String, TaintMark>()

    fun loadConfig(config: SerializedTaintConfig) {
        config.entryPoint?.let { entryPointConfig.addRules(it) }
        config.source?.let { sourceConfig.addRules(it) }
        config.sink?.let { sinkConfig.addRules(it) }
        config.passThrough?.let { passThroughConfig.addRules(it) }
        config.cleaner?.let { cleanerConfig.addRules(it) }
        config.methodExitSink?.let { methodExitSinkConfig.addRules(it) }
        config.methodEntrySink?.let { methodEntrySinkConfig.addRules(it) }
    }

    fun entryPointForMethod(method: JIRMethod): List<TaintEntryPointSource> = entryPointConfig.getConfigForMethod(method)
    fun sourceForMethod(method: JIRMethod): List<TaintMethodSource> = sourceConfig.getConfigForMethod(method)
    fun sinkForMethod(method: JIRMethod): List<TaintMethodSink> = sinkConfig.getConfigForMethod(method)
    fun passThroughForMethod(method: JIRMethod): List<TaintPassThrough> = passThroughConfig.getConfigForMethod(method)
    fun cleanerForMethod(method: JIRMethod): List<TaintCleaner> = cleanerConfig.getConfigForMethod(method)
    fun methodExitSinkForMethod(method: JIRMethod): List<TaintMethodExitSink> = methodExitSinkConfig.getConfigForMethod(method)
    fun methodEntrySinkForMethod(method: JIRMethod): List<TaintMethodEntrySink> = methodEntrySinkConfig.getConfigForMethod(method)

    private inner class TaintRulesStorage<S : SerializedRule, T : TaintConfigurationItem> {
        private val rulesTrie = TaintConfigurationTrie<S>()
        private val classRules = hashMapOf<JIRClassOrInterface, List<S>>()
        private val methodItems = hashMapOf<JIRMethod, List<T>>()

        fun addRules(rules: List<S>) {
            rulesTrie.addRules(rules)

            // invalidate rules cache
            classRules.clear()
            methodItems.clear()
        }

        @Synchronized
        fun getConfigForMethod(method: JIRMethod): List<T> = methodItems.getOrPut(method) {
            resolveMethodItems(method)
        }

        private fun getClassRules(clazz: JIRClassOrInterface) = classRules.getOrPut(clazz) {
            rulesTrie.getRulesForClass(clazz)
        }

        private fun resolveMethodItems(method: JIRMethod): List<T> {
            val rules = getClassRules(method.enclosingClass).toMutableList()
            method.enclosingClass.allSuperHierarchySequence.distinct().forEach { cls ->
                getClassRules(cls).filterTo(rules) { it.overrides }
            }

            rules.removeAll { !it.function.name.matchFunctionName(method) }
            rules.removeAll { it.signature?.matchFunctionSignature(method) == false }

            return rules.flatMap { resolveMethodRule(it, method) }
        }

        @Suppress("UNCHECKED_CAST")
        private fun resolveMethodRule(rule: S, method: JIRMethod): List<T> =
            rule.resolveMethodRule(method) as List<T>
    }

    private inner class TaintConfigurationTrie<T : SerializedRule> : ConfigurationTrie<T>() {
        override fun nameMatches(matcher: NameMatcher, name: String): Boolean =
            matcher.serializedNameMatcher().match(name)

        override fun ruleClassNameMatcher(rule: T): ClassMatcher {
            val function = rule.function
            return ClassMatcher(function.`package`.nameMatcher(), function.`class`.nameMatcher())
        }

        override fun updateRuleClassNameMatcher(rule: T, matcher: ClassMatcher): T {
            val updatedFunction = SerializedFunctionNameMatcher.Complex(
                `package` = matcher.pkg.serializedNameMatcher(),
                `class` = matcher.classNameMatcher.serializedNameMatcher(),
                name = rule.function.name,
            ).simplify()

            @Suppress("UNCHECKED_CAST")
            return when (val r = rule as SerializedRule) {
                is SerializedRule.Cleaner -> r.copy(function = updatedFunction)
                is SerializedRule.EntryPoint -> r.copy(function = updatedFunction)
                is SerializedRule.PassThrough -> r.copy(function = updatedFunction)
                is SerializedRule.Sink -> r.copy(function = updatedFunction)
                is SerializedRule.Source -> r.copy(function = updatedFunction)
                is SerializedRule.MethodExitSink -> r.copy(function = updatedFunction)
                is SerializedRule.MethodEntrySink -> r.copy(function = updatedFunction)
            } as T
        }
    }

    private fun SerializedNameMatcher.nameMatcher(): NameMatcher = when (this) {
        is SerializedNameMatcher.Simple -> if (value == "*") AnyNameMatcher else NameExactMatcher(value)
        is SerializedNameMatcher.Pattern -> NamePatternMatcher(pattern)
        is SerializedNameMatcher.ClassPattern -> error("Unexpected serialized name: $this")
    }

    private fun SerializedNameMatcher.typeNameMatcher(): TypeMatcher = when (this) {
        is SerializedNameMatcher.Simple -> if (value == "*") AnyTypeMatcher else JIRTypeNameMatcher(value)

        is SerializedNameMatcher.Pattern -> if (pattern == ".*") {
            AnyTypeMatcher
        } else {
            JIRTypeNamePatternMatcher(NamePatternMatcher(pattern))
        }

        is SerializedNameMatcher.ClassPattern -> {
            ClassMatcher(`package`.nameMatcher(), `class`.nameMatcher())
        }
    }

    private fun NameMatcher.serializedNameMatcher(): SerializedNameMatcher = when (this) {
        is NameExactMatcher -> SerializedNameMatcher.Simple(name)
        is NamePatternMatcher -> SerializedNameMatcher.Pattern(pattern)
        AnyNameMatcher -> SerializedNameMatcher.Pattern(".*")
    }

    private val compiledMatchers = hashMapOf<String, Regex>()

    private fun compilePattern(pattern: String): Regex =
        compiledMatchers.getOrPut(pattern) { pattern.toRegex() }

    private fun matchPattern(pattern: String, str: String): Boolean =
        compilePattern(pattern).containsMatchIn(str)

    private fun SerializedNameMatcher.match(name: String): Boolean = when (this) {
        is SerializedNameMatcher.Simple -> if (value == "*") true else value == name
        is SerializedNameMatcher.Pattern -> matchPattern(pattern, name)
        is SerializedNameMatcher.ClassPattern -> {
            `package`.match(name.substringBeforeLast('.', missingDelimiterValue = ""))
                    && `class`.match(name.substringAfterLast('.', missingDelimiterValue = name))
        }
    }

    private fun SerializedNameMatcher.matchFunctionName(method: JIRMethod): Boolean {
        if (match(method.name)) return true

        if (method.isConstructor) {
            val constructorNames = arrayOf("init^", "<init>")
            if (constructorNames.any { match(it) }) return true
        }

        return false
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
                if (`return` != null && !`return`.match(method.returnType.typeName)) return false

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

    private fun SerializedRule.resolveMethodRule(method: JIRMethod): List<TaintConfigurationItem> {
        val serializedCondition = when (this) {
            is SerializedRule.SinkRule -> condition
            is SerializedRule.SourceRule -> condition
            is SerializedRule.Cleaner -> condition
            is SerializedRule.PassThrough -> condition
        }

        val contexts = serializedCondition.anyArgSpecializationContexts(method)
        return contexts.mapNotNull { resolveMethodRule(method, serializedCondition, it) }
    }

    private fun SerializedRule.resolveMethodRule(
        method: JIRMethod,
        serializedCondition: SerializedCondition?,
        ctx: AnyArgSpecializationCtx,
    ): TaintConfigurationItem? {
        val partiallyResolvedCondition = serializedCondition.resolve(method, ctx)
        if (partiallyResolvedCondition.isFalse()) return null

        val condition = partiallyResolvedCondition.resolveIsType(method).simplify()
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

    private fun SerializedRule.SinkRule.ruleId(): String {
        id?.let { return it }
        meta?.cwe?.firstOrNull()?.let { return "CWE-$it" }
        return "generated-id-${ruleIdGen.incrementAndGet()}"
    }

    private fun SerializedRule.SinkRule.meta(): TaintSinkMeta = TaintSinkMeta(
        message = meta?.message() ?: "",
        severity = meta?.severity ?: CommonTaintConfigurationSinkMeta.Severity.Warning,
        cwe = meta?.cwe
    )

    private fun SerializedRule.SinkMetaData.message(): String? {
        if (cwe == null && note == null) return null

        val cweStr = cwe?.let { "CWE $it " }.orEmpty()
        val noteStr = note.orEmpty()
        return cweStr + noteStr
    }

    private fun taintMark(name: String): TaintMark = taintMarks.getOrPut(name) { TaintMark(name) }

    data class AnyArgSpecializationCtx(val positions: Map<String, Argument>) {
        fun resolve(anyArg: PositionBase.AnyArgument): Argument =
            positions[anyArg.classifier]
                ?: error("Unresolved anyarg classifier")
    }

    private fun SerializedCondition?.anyArgSpecializationContexts(method: JIRMethod): List<AnyArgSpecializationCtx> {
        val classifiers = hashSetOf<String>()
        collectAnyArgumentClassifiers(classifiers)

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
            pos.resolve(method, ctx).map { ConstantMatches(it, compilePattern(constantMatches)) })

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
            matchPattern(nameMatches, method.name).asCondition()
        }
    }

    private fun Boolean.asCondition(): Condition = if (this) mkTrue() else mkFalse()

    private data class UnresolvedIsType(
        val typedPositions: List<Pair<Position, TypeName>>,
        val typeMatcher: TypeMatcher,
    ) : Condition {
        override fun <R> accept(conditionVisitor: ConditionVisitor<R>): R {
            if (conditionVisitor is ConditionSimplifier) {
                @Suppress("UNCHECKED_CAST")
                return this as R
            }

            error("Unresolved Is Type is auxiliary expression")
        }
    }

    private fun SerializedCondition.IsType.resolveIsType(method: JIRMethod, ctx: AnyArgSpecializationCtx): Condition {
        val position = pos.resolve(method, ctx)
        if (position.isEmpty()) return mkFalse()

        val typeMatcher = typeIs.typeNameMatcher()
        if (typeMatcher is AnyTypeMatcher) return mkTrue()

        val positionTypes = position.mapNotNull {
            it to when (it) {
                is Argument -> method.parameters[it.index].type
                Result -> method.returnType
                This -> TypeNameImpl.fromTypeName(method.enclosingClass.name)
                is PositionWithAccess,
                is ClassStatic -> return@mapNotNull null
            }
        }

        if (positionTypes.any { typeMatcher.match(it.second, ::matchName) }) {
            return mkTrue()
        }

        return UnresolvedIsType(positionTypes, typeMatcher)
    }

    private fun Condition.resolveIsType(method: JIRMethod): Condition = when (this) {
        is Not -> Not(arg.resolveIsType(method))
        is And -> And(args.map { it.resolveIsType(method) })
        is Or -> Or(args.map { it.resolveIsType(method) })
        is UnresolvedIsType -> resolveIsType(method)
        else -> this
    }

    private fun UnresolvedIsType.resolveIsType(method: JIRMethod): Condition {
        val cp = method.enclosingClass.classpath
        val typePositions = typedPositions.groupBy({ cp.findTypeOrNull(it.second) }, { it.first })

        val (types, matchPatterns) = typeMatcher.resolveTypeMatcherCondition(cp, typePositions.keys, ::matchName)
            ?: return mkTrue()

        val condition = mutableListOf<Condition>()
        for ((baseType, positions) in typePositions) {
            val suitableTypes = if (baseType == null) types else types.filter { it.isAssignable(baseType) }
            positions.forEach { pos ->
                suitableTypes.mapTo(condition) { TypeMatches(pos, it) }
            }
        }

        for (pattern in matchPatterns) {
            val compiledPattern = compilePattern(pattern)
            for ((_, positions) in typePositions) {
                positions.mapTo(condition) { TypeMatchesPattern(it, compiledPattern) }
            }
        }

        return mkOr(condition)
    }

    private fun matchName(matcher: NameMatcher, name: String): Boolean {
        return matcher.serializedNameMatcher().match(name)
    }

    private fun SerializedTaintAssignAction.resolve(method: JIRMethod, ctx: AnyArgSpecializationCtx): List<AssignMark> =
        pos.resolvePositionWithAnnotationConstraint(method, ctx, annotatedWith?.asAnnotationConstraint())
            .map { AssignMark(taintMark(kind), it) }

    private fun SerializedTaintPassAction.resolve(method: JIRMethod, ctx: AnyArgSpecializationCtx): List<Action> =
        from.resolvePosition(method, ctx).flatMap { fromPos ->
            to.resolvePosition(method, ctx).map { toPos ->
                if (taintKind == null) {
                    CopyAllMarks(fromPos, toPos)
                } else {
                    CopyMark(taintMark(taintKind), fromPos, toPos)
                }
            }
        }

    private fun SerializedTaintCleanAction.resolve(method: JIRMethod, ctx: AnyArgSpecializationCtx): List<Action> =
        pos.resolvePosition(method, ctx).map { pos ->
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
                matchPattern(param.pattern, paramValueStr)
            }

            is SerializedCondition.AnnotationParamStringMatcher -> {
                paramValueStr == param.value
            }
        }
    }
}
