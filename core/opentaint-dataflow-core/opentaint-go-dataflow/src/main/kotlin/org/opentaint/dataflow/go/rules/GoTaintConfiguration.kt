package org.opentaint.dataflow.go.rules

import org.opentaint.dataflow.configuration.CommonCondition
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.go.serialized.GoNameMatcher
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedAssignAction
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedCleanAction
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedCondition
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedFieldSource
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedGlobalSource
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedPassAction
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedRule
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedTaintConfig
import org.opentaint.dataflow.configuration.isFalse
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.go.GoFieldSignature
import org.opentaint.dataflow.go.GoFunctionSignature
import org.opentaint.dataflow.go.GoGlobalFieldSignature
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.type.GoIRType
import java.util.concurrent.atomic.AtomicInteger

class GoTaintConfiguration : GoTaintRulesProvider {
    private val globalSourceSimple = hashMapOf<String, MutableList<GoSerializedGlobalSource>>()
    private val globalSourcePatterns = mutableListOf<GoSerializedGlobalSource>()
    private val globalSourceMemo = hashMapOf<GoGlobalFieldSignature, List<TaintRule.GlobalReadSource>>()

    private val fieldSourceSimple = hashMapOf<String, MutableList<GoSerializedFieldSource>>()
    private val fieldSourcePatterns = mutableListOf<GoSerializedFieldSource>()
    private val fieldSourceMemo = hashMapOf<GoFieldSignature, List<TaintRule.FieldReadSource>>()

    private val sourceSimple = hashMapOf<String, MutableList<GoSerializedRule.Source>>()
    private val sourcePatterns = mutableListOf<GoSerializedRule.Source>()
    private val sourceMemo = hashMapOf<GoFunctionSignature, List<TaintRule.Source>>()

    private val sinkSimple = hashMapOf<String, MutableList<GoSerializedRule.Sink>>()
    private val sinkPatterns = mutableListOf<GoSerializedRule.Sink>()
    private val sinkMemo = hashMapOf<GoFunctionSignature, List<TaintRule.Sink>>()

    private val passSimple = hashMapOf<String, MutableList<GoSerializedRule.PassThrough>>()
    private val passPatterns = mutableListOf<GoSerializedRule.PassThrough>()
    private val passMemo = hashMapOf<GoFunctionSignature, List<TaintRule.PassThrough>>()

    private val cleanerSimple = hashMapOf<String, MutableList<GoSerializedRule.Cleaner>>()
    private val cleanerPatterns = mutableListOf<GoSerializedRule.Cleaner>()
    private val cleanerMemo = hashMapOf<GoFunctionSignature, List<TaintRule.Cleaner>>()

    private val ruleIdGen = AtomicInteger()

    @Synchronized
    fun loadConfig(config: GoSerializedTaintConfig) {
        config.globalSource.forEach { addRule(it) }
        config.fieldSource.forEach { addRule(it) }
        config.source.forEach { addRule(it) }
        config.sink.forEach { addRule(it) }
        config.passThrough.forEach { addRule(it) }
        config.cleaner.forEach { addRule(it) }

        // Invalidate memo: any subsequent lookup must re-resolve.
        globalSourceMemo.clear()
        fieldSourceMemo.clear()
        sourceMemo.clear()
        sinkMemo.clear()
        passMemo.clear()
        cleanerMemo.clear()
    }

    override fun sourceRulesForGlobal(signature: GoGlobalFieldSignature, statement: GoIRInst): List<TaintRule.GlobalReadSource> =
        sourceForGlobal(signature)

    override fun sourceRulesForFieldRead(signature: GoFieldSignature, statement: GoIRInst): List<TaintRule.FieldReadSource> =
        sourceForFieldRead(signature)

    override fun sourceRulesForCall(
        signature: GoFunctionSignature, statement: GoIRInst, allRelevant: Boolean,
    ): List<TaintRule.Source> = sourceForFunction(signature, allRelevant)

    override fun sinkRulesForCall(signature: GoFunctionSignature, statement: GoIRInst): List<TaintRule.Sink> =
        sinkForFunction(signature)

    override fun passThroughRulesForCall(signature: GoFunctionSignature, statement: GoIRInst): List<TaintRule.PassThrough> =
        passThroughForFunction(signature)

    override fun cleanerRulesForCall(
        signature: GoFunctionSignature, statement: GoIRInst, allRelevant: Boolean,
    ): List<TaintRule.Cleaner> = cleanerForFunction(signature, allRelevant)

    private fun addRule(rule: GoSerializedGlobalSource) {
        when (val gv = rule.global) {
            is GoNameMatcher.Simple -> globalSourceSimple.getOrPut(gv.name, ::mutableListOf).add(rule)
            is GoNameMatcher.Pattern -> globalSourcePatterns.add(rule)
        }
    }

    private fun addRule(rule: GoSerializedFieldSource) {
        when (val fv = rule.field) {
            is GoNameMatcher.Simple -> fieldSourceSimple.getOrPut(fv.name, ::mutableListOf).add(rule)
            is GoNameMatcher.Pattern -> fieldSourcePatterns.add(rule)
        }
    }

    private fun addRule(rule: GoSerializedRule.Source) {
        when (val fn = rule.function) {
            is GoNameMatcher.Simple -> sourceSimple.getOrPut(fn.name, ::mutableListOf).add(rule)
            is GoNameMatcher.Pattern -> sourcePatterns.add(rule)
        }
    }

    private fun addRule(rule: GoSerializedRule.Sink) {
        when (val fn = rule.function) {
            is GoNameMatcher.Simple -> sinkSimple.getOrPut(fn.name, ::mutableListOf).add(rule)
            is GoNameMatcher.Pattern -> sinkPatterns.add(rule)
        }
    }

    private fun addRule(rule: GoSerializedRule.PassThrough) {
        when (val fn = rule.function) {
            is GoNameMatcher.Simple -> passSimple.getOrPut(fn.name, ::mutableListOf).add(rule)
            is GoNameMatcher.Pattern -> passPatterns.add(rule)
        }
    }

    private fun addRule(rule: GoSerializedRule.Cleaner) {
        when (val fn = rule.function) {
            is GoNameMatcher.Simple -> cleanerSimple.getOrPut(fn.name, ::mutableListOf).add(rule)
            is GoNameMatcher.Pattern -> cleanerPatterns.add(rule)
        }
    }

    @Synchronized
    fun sourceForGlobal(signature: GoGlobalFieldSignature): List<TaintRule.GlobalReadSource> = globalSourceMemo.getOrPut(signature) {
        val (pkgName, varName) = signature.name.splitFullName()
        candidates(varName, globalSourceSimple, globalSourcePatterns, { global })
            .filter { it.pkg.matchPackage(pkgName) }
            .mapNotNull { specialize(it, signature.name, signature.type) }
    }

    @Synchronized
    fun sourceForFieldRead(signature: GoFieldSignature): List<TaintRule.FieldReadSource> = fieldSourceMemo.getOrPut(signature) {
        candidates(signature.name, fieldSourceSimple, fieldSourcePatterns, { field })
            .mapNotNull { specialize(it, signature.name, signature.type, signature.receiverType) }
    }

    @Synchronized
    fun sourceForFunction(signature: GoFunctionSignature, allRelevant: Boolean): List<TaintRule.Source> = sourceMemo.getOrPut(signature) {
        candidates(signature, sourceSimple, sourcePatterns).mapNotNull { specialize(it, signature) }
    }

    @Synchronized
    fun sinkForFunction(signature: GoFunctionSignature): List<TaintRule.Sink> = sinkMemo.getOrPut(signature) {
        candidates(signature, sinkSimple, sinkPatterns).mapNotNull { specialize(it, signature) }
    }

    @Synchronized
    fun passThroughForFunction(signature: GoFunctionSignature): List<TaintRule.PassThrough> = passMemo.getOrPut(signature) {
        candidates(signature, passSimple, passPatterns).mapNotNull { specialize(it, signature) }
    }

    @Synchronized
    fun cleanerForFunction(signature: GoFunctionSignature, allRelevant: Boolean): List<TaintRule.Cleaner> = cleanerMemo.getOrPut(signature) {
        candidates(signature, cleanerSimple, cleanerPatterns).mapNotNull { specialize(it, signature) }
    }

    private fun <R : GoSerializedRule> candidates(
        sig: GoFunctionSignature,
        simpleByName: Map<String, List<R>>,
        patternRules: List<R>,
    ): List<R> {
        val (pkgName, functionName) = sig.name.splitFullName()
        return candidates(functionName, simpleByName, patternRules) { function }
            .filter { it.pkg.matchPackage(pkgName) || (sig.pkgName != null && it.pkg.matches(sig.pkgName)) }
    }

    private fun <R> candidates(
        name: String,
        simpleByName: Map<String, List<R>>,
        patternRules: List<R>,
        ruleNameMatcher: R.() -> GoNameMatcher,
    ): List<R> {
        val genericOriginName = name.substringBefore('[', name)
        val direct = if (genericOriginName == name) {
            simpleByName[name].orEmpty()
        } else {
            simpleByName[name].orEmpty() + simpleByName[genericOriginName].orEmpty()
        }
        val patternMatches = patternRules.filter { it.ruleNameMatcher().matches(name) }
        if (patternMatches.isEmpty()) return direct
        if (direct.isEmpty()) return patternMatches
        return direct + patternMatches
    }

    private fun specialize(rule: GoSerializedGlobalSource, name: String, fieldType: GoIRType) =
        specializeFieldSourceRule(name, fieldType, null, rule.condition, rule.taint) { name, condition, actions ->
            TaintRule.GlobalReadSource(name, condition, actions, rule.info, rule.serializedId)
        }

    private fun specialize(rule: GoSerializedFieldSource, name: String, fieldType: GoIRType, receiverType: GoIRType?)  =
        specializeFieldSourceRule(name, fieldType, receiverType, rule.condition, rule.taint) { name, condition, actions ->
            TaintRule.FieldReadSource(name, condition, actions, rule.info, rule.serializedId)
        }

    private inline fun <T> specializeFieldSourceRule(
        name: String,
        type: GoIRType,
        receiverType: GoIRType?,
        condition: GoSerializedCondition?,
        taint: List<GoSerializedAssignAction>,
        buildRule: (String, CommonCondition<GoRuleCondition>, List<GoAssignAction>) -> T
    ): T? {
        condition?.let { validateConditionForFieldSource(it) }
        taint.forEach { validateAssignActionForFieldSource(it) }

        val fakeSig = GoFunctionSignature(
            name = name, receiverType = receiverType, paramTypes = emptyList(), resultType = type
        )
        val condition = condition.resolveToRuleCondition(fakeSig)
        if (condition.isFalse()) return null

        val actions = taint.specialize(fakeSig)
        return buildRule(name, condition, actions)
    }

    private fun specialize(rule: GoSerializedRule.Source, signature: GoFunctionSignature): TaintRule.Source? {
        val condition = rule.condition.resolveToRuleCondition(signature)
        if (condition.isFalse()) return null

        val actions = rule.taint.specialize(signature)
        return TaintRule.Source(signature.name, condition, actions, rule.info, rule.serializedId)
    }

    private fun specialize(rule: GoSerializedRule.Sink, signature: GoFunctionSignature): TaintRule.Sink? {
        val condition = rule.condition.resolveToRuleCondition(signature)
        if (condition.isFalse()) return null

        val trackFacts = rule.trackFactsReachAnalysisEnd
            .orEmpty()
            .specialize(signature)

        val id = rule.id ?: generateRuleId(rule)
        val meta = rule.meta ?: defaultMeta(signature.name)

        return TaintRule.Sink(signature.name, condition, trackFacts, id, meta, rule.info, rule.serializedId)
    }

    private fun List<GoSerializedAssignAction>.specialize(signature: GoFunctionSignature) = flatMap { t ->
        when (t) {
            is GoSerializedAssignAction.Direct -> t.pos.resolve(signature)
                .map { GoAssignAction.Direct(t.kind, it) }

            is GoSerializedAssignAction.AnyAccessor -> t.pos.resolve(signature)
                .flatMap {
                    listOf(
                        GoAssignAction.AnyAccessor(t.kind, it),
                        GoAssignAction.Direct(t.kind, it), // todo: remove this hack after fact fix
                    )
                }
        }
    }

    private fun specialize(rule: GoSerializedRule.PassThrough, signature: GoFunctionSignature): TaintRule.PassThrough? {
        val actions = rule.copy.flatMap { it.toTaintAction(signature) }
        return TaintRule.PassThrough(signature.name, actions, rule.info, rule.serializedId)
    }

    private fun specialize(rule: GoSerializedRule.Cleaner, signature: GoFunctionSignature): TaintRule.Cleaner? {
        val condition = rule.condition.resolveToRuleCondition(signature)
        if (condition.isFalse()) return null

        val actions = rule.cleans.flatMap { it.toTaintAction(signature) }
        return TaintRule.Cleaner(signature.name, condition, actions, rule.info, rule.serializedId)
    }

    private fun GoSerializedPassAction.toTaintAction(signature: GoFunctionSignature): List<GoTaintAction> =
        from.resolve(signature).flatMap { f ->
            to.resolve(signature).map { t ->
                val kind = taintKind
                if (kind == null) CopyData(f, t) else CopyTaintMark(kind, f, t)
            }
        }

    private fun GoSerializedCleanAction.toTaintAction(signature: GoFunctionSignature): List<GoTaintAction> =
        pos.resolve(signature).map {
            val kind = taintKind
            if (kind == null) RemoveAllMarks(it) else RemoveMark(kind, it)
        }

    private fun generateRuleId(rule: GoSerializedRule.Sink): String {
        rule.meta?.cwe?.firstOrNull()?.let { return "CWE-$it" }
        return "go-generated-id-${ruleIdGen.incrementAndGet()}"
    }

    private fun defaultMeta(function: String): CommonTaintConfigurationSinkMeta =
        TaintRule.Sink.DefaultMeta("Taint sink: $function")

    private fun validateConditionForFieldSource(condition: GoSerializedCondition) {
        when (condition) {
            is GoSerializedCondition.True -> Unit
            is GoSerializedCondition.NumberOfArgs -> Unit
            is GoSerializedCondition.And -> condition.allOf.forEach { validateConditionForFieldSource(it) }
            is GoSerializedCondition.Or -> condition.anyOf.forEach { validateConditionForFieldSource(it) }
            is GoSerializedCondition.Not -> validateConditionForFieldSource(condition.not)

            is GoSerializedCondition.ContainsMark -> validatePositionWithModifiersForFieldSource(condition.pos)
            is GoSerializedCondition.ContainsMarkOnAnyAccessor -> validatePositionWithModifiersForFieldSource(condition.pos)

            is GoSerializedCondition.ConstantCmp -> validatePositionBaseForFieldSource(condition.pos)
            is GoSerializedCondition.ConstantMatches -> validatePositionBaseForFieldSource(condition.pos)
            is GoSerializedCondition.IsNull -> validatePositionBaseForFieldSource(condition.pos)
            is GoSerializedCondition.IsConstant -> validatePositionBaseForFieldSource(condition.pos)
            is GoSerializedCondition.IsType -> validatePositionBaseForFieldSource(condition.pos)
        }
    }

    private fun validateAssignActionForFieldSource(action: GoSerializedAssignAction) {
        val pos = when (action) {
            is GoSerializedAssignAction.AnyAccessor -> action.pos
            is GoSerializedAssignAction.Direct -> action.pos
        }
        check(pos.base is PositionBase.Result) { "Unsupported field-source taint target: ${pos.base}" }
    }

    private fun validatePositionWithModifiersForFieldSource(pos: PositionBaseWithModifiers) {
        validatePositionBaseForFieldSource(pos.base)
    }

    private fun validatePositionBaseForFieldSource(pos: PositionBase) {
        check(pos is PositionBase.Result || pos is PositionBase.This) { "Unsupported field-source position: $pos" }
    }

    private fun GoNameMatcher.matchPackage(pkgName: String): Boolean {
        if (matches(pkgName)) return true
        if (this is GoNameMatcher.Simple && name.contains('/')) return false

        val lastPkgPart = pkgName.substringAfterLast('/')
        return matches(lastPkgPart)
    }
}
