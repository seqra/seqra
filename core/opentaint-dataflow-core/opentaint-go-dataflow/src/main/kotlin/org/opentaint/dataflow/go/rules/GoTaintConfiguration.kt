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
import org.opentaint.dataflow.configuration.selection.RuleRole
import org.opentaint.dataflow.configuration.selection.RuleSelectionSnapshot
import org.opentaint.dataflow.configuration.selection.ReducedMarkPlan
import org.opentaint.dataflow.go.GoFieldSignature
import org.opentaint.dataflow.go.GoFunctionSignature
import org.opentaint.dataflow.go.GoGlobalFieldSignature
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.type.GoIRType
import java.util.concurrent.atomic.AtomicInteger

class GoTaintConfiguration : GoRuleSelectionCapability {
    private var catalogGeneration = 0L
    private val rawGlobalSources = mutableListOf<GoSerializedGlobalSource>()
    private val rawFieldSources = mutableListOf<GoSerializedFieldSource>()
    private val rawSources = mutableListOf<GoSerializedRule.Source>()
    private val rawSinks = mutableListOf<GoSerializedRule.Sink>()
    private val rawPasses = mutableListOf<GoSerializedRule.PassThrough>()
    private val rawCleaners = mutableListOf<GoSerializedRule.Cleaner>()
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
        rawGlobalSources += config.globalSource
        rawFieldSources += config.fieldSource
        rawSources += config.source
        rawSinks += config.sink
        rawPasses += config.passThrough
        rawCleaners += config.cleaner
        catalogGeneration++
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

    /** Compiler inventory only. Binding always uses the original raw rows. */
    @Synchronized
    fun rawCatalog(): GoSerializedTaintConfig = GoSerializedTaintConfig(
        rawGlobalSources.toList(), rawFieldSources.toList(), rawSources.toList(),
        rawSinks.toList(), rawPasses.toList(), rawCleaners.toList())

    @Synchronized
    override fun executableRuleCatalog(): GoExecutableRuleCatalog = GoExecutableRuleCatalog(rawCatalog(), catalogGeneration)

    @Synchronized
    override fun markReductionView(plan: ReducedMarkPlan): GoTaintRulesProvider {
        val raw = rawCatalog()
        val ids = (raw.globalSource + raw.fieldSource + raw.source + raw.sink + raw.passThrough + raw.cleaner)
            .mapNotNullTo(linkedSetOf()) { it.serializedId }
        return selectedView(RuleSelectionSnapshot(catalogGeneration, ids, ids, ids, plan))
    }

    /** The caller installs this view only after the previous phase's readers join.
     * The unfiltered configuration remains a separate compatibility consumer. */
    @Synchronized
    fun selectedView(snapshot: RuleSelectionSnapshot): SelectedView {
        globalSourceMemo.clear()
        fieldSourceMemo.clear()
        sourceMemo.clear()
        passMemo.clear()
        cleanerMemo.clear()
        // Unsafe broad sink values and the owner-wide generator are one lane.
        // Removing these values would assign different public IDs on a rebuild.
        sinkMemo.keys.removeAll { signature -> sinkCandidates(signature).all(::stableSinkBinding) }
        return SelectedView(this, snapshot, catalogGeneration)
    }

    private fun sinkCandidates(signature: GoFunctionSignature) = candidates(signature, sinkSimple, sinkPatterns)
    private fun stableSinkIdentity(rule: GoSerializedRule.Sink): Boolean =
        rule.id != null || !rule.meta?.cwe.isNullOrEmpty()

    private fun stableSourceBinding(rule: GoSerializedRule.Source): Boolean =
        GoBindingSafety.condition(rule.condition) && rule.taint.all { GoBindingSafety.position(it.rawPosition()) }

    private fun stableSinkBinding(rule: GoSerializedRule.Sink): Boolean =
        stableSinkIdentity(rule) && GoBindingSafety.condition(rule.condition) &&
            rule.trackFactsReachAnalysisEnd.orEmpty().all { GoBindingSafety.position(it.rawPosition()) }

    private fun stableCleanerBinding(rule: GoSerializedRule.Cleaner): Boolean =
        GoBindingSafety.condition(rule.condition) && rule.cleans.all { GoBindingSafety.position(it.pos) }

    class SelectedView internal constructor(
        private val owner: GoTaintConfiguration,
        private val snapshot: RuleSelectionSnapshot,
        private val generation: Long,
    ) : GoRuleSelectionCapability {
        private val normalizer = GoSelectedRuleNormalizer(snapshot.markPlan)
        private val globals = hashMapOf<GoGlobalFieldSignature, List<TaintRule.GlobalReadSource>>()
        private val fields = hashMapOf<GoFieldSignature, List<TaintRule.FieldReadSource>>()
        private val sources = hashMapOf<Pair<GoFunctionSignature, Boolean>, List<TaintRule.Source>>()
        private val sinks = hashMapOf<GoFunctionSignature, List<TaintRule.Sink>>()
        private val passes = hashMapOf<GoFunctionSignature, List<TaintRule.PassThrough>>()
        private val cleaners = hashMapOf<Pair<GoFunctionSignature, Boolean>, List<TaintRule.Cleaner>>()
        private val functionPresence = hashMapOf<Triple<GoFunctionSignature, RuleRole, Boolean>, Boolean>()
        private val globalPresence = hashMapOf<GoGlobalFieldSignature, Boolean>()
        private val fieldPresence = hashMapOf<GoFieldSignature, Boolean>()

        private inline fun <T> query(block: () -> T): T = synchronized(owner) {
            check(generation == owner.catalogGeneration) { "Obsolete Go rule-selection catalog generation" }
            block()
        }

        private fun <Raw, Bound : Any> bindSelected(
            raw: List<Raw>, role: RuleRole, metadata: Boolean,
            id: (Raw) -> String?, stable: (Raw) -> Boolean, bind: (Raw) -> Bound?,
        ): List<Bound> = if (raw.all(stable)) {
            raw.asSequence().filter { snapshot.keepsRaw(id(it), role, metadata) }.mapNotNull(bind).toList()
        } else {
            // Complete original order, even for rejected validation/IR-dependent rows.
            raw.mapNotNull { row -> bind(row)?.let { id(row) to it } }
                .filter { snapshot.keepsRaw(it.first, role, metadata) }.map { it.second }
        }

        fun sourceForGlobal(signature: GoGlobalFieldSignature): List<TaintRule.GlobalReadSource> = query {
            globals.getOrPut(signature) {
                val (pkg, name) = signature.name.splitFullName()
                val raw = owner.candidates(name, owner.globalSourceSimple, owner.globalSourcePatterns) { global }
                    .filter { owner.run { it.pkg.matchPackage(pkg) } }
                bindSelected(raw, RuleRole.SOURCE, false, { it.serializedId }, {
                    GoBindingSafety.condition(it.condition, field = true) && it.taint.all { action ->
                        action.rawPosition().base is PositionBase.Result && GoBindingSafety.position(action.rawPosition())
                    }
                }) { owner.specialize(it, signature.name, signature.type) }.also {
                    globalPresence[signature] = it.isNotEmpty()
                }.mapNotNull(normalizer::global)
            }
        }

        fun sourceForFieldRead(signature: GoFieldSignature): List<TaintRule.FieldReadSource> = query {
            fields.getOrPut(signature) {
                val raw = owner.candidates(signature.name, owner.fieldSourceSimple, owner.fieldSourcePatterns) { field }
                bindSelected(raw, RuleRole.SOURCE, false, { it.serializedId }, {
                    GoBindingSafety.condition(it.condition, field = true) && it.taint.all { action ->
                        action.rawPosition().base is PositionBase.Result && GoBindingSafety.position(action.rawPosition())
                    }
                }) { owner.specialize(it, signature.name, signature.type, signature.receiverType) }
                    .also { fieldPresence[signature] = it.isNotEmpty() }
                    .mapNotNull(normalizer::field)
            }
        }

        fun sourceForFunction(signature: GoFunctionSignature, allRelevant: Boolean): List<TaintRule.Source> = query {
            sources.getOrPut(signature to allRelevant) {
                bindSelected(owner.candidates(signature, owner.sourceSimple, owner.sourcePatterns),
                    RuleRole.SOURCE, allRelevant, { it.serializedId }, owner::stableSourceBinding) {
                    owner.specialize(it, signature)
                }.also { functionPresence[Triple(signature, RuleRole.SOURCE, allRelevant)] = it.isNotEmpty() }
                    .mapNotNull { normalizer.source(it, allRelevant) }
            }
        }

        fun sinkForFunction(signature: GoFunctionSignature): List<TaintRule.Sink> = query {
            sinks.getOrPut(signature) {
                val candidates = owner.sinkCandidates(signature)
                val bound = if (candidates.all(owner::stableSinkBinding)) {
                    candidates.asSequence().filter { snapshot.keepsRaw(it.serializedId, RuleRole.SINK, false) }
                        .mapNotNull { owner.specialize(it, signature) }.toList()
                } else {
                    owner.sinkForFunction(signature).filter {
                        snapshot.keepsRaw(it.serializedId, RuleRole.SINK, false)
                    }
                }
                functionPresence[Triple(signature, RuleRole.SINK, false)] = bound.isNotEmpty()
                bound.mapNotNull(normalizer::sink)
            }
        }

        fun passThroughForFunction(signature: GoFunctionSignature): List<TaintRule.PassThrough> = query {
            passes.getOrPut(signature) {
                owner.candidates(signature, owner.passSimple, owner.passPatterns).mapNotNull { owner.specialize(it, signature) }
            }
        }

        fun cleanerForFunction(signature: GoFunctionSignature, allRelevant: Boolean): List<TaintRule.Cleaner> = query {
            cleaners.getOrPut(signature to allRelevant) {
                bindSelected(owner.candidates(signature, owner.cleanerSimple, owner.cleanerPatterns),
                    RuleRole.CLEANER, allRelevant, { it.serializedId }, owner::stableCleanerBinding) {
                    owner.specialize(it, signature)
                }.also { functionPresence[Triple(signature, RuleRole.CLEANER, allRelevant)] = it.isNotEmpty() }
                    .mapNotNull { normalizer.cleaner(it, allRelevant) }
            }
        }

        override fun sourceRulesForGlobal(signature: GoGlobalFieldSignature, statement: GoIRInst) = sourceForGlobal(signature)
        override fun sourceRulesForFieldRead(signature: GoFieldSignature, statement: GoIRInst) = sourceForFieldRead(signature)
        override fun sourceRulesForCall(signature: GoFunctionSignature, statement: GoIRInst, allRelevant: Boolean) =
            sourceForFunction(signature, allRelevant)
        override fun sinkRulesForCall(signature: GoFunctionSignature, statement: GoIRInst) = sinkForFunction(signature)
        override fun passThroughRulesForCall(signature: GoFunctionSignature, statement: GoIRInst) = passThroughForFunction(signature)
        override fun cleanerRulesForCall(signature: GoFunctionSignature, statement: GoIRInst, allRelevant: Boolean) =
            cleanerForFunction(signature, allRelevant)
        override fun sourceRulesForGlobalWithPresence(signature: GoGlobalFieldSignature, statement: GoIRInst) = query {
            val rows = sourceForGlobal(signature)
            GoRuleQueryResult(rows, globalPresence.getValue(signature))
        }
        override fun sourceRulesForFieldReadWithPresence(signature: GoFieldSignature, statement: GoIRInst) = query {
            val rows = sourceForFieldRead(signature)
            GoRuleQueryResult(rows, fieldPresence.getValue(signature))
        }
        override fun sourceRulesForCallWithPresence(signature: GoFunctionSignature, statement: GoIRInst, allRelevant: Boolean) = query {
            val rows = sourceForFunction(signature, allRelevant)
            GoRuleQueryResult(rows, functionPresence.getValue(Triple(signature, RuleRole.SOURCE, allRelevant)))
        }
        override fun sinkRulesForCallWithPresence(signature: GoFunctionSignature, statement: GoIRInst) = query {
            val rows = sinkForFunction(signature)
            GoRuleQueryResult(rows, functionPresence.getValue(Triple(signature, RuleRole.SINK, false)))
        }
        override fun cleanerRulesForCallWithPresence(signature: GoFunctionSignature, statement: GoIRInst, allRelevant: Boolean) = query {
            val rows = cleanerForFunction(signature, allRelevant)
            GoRuleQueryResult(rows, functionPresence.getValue(Triple(signature, RuleRole.CLEANER, allRelevant)))
        }
        override fun selectedMarkPlan(): ReducedMarkPlan = snapshot.markPlan
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
        val direct = simpleByName[name].orEmpty()
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
