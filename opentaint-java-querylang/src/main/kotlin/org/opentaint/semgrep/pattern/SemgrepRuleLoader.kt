package org.opentaint.semgrep.pattern

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlScalar
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.jvm.serialized.SinkMetaData
import org.opentaint.semgrep.pattern.SemgrepErrorEntry.Reason
import org.opentaint.semgrep.pattern.SemgrepTraceEntry.Step
import org.opentaint.semgrep.pattern.conversion.ActionListBuilder
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternParser
import org.opentaint.semgrep.pattern.conversion.SemgrepRuleAutomataBuilder
import org.opentaint.semgrep.pattern.conversion.automata.SemgrepRuleAutomata
import org.opentaint.semgrep.pattern.conversion.taint.convertToTaintRules

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

private typealias BuiltRule = RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>

class SemgrepRuleLoader(
    private val parser: SemgrepPatternParser = SemgrepPatternParser.create().cached(),
    private val converter: ActionListBuilder = ActionListBuilder.create().cached()
) {
    data class RegisteredRule(
        val ruleId: String,
        val rule: SemgrepYamlRule,
        val semgrepRuleTrace: SemgrepRuleLoadTrace
    )

    private val registeredRules = hashMapOf<String, RegisteredRule>()

    fun registerRuleSet(
        ruleSetText: String,
        ruleSetName: String,
        semgrepFileTrace: SemgrepFileLoadTrace
    ) {
        val ruleSet = parseSemgrepYaml(ruleSetText, semgrepFileTrace) ?: return

        val (javaRules, otherRules) = ruleSet.rules.partition { it.isJavaRule() }
        semgrepFileTrace.info("Found ${javaRules.size} java rules in $ruleSetName")

        otherRules.forEach {
            val ruleId = SemgrepRuleUtils.getRuleId(ruleSetName, it.id)
            semgrepFileTrace
                .ruleTrace(ruleId, it.id)
                .error(Step.LOAD_RULESET, "Unsupported rule", Reason.ERROR)
        }

        javaRules.forEach {
            val ruleId = SemgrepRuleUtils.getRuleId(ruleSetName, it.id)
            registeredRules[ruleId] = RegisteredRule(ruleId, it, semgrepFileTrace.ruleTrace(ruleId, it.id))
        }

        semgrepFileTrace.info("Register ${javaRules.size} rules from $ruleSetName")
    }

    fun loadRules(): List<Pair<TaintRuleFromSemgrep, RuleMetadata>> {
        registeredRules.values.forEach { buildRule(it) }

        val loaded = mutableListOf<Pair<TaintRuleFromSemgrep, RuleMetadata>>()
        patternRules.values.mapNotNullTo(loaded) { loadRule(it) }
        taintRules.values.mapNotNullTo(loaded) { loadRule(it) }
        return loaded
    }

    private data class PreparedRule<P, R : SemgrepRule<P>>(
        val ruleId: String,
        val rule: R,
        val metadata: RuleMetadata,
        val sinkMeta: SinkMetaData,
        val semgrepRuleTrace: SemgrepRuleLoadTrace,
    )

    private val patternRules = hashMapOf<String, PreparedRule<BuiltRule, SemgrepMatchingRule<BuiltRule>>>()
    private val taintRules = hashMapOf<String, PreparedRule<BuiltRule, SemgrepTaintRule<BuiltRule>>>()

    private fun buildRule(registeredRule: RegisteredRule) {
        val ruleId = registeredRule.ruleId
        val rule = registeredRule.rule
        val semgrepRuleTrace = registeredRule.semgrepRuleTrace

        val ruleAutomataBuilder = SemgrepRuleAutomataBuilder(parser, converter)
        val ruleAutomata = runCatching {
            ruleAutomataBuilder.build(rule, semgrepRuleTrace)
        }.onFailure {
            semgrepRuleTrace.stepTrace(Step.BUILD).error("Failed to build rule automata", Reason.ERROR)
            return
        }.getOrThrow()

        val stats = ruleAutomataBuilder.stats
        if (stats.isFailure) {
            semgrepRuleTrace.stepTrace(Step.BUILD).error("Automata build issues", Reason.ERROR)
        }

        val ruleCwe = rule.cweInfo()
        val severity = when (rule.severity.lowercase()) {
            "high", "critical", "error" -> CommonTaintConfigurationSinkMeta.Severity.Error
            "medium", "warning" -> CommonTaintConfigurationSinkMeta.Severity.Warning
            else -> CommonTaintConfigurationSinkMeta.Severity.Note
        }

        val sinkMeta = SinkMetaData(
            cwe = ruleCwe,
            note = rule.message,
            severity = severity
        )

        val metadata = RuleMetadata(ruleId, rule.id, rule.message, severity, rule.metadata)

        when (ruleAutomata) {
            is SemgrepMatchingRule<BuiltRule> -> {
                val preparedRule = PreparedRule(ruleId, ruleAutomata, metadata, sinkMeta, semgrepRuleTrace)
                patternRules[ruleId] = preparedRule
            }

            is SemgrepTaintRule<BuiltRule> -> {
                val preparedRule = PreparedRule(ruleId, ruleAutomata, metadata, sinkMeta, semgrepRuleTrace)
                taintRules[ruleId] = preparedRule
            }
        }
    }

    private fun <R : SemgrepRule<BuiltRule>> loadRule(
        preparedRule: PreparedRule<BuiltRule, R>
    ): Pair<TaintRuleFromSemgrep, RuleMetadata>? {
        val semgrepRuleTrace = preparedRule.semgrepRuleTrace
        val a2trTrace = semgrepRuleTrace.stepTrace(Step.AUTOMATA_TO_TAINT_RULE)
        return runCatching {
            val rules = convertToTaintRules(preparedRule.rule, preparedRule.ruleId, preparedRule.sinkMeta, a2trTrace)
            rules to preparedRule.metadata
        }.onFailure {
            a2trTrace.error("Failed to create taint rules", Reason.ERROR)
            return null
        }.getOrThrow().also {
            semgrepRuleTrace.info("Generate ${it.first.size} rules from ${it.first.ruleId}")
        }
    }

    private fun SemgrepYamlRule.isJavaRule(): Boolean = languages.any {
        it.equals("java", ignoreCase = true)
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

    companion object {
        private val cweRegex = Regex("CWE-(\\d+).*", RegexOption.IGNORE_CASE)
    }
}
