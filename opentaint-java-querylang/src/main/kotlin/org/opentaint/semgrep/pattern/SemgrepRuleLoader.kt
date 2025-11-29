package org.opentaint.semgrep.pattern

import com.charleskorn.kaml.AnchorsAndAliases
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlScalar
import kotlinx.serialization.decodeFromString
import mu.KLogging
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.jvm.serialized.SinkMetaData
import org.opentaint.semgrep.pattern.conversion.ActionListBuilder
import org.opentaint.semgrep.pattern.conversion.SemgrepPatternParser
import org.opentaint.semgrep.pattern.conversion.SemgrepRuleAutomataBuilder
import org.opentaint.semgrep.pattern.conversion.taint.convertToTaintRules

class SemgrepRuleLoader {
    private val parser = SemgrepPatternParser.create().cached()
    private val converter = ActionListBuilder.create().cached()

    private val yaml = Yaml(
        configuration = YamlConfiguration(
            strictMode = false,
            anchorsAndAliases = AnchorsAndAliases.Permitted()
        )
    )

    fun loadRuleSet(ruleSetText: String, ruleSetName: String): List<TaintRuleFromSemgrep> {
        val ruleSet = runCatching {
            yaml.decodeFromString<SemgrepYamlRuleSet>(ruleSetText)
        }.onFailure { ex ->
            logger.error(ex) { "Failed to load rule set: $ruleSetName" }
            return emptyList()
        }.getOrThrow()

        val (javaRules, otherRules) = ruleSet.rules.partition { it.isJavaRule() }
        logger.info { "Found ${javaRules.size} java rules in $ruleSetName" }

        if (otherRules.isNotEmpty()) {
            logger.warn { "Found ${otherRules.size} unsupported rules in $ruleSetName" }
        }

        val rules = javaRules.mapNotNull { loadRule(it, ruleSetName) }
        logger.info { "Load ${rules.size} rules from $ruleSetName" }
        return rules
    }

    private fun loadRule(rule: SemgrepYamlRule, ruleSetName: String): TaintRuleFromSemgrep? {
        val ruleId = "${ruleSetName}/${rule.id}"

        val ruleAutomataBuilder = SemgrepRuleAutomataBuilder(parser, converter)
        val ruleAutomata = runCatching {
            ruleAutomataBuilder.build(rule)
        }.onFailure { ex ->
            logger.error(ex) { "Failed to build rule automata: $ruleId" }
            return null
        }.getOrThrow()

        val stats = ruleAutomataBuilder.stats
        if (stats.isFailure) {
            logger.warn { "Rule $ruleId automata build issues: $stats" }
        }

        val ruleCwe = rule.cweInfo()

        val sinkMeta = SinkMetaData(
            cwe = ruleCwe,
            note = rule.message,
            severity = when (rule.severity.lowercase()) {
                "high", "critical" -> CommonTaintConfigurationSinkMeta.Severity.Error
                "medium" -> CommonTaintConfigurationSinkMeta.Severity.Warning
                else -> CommonTaintConfigurationSinkMeta.Severity.Note
            }
        )

        return runCatching {
            convertToTaintRules(ruleAutomata, ruleId, sinkMeta)
        }.onFailure { ex ->
            logger.error(ex) { "Failed to create taint rules: $ruleId" }
            return null
        }.getOrThrow()
    }

    private fun SemgrepYamlRule.isJavaRule(): Boolean = languages.any {
        it.equals("java", ignoreCase = true)
    }

    private fun SemgrepYamlRule.cweInfo(): List<Int>? {
        val metadata = metadata ?: return null
        val cweEntry = metadata.entries.entries.find { it.key.content.lowercase() == "cwe" } ?: return null
        val cweValue = cweEntry.value
        when (cweValue) {
            is YamlScalar -> {
                val cwe = parseCwe(cweValue.content) ?: return null
                return listOf(cwe)
            }
            is YamlList -> {
                val cwe = cweValue.items.mapNotNull { (it as? YamlScalar)?.content?.let { s -> parseCwe(s) } }
                return cwe.ifEmpty { null }
            }
            else -> return null
        }
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
