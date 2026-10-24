package org.opentaint.jvm.sast.dataflow.rules

import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.bidi.ap.RawMarkDeclarations
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
import org.opentaint.dataflow.configuration.jvm.TaintStaticFieldSource
import org.opentaint.dataflow.configuration.jvm.SpecializedRules
import org.opentaint.dataflow.configuration.jvm.originalCondition
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedFieldRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedFunctionNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedItem
import org.opentaint.dataflow.configuration.jvm.serialized.SourceRule
import org.opentaint.dataflow.configuration.jvm.serialized.SinkRule
import org.opentaint.dataflow.configuration.jvm.serialized.UserDefinedRuleInfo
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSimpleNameMatcher.Pattern
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSimpleNameMatcher.Simple
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTypeNameMatcher.ClassPattern
import org.opentaint.dataflow.configuration.mkTrue
import org.opentaint.dataflow.jvm.util.JIRHierarchyInfo
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.ext.allSuperHierarchySequence
import org.opentaint.ir.api.jvm.ext.objectClass
import org.opentaint.ir.impl.util.adjustEmptyList
import org.opentaint.jvm.util.typename
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class TaintConfiguration(private val cp: JIRClasspath,
                         val resolutionStatistics: RuleResolutionStatistics? = null) {
    /** Selection belongs to a provider view, not to this shared raw owner. */
    class ResolverSelection(
        val identity: Any,
        val keepRaw: (SerializedItem, Boolean) -> Boolean,
        val normalize: (TaintConfigurationItem, Boolean, Boolean) -> TaintConfigurationItem?,
        val validate: () -> Unit = {},
    )

    private class ResolverEpoch(val selection: ResolverSelection?, val catalogGeneration: Long) {
        val caches = ConcurrentHashMap<Any, ConcurrentHashMap<Any, List<*>>>()
    }

    inner class ResolverView internal constructor(internal val statistics: RuleResolutionStatistics? = null) {
        @Volatile private var epoch = ResolverEpoch(null, catalogGeneration)

        fun install(selection: ResolverSelection) {
            // The identity is the complete immutable policy, not an integer/hash.
            if (epoch.selection?.identity === selection.identity && epoch.catalogGeneration == catalogGeneration) return
            epoch = ResolverEpoch(selection, catalogGeneration)
        }

        internal fun requireOwner(owner: TaintConfiguration) {
            check(owner === this@TaintConfiguration) { "Rule resolver view belongs to another configuration owner" }
        }

        @Suppress("UNCHECKED_CAST")
        internal fun <T> cached(storage: Any, key: Any, resolve: (ResolverSelection?) -> List<T>): List<T> {
            statistics?.queried()
            // A query publishes only into the epoch it captured. Handoff drops
            // that complete cache root after the caller has joined its readers.
            var captured = epoch
            if (captured.catalogGeneration != catalogGeneration) {
                check(captured.selection == null) { "Selected rules require a new catalog generation" }
                captured = ResolverEpoch(null, catalogGeneration).also { epoch = it }
            }
            captured.selection?.validate?.invoke()
            val cache = captured.caches.computeIfAbsent(storage) { ConcurrentHashMap() }
            cache[key]?.let { return it as List<T> }
            return synchronized(cache) {
                cache.getOrPut(key) { resolve(captured.selection) } as List<T>
            }
        }
    }

    private data class MethodQuery(val method: JIRMethod, val allRelevant: Boolean, val ignoreEndFacts: Boolean)
    private data class FieldQuery(val field: JIRField, val allRelevant: Boolean)
    var catalogGeneration: Long = 0
        private set
    private val originalView = ResolverView(resolutionStatistics)
    fun resolverView(statistics: RuleResolutionStatistics? = resolutionStatistics): ResolverView = ResolverView(statistics)

    private val loadedItems = mutableListOf<SerializedItem>()
    fun rawItems(): List<SerializedItem> = loadedItems.toList()
    private val declaredNames = linkedSetOf<String>()
    private val patternManager = PatternManager()
    private val taintMarkManager = TaintMarkManager()
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

    fun loadConfig(config: SerializedTaintConfig) {
        catalogGeneration++
        loadedItems += config.entryPoint.orEmpty()
        loadedItems += config.source.orEmpty()
        loadedItems += config.methodExitSource.orEmpty()
        loadedItems += config.sink.orEmpty()
        loadedItems += config.passThrough.orEmpty()
        loadedItems += config.cleaner.orEmpty()
        loadedItems += config.methodExitSink.orEmpty()
        loadedItems += config.methodEntrySink.orEmpty()
        loadedItems += config.staticFieldSource.orEmpty()
        collectDeclarations(config)
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

    /** Detached names only. Ownership is completed once after all provider branches are merged. */
    fun rawMarkDeclarations(): RawMarkDeclarations = RawMarkDeclarations(
        names = Collections.unmodifiableSet(LinkedHashSet(declaredNames)),
        explicitOwners = emptyMap(),
        primitiveByOwner = emptyMap(),
    )

    private fun collectDeclarations(config: SerializedTaintConfig) {
        config.entryPoint.orEmpty().forEach(::collectDeclarations)
        config.source.orEmpty().forEach(::collectDeclarations)
        config.methodExitSource.orEmpty().forEach(::collectDeclarations)
        config.sink.orEmpty().forEach(::collectDeclarations)
        config.passThrough.orEmpty().forEach(::collectDeclarations)
        config.cleaner.orEmpty().forEach(::collectDeclarations)
        config.methodExitSink.orEmpty().forEach(::collectDeclarations)
        config.methodEntrySink.orEmpty().forEach(::collectDeclarations)
        config.staticFieldSource.orEmpty().forEach(::collectDeclarations)
    }

    private fun collectDeclarations(item: SerializedItem) {
        when (item) {
            is SourceRule -> {
                collectDeclarations(item.condition)
                item.taint.forEach { declaredNames += it.kind }
            }
            is SinkRule -> {
                collectDeclarations(item.condition)
                // Names remain declarations; no end-action position is resolved or executed.
                item.trackFactsReachAnalysisEnd.orEmpty().forEach { declaredNames += it.kind }
            }
            is SerializedRule.PassThrough -> {
                collectDeclarations(item.condition)
                item.copy.forEach { it.taintKind?.let(declaredNames::add) }
            }
            is SerializedRule.Cleaner -> {
                collectDeclarations(item.condition)
                item.cleans.forEach { it.taintKind?.let(declaredNames::add) }
            }
        }
        (item.info as? UserDefinedRuleInfo)?.relevantTaintMarks?.forEach(declaredNames::add)
    }

    private fun collectDeclarations(condition: SerializedCondition?) {
        when (condition) {
            is SerializedCondition.Not -> collectDeclarations(condition.not)
            is SerializedCondition.And -> condition.allOf.forEach(::collectDeclarations)
            is SerializedCondition.Or -> condition.anyOf.forEach(::collectDeclarations)
            is SerializedCondition.ContainsMark -> declaredNames += condition.tainted
            else -> Unit
        }
    }

    fun entryPointForMethod(method: JIRMethod, allRelevant: Boolean, view: ResolverView = originalView): List<TaintEntryPointSource> = entryPointConfig.configForMethod(method, allRelevant, view = view)
    fun sourceForMethod(method: JIRMethod, allRelevant: Boolean, view: ResolverView = originalView): List<TaintMethodSource> = sourceConfig.configForMethod(method, allRelevant, view = view)
    fun exitSourceForMethod(method: JIRMethod, allRelevant: Boolean, view: ResolverView = originalView): List<TaintMethodExitSource> = exitSourceConfig.configForMethod(method, allRelevant, view = view)
    fun sinkForMethod(method: JIRMethod, allRelevant: Boolean, ignoreEndFacts: Boolean = false, view: ResolverView = originalView): List<TaintMethodSink> = sinkConfig.configForMethod(method, allRelevant, ignoreEndFacts, view)
    fun passThroughForMethod(method: JIRMethod, allRelevant: Boolean, view: ResolverView = originalView): List<TaintPassThrough> = passThroughConfig.configForMethod(method, allRelevant, view = view)
    fun cleanerForMethod(method: JIRMethod, allRelevant: Boolean, view: ResolverView = originalView): List<TaintCleaner> = cleanerConfig.configForMethod(method, allRelevant, view = view)
    fun methodExitSinkForMethod(method: JIRMethod, allRelevant: Boolean, ignoreEndFacts: Boolean = false, view: ResolverView = originalView): List<TaintMethodExitSink> = methodExitSinkConfig.configForMethod(method, allRelevant, ignoreEndFacts, view)
    fun methodEntrySinkForMethod(method: JIRMethod, allRelevant: Boolean, ignoreEndFacts: Boolean = false, view: ResolverView = originalView): List<TaintMethodEntrySink> = methodEntrySinkConfig.configForMethod(method, allRelevant, ignoreEndFacts, view)

    fun sourceForStaticField(field: JIRField, allRelevant: Boolean = false, view: ResolverView = originalView): List<TaintStaticFieldSource> {
        check(field.isStatic)
        return staticFieldSourceConfig.getConfigForField(field, allRelevant, view)
    }

    private inner class TaintFieldRulesStorage<S : SerializedFieldRule, T : TaintConfigurationItem> {
        private val fieldRules = hashMapOf<String, MutableList<S>>()
        private val fieldPatterns = hashMapOf<String, MutableList<S>>()


        fun addRules(rules: List<S>) {
            for (rule in rules) {
                when (val fn = rule.fieldName) {
                    is Simple -> fieldRules.getOrPut(fn.value, ::mutableListOf).add(rule)
                    is Pattern -> fieldPatterns.getOrPut(fn.pattern, ::mutableListOf).add(rule)
                }
            }

        }

        fun getConfigForField(field: JIRField, allRelevant: Boolean, view: ResolverView): List<T> {
            view.requireOwner(this@TaintConfiguration)
            return view.cached(this, FieldQuery(field, allRelevant)) { selection ->
                resolveFieldItems(field, allRelevant, selection).retainRuleLabels()
            }
        }

        @Synchronized
        private fun resolveFieldItems(field: JIRField, allRelevant: Boolean, selection: ResolverSelection?): List<T> {
            val rules = mutableListOf<S>()
            rules += fieldRules[field.name].orEmpty()

            fieldPatterns
                .filter { patternManager.matchPattern(it.key, field.name) }
                .flatMapTo(rules) { it.value }

            rules.removeAll { !it.className.match(patternManager, field.enclosingClass.name) }
            val stable = rules.all { rule ->
                rule is SerializedFieldRule.SerializedStaticFieldSource &&
                    (rule.condition == null || rule.condition is SerializedCondition.True) &&
                    rule.taint.all { action -> val position = action.pos
                        position is PositionBaseWithModifiers.BaseOnly && position.base is PositionBase.Result }
            }
            val candidates = if (selection == null || !stable) rules else
                rules.filter { selection.keepRaw(it, allRelevant) }
            val bound = candidates.flatMap { rule ->
                val resolved = resolveFieldRule(rule, field)
                if (selection != null && !stable && !selection.keepRaw(rule, allRelevant)) emptyList() else resolved
            }
            return normalize(bound, allRelevant, false, selection)
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

        fun configForMethod(method: JIRMethod, allRelevant: Boolean, ignoreEndFacts: Boolean = false,
                            view: ResolverView): List<T> {
            view.requireOwner(this@TaintConfiguration)
            return view.cached(this, MethodQuery(method, allRelevant, ignoreEndFacts)) { selection ->
                resolveItems(method, allRelevant, ignoreEndFacts, selection, view.statistics).retainRuleLabels()
            }
        }

        @Suppress("UNCHECKED_CAST")
        @Synchronized
        private fun resolveItems(method: JIRMethod, allRelevant: Boolean, ignoreEndFacts: Boolean,
                                 selection: ResolverSelection?, statistics: RuleResolutionStatistics?): List<T> {
            val rules = mutableListOf<S>()
            storage().findRules(rules, method)

            rules.removeAll { !it.function.matchFunctionName(method) }
            if (rules.isEmpty()) return emptyList()

            val resolver = MethodTaintConfigurationResolver(patternManager, taintMarkManager, cp, objectTypeName, method, ignoreEndFacts, statistics)
            rules.removeAll {
                with(resolver) {
                    it.signature?.matchFunctionSignature() == false
                }
            }

            // A rejected fallback sink still consumes this resolver's report-ID
            // counter. Keep the complete original query when any candidate is
            // not stable, then select its complete bound rows.
            val stable = rules.all { rule ->
                rule !is SinkRule || rule.id != null || !rule.meta?.cwe.isNullOrEmpty()
            }
            val candidates = if (selection == null || !stable) rules else
                rules.filter { selection.keepRaw(it, allRelevant) }
            val bound = candidates.flatMap { rule ->
                val resolved = with(resolver) {
                    (if (allRelevant) rule.resolveRelevantRule() else rule.resolveRule()) as List<T>
                }
                if (selection != null && !stable && !selection.keepRaw(rule, allRelevant)) emptyList() else resolved
            }
            return normalize(bound, allRelevant, ignoreEndFacts, selection)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : TaintConfigurationItem> normalize(items: List<T>, allRelevant: Boolean,
                                                     ignoreEndFacts: Boolean,
                                                     selection: ResolverSelection?): List<T> {
        if (selection == null) return items
        return SpecializedRules(items.mapIndexedNotNull { ordinal, item ->
            selection.normalize(JVMRuleSpecialization.indexed(item, ordinal, items.size), allRelevant, ignoreEndFacts) as T?
        }, items.size, items.map { it.originalCondition() })
    }

    private fun <T> List<T>.retainRuleLabels(): List<T> =
        if (this is SpecializedRules<*>) this else adjustEmptyList()

    private fun SerializedFunctionNameMatcher.matchFunctionName(method: JIRMethod): Boolean {
        if (!method.isConstructor && !method.isFinal && !method.isStatic) {
            // storage().findRules is ok
            return true
        }

        // method name matches, but class name may be not
        val classMatcher = ClassPattern(`package`, `class`)
        if (classMatcher.match(patternManager, method.enclosingClass.name)) return true

        if (method.isStatic) return false

        return method.enclosingClass.allSuperHierarchySequence.any {
            classMatcher.match(patternManager, it.name)
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
                    actions += AssignMark(taintMarkManager.taintMark(action.kind), Result)
                }
                return listOf(TaintStaticFieldSource(field, mkTrue(), actions, info, serializedId))
            }
        }
    }
}
