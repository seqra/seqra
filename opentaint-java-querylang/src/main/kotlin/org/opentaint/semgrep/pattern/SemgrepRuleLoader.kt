package org.opentaint.semgrep.pattern

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlScalar
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.jvm.serialized.SinkMetaData
import org.opentaint.semgrep.pattern.SemgrepErrorEntry.Reason
import org.opentaint.semgrep.pattern.SemgrepTraceEntry.Step
import org.opentaint.semgrep.pattern.conversion.ActionListBuilder
import org.opentaint.semgrep.pattern.conversion.MetavarAtom
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternParser
import org.opentaint.semgrep.pattern.conversion.SemgrepRuleAutomataBuilder
import org.opentaint.semgrep.pattern.conversion.taint.TaintAutomataJoinMetaVarRef
import org.opentaint.semgrep.pattern.conversion.taint.TaintAutomataJoinOperation
import org.opentaint.semgrep.pattern.conversion.taint.TaintAutomataJoinRule
import org.opentaint.semgrep.pattern.conversion.taint.TaintAutomataJoinRuleItem
import org.opentaint.semgrep.pattern.conversion.taint.TaintAutomataJoinRuleMetaVarRename
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata
import org.opentaint.semgrep.pattern.conversion.taint.convertTaintAutomataJoinToTaintRules
import org.opentaint.semgrep.pattern.conversion.taint.convertTaintAutomataToTaintRules
import org.opentaint.semgrep.pattern.conversion.taint.createTaintAutomata
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

data class RuleMetadata(
    val path: String,
    val ruleId: String,
    val message: String,
    val severity: CommonTaintConfigurationSinkMeta.Severity,
    val metadata: YamlMap?
)

fun YamlMap.readStrings(key: String): List<String>? {
    val entry = entries.entries.find { it.key.content.lowercase() == key.lowercase() } ?: return null
    return when (val value = entry.value) {
        is YamlScalar -> {
            listOf(value.content)
        }
        is YamlList -> {
            value.items.mapNotNull { (it as? YamlScalar)?.content }
        }
        else -> null
    }
}

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
            registeredRules[ruleId] = RegisteredRule(ruleId, it, pathInfo, semgrepFileTrace.ruleTrace(ruleId, it.id))
        }

        semgrepFileTrace.info("Register ${supportedRules.size} rules")
    }

    fun loadRules(): List<Pair<TaintRuleFromSemgrep, RuleMetadata>> {
        registeredRules.values.forEach { parseRule(it, forceLibraryMode = false) }
        parsedNormalRules.values.forEach { buildNormalRule(it) }

        val loaded = mutableListOf<Pair<TaintRuleFromSemgrep, RuleMetadata>>()
        builtNormalRules.values.filterNot { it.info.isLibraryRule }.forEach {
            loaded += loadNormalRule(it) ?: return@forEach
        }

        parsedJoinRules.values.filterNot { it.info.isLibraryRule }.forEach {
            loaded += loadJoinRule(it) ?: return@forEach
        }

        return loaded
    }

    private data class RuleInfo(
        val ruleId: String,
        val isLibraryRule: Boolean,
        val metadata: RuleMetadata,
        val sinkMeta: SinkMetaData,
        val ruleTrace: SemgrepRuleLoadTrace,
        val pathInfo: RuleSetPathInfo,
    )

    private data class NormalRule<P>(val rule: SemgrepRule<P>, val info: RuleInfo)

    private data class JoinRule(
        val refs: List<SemgrepYamlJoinRuleRef>,
        val on: List<SemgrepJoinRuleOn>,
        val info: RuleInfo,
    )

    private val parsedNormalRules = hashMapOf<String, NormalRule<Formula>>()
    private val parsedJoinRules = hashMapOf<String, JoinRule>()

    private fun parseRule(registeredRule: RegisteredRule, forceLibraryMode: Boolean) {
        val ruleInfo = parseRuleInfo(registeredRule, forceLibraryMode)
        val loadTrace = ruleInfo.ruleTrace.stepTrace(Step.LOAD_RULESET)

        val rule = registeredRule.rule
        when (rule.mode) {
            null, "search" -> {
                val parsed = parseMatchingRule(rule, loadTrace) ?: return
                parsedNormalRules[ruleInfo.ruleId] = NormalRule(parsed, ruleInfo)
            }

            "taint" -> {
                val parsed = parseTaintRule(rule, loadTrace)
                parsedNormalRules[ruleInfo.ruleId] = NormalRule(parsed, ruleInfo)
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
                    parseRule(nestedRegistered, forceLibraryMode = true)

                    ref.copy(rule = nestedRuleId)
                }

                val parsedJoin = JoinRule(refs, parsed.on, ruleInfo)
                parsedJoinRules[ruleInfo.ruleId] = parsedJoin
            }

            else -> {
                loadTrace.error("Unsupported mode: ${rule.mode}", Reason.ERROR)
                return
            }
        }
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
            trace.stepTrace(Step.BUILD).error("Automata build issues", Reason.ERROR)
        }

        val btaTrace = trace.stepTrace(Step.BUILD_TAINT_AUTOMATA)
        val taintAutomata = createTaintAutomata(ruleAutomata, btaTrace)

        builtNormalRules[rule.info.ruleId] = NormalRule(taintAutomata, rule.info)
    }

    private fun loadNormalRule(rule: NormalRule<BuiltRule>): Pair<TaintRuleFromSemgrep, RuleMetadata>? {
        val trace = rule.info.ruleTrace

        val a2trTrace = trace.stepTrace(Step.AUTOMATA_TO_TAINT_RULE)
        return runCatching {
            val rules = convertTaintAutomataToTaintRules(rule.rule, rule.info.ruleId, rule.info.sinkMeta, a2trTrace)
            rules to rule.info.metadata
        }.onFailure { ex ->
            a2trTrace.error("Failed to create taint rules: ${ex.message}", Reason.ERROR)
            return null
        }.getOrThrow().also {
            trace.info("Generate ${it.first.size} rules from ${it.first.ruleId}")
        }
    }

    private fun loadJoinRule(rule: JoinRule): Pair<TaintRuleFromSemgrep, RuleMetadata>? {
        val trace = rule.info.ruleTrace

        val taintAutomata = buildJoinRule(rule, trace.stepTrace(Step.BUILD))
            ?: return null

        val a2trTrace = trace.stepTrace(Step.AUTOMATA_TO_TAINT_RULE)
        return runCatching {
            val rules = convertTaintAutomataJoinToTaintRules(
                taintAutomata, rule.info.ruleId, rule.info.sinkMeta, a2trTrace
            ) ?: return null
            rules to rule.info.metadata
        }.onFailure { ex ->
            a2trTrace.error("Failed to create taint rules: ${ex.message}", Reason.ERROR)
            return null
        }.getOrThrow().also {
            trace.info("Generate ${it.first.size} rules from ${it.first.ruleId}")
        }
    }

    private fun buildJoinRule(rule: JoinRule, trace: SemgrepRuleLoadStepTrace): TaintAutomataJoinRule? {
        val items = hashMapOf<String, TaintAutomataJoinRuleItem>()
        for (ref in rule.refs) {
            val refId = resolveRefRuleId(ref.rule, rule.info)
            val itemAutomata = builtNormalRules[refId]
            if (itemAutomata == null) {
                val registeredRef = registeredRules[refId]
                if (registeredRef == null) {
                    trace.error("Ref ${ref.rule} not registered", Reason.ERROR)
                    return null
                }

                trace.error("Ref ${ref.rule} not loaded", Reason.ERROR)
                return null
            }

            val renames = ref.renames.map {
                val from = parseMetaVar(it.from, trace) ?: return null
                val to = parseMetaVar(it.to, trace) ?: return null
                TaintAutomataJoinRuleMetaVarRename(from, to)
            }

            items[ref.`as`] = TaintAutomataJoinRuleItem(itemAutomata.info.ruleId, itemAutomata.rule, renames)
        }

        val operations = rule.on.map { op ->
            if (op.left.ruleName !in items || op.right.ruleName !in items) {
                trace.error("Incorrect join-on condition", Reason.ERROR)
            }

            val lhs = TaintAutomataJoinMetaVarRef(
                op.left.ruleName,
                parseMetaVar(op.left.varName, trace) ?: return null
            )

            val rhs = TaintAutomataJoinMetaVarRef(
                op.right.ruleName,
                parseMetaVar(op.right.varName, trace) ?: return null
            )

            TaintAutomataJoinOperation(op.op, lhs, rhs)
        }

        if (operations.isEmpty()) {
            trace.error("Join rule without join-on", Reason.WARNING)
            return null
        }

        return TaintAutomataJoinRule(items, operations)
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
            "high", "critical", "error" -> CommonTaintConfigurationSinkMeta.Severity.Error
            "medium", "warning" -> CommonTaintConfigurationSinkMeta.Severity.Warning
            else -> CommonTaintConfigurationSinkMeta.Severity.Note
        }

        val sinkMeta = SinkMetaData(ruleCwe, semgrepRule.message, severity)
        val metadata = RuleMetadata(rule.ruleId, semgrepRule.id, semgrepRule.message, severity, semgrepRule.metadata)
        return RuleInfo(
            rule.ruleId,
            isLibraryRule = forceLibraryMode || semgrepRule.isLibraryRule(),
            metadata, sinkMeta, rule.ruleTrace, rule.pathInfo
        )
    }

    private fun SemgrepYamlRule.isJavaRule(): Boolean =
        languages.orEmpty().any { it.equals("java", ignoreCase = true) }

    private fun SemgrepYamlRule.isJoinRule(): Boolean =
        mode?.equals("join", ignoreCase = true) ?: false

    private fun SemgrepYamlRule.isLibraryRule(): Boolean =
        (options?.get("lib") as? YamlScalar)?.content?.lowercase() == "true"

    private fun SemgrepYamlRule.cweInfo(): List<Int>? {
        val rawCwes = metadata?.readStrings("cwe") ?: return null
        val cwes = rawCwes.mapNotNull { s -> parseCwe(s) }
        return cwes.ifEmpty { null }
    }

    private fun parseCwe(str: String): Int? {
        val match = cweRegex.matchEntire(str) ?: return null
        return match.groupValues[1].toInt()
    }

    private fun RuleSetPathInfo.ruleSetName(): String =
        rulesRoot.resolve(ruleRelativePath).absolutePathString()

    private fun resolveRefRuleId(refRule: String, ruleInfo: RuleInfo): String {
        val refRuleId = refRule.substringAfter('#')

        val refRulePath = refRule.substringBefore('#', missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
            ?.let { Path(it) }

        val refRulePathInfo = RuleSetPathInfo(
            ruleInfo.pathInfo.rulesRoot,
            refRulePath ?: ruleInfo.pathInfo.ruleRelativePath
        )
        val refRuleSetName = refRulePathInfo.ruleSetName()
        return SemgrepRuleUtils.getRuleId(refRuleSetName, refRuleId)
    }

    companion object {
        private val cweRegex = Regex("CWE-(\\d+).*", RegexOption.IGNORE_CASE)
    }
}
