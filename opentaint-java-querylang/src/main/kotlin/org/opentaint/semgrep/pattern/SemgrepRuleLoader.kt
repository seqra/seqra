package org.opentaint.semgrep.pattern

import com.charleskorn.kaml.AnchorsAndAliases
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlScalar
import kotlinx.serialization.decodeFromString
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.jvm.serialized.SinkMetaData
import org.opentaint.semgrep.pattern.SemgrepTraceEntry.Step
import org.opentaint.semgrep.pattern.conversion.ActionListBuilder
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternParser
import org.opentaint.semgrep.pattern.conversion.SemgrepRuleAutomataBuilder
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

class SemgrepRuleLoader {
    private val parser = SemgrepPatternParser.create().cached()
    private val converter = ActionListBuilder.create().cached()

    private val yaml = Yaml(
        configuration = YamlConfiguration(
            codePointLimit = Int.MAX_VALUE,
            strictMode = false,
            anchorsAndAliases = AnchorsAndAliases.Permitted()
        )
    )

    fun loadRuleSet(
        ruleSetText: String,
        ruleSetName: String,
        semgrepFileTrace: SemgrepFileLoadTrace
    ): List<Pair<TaintRuleFromSemgrep, RuleMetadata>> {
        val ruleSet = runCatching {
            yaml.decodeFromString<SemgrepYamlRuleSet>(ruleSetText)
        }.onFailure { ex ->
            semgrepFileTrace.error(
                Step.LOAD_RULESET,
                "Failed to load rule set from yaml \"$ruleSetName\": ${ex.message}",
                SemgrepErrorEntry.Reason.ERROR,
            )
            return emptyList()
        }.getOrThrow()

        val (javaRules, otherRules) = ruleSet.rules.partition { it.isJavaRule() }
        semgrepFileTrace.info("Found ${javaRules.size} java rules in $ruleSetName")

        otherRules.forEach {
            val ruleId = SemgrepRuleUtils.getRuleId(ruleSetName, it.id)
            semgrepFileTrace
                .ruleTrace(ruleId, it.id)
                .error(
                    Step.LOAD_RULESET,
                    "Unsupported rule",
                    SemgrepErrorEntry.Reason.ERROR
                )
        }

        val rulesAndMetadata = javaRules.mapNotNull {
            val ruleId = SemgrepRuleUtils.getRuleId(ruleSetName, it.id)
            loadRule(ruleId, it, semgrepFileTrace.ruleTrace(ruleId, it.id))
        }
        semgrepFileTrace.info("Load ${rulesAndMetadata.size} rules from $ruleSetName")
        return rulesAndMetadata
    }

    private fun loadRule(
        ruleId: String,
        rule: SemgrepYamlRule,
        semgrepRuleTrace: SemgrepRuleLoadTrace
    ): Pair<TaintRuleFromSemgrep, RuleMetadata>? {
        val ruleAutomataBuilder = SemgrepRuleAutomataBuilder(parser, converter)
        val ruleAutomata = runCatching {
            ruleAutomataBuilder.build(rule, semgrepRuleTrace)
        }.onFailure {
            semgrepRuleTrace.error(
                Step.LOAD_RULESET,
                "Failed to build rule automata: $ruleId",
                SemgrepErrorEntry.Reason.ERROR
            )
            return null
        }.getOrThrow()

        val stats = ruleAutomataBuilder.stats
        if (stats.isFailure) {
            semgrepRuleTrace.error(
                Step.LOAD_RULESET,
                "Rule $ruleId automata build issues: $stats",
                SemgrepErrorEntry.Reason.ERROR
            )
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

        return runCatching {
            convertToTaintRules(
                ruleAutomata, ruleId, sinkMeta,
                semgrepRuleTrace.stepTrace(Step.AUTOMATA_TO_TAINT_RULE)
            ) to metadata
        }.onFailure {
            semgrepRuleTrace.error(
                Step.AUTOMATA_TO_TAINT_RULE,
                "Failed to create taint rules: $ruleId",
                SemgrepErrorEntry.Reason.ERROR
            )
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
