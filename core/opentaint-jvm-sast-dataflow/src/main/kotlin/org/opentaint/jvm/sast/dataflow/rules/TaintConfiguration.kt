package org.opentaint.jvm.sast.dataflow.rules

import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.configuration.jvm.ConstantTrue
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
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedCondition
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedFieldRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedFunctionNameMatcher
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedRule
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSimpleNameMatcher.Pattern
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedSimpleNameMatcher.Simple
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTypeNameMatcher.ClassPattern
import org.opentaint.dataflow.jvm.util.JIRHierarchyInfo
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.ext.allSuperHierarchySequence
import org.opentaint.ir.api.jvm.ext.objectClass
import org.opentaint.ir.impl.util.adjustEmptyList
import org.opentaint.jvm.util.typename

class TaintConfiguration(private val cp: JIRClasspath) {
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

            rules.removeAll { !it.className.match(patternManager, field.enclosingClass.name) }
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

        private fun resolveMethodItems(method: JIRMethod): List<T> = resolveItems(method) {
            @Suppress("UNCHECKED_CAST")
            it.resolveRule() as List<T>
        }

        private fun resolveMethodRelevantItems(method: JIRMethod): List<T> = resolveItems(method) {
            @Suppress("UNCHECKED_CAST")
            it.resolveRelevantRule() as List<T>
        }

        private inline fun resolveItems(method: JIRMethod, resolveItem: MethodTaintConfigurationResolver.(S) -> List<T>): List<T> {
            val rules = mutableListOf<S>()
            storage().findRules(rules, method)

            rules.removeAll { !it.function.matchFunctionName(method) }
            if (rules.isEmpty()) return emptyList()

            val resolver = MethodTaintConfigurationResolver(patternManager, taintMarkManager, cp, objectTypeName, method)
            rules.removeAll {
                with(resolver) {
                    it.signature?.matchFunctionSignature() == false
                }
            }

            return rules.flatMap { resolver.resolveItem(it) }
        }
    }

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
                return listOf(TaintStaticFieldSource(field, ConstantTrue, actions, info))
            }
        }
    }
}
