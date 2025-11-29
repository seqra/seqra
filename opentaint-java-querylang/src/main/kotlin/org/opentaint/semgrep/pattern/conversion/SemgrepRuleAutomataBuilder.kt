package org.opentaint.semgrep.pattern.conversion

import org.opentaint.semgrep.pattern.ActionListSemgrepRule
import org.opentaint.semgrep.pattern.MetaVarConstraint
import org.opentaint.semgrep.pattern.MetaVarConstraints
import org.opentaint.semgrep.pattern.NormalizedSemgrepRule
import org.opentaint.semgrep.pattern.RawMetaVarInfo
import org.opentaint.semgrep.pattern.RawSemgrepRule
import org.opentaint.semgrep.pattern.ResolvedMetaVarInfo
import org.opentaint.semgrep.pattern.RuleWithMetaVars
import org.opentaint.semgrep.pattern.SemgrepMatchingRule
import org.opentaint.semgrep.pattern.SemgrepRule
import org.opentaint.semgrep.pattern.SemgrepTaintRule
import org.opentaint.semgrep.pattern.SemgrepYamlRule
import org.opentaint.semgrep.pattern.conversion.automata.SemgrepRuleAutomata
import org.opentaint.semgrep.pattern.conversion.automata.operations.containsAcceptState
import org.opentaint.semgrep.pattern.conversion.automata.transformSemgrepRuleToAutomata
import org.opentaint.semgrep.pattern.convertToRawRule
import org.opentaint.semgrep.pattern.parseSemgrepRule

class SemgrepRuleAutomataBuilder(
    private val parser: SemgrepPatternParser = SemgrepPatternParser.create(),
    private val converter: ActionListBuilder = ActionListBuilder.create(),
) {
    data class Stats(
        var ruleParsingFailure: Int = 0,
        var ruleWithoutPattern: Int = 0,
        var metaVarResolvingFailure: Int = 0,
        var actionListConversionFailure: Int = 0,
        var emptyAutomata: Int = 0,
    ) {
        val isFailure: Boolean
            get() = (ruleParsingFailure + ruleWithoutPattern + metaVarResolvingFailure + actionListConversionFailure + emptyAutomata) > 0
    }

    val stats = Stats()

    fun build(rule: SemgrepYamlRule): SemgrepRule<RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>> {
        val semgrepRule = parseSemgrepRule(rule)
        val rawRules = convertToRawRule(semgrepRule)

        val normalRules = rawRules.fFlatMap { r ->
            if (r.patterns.isNotEmpty()) {
                listOf(r)
            } else {
                stats.ruleWithoutPattern++
                emptyList()
            }
        }

        val parsedRules = normalRules.fFlatMap { r ->
            parseSemgrepRule(r)?.let { listOf(it) } ?: run {
                stats.ruleParsingFailure++
                emptyList()
            }
        }

        val rulesWithResolvedMetaVar = parsedRules.flatMap { r ->
            r.resolveMetaVarInfo()?.let { listOf(it) } ?: run {
                stats.metaVarResolvingFailure++
                emptyList()
            }
        }

        val ruleAfterRewrite = rulesWithResolvedMetaVar.transform { rewriteRule(it) }

        val ruleActionList = ruleAfterRewrite.fFlatMap { r ->
            convertToActionList(r)?.let { listOf(it) } ?: run {
                stats.actionListConversionFailure++
                emptyList()
            }
        }

        val ruleActionListWithoutDuplicates = ruleActionList.removeDuplicateRules()

        val ruleAutomata = ruleActionListWithoutDuplicates.flatMap { r ->
            val automata = transformSemgrepRuleToAutomata(r.rule, r.metaVarInfo)
            if (automata.containsAcceptState()) {
                listOf(RuleWithMetaVars(automata, r.metaVarInfo))
            } else {
                stats.emptyAutomata++
                emptyList()
            }
        }

        return ruleAutomata
    }

    private fun convertToActionList(rule: NormalizedSemgrepRule): ActionListSemgrepRule? {
        return ActionListSemgrepRule(
            patterns = rule.patterns.map { converter.createActionList(it) ?: return null },
            patternNots = rule.patternNots.map { converter.createActionList(it) ?: return null },
            patternInsides = rule.patternInsides.map { converter.createActionList(it) ?: return null },
            patternNotInsides = rule.patternNotInsides.map { converter.createActionList(it) ?: return null },
        )
    }

    private fun parseSemgrepRule(rule: RawSemgrepRule): NormalizedSemgrepRule? {
        return NormalizedSemgrepRule(
            patterns = rule.patterns.map { parser.parseOrNull(it) ?: return null },
            patternNots = rule.patternNots.map { parser.parseOrNull(it) ?: return null },
            patternInsides = rule.patternInsides.map { parser.parseOrNull(it) ?: return null },
            patternNotInsides = rule.patternNotInsides.map { parser.parseOrNull(it) ?: return null },
        )
    }

    private fun rewriteRule(
        rule: RuleWithMetaVars<NormalizedSemgrepRule, ResolvedMetaVarInfo>
    ): RuleWithMetaVars<NormalizedSemgrepRule, ResolvedMetaVarInfo> {
        var resultRule = rule.rule
        var resultMetaVarInfo = rule.metaVarInfo

        resultRule = rewriteAddExpr(resultRule)
        resultRule = rewriteAssignEllipsis(resultRule)
        resultRule = rewriteMethodInvocationObj(resultRule)
        resultRule = rewriteStaticFieldAccess(resultRule)
        resultRule = rewriteReturnStatement(resultRule)

        run {
            val result = rewriteTypeNameWithMetaVar(resultRule, resultMetaVarInfo)
            resultRule = result.first
            resultMetaVarInfo = result.second
        }

        return RuleWithMetaVars(resultRule, resultMetaVarInfo)
    }

    private inline fun <T, R, C> SemgrepRule<RuleWithMetaVars<T, C>>.fFlatMap(crossinline body: (T) -> List<R>): SemgrepRule<RuleWithMetaVars<R, C>> =
        flatMap { r -> r.flatMap { body(it) } }

    private fun <T, C> SemgrepRule<RuleWithMetaVars<T, C>>.removeDuplicateRules() = when (this) {
        is SemgrepMatchingRule -> removeDuplicateRules()
        is SemgrepTaintRule -> removeDuplicateRules()
    }

    private fun <T, C> SemgrepMatchingRule<RuleWithMetaVars<T, C>>.removeDuplicateRules() =
        SemgrepMatchingRule(rules.distinct())

    private fun <T, C> SemgrepTaintRule<RuleWithMetaVars<T, C>>.removeDuplicateRules() =
        SemgrepTaintRule(sources.distinct(), sinks.distinct(), propagators.distinct(), sanitizers.distinct())

    private fun <R> RuleWithMetaVars<R, RawMetaVarInfo>.resolveMetaVarInfo(): RuleWithMetaVars<R, ResolvedMetaVarInfo>? {
        val resolvedInfo = resolveMetaVarInfo(metaVarInfo) ?: return null
        return RuleWithMetaVars(rule, resolvedInfo)
    }

    private fun resolveMetaVarInfo(info: RawMetaVarInfo): ResolvedMetaVarInfo? {
        if (info.metaVariableRegex.isEmpty() && info.metaVariablePatterns.isEmpty()) {
            return ResolvedMetaVarInfo(info.focusMetaVars, emptyMap())
        }

        val metaVarConstraints = hashMapOf<String, MutableSet<MetaVarConstraint>>()
        for ((metaVar, regex) in info.metaVariableRegex) {
            val constraints = metaVarConstraints.getOrPut(metaVar, ::hashSetOf)
            regex.mapTo(constraints) { MetaVarConstraint.RegExp(it) }
        }

        for ((metaVar, patterns) in info.metaVariablePatterns) {
            val constraints = metaVarConstraints.getOrPut(metaVar, ::hashSetOf)
            patterns.mapTo(constraints) { patternConstraintValue(it) ?: return null }
        }

        val constraints = metaVarConstraints.mapValues { (_, v) -> MetaVarConstraints(v) }
        return ResolvedMetaVarInfo(info.focusMetaVars, constraints)
    }

    private fun patternConstraintValue(pattern: String): MetaVarConstraint? {
        val parsed = parser.parseOrNull(pattern) ?: return null
        val patternConcreteValue = tryExtractPatternDotSeparatedParts(parsed) ?: return null
        val patternConcreteNames = tryExtractConcreteNames(patternConcreteValue) ?: return null
        return MetaVarConstraint.Concrete(patternConcreteNames.joinToString(separator = "."))
    }
}
