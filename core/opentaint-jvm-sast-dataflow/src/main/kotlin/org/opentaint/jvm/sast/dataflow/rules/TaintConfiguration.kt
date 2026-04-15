package org.opentaint.jvm.sast.dataflow.rules

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.jvm.Action
import org.opentaint.dataflow.configuration.jvm.Argument
import org.opentaint.dataflow.configuration.jvm.AssignAction
import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.configuration.jvm.AssignMarkAnyField
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
import org.opentaint.dataflow.configuration.jvm.ContainsMarkOnAnyField
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
import org.opentaint.dataflow.configuration.jvm.TaintMark
import org.opentaint.dataflow.configuration.jvm.TaintMethodEntrySink
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSink
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSource
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
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.PositionModifier
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedAssignAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.AnnotationConstraint
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition.AnnotationParamMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedFieldRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedFunctionNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSignatureMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSimpleNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSimpleNameMatcher.Pattern
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSimpleNameMatcher.Simple
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintAssignAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintAssignAnyFieldAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintCleanAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintPassAction
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTypeNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTypeNameMatcher.ClassPattern
import org.opentaint.dataflow.configuration.jvm.serialized.SinkMetaData
import org.opentaint.dataflow.configuration.jvm.serialized.SinkRule
import org.opentaint.dataflow.configuration.jvm.serialized.SourceRule
import org.opentaint.dataflow.configuration.jvm.simplify
import org.opentaint.dataflow.jvm.util.JIRHierarchyInfo
import org.opentaint.ir.api.jvm.JIRAnnotated
import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.PredefinedPrimitives
import org.opentaint.ir.api.jvm.TypeName
import org.opentaint.ir.api.jvm.ext.allSuperHierarchySequence
import org.opentaint.ir.api.jvm.ext.objectClass
import org.opentaint.ir.impl.cfg.util.isArray
import org.opentaint.ir.impl.util.adjustEmptyList
import org.opentaint.jvm.sast.dataflow.matchedAnnotations
import org.opentaint.jvm.util.typename
import java.util.concurrent.atomic.AtomicInteger

class TaintConfiguration(cp: JIRClasspath) {
    private val patternManager = PatternManager()
    private val hierarchyInfo = JIRHierarchyInfo(cp)
    private val objectTypeName = cp.objectClass.typename

    private val entryPointConfig = TaintRulesStorage<SerializedRule.EntryPoint, TaintEntryPointSource>()
    private val sourceConfig = TaintRulesStorage<SerializedRule.Source, TaintMethodSource>()
    private val exitSourceConfig = TaintRulesStorage<SerializedRule.MethodExitSource, TaintMethodExitSource>()
    private val sinkConfig = TaintRulesStorage<SerializedRule.Sink, TaintMethodSink>()
    private val passThroughConfig = TaintRulesStorage<SerializedRule.PassThrough, TaintPassThrough>()
    private val cleanerConfig = TaintRulesStorage<SerializedRule.Cleaner, TaintCleaner>()
    private val methodExitSinkConfig = TaintRulesStorage<SerializedRule.MethodExitSink, TaintMethodExitSink>()
    private val methodEntrySinkConfig = TaintRulesStorage<SerializedRule.MethodEntrySink, TaintMethodEntrySink>()

    private val staticFieldSourceConfig = TaintFieldRulesStorage<SerializedFieldRule.SerializedStaticFieldSource, TaintStaticFieldSource>()

    private val taintMarks = hashMapOf<String, TaintMark>()

    fun loadConfig(config: SerializedTaintConfig) {
        config.entryPoint?.let { entryPointConfig.addRules(it) }
        config.source?.let { sourceConfig.addRules(it) }
        config.methodExitSource?.let { exitSourceConfig.addRules(it) }
        config.sink?.let { sinkConfig.addRules(it) }
        config.passThrough?.let { passThroughConfig.addRules(it) }
        config.cleaner?.let { cleanerConfig.addRules(it) }
        config.methodExitSink?.let { methodExitSinkConfig.addRules(it) }
        config.methodEntrySink?.let { methodEntrySinkConfig.addRules(it) }
        config.staticFieldSource?.let { staticFieldSourceConfig.addRules(it) }
    }

    fun entryPointForMethod(method: JIRMethod, allRelevant: Boolean): List<TaintEntryPointSource> = entryPointConfig.configForMethod(method, allRelevant)
    fun sourceForMethod(method: JIRMethod, allRelevant: Boolean): List<TaintMethodSource> = sourceConfig.configForMethod(method, allRelevant)
    fun exitSourceForMethod(method: JIRMethod, allRelevant: Boolean): List<TaintMethodExitSource> = exitSourceConfig.configForMethod(method, allRelevant)
    fun sinkForMethod(method: JIRMethod, allRelevant: Boolean): List<TaintMethodSink> = sinkConfig.configForMethod(method, allRelevant)
    fun passThroughForMethod(method: JIRMethod, allRelevant: Boolean): List<TaintPassThrough> = passThroughConfig.configForMethod(method, allRelevant)
    fun cleanerForMethod(method: JIRMethod, allRelevant: Boolean): List<TaintCleaner> = cleanerConfig.configForMethod(method, allRelevant)
    fun methodExitSinkForMethod(method: JIRMethod, allRelevant: Boolean): List<TaintMethodExitSink> = methodExitSinkConfig.configForMethod(method, allRelevant)
    fun methodEntrySinkForMethod(method: JIRMethod, allRelevant: Boolean): List<TaintMethodEntrySink> = methodEntrySinkConfig.configForMethod(method, allRelevant)

    fun sourceForStaticField(field: JIRField): List<TaintStaticFieldSource> {
        check(field.isStatic)
        return staticFieldSourceConfig.getConfigForField(field)
    }

    private inner class TaintFieldRulesStorage<S : SerializedFieldRule, T : TaintConfigurationItem> {
        private val fieldRules = hashMapOf<String, MutableList<S>>()
        private val fieldPatterns = hashMapOf<String, MutableList<S>>()

        private val fieldItems = hashMapOf<JIRField, List<T>>()

        fun addRules(rules: List<S>) {
            for (rule in rules) {
                when (val fn = rule.fieldName) {
                    is Simple -> fieldRules.getOrPut(fn.value, ::mutableListOf).add(rule)
                    is Pattern -> fieldPatterns.getOrPut(fn.pattern, ::mutableListOf).add(rule)
                }
            }

            // invalidate rules cache
            fieldItems.clear()
        }

        @Synchronized
        fun getConfigForField(field: JIRField): List<T> = fieldItems.getOrPut(field) {
            resolveFieldItems(field).adjustEmptyList()
        }

        private fun resolveFieldItems(field: JIRField): List<T> {
            val rules = mutableListOf<S>()
            rules += fieldRules[field.name].orEmpty()

            fieldPatterns
                .filter { patternManager.matchPattern(it.key, field.name) }
                .flatMapTo(rules) { it.value }

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
        private val methodAllRelevantItems = hashMapOf<JIRMethod, List<T>>()

        fun configForMethod(method: JIRMethod, allRelevant: Boolean): List<T> = if (!allRelevant) {
            getConfigForMethod(method)
        } else {
            getAllRelevantConfigForMethod(method)
        }

        @Synchronized
        private fun getConfigForMethod(method: JIRMethod): List<T> = methodItems.getOrPut(method) {
            resolveMethodItems(method).adjustEmptyList()
        }

        @Synchronized
        private fun getAllRelevantConfigForMethod(method: JIRMethod): List<T> = methodAllRelevantItems.getOrPut(method) {
            resolveMethodRelevantItems(method).adjustEmptyList()
        }

        private fun resolveMethodItems(method: JIRMethod): List<T> = resolveItems(method, ::resolveMethodRule)

        private fun resolveMethodRelevantItems(method: JIRMethod): List<T> = resolveItems(method, ::resolveMethodRelevantRule)

        @Suppress("UNCHECKED_CAST")
        private fun resolveMethodRule(rule: S, method: JIRMethod): List<T> =
            rule.resolveMethodRule(method) as List<T>

        @Suppress("UNCHECKED_CAST")
        private fun resolveMethodRelevantRule(rule: S, method: JIRMethod): List<T> =
            rule.resolveMethodRelevantRule(method) as List<T>

        private inline fun resolveItems(method: JIRMethod, resolveItem: (S, JIRMethod) -> List<T>): List<T> {
            val rules = mutableListOf<S>()
            storage().findRules(rules, method)

            rules.removeAll { !it.function.matchFunctionName(method) }
            rules.removeAll { it.signature?.matchFunctionSignature(method) == false }

            return rules.flatMap { resolveItem(it, method) }
        }
    }

    private fun SerializedTypeNameMatcher.match(name: String): Boolean {
        if (matchNormalizedTypeName(name)) return true

        val nameWithDots = name.innerClassNameWithDots()
            ?: return false

        return matchNormalizedTypeName(nameWithDots)
    }

    private fun SerializedTypeNameMatcher.matchNormalizedTypeName(name: String): Boolean = when (this) {
        is SerializedSimpleNameMatcher -> match(name)

        is ClassPattern -> {
            val (pkgName, clsName) = splitClassName(name)
            `package`.matchNormalizedTypeName(pkgName) && `class`.matchNormalizedTypeName(clsName)
        }

        is SerializedTypeNameMatcher.Array -> {
            val nameWithoutArrayModifier = name.removeSuffix("[]")
            name != nameWithoutArrayModifier && element.matchNormalizedTypeName(nameWithoutArrayModifier)
        }
    }

    private fun SerializedSimpleNameMatcher.match(name: String): Boolean = when (this) {
        is Simple -> if (value == "*") true else value == name
        is Pattern -> isAny() || patternManager.matchPattern(pattern, name)
    }

    private fun SerializedFunctionNameMatcher.matchFunctionName(method: JIRMethod): Boolean {
        if (!method.isConstructor && !method.isFinal && !method.isStatic) {
            // storage().findRules is ok
            return true
        }

        // method name matches, but class name may be not
        val classMatcher = ClassPattern(`package`, `class`)
        if (classMatcher.match(method.enclosingClass.name)) return true

        if (method.isStatic) return false

        return method.enclosingClass.allSuperHierarchySequence.any {
            classMatcher.match(it.name)
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
                return listOf(TaintStaticFieldSource(field, ConstantTrue, actions, info))
            }
        }
    }

    private fun SerializedRule.resolveMethodRelevantRule(method: JIRMethod): List<TaintConfigurationItem> =
        resolveMethodRuleWithConditionResolver(method) { condition, ctx ->
            condition.resolveRelevant(method, ctx)
        }

    private fun SerializedRule.resolveMethodRule(method: JIRMethod): List<TaintConfigurationItem> =
        resolveMethodRuleWithConditionResolver(method) { condition, ctx ->
            condition.resolve(method, ctx)
        }

    private inline fun SerializedRule.resolveMethodRuleWithConditionResolver(
        method: JIRMethod,
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

        val contexts = anyArgSpecializationContexts(method, serializedCondition, actions)
        return contexts.mapNotNull {
            val condition = resolveCondition(serializedCondition, it).simplify()
            if (condition.isFalse()) return@mapNotNull null

            resolveMethodRule(method, condition, it)
        }
    }

    private fun SerializedRule.resolveMethodRule(
        method: JIRMethod,
        condition: Condition,
        ctx: AnyArgSpecializationCtx,
    ): TaintConfigurationItem = when (this) {
        is SerializedRule.EntryPoint -> {
            TaintEntryPointSource(method, condition, taint.flatMap { it.resolveWithArray(method, ctx) }, info)
        }

        is SerializedRule.Source -> {
            TaintMethodSource(method, condition, taint.flatMap { it.resolveWithArray(method, ctx) }, info)
        }

        is SerializedRule.MethodExitSource -> {
            TaintMethodExitSource(method, condition, taint.flatMap { it.resolveWithArray(method, ctx) }, info)
        }

        is SerializedRule.Sink -> {
            TaintMethodSink(
                method, condition,
                trackFactsReachAnalysisEnd?.flatMap { it.resolveNoArray(method, ctx) }.orEmpty(),
                ruleId(), meta(), info
            )
        }

        is SerializedRule.MethodExitSink -> {
            TaintMethodExitSink(
                method, condition,
                trackFactsReachAnalysisEnd?.flatMap { it.resolveNoArray(method, ctx) }.orEmpty(),
                ruleId(), meta(), info
            )
        }

        is SerializedRule.MethodEntrySink -> {
            TaintMethodEntrySink(
                method, condition,
                trackFactsReachAnalysisEnd?.flatMap { it.resolveNoArray(method, ctx) }.orEmpty(),
                ruleId(), meta(), info
            )
        }

        is SerializedRule.PassThrough -> {
            TaintPassThrough(method, condition, copy.flatMap { it.resolve(method, ctx) }, info)
        }

        is SerializedRule.Cleaner -> {
            TaintCleaner(method, condition, cleans.flatMap { it.resolve(method, ctx) }, info)
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
                is SerializedTaintAssignAnyFieldAction -> it.posAnyField.collectAnyArgumentClassifiers(classifiers)
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
        is SerializedCondition.ContainsMarkAnyField -> pos.collectAnyArgumentClassifiers(classifiers)
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
        method: JIRMethod,
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

        return relevantCondition.resolve(method, ctx)
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
        method: JIRMethod,
        ctx: AnyArgSpecializationCtx,
    ): Condition = when (this) {
        null -> ConstantTrue
        is SerializedCondition.Not -> Not(not.resolve(method, ctx))
        is SerializedCondition.And -> mkAnd(allOf.map { it.resolve(method, ctx) })
        is SerializedCondition.Or -> mkOr(anyOf.map { it.resolve(method, ctx) })
        is SerializedCondition.True -> ConstantTrue
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

        is SerializedCondition.IsNull -> mkOr(isNull.resolve(method, ctx).map { IsNull(it) })

        is SerializedCondition.IsStaticField -> {
            val className = className.normalizeAnyName()
                .toConditionNameMatcher(patternManager)

            val fieldName = fieldName.normalizeAnyName()
                .toConditionNameMatcher(patternManager)

            if (className == null && fieldName == null) {
                mkTrue()
            } else {
                mkOr(pos.resolve(method, ctx).map {
                    IsStaticField(
                        it,
                        className ?: ConditionNameMatcher.AnyName,
                        fieldName ?: ConditionNameMatcher.AnyName
                    )
                })
            }
        }

        is SerializedCondition.ContainsMark -> mkOr(
            pos.resolvePosition(method, ctx)
                .flatMap { it.resolveArrayPosition(method) }
                .map { ContainsMark(it, taintMark(tainted)) }
        )

        is SerializedCondition.IsType -> resolveIsType(method, ctx)

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
            val containsAnnotation = pos.resolveWithAnnotationConstraint(method, ctx, annotation).any()
            containsAnnotation.asCondition()
        }

        is SerializedCondition.MethodNameMatches -> {
            methodName.match(method.name).asCondition()
        }

        is SerializedCondition.ClassNameMatches -> {
            className.match(method.enclosingClass.name).asCondition()
        }

        is SerializedCondition.ContainsMarkAnyField -> mkOr(
            pos.resolvePosition(method, ctx)
                .flatMap { it.resolveArrayPosition(method) }
                .map { ContainsMarkOnAnyField(it, taintMark(tainted)) }
        )
    }

    private fun Boolean.asCondition(): Condition = if (this) mkTrue() else mkFalse()

    private fun SerializedCondition.IsType.resolveIsType(method: JIRMethod, ctx: AnyArgSpecializationCtx): Condition {
        val position = pos.resolve(method, ctx)
        if (position.isEmpty()) return mkFalse()

        val falsePositions = hashSetOf<Position>()

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

            if (pos is This) {
                if (method.enclosingClass.allSuperHierarchySequence.any { normalizedTypeIs.match(it.name) }) {
                    return mkTrue()
                }

                if (method.isConstructor || method.isFinal) {
                    falsePositions.add(pos)
                }
            }
        }

        val matcher = normalizedTypeIs.toConditionNameMatcher(patternManager)
            ?: return mkTrue()

        val nonFalsePositions = position.filter { it !in falsePositions }
        return mkOr(nonFalsePositions.map { TypeMatchesPattern(it, matcher) })
    }

    private fun SerializedAssignAction.resolveWithArray(method: JIRMethod, ctx: AnyArgSpecializationCtx): List<AssignAction> {
        val (pos, ctr) = when (this) {
            is SerializedTaintAssignAction -> pos to ::AssignMark
            is SerializedTaintAssignAnyFieldAction ->
                posAnyField to ::AssignMarkAnyField
        }
        return createAssignWithArray(method, ctx, pos, ctr)
    }

    private fun SerializedAssignAction.resolveNoArray(method: JIRMethod, ctx: AnyArgSpecializationCtx): List<AssignAction> {
        val (pos, ctr) = when (this) {
            is SerializedTaintAssignAction -> pos to ::AssignMark
            is SerializedTaintAssignAnyFieldAction ->
                posAnyField to ::AssignMarkAnyField
        }
        return createAssignNoArray(method, ctx, pos, ctr)
    }

    private fun SerializedAssignAction.createAssignWithArray(
        method: JIRMethod, ctx: AnyArgSpecializationCtx, pos: PositionBaseWithModifiers,
        ctr: (TaintMark, Position) -> AssignAction
    ): List<AssignAction> =
        pos.resolvePositionWithAnnotationConstraint(method, ctx, annotatedWith?.asAnnotationConstraint())
            .flatMap { it.resolveArrayPosition(method) }
            .map { ctr(taintMark(kind), it) }

    private fun SerializedAssignAction.createAssignNoArray(
        method: JIRMethod, ctx: AnyArgSpecializationCtx, pos: PositionBaseWithModifiers,
        ctr: (TaintMark, Position) -> AssignAction
    ): List<AssignAction> =
        pos.resolvePositionWithAnnotationConstraint(method, ctx, annotatedWith?.asAnnotationConstraint())
            .map { ctr(taintMark(kind), it) }

    private fun Position.resolveArrayPosition(method: JIRMethod): List<Position> = when (this) {
        is ClassStatic -> listOf(this)
        is PositionWithAccess -> base.resolveArrayPosition(method).map { PositionWithAccess(it, access) }
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
        pos.resolvePosition(method, ctx)
            .map { pos ->
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
            if (!param.matched(annotation)) return@mapNotNull null

            arg
        }
    }

    private fun SerializedTypeNameMatcher.asAnnotationConstraint(): AnnotationConstraint =
        AnnotationConstraint(this, params = null)

    private fun JIRAnnotated.matched(constraint: AnnotationConstraint): Boolean =
        matchedAnnotations { constraint.type.match(it) }
            .any { it.paramsMatched(constraint) }

    private fun JIRAnnotation.paramsMatched(constraint: AnnotationConstraint): Boolean {
        val paramMatchers = constraint.params ?: return true
        return paramMatchers.all { matched(it) }
    }

    private fun JIRAnnotation.matched(param: AnnotationParamMatcher): Boolean {
        val matchedParams = values.filter { param.name.match(it.key) }
        val rawParamValues = matchedParams.mapNotNull { it.value }
        val flatParamValues = rawParamValues.flatMap { it.flatAnnotationValues() }
        return flatParamValues.any { paramValue ->
            val paramValueStr = paramValue.toString()

            when (param) {
                is SerializedCondition.AnnotationParamStringMatcher -> {
                    param.value.match(paramValueStr)
                }
            }
        }
    }

    private fun Any.flatAnnotationValues(): List<Any> =
        if (this !is List<*>) listOf(this) else flatMap { it?.flatAnnotationValues().orEmpty() }
}
