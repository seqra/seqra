package org.opentaint.semgrep.pattern.conversion

import org.opentaint.semgrep.pattern.AbstractSemgrepError
import org.opentaint.semgrep.pattern.ActionListSemgrepRule
import org.opentaint.semgrep.pattern.MetaVarConstraint
import org.opentaint.semgrep.pattern.MetaVarConstraints
import org.opentaint.semgrep.pattern.NormalizedSemgrepRule
import org.opentaint.semgrep.pattern.RawMetaVarConstraint
import org.opentaint.semgrep.pattern.RawMetaVarInfo
import org.opentaint.semgrep.pattern.RawSemgrepRule
import org.opentaint.semgrep.pattern.ResolvedMetaVarInfo
import org.opentaint.semgrep.pattern.RuleWithMetaVars
import org.opentaint.semgrep.pattern.SemgrepError
import org.opentaint.semgrep.pattern.SemgrepMatchingRule
import org.opentaint.semgrep.pattern.SemgrepRule
import org.opentaint.semgrep.pattern.SemgrepRuleErrors
import org.opentaint.semgrep.pattern.SemgrepTaintRule
import org.opentaint.semgrep.pattern.SemgrepYamlRule
import org.opentaint.semgrep.pattern.conversion.automata.SemgrepRuleAutomata
import org.opentaint.semgrep.pattern.conversion.automata.operations.containsAcceptState
import org.opentaint.semgrep.pattern.conversion.automata.transformSemgrepRuleToAutomata
import org.opentaint.semgrep.pattern.convertToRawRule
import org.opentaint.semgrep.pattern.parseSemgrepRule
import org.opentaint.semgrep.pattern.transform
import org.slf4j.event.Level
import kotlin.time.Duration.Companion.seconds

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

    fun build(
        yamlRule: SemgrepYamlRule,
        semgrepRuleErrors: SemgrepRuleErrors
    ): SemgrepRule<RuleWithMetaVars<SemgrepRuleAutomata, ResolvedMetaVarInfo>> {
        val semgrepRule = parseSemgrepRule(yamlRule)
        val convertToRawRuleError = SemgrepError(
            SemgrepError.Step.BUILD_CONVERT_TO_RAW_RULE,
            "Error during converting to raw rule",
            Level.WARN,
            SemgrepError.Reason.WARNING,
        )
        val rawRules = convertToRawRule(semgrepRule, convertToRawRuleError)
        semgrepRuleErrors.handlePhase(0, convertToRawRuleError)

        var ruleWithoutPattern = 0
        val normalRules = rawRules.fFlatMap { r ->
            if (r.patterns.isNotEmpty()) {
                listOf(r)
            } else {
                ruleWithoutPattern++
                emptyList()
            }
        }
        stats.ruleWithoutPattern += ruleWithoutPattern
        semgrepRuleErrors.handlePhase(
            ruleWithoutPattern,
            SemgrepError(
                SemgrepError.Step.BUILD_CONVERT_TO_RAW_RULE,
                "Empty patterns after convertToRawRule: $ruleWithoutPattern times",
                Level.WARN,
                SemgrepError.Reason.WARNING,
            )
        )

        var ruleParsingFailure = 0
        val parseSemgrepRuleError = SemgrepError(
            SemgrepError.Step.BUILD_PARSE_SEMGREP_RULE,
            "Failed parse normalized rule: $ruleParsingFailure times",
            Level.WARN,
            SemgrepError.Reason.WARNING,
        )
        val parsedRules = normalRules.fFlatMap { r ->
            parseSemgrepRule(r, parseSemgrepRuleError)?.let { listOf(it) } ?: run {
                ruleParsingFailure++
                emptyList()
            }
        }
        stats.ruleParsingFailure += ruleParsingFailure
        semgrepRuleErrors.handlePhase(
            ruleParsingFailure,
            parseSemgrepRuleError,
        )

        var metaVarResolvingFailure = 0
        val metaVarResolvingError = SemgrepError(
            SemgrepError.Step.BUILD_META_VAR_RESOLVING,
            "Failed resolve MetaVar",
            Level.WARN,
            SemgrepError.Reason.WARNING,
        )
        val rulesWithResolvedMetaVar = parsedRules.flatMap { r ->
            r.resolveMetaVarInfo(metaVarResolvingError)?.let { listOf(it) } ?: run {
                metaVarResolvingFailure++
                emptyList()
            }
        }
        stats.metaVarResolvingFailure += metaVarResolvingFailure
        semgrepRuleErrors.handlePhase(
            metaVarResolvingFailure,
            metaVarResolvingError,
        )

        val ruleAfterRewrite = rulesWithResolvedMetaVar.flatMap { rewriteRule(it) }

        var actionListConversionFailure = 0
        val actionListConversionError = SemgrepError(
            SemgrepError.Step.BUILD_ACTION_LIST_CONVERSION,
            "Failed to convert to action list",
            Level.WARN,
            SemgrepError.Reason.WARNING,
        )
        val ruleActionList = ruleAfterRewrite.fFlatMap { r ->
            convertToActionList(r, actionListConversionError)?.let { listOf(it) } ?: run {
                actionListConversionFailure++
                emptyList()
            }
        }
        stats.actionListConversionFailure += actionListConversionFailure
        semgrepRuleErrors.handlePhase(
            actionListConversionFailure,
            actionListConversionError,
        )

        val ruleActionListWithoutDuplicates = ruleActionList.removeDuplicateRules()

        var emptyAutomataFailure = 0
        val automataFailures = mutableListOf<SemgrepError>()
        val ruleAutomata = ruleActionListWithoutDuplicates.flatMap { r ->
            val automata = runCatching {
                transformSemgrepRuleToAutomata(r.rule, r.metaVarInfo, automataBuildTimeout)
            }.onFailure {
                automataFailures += SemgrepError(
                    SemgrepError.Step.BUILD_TRANSFORM_TO_AUTOMATA,
                    it.message ?: "",
                    Level.ERROR,
                    SemgrepError.Reason.ERROR,
                )
                return@flatMap emptyList()
            }.getOrThrow()

            if (automata.containsAcceptState()) {
                listOf(RuleWithMetaVars(automata, r.metaVarInfo))
            } else {
                emptyAutomataFailure++
                emptyList()
            }
        }

        semgrepRuleErrors.handlePhase(automataFailures)

        stats.emptyAutomata += emptyAutomataFailure
        semgrepRuleErrors.handlePhase(
            emptyAutomataFailure,
            SemgrepError(
                SemgrepError.Step.BUILD_TRANSFORM_TO_AUTOMATA,
                "Empty accepting state",
                Level.WARN,
                SemgrepError.Reason.WARNING,
            )
        )

        return ruleAutomata
    }

    private fun convertToActionList(
        rule: NormalizedSemgrepRule,
        semgrepError: AbstractSemgrepError
    ): ActionListSemgrepRule? {
        return ActionListSemgrepRule(
            patterns = rule.patterns.map {
                converter.createActionList(it, semgrepError) ?: return null
            },
            patternNots = rule.patternNots.map {
                converter.createActionList(it, semgrepError) ?: return null
            },
            patternInsides = rule.patternInsides.map {
                converter.createActionList(it, semgrepError) ?: return null
            },
            patternNotInsides = rule.patternNotInsides.map {
                converter.createActionList(it, semgrepError) ?: return null
            },
        )
    }

    private fun parseSemgrepRule(
        rule: RawSemgrepRule,
        semgrepError: AbstractSemgrepError
    ): NormalizedSemgrepRule? {
        return NormalizedSemgrepRule(
            patterns = rule.patterns.map {
                parser.parseOrNull(it, semgrepError, SemgrepError.Step.BUILD_PARSE_SEMGREP_RULE) ?: return null
            },
            patternNots = rule.patternNots.map {
                parser.parseOrNull(it, semgrepError, SemgrepError.Step.BUILD_PARSE_SEMGREP_RULE) ?: return null
            },
            patternInsides = rule.patternInsides.map {
                parser.parseOrNull(it, semgrepError, SemgrepError.Step.BUILD_PARSE_SEMGREP_RULE) ?: return null
            },
            patternNotInsides = rule.patternNotInsides.map {
                parser.parseOrNull(it, semgrepError, SemgrepError.Step.BUILD_PARSE_SEMGREP_RULE) ?: return null
            },
        )
    }

    private fun rewriteRule(
        rule: RuleWithMetaVars<NormalizedSemgrepRule, ResolvedMetaVarInfo>
    ): List<RuleWithMetaVars<NormalizedSemgrepRule, ResolvedMetaVarInfo>> {
        var resultRules = listOf(rule.rule)

        resultRules = resultRules.flatMap(::rewriteAddExpr)
        resultRules = resultRules.flatMap(::rewriteAssignEllipsis)
        resultRules = resultRules.flatMap(::rewriteMethodInvocationObj)
        resultRules = resultRules.flatMap(::rewriteStaticFieldAccess)
        resultRules = resultRules.flatMap(::rewriteReturnStatement)
        resultRules = resultRules.flatMap(::rewriteEllipsisMethodInvocations)

        return resultRules.flatMap { resultRule ->
            val result = rewriteTypeNameWithMetaVar(resultRule, rule.metaVarInfo)
            result.first.map { RuleWithMetaVars(it, result.second) }
        }
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

    private fun <R> RuleWithMetaVars<R, RawMetaVarInfo>.resolveMetaVarInfo(
        semgrepError: AbstractSemgrepError
    ): RuleWithMetaVars<R, ResolvedMetaVarInfo>? {
        val resolvedInfo = resolveMetaVarInfo(metaVarInfo, semgrepError) ?: return null
        return RuleWithMetaVars(rule, resolvedInfo)
    }

    private fun resolveMetaVarInfo(
        info: RawMetaVarInfo,
        semgrepError: AbstractSemgrepError
    ): ResolvedMetaVarInfo? {
        if (info.metaVariableConstraints.isEmpty()) {
            return ResolvedMetaVarInfo(info.focusMetaVars, emptyMap())
        }

        class PatternConstraintFailure : Exception() {
            override fun fillInStackTrace(): Throwable = this
        }

        val constraints = info.metaVariableConstraints.mapValues { (_, constraint) ->
            val formula = try {
                constraint.transform {
                    when (it) {
                        is RawMetaVarConstraint.Pattern -> {
                            patternConstraintValue(it.value, semgrepError) ?: throw PatternConstraintFailure()
                        }

                        is RawMetaVarConstraint.RegExp -> {
                            MetaVarConstraint.RegExp(it.regex)
                        }
                    }
                }
            } catch (e: PatternConstraintFailure) {
                return null
            }

            MetaVarConstraints(formula)
        }

        return ResolvedMetaVarInfo(info.focusMetaVars, constraints)
    }

    private fun patternConstraintValue(
        pattern: String,
        semgrepError: AbstractSemgrepError
    ): MetaVarConstraint? {
        val parsed = parser.parseOrNull(pattern, semgrepError, SemgrepError.Step.BUILD_META_VAR_RESOLVING)
            ?: return null

        val patternConcreteValue = tryExtractPatternDotSeparatedParts(parsed) ?: return null
        val patternConcreteNames = tryExtractConcreteNames(patternConcreteValue) ?: return null
        return MetaVarConstraint.Concrete(patternConcreteNames.joinToString(separator = "."))
    }

    companion object {
        private val automataBuildTimeout = 1.seconds
    }
}
