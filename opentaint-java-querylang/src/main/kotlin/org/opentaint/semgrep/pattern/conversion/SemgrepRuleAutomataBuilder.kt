package org.opentaint.org.opentaint.semgrep.pattern.conversion

import org.opentaint.org.opentaint.semgrep.pattern.ActionListSemgrepRule
import org.opentaint.org.opentaint.semgrep.pattern.NormalizedSemgrepRule
import org.opentaint.org.opentaint.semgrep.pattern.ParsedSemgprepRule
import org.opentaint.org.opentaint.semgrep.pattern.RawSemgrepRule
import org.opentaint.org.opentaint.semgrep.pattern.SemgrepMatchingRule
import org.opentaint.org.opentaint.semgrep.pattern.SemgrepYamlRule
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.SemgrepRuleAutomata
import org.opentaint.org.opentaint.semgrep.pattern.conversion.automata.transformSemgrepRuleToAutomata
import org.opentaint.org.opentaint.semgrep.pattern.convertToRawRule
import org.opentaint.org.opentaint.semgrep.pattern.parseSemgrepRule

class SemgrepRuleAutomataBuilder(
    private val parser: SemgrepPatternParser = SemgrepPatternParser.create(),
    private val converter: ActionListBuilder = ActionListBuilder.create(),
) {
    data class Stats(
        var success: Int = 0,
        var failure: Int = 0,
        var ruleParsingFailure: Int = 0,
        var metaVarSpecializationFailure: Int = 0,
        var actionListConversionFailure: Int = 0,
    )

    val  stats = Stats()

    fun build(rule: SemgrepYamlRule): List<SemgrepRuleAutomata>? {
        val semgrepRule = parseSemgrepRule(rule)

        if (semgrepRule !is SemgrepMatchingRule) {
            TODO()
        }

        val rawRules = convertToRawRule(semgrepRule.rule)

        val parsedRules = rawRules.mapNotNull { parseSemgrepRule(it) }
        if (rawRules.size != parsedRules.size) {
            stats.ruleParsingFailure++
        }

        val metaVarSpecializedRulesList = parsedRules.mapNotNull { specializeMetaVars(it) }
        if (parsedRules.size != metaVarSpecializedRulesList.size) {
            stats.metaVarSpecializationFailure++
        }

        val metaVarSpecializedRules = metaVarSpecializedRulesList.flatten()

        val rewriterRules = metaVarSpecializedRules.map { rewriteRule(it) }

        val actionListRules = rewriterRules.mapNotNull { convertToActionList(it) }
        if (actionListRules.size != rewriterRules.size) {
            stats.actionListConversionFailure++
        }

        val ruleAutomata = actionListRules.map { transformSemgrepRuleToAutomata(it) }

        if (ruleAutomata.isEmpty()) {
            stats.failure++
            return null
        }

        stats.success++
        return ruleAutomata
    }

    fun convertToActionList(rule: NormalizedSemgrepRule): ActionListSemgrepRule? {
        return ActionListSemgrepRule(
            patterns = rule.patterns.map { converter.createActionList(it) ?: return null },
            patternNots = rule.patternNots.map { converter.createActionList(it) ?: return null },
            patternInsides = rule.patternInsides.map { converter.createActionList(it) ?: return null },
            patternNotInsides = rule.patternNotInsides.map { converter.createActionList(it) ?: return null },
        )
    }

    fun parseSemgrepRule(rule: RawSemgrepRule): ParsedSemgprepRule? {
        return ParsedSemgprepRule(
            patterns = rule.patterns.map { parser.parseOrNull(it) ?: return null },
            patternNots = rule.patternNots.map { parser.parseOrNull(it) ?: return null },
            patternInsides = rule.patternInsides.map { parser.parseOrNull(it) ?: return null },
            patternNotInsides = rule.patternNotInsides.map { parser.parseOrNull(it) ?: return null },
            metaVariablePatterns = rule.metaVariablePatterns.mapValues { (_, patterns) ->
                patterns.map { parser.parseOrNull(it) ?: return null }
            },
            metaVariableRegex = rule.metaVariableRegex
        )
    }

    fun rewriteRule(rule: NormalizedSemgrepRule): NormalizedSemgrepRule {
        return rewriteAddExpr(rule)
            .let { rewriteAssignEllipsis(it) }
    }
}
