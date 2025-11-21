package org.opentaint.org.opentaint.semgrep.pattern.conversion

import org.opentaint.org.opentaint.semgrep.pattern.ActionListSemgrepRule
import org.opentaint.org.opentaint.semgrep.pattern.NormalizedSemgrepRule
import org.opentaint.org.opentaint.semgrep.pattern.ParsedSemgprepRule
import org.opentaint.org.opentaint.semgrep.pattern.RawSemgrepRule
import org.opentaint.org.opentaint.semgrep.pattern.RuleWithFocusMetaVars
import org.opentaint.org.opentaint.semgrep.pattern.SemgrepRule
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
        var ruleParsingFailure: Int = 0,
        var metaVarSpecializationFailure: Int = 0,
        var actionListConversionFailure: Int = 0,
    ) {
        val isFailure: Boolean get() = (ruleParsingFailure + metaVarSpecializationFailure + actionListConversionFailure) > 0
    }

    val stats = Stats()

    fun build(rule: SemgrepYamlRule): SemgrepRule<RuleWithFocusMetaVars<SemgrepRuleAutomata>> {
        val semgrepRule = parseSemgrepRule(rule)
        val rawRules = convertToRawRule(semgrepRule)

        val parsedRules = rawRules.fFlatMap { r ->
            parseSemgrepRule(r)?.let { listOf(it) } ?: run {
                stats.ruleParsingFailure++
                emptyList()
            }
        }

        val metaVarSpecialized = parsedRules.fFlatMap { r ->
            specializeMetaVars(r) ?: run {
                stats.metaVarSpecializationFailure++
                emptyList()
            }
        }

        val ruleAfterRewrite = metaVarSpecialized.fmap { rewriteRule(it) }

        val ruleActionList = ruleAfterRewrite.fFlatMap { r ->
            convertToActionList(r)?.let { listOf(it) } ?: run {
                stats.actionListConversionFailure++
                emptyList()
            }
        }

        return ruleActionList.fmap { transformSemgrepRuleToAutomata(it) }
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

    private inline fun <T, R> SemgrepRule<RuleWithFocusMetaVars<T>>.fmap(crossinline body: (T) -> R): SemgrepRule<RuleWithFocusMetaVars<R>> =
        transform { r -> r.map { body(it) } }

    private inline fun <T, R> SemgrepRule<RuleWithFocusMetaVars<T>>.fFlatMap(crossinline body: (T) -> List<R>): SemgrepRule<RuleWithFocusMetaVars<R>> =
        flatMap { r -> r.flatMap { body(it) } }
}
