package org.opentaint.semgrep.pattern

import com.charleskorn.kaml.YamlMap
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta.Severity
import org.opentaint.dataflow.configuration.jvm.serialized.SinkMetaData
import org.opentaint.semgrep.pattern.SemgrepErrorEntry.Reason
import org.opentaint.semgrep.pattern.SemgrepTraceEntry.Step
import org.opentaint.semgrep.pattern.conversion.ActionListBuilder
import org.opentaint.semgrep.pattern.conversion.MetavarAtom
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternParser
import org.opentaint.semgrep.pattern.conversion.SemgrepRuleAutomataBuilder
import org.opentaint.semgrep.pattern.conversion.taint.RuleConversionCtx
import org.opentaint.semgrep.pattern.conversion.taint.TaintAutomataJoinMetaVarRef
import org.opentaint.semgrep.pattern.conversion.taint.TaintAutomataJoinOperation
import org.opentaint.semgrep.pattern.conversion.taint.TaintAutomataJoinRule
import org.opentaint.semgrep.pattern.conversion.taint.TaintAutomataJoinRuleItem
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata
import org.opentaint.semgrep.pattern.conversion.taint.convertTaintAutomataJoinToTaintRules
import org.opentaint.semgrep.pattern.conversion.taint.convertTaintAutomataToTaintRules
import org.opentaint.semgrep.pattern.conversion.taint.createTaintAutomata
import java.nio.file.Path
import kotlin.io.path.Path

data class RuleMetadata(
    val ruleId: String,
    val shortRuleId: String,
    val message: String,
    val severity: Severity,
    val metadata: YamlMap?
)

private typealias BuiltRule = RuleWithMetaVars<TaintRegisterStateAutomata, ResolvedMetaVarInfo>

class SemgrepRuleLoader(
    private val parser: SemgrepPatternParser = SemgrepPatternParser.create().cached(),
    private val converter: ActionListBuilder = ActionListBuilder.create().cached()
) {
    private data class RegisteredRule(
        val ruleId: String,
        val rule: SemgrepYamlRule,
        val pathInfo: RuleSetPathInfo,
        val ruleTrace: SemgrepRuleLoadTrace
    )

    private data class RuleSetPathInfo(
        val rulesRoot: Path,
        val ruleRelativePath: Path,
    )

    private val registeredRules = hashMapOf<String, RegisteredRule>()

    fun registerRuleSet(
        ruleSetText: String,
        ruleRelativePath: Path,
        rulesRoot: Path,
        trace: SemgrepLoadTrace,
    ) {
        val pathInfo = RuleSetPathInfo(rulesRoot, ruleRelativePath)
        val ruleSetName = pathInfo.ruleSetName()
        registerRuleSet(ruleSetText, ruleSetName, pathInfo, trace.fileTrace(ruleSetName))
    }

    private fun registerRuleSet(
        ruleSetText: String,
        ruleSetName: String,
        pathInfo: RuleSetPathInfo,
        semgrepFileTrace: SemgrepFileLoadTrace
    ) {
        val ruleSet = parseSemgrepYaml(ruleSetText, semgrepFileTrace) ?: return

        val (supportedRules, otherRules) = ruleSet.rules.partition { it.isJavaRule() || it.isJoinRule() }
        semgrepFileTrace.info("Found ${supportedRules.size} supported rules")

        otherRules.forEach {
            val ruleId = SemgrepRuleUtils.getRuleId(ruleSetName, it.id)
            semgrepFileTrace
                .ruleTrace(ruleId, it.id)
                .error(Step.LOAD_RULESET, "Unsupported rule", Reason.ERROR)
        }

        supportedRules.forEach {
            val ruleId = SemgrepRuleUtils.getRuleId(ruleSetName, it.id)
            val trace = semgrepFileTrace.ruleTrace(ruleId, it.id)
            registerRule(RegisteredRule(ruleId, it, pathInfo, trace))
        }

        semgrepFileTrace.info("Register ${supportedRules.size} rules")
    }

    private fun registerRule(rule: RegisteredRule) {
        if (rule.ruleId in registeredRules) {
            rule.ruleTrace.stepTrace(Step.LOAD_RULESET)
                .error("Duplicate rule", Reason.ERROR)
            return
        }

        registeredRules[rule.ruleId] = rule
    }

    data class RuleLoadResult(
        val rulesWithMeta: List<Pair<TaintRuleFromSemgrep, RuleMetadata>>,
        val disabledRules: Set<String>,
    )

    fun loadRules(severity: List<Severity> = emptyList()): RuleLoadResult {
        fun Rule<*>.skip(): Boolean =
            info.isDisabled || info.isLibraryRule || !ruleSeverityAllow(this, severity)

        registeredRules.values.toList()
            .forEach { parseRule(it, forceLibraryMode = false) }

        resolveRuleOverrides()

        parsedRules.values
            .filterIsInstance<NormalRule<Formula>>()
            .forEach { buildNormalRule(it) }

        val loaded = mutableListOf<Pair<TaintRuleFromSemgrep, RuleMetadata>>()
        builtNormalRules.values
            .filterNot { it.skip() }
            .forEach {
                loaded += loadNormalRule(it) ?: return@forEach
            }

        parsedRules.values
            .filterIsInstance<JoinRule<*>>()
            .filterNot { it.skip() }
            .forEach {
                loaded += loadJoinRule(it) ?: return@forEach
            }

        return RuleLoadResult(loaded, disabledRules)
    }

    private data class RuleInfo(
        val ruleId: String,
        val shortRuleId: String,
        val overridesRuleId: String?,
        val isLibraryRule: Boolean,
        val isDisabled: Boolean,
        val metadata: RuleMetadata,
        val sinkMeta: SinkMetaData,
        val ruleTrace: SemgrepRuleLoadTrace,
        val pathInfo: RuleSetPathInfo,
    )

    private fun resolveRuleOverrides() {
        val overrideMapping = hashMapOf<String, String>()
        for ((ruleId, rule) in parsedRules) {
            val override = rule.info.overridesRuleId ?: continue
            val prev = overrideMapping.putIfAbsent(override, ruleId)
            if (prev != null) {
                registeredRules[ruleId]?.ruleTrace
                    ?.stepTrace(Step.LOAD_RULESET)
                    ?.error("Ambiguous override: $prev", Reason.ERROR)
            }
        }

        for ((ruleId, overrideId) in overrideMapping) {
            val info = parsedRules[ruleId]?.info
            if (info == null) {
                registeredRules[overrideId]?.ruleTrace
                    ?.stepTrace(Step.LOAD_RULESET)
                    ?.error("Rule overrides nothing", Reason.WARNING)
                continue
            }
            parsedRules[ruleId] = RuleOverride(overrideId, info)
        }
    }

    private sealed interface Rule<P> {
        val info: RuleInfo
    }

    private data class NormalRule<P>(
        val rule: SemgrepRule<P>,
        override val info: RuleInfo
    ) : Rule<P>

    private data class JoinRule<P>(
        val refs: List<SemgrepYamlJoinRuleRef>,
        val on: List<SemgrepJoinRuleOn>,
        override val info: RuleInfo,
    ) : Rule<P>

    private data class RuleOverride<P>(
        val refId: String,
        override val info: RuleInfo
    ) : Rule<P>

    private val parsedRules = hashMapOf<String, Rule<Formula>>()

    private val disabledRules = hashSetOf<String>()

    private fun parseRule(registeredRule: RegisteredRule, forceLibraryMode: Boolean) {
        val ruleInfo = parseRuleInfo(registeredRule, forceLibraryMode)
        val loadTrace = ruleInfo.ruleTrace.stepTrace(Step.LOAD_RULESET)

        if (ruleInfo.isDisabled) {
            disabledRules.add(ruleInfo.ruleId)
            loadTrace.info("Skip disabled rule")
            return
        }

        val rule = registeredRule.rule
        when (rule.mode) {
            null, "search" -> {
                val parsed = parseMatchingRule(rule, loadTrace) ?: return
                addParsedRule(ruleInfo, NormalRule(parsed, ruleInfo), loadTrace)
            }

            "taint" -> {
                val parsed = parseTaintRule(rule, loadTrace)
                addParsedRule(ruleInfo, NormalRule(parsed, ruleInfo), loadTrace)
            }
            "join" -> {
                val joinRule = rule.join ?: run {
                    loadTrace.error("Join rule without join section", Reason.ERROR)
                    return
                }

                val parsed = parseJoinRule(joinRule, loadTrace) ?: return

                val refs = parsed.refs.map { ref ->
                    val nestedRule = parsed.rules.firstOrNull { it.id == ref.rule }
                    if (nestedRule == null) return@map ref

                    val ruleSetName = registeredRule.pathInfo.ruleSetName()
                    val nestedRuleId = SemgrepRuleUtils.getRuleId(ruleSetName, nestedRule.id)
                    val nestedRegistered = RegisteredRule(
                        nestedRuleId, nestedRule, registeredRule.pathInfo, ruleInfo.ruleTrace
                    )
                    registerRule(nestedRegistered)
                    parseRule(nestedRegistered, forceLibraryMode = true)

                    ref.copy(rule = nestedRule.id)
                }

                val parsedJoin = JoinRule<Formula>(refs, parsed.on, ruleInfo)
                addParsedRule(ruleInfo, parsedJoin, loadTrace)
            }

            else -> {
                loadTrace.error("Unsupported mode: ${rule.mode}", Reason.ERROR)
                return
            }
        }
    }

    private fun addParsedRule(ruleInfo: RuleInfo, rule: Rule<Formula>, trace: SemgrepRuleLoadStepTrace) {
        if (rule is NormalRule && rule.rule.isEmpty && !ruleInfo.isLibraryRule) {
            trace.error("Empty rule after parse", Reason.ERROR)
            return
        }

        val id = ruleInfo.ruleId
        if (id in parsedRules) {
            trace.error("Duplicate rule", Reason.ERROR)
            return
        }

        parsedRules[id] = rule
    }

    private val builtNormalRules = hashMapOf<String, NormalRule<BuiltRule>>()

    private fun buildNormalRule(rule: NormalRule<Formula>) {
        val trace = rule.info.ruleTrace

        val ruleAutomataBuilder = SemgrepRuleAutomataBuilder(parser, converter)
        val ruleAutomata = runCatching {
            ruleAutomataBuilder.build(rule.rule, trace)
        }.onFailure { ex ->
            trace.stepTrace(Step.BUILD).error("Failed to build rule automata: ${ex.message}", Reason.ERROR)
            return
        }.getOrThrow()

        val stats = ruleAutomataBuilder.stats
        if (stats.isFailure) {
            trace.stepTrace(Step.BUILD).error("Automata build issues", Reason.WARNING)
        }

        val btaTrace = trace.stepTrace(Step.BUILD_TAINT_AUTOMATA)
        val taintAutomata = createTaintAutomata(ruleAutomata, btaTrace)

        if (taintAutomata.isEmpty && !rule.info.isLibraryRule) {
            trace.stepTrace(Step.BUILD).error("Empty rule after build", Reason.ERROR)
            return
        }

        builtNormalRules[rule.info.ruleId] = NormalRule(taintAutomata, rule.info)
    }

    private fun loadNormalRule(rule: NormalRule<BuiltRule>): Pair<TaintRuleFromSemgrep, RuleMetadata>? {
        val trace = rule.info.ruleTrace

        val a2trTrace = trace.stepTrace(Step.AUTOMATA_TO_TAINT_RULE)
        return runCatching {
            val ctx = RuleConversionCtx(rule.info.ruleId, rule.info.sinkMeta, a2trTrace)
            val rules = ctx.convertTaintAutomataToTaintRules(rule.rule)
            rules to rule.info.metadata
        }.onFailure { ex ->
            a2trTrace.error("Failed to create taint rules: ${ex.message}", Reason.ERROR)
            return null
        }.getOrThrow().also {
            trace.info("Generate ${it.first.size} rules from ${it.first.ruleId}")
        }
    }

    private fun loadJoinRule(rule: JoinRule<*>): Pair<TaintRuleFromSemgrep, RuleMetadata>? {
        val trace = rule.info.ruleTrace

        val taintAutomata = buildJoinRule(rule, trace.stepTrace(Step.BUILD))
            ?: return null

        val a2trTrace = trace.stepTrace(Step.AUTOMATA_TO_TAINT_RULE)
        return runCatching {
            val ctx = RuleConversionCtx(rule.info.ruleId, rule.info.sinkMeta, a2trTrace)
            val rules = ctx.convertTaintAutomataJoinToTaintRules(taintAutomata)
                ?: return null
            rules to rule.info.metadata
        }.onFailure { ex ->
            a2trTrace.error("Failed to create taint rules: ${ex.message}", Reason.ERROR)
            return null
        }.getOrThrow().also {
            trace.info("Generate ${it.first.size} rules from ${it.first.ruleId}")
        }
    }

    private fun resolveBuiltRuleWrtOverrides(
        ruleId: String,
        trace: SemgrepRuleLoadStepTrace,
        overrideChain: MutableSet<String>
    ): NormalRule<BuiltRule>? {
        val parsedRule = parsedRules[ruleId]
        if (parsedRule == null) {
            trace.error("Ref $ruleId not registered", Reason.ERROR)
            return null
        }

        if (parsedRule is RuleOverride<*>) {
            if (!overrideChain.add(ruleId)) {
                trace.error("Override loop", Reason.ERROR)
                return null
            }

            return resolveBuiltRuleWrtOverrides(parsedRule.refId, trace, overrideChain)
        }

        val builtRule = builtNormalRules[ruleId]
        if (builtRule == null) {
            trace.error("Ref $ruleId not loaded", Reason.ERROR)
            return null
        }

        return builtRule
    }

    private fun buildJoinRule(rule: JoinRule<*>, trace: SemgrepRuleLoadStepTrace): TaintAutomataJoinRule? {
        val items = hashMapOf<String, TaintAutomataJoinRuleItem>()
        val itemRenames = hashMapOf<String, List<Pair<MetavarAtom, MetavarAtom>>>()

        for (ref in rule.refs) {
            val refId = resolveRefRuleId(ref.rule, rule.info.pathInfo.ruleRelativePath)
            val itemAutomata = resolveBuiltRuleWrtOverrides(refId, trace, hashSetOf())
                ?: return null

            val renames = ref.renames.map {
                val from = parseMetaVar(it.from, trace) ?: return null
                val to = parseMetaVar(it.to, trace) ?: return null
                Pair(from, to)
            }

            items[ref.`as`] = TaintAutomataJoinRuleItem(itemAutomata.info.ruleId, itemAutomata.rule)
            itemRenames[ref.`as`] = renames
        }

        val operations = rule.on.map { op ->
            if (op.left.ruleName !in items || op.right.ruleName !in items) {
                trace.error("Incorrect join-on condition", Reason.ERROR)
            }

            val lhs = parseJoinMetaVarWithRenames(op.left, itemRenames, trace) ?: return null
            val rhs = parseJoinMetaVarWithRenames(op.right, itemRenames, trace) ?: return null

            TaintAutomataJoinOperation(op.op, lhs, rhs)
        }

        if (operations.isEmpty()) {
            trace.error("Join rule without join-on", Reason.WARNING)
            return null
        }

        return TaintAutomataJoinRule(items, operations)
    }

    private fun parseJoinMetaVarWithRenames(
        ref: SemgrepJoinRuleOnVar,
        renames: Map<String, List<Pair<MetavarAtom, MetavarAtom>>>,
        trace: SemgrepRuleLoadStepTrace
    ): TaintAutomataJoinMetaVarRef? {
        var metaVar = parseMetaVar(ref.varName, trace) ?: return null
        val rename = renames[ref.ruleName].orEmpty()

        for ((from, to) in rename) {
            if (to == metaVar) {
                metaVar = from
            }
        }

        return TaintAutomataJoinMetaVarRef(ref.ruleName, metaVar)
    }

    private fun parseMetaVar(metaVarStr: String, trace: SemgrepRuleLoadStepTrace): MetavarAtom? {
        val parsed = parser.parseOrNull(metaVarStr, trace) ?: return null
        if (parsed !is Metavar) {
            trace.error("Metavar expected, but $metaVarStr", Reason.NOT_IMPLEMENTED)
            return null
        }
        return MetavarAtom.create(parsed.name)
    }

    private fun parseRuleInfo(rule: RegisteredRule, forceLibraryMode: Boolean): RuleInfo {
        val semgrepRule = rule.rule
        val ruleCwe = semgrepRule.cweInfo()
        val severity = when (semgrepRule.severity.lowercase()) {
            "high", "critical", "error" -> Severity.Error
            "medium", "warning" -> Severity.Warning
            else -> Severity.Note
        }

        val sinkMeta = SinkMetaData(ruleCwe, semgrepRule.message, severity)
        val metadata = RuleMetadata(rule.ruleId, semgrepRule.id, semgrepRule.message, severity, semgrepRule.metadata)
        val overrides = semgrepRule.overrides(rule.pathInfo.ruleRelativePath)
        return RuleInfo(
            rule.ruleId, semgrepRule.id,
            overridesRuleId = overrides,
            isLibraryRule = forceLibraryMode || semgrepRule.isLibraryRule(),
            isDisabled = semgrepRule.isDisabled(),
            metadata, sinkMeta, rule.ruleTrace, rule.pathInfo
        )
    }

    private fun SemgrepYamlRule.isJavaRule(): Boolean =
        languages.orEmpty().any { it.equals("java", ignoreCase = true) }

    private fun SemgrepYamlRule.isJoinRule(): Boolean =
        mode?.equals("join", ignoreCase = true) ?: false

    private fun SemgrepYamlRule.isLibraryRule(): Boolean =
        options?.getBoolKeyOrFalse("lib") ?: false

    private fun SemgrepYamlRule.isDisabled(): Boolean =
        options?.getKey("disabled") != null

    private fun SemgrepYamlRule.overrides(ruleRelativePath: Path): String? {
        val overrides = options?.getScalar("overrides") ?: return null
        return resolveRefRuleId(overrides.content, ruleRelativePath)
    }

    private fun SemgrepYamlRule.cweInfo(): List<Int>? {
        val rawCwes = metadata?.readStrings("cwe") ?: return null
        val cwes = rawCwes.mapNotNull { s -> parseCwe(s) }
        return cwes.ifEmpty { null }
    }

    private fun parseCwe(str: String): Int? {
        val match = cweRegex.matchEntire(str) ?: return null
        return match.groupValues[1].toInt()
    }

    private fun RuleSetPathInfo.ruleSetName(): String = ruleRelativePath.ruleSetName()
    private fun Path.ruleSetName(): String = this.toString()

    private fun resolveRefRuleId(refRule: String, ruleRelativePath: Path): String {
        val refRuleId = refRule.substringAfter('#')

        val refRulePath = refRule.substringBefore('#', missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
            ?.removePrefix("/")
            ?.let { Path(it) }

        val refRulePathInfo = refRulePath ?: ruleRelativePath
        val refRuleSetName = refRulePathInfo.ruleSetName()
        return SemgrepRuleUtils.getRuleId(refRuleSetName, refRuleId)
    }

    private fun ruleSeverityAllow(rule: Rule<*>, severity: List<Severity>): Boolean =
        severity.isEmpty() || severity.contains(rule.info.metadata.severity)

    companion object {
        private val cweRegex = Regex("CWE-(\\d+).*", RegexOption.IGNORE_CASE)
    }
}
