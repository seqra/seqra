package org.opentaint.semgrep.pattern

import com.charleskorn.kaml.AnchorsAndAliases
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlScalar
import kotlinx.serialization.decodeFromString
import mu.KLogging
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.jvm.serialized.SinkMetaData
import org.slf4j.event.Level
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
        semgrepFileErrors: SemgrepFileErrors
    ): List<Pair<TaintRuleFromSemgrep, RuleMetadata>> {
        val ruleSet = runCatching {
            yaml.decodeFromString<SemgrepYamlRuleSet>(ruleSetText)
        }.onFailure { ex ->
            semgrepFileErrors += SemgrepError(
                SemgrepError.Step.LOAD_RULESET,
                "Failed to load rule set from yaml \"$ruleSetName\": ${ex.message}",
                Level.ERROR,
                SemgrepError.Reason.ERROR,
            )
            return emptyList()
        }.getOrThrow()

        val (javaRules, otherRules) = ruleSet.rules.partition { it.isJavaRule() }
        logger.info { "Found ${javaRules.size} java rules in $ruleSetName" }

        if (otherRules.isNotEmpty()) {
            logger.warn { "Found ${otherRules.size} unsupported rules in $ruleSetName" }
            otherRules.forEach { it ->
                semgrepFileErrors += SemgrepRuleErrors(
                    it.id,
                    arrayListOf(SemgrepError(
                        SemgrepError.Step.LOAD_RULESET,
                        "Unsupported rule",
                        Level.TRACE,
                        SemgrepError.Reason.ERROR
                    )),
                    ruleSetName
                )
            }
        }

        val rulesAndMetadata = javaRules.mapNotNull {
            val semgrepRuleErrors = SemgrepRuleErrors(
                it.id,
                ruleSetName = ruleSetName
            )
            semgrepFileErrors += semgrepRuleErrors
            loadRule(it, ruleSetName, semgrepRuleErrors)
        }
        logger.info { "Load ${rulesAndMetadata.size} rules from $ruleSetName" }
        return rulesAndMetadata
    }

    private fun loadRule(
        rule: SemgrepYamlRule, ruleSetName: String,
        semgrepRuleErrors: SemgrepRuleErrors
    ): Pair<TaintRuleFromSemgrep, RuleMetadata>? {
        val ruleId = SemgrepRuleUtils.getRuleId(ruleSetName, rule.id)

        val ruleAutomataBuilder = SemgrepRuleAutomataBuilder(parser, converter)
        val ruleAutomata = runCatching {
            ruleAutomataBuilder.build(rule, semgrepRuleErrors)
        }.onFailure { ex ->
            semgrepRuleErrors += SemgrepError(
                SemgrepError.Step.LOAD_RULESET,
                "Failed to build rule automata: $ruleId",
                Level.ERROR,
                SemgrepError.Reason.ERROR
            )
            return null
        }.getOrThrow()

        val stats = ruleAutomataBuilder.stats
        if (stats.isFailure) {
            semgrepRuleErrors += SemgrepError(
                SemgrepError.Step.LOAD_RULESET,
                "Rule $ruleId automata build issues: $stats",
                Level.TRACE,
                SemgrepError.Reason.ERROR
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
            convertToTaintRules(ruleAutomata, ruleId, sinkMeta, semgrepRuleErrors) to metadata
        }.onFailure { ex ->
            semgrepRuleErrors += SemgrepError(
                SemgrepError.Step.AUTOMATA_TO_TAINT_RULE,
                "Failed to create taint rules: $ruleId",
                Level.ERROR,
                SemgrepError.Reason.ERROR
            )
            return null
        }.getOrThrow()
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
        private val logger = object : KLogging() {}.logger
        private val cweRegex = Regex("CWE-(\\d+).*", RegexOption.IGNORE_CASE)
    }
}
