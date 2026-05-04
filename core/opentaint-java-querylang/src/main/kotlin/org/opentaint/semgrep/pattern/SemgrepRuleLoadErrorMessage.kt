package org.opentaint.semgrep.pattern

import org.opentaint.semgrep.pattern.SemgrepErrorEntry.Category
import org.opentaint.semgrep.pattern.SemgrepErrorEntry.Severity

abstract class SemgrepRuleLoadErrorMessage(val category: Category, val severity: Severity) {
    abstract val message: String
}

sealed class RuleIssueBlockingMessage : SemgrepRuleLoadErrorMessage(Category.RULE_ISSUE, Severity.BLOCKING)
sealed class RuleIssueNonBlockingMessage : SemgrepRuleLoadErrorMessage(Category.RULE_ISSUE, Severity.NON_BLOCKING)
sealed class UnsupportedFeatureBlockingMessage : SemgrepRuleLoadErrorMessage(Category.UNSUPPORTED_FEATURE, Severity.BLOCKING)
sealed class UnsupportedFeatureNonBlockingMessage : SemgrepRuleLoadErrorMessage(Category.UNSUPPORTED_FEATURE, Severity.NON_BLOCKING)
sealed class InternalWarningBlockingMessage : SemgrepRuleLoadErrorMessage(Category.INTERNAL_WARNING, Severity.BLOCKING)
sealed class InternalWarningNonBlockingMessage : SemgrepRuleLoadErrorMessage(Category.INTERNAL_WARNING, Severity.NON_BLOCKING)

class UnsupportedRuleLanguage(languages: List<String>?) : RuleIssueBlockingMessage() {
    override val message: String = if (languages.isNullOrEmpty())
        "Unsupported rule: no target language specified"
    else
        "Unsupported rule: target language(s) [${languages.joinToString(", ")}] are not supported (only Java rules are processed)"
}

class DuplicateRule(ruleId: String) : RuleIssueBlockingMessage() {
    override val message: String = "Duplicate rule: '$ruleId' is already registered; skipping this definition"
}

class AmbiguousOverride(currentRuleId: String, previousRuleId: String) : RuleIssueNonBlockingMessage() {
    override val message: String = "Ambiguous override: both '$currentRuleId' and '$previousRuleId' declare an override of the same rule; only the first will be applied"
}

class RuleOverridesNothing(targetRuleId: String) : RuleIssueBlockingMessage() {
    override val message: String = "Rule overrides nothing: target rule '$targetRuleId' was not found or not parsed"
}

class JoinRuleWithoutJoinSection : RuleIssueBlockingMessage() {
    override val message: String = "Rule has mode 'join' but is missing the required 'join' section"
}

class UnsupportedMode(mode: String?) : RuleIssueBlockingMessage() {
    override val message: String = "Unsupported rule mode '${mode}': expected one of [search, taint, join]"
}

class EmptyRuleAfterParse : RuleIssueBlockingMessage() {
    override val message: String = "Rule produced no patterns after parsing; the rule will be skipped"
}

class FailedToBuildRuleAutomata(causeMessage: String?) : RuleIssueBlockingMessage() {
    override val message: String = "Failed to build rule automata: ${causeMessage ?: "unknown error"}"
}

class AutomataBuildIssues : InternalWarningNonBlockingMessage() {
    override val message: String = "Automata build completed with non-fatal issues; some patterns may be skipped"
}

class EmptyRuleAfterBuild : RuleIssueBlockingMessage() {
    override val message: String = "Rule produced an empty automata after build; the rule will be skipped"
}

class FailedToCreateTaintRules(causeMessage: String?) : RuleIssueBlockingMessage() {
    override val message: String = "Failed to create taint rules: ${causeMessage ?: "unknown error"}"
}

class RefRuleNotRegistered(ruleId: String) : RuleIssueBlockingMessage() {
    override val message: String = "Referenced rule '$ruleId' was not registered; check that the rule ID is correct and the rule file is loaded"
}

class OverrideLoop : RuleIssueBlockingMessage() {
    override val message: String = "Circular override detected: this rule participates in an override cycle and cannot be resolved"
}

class RefRuleNotLoaded(ruleId: String) : RuleIssueBlockingMessage() {
    override val message: String = "Referenced rule '$ruleId' was registered but failed to build; it cannot be used as a base rule"
}

class IncorrectJoinOnCondition : RuleIssueBlockingMessage() {
    override val message: String = "Malformed 'on' condition in join rule: expected format '<rule>.<var> <op> <rule>.<var>'"
}

class JoinRuleWithoutJoinOn : RuleIssueBlockingMessage() {
    override val message: String = "Join rule is missing the required 'on' field that specifies how results are correlated"
}

class JoinRuleMetavarExpected(metaVarStr: String) : RuleIssueBlockingMessage() {
    override val message: String = "Expected a metavariable (e.g. \$X) in join condition, but got: '$metaVarStr'"
}

class FailedToLoadRuleSetFromYaml(causeMessage: String?) : RuleIssueBlockingMessage() {
    override val message: String = "Failed to load rule set from YAML: ${causeMessage ?: "unknown error"}"
}

class UnknownProperty(propertyName: String) : RuleIssueNonBlockingMessage() {
    override val message: String = "Unknown YAML property '$propertyName'; it will be ignored during parsing"
}

class JoinOnConditionParseFailed(condition: String) : RuleIssueBlockingMessage() {
    override val message: String = "Join 'on' condition parse failed: '$condition'"
}

class UnsupportedPattern : UnsupportedFeatureBlockingMessage() {
    override val message: String = "Rule contains no recognized pattern field (pattern, patterns, pattern-either); the rule will be skipped"
}

class PatternInsideIsNotLeaf(inside: Any) : UnsupportedFeatureBlockingMessage() {
    override val message: String = "'pattern-inside' must be a simple pattern, but got a compound formula: $inside"
}

class NotImplementedNegatedMetavarFocus(metavarName: String) : UnsupportedFeatureBlockingMessage() {
    override val message: String = "Negated 'focus-metavariable: $metavarName' is not yet supported"
}

class NotImplementedMetavarCond(metavarName: String) : UnsupportedFeatureBlockingMessage() {
    override val message: String = "'metavariable-comparison' on '$metavarName' is not yet supported"
}

class NotImplementedComplexMetavarPattern(metavarName: String) : UnsupportedFeatureBlockingMessage() {
    override val message: String = "Complex 'metavariable-pattern' on '$metavarName' is not yet supported; only simple constraints are handled"
}

class NotImplementedRegex(pattern: String) : UnsupportedFeatureBlockingMessage() {
    override val message: String = "'pattern-regex: $pattern' is not yet supported"
}

class RuleHasNoPatterns : RuleIssueNonBlockingMessage() {
    override val message: String = "Rule resolved to no positive patterns after normalization; matching may be incomplete"
}

class UnexpectedComplexPattern(pattern: Any) : UnsupportedFeatureBlockingMessage() {
    override val message: String = "Encountered an unrecognized complex pattern structure that cannot be converted: $pattern"
}

class PatternParsingAstFailed(errors: List<String>) : RuleIssueBlockingMessage() {
    override val message: String = "Pattern could not be parsed into a valid AST:\n${errors.joinToString("\n")}"
}

class PatternParsingFailureWithElement(causeMessage: String?, elementText: String) :
    RuleIssueBlockingMessage() {
    override val message: String = "Pattern parsing failed at element '$elementText': ${causeMessage ?: "unknown error"}"
}

class PatternParsingFailure(causeMessage: String?) : RuleIssueBlockingMessage() {
    override val message: String = "Pattern parsing failed: ${causeMessage ?: "unknown error"}"
}

class FailedTransformationToActionList(causeMessage: String?) : RuleIssueBlockingMessage() {
    override val message: String = "Failed to transform pattern into an action list: ${causeMessage ?: "unknown error"}"
}


class EmptyPatternsAfterConvertToRawRule(times: Int) : InternalWarningNonBlockingMessage() {
    override val message: String = "$times pattern variant(s) were dropped during normalization because they produced no positive patterns"
}

class FailedParseNormalizedRule(times: Int) : InternalWarningNonBlockingMessage() {
    override val message: String = "$times normalized pattern variant(s) failed to parse and were skipped"
}

class FailedResolveMetaVar : InternalWarningNonBlockingMessage() {
    override val message: String = "Failed to resolve a metavariable reference; the affected pattern variant will be skipped"
}

class FailedToConvertToActionList : InternalWarningNonBlockingMessage() {
    override val message: String = "Failed to convert a normalized pattern to an action list; the affected pattern variant will be skipped"
}

class TransformToAutomataFailure(causeMessage: String?) : RuleIssueBlockingMessage() {
    override val message: String = "Failed to transform pattern to automata: ${causeMessage ?: "unknown error"}"
}

class RuleContainsIncompatiblePatterns : RuleIssueNonBlockingMessage() {
    override val message: String = "One or more pattern variants could not be transformed to automata (no accepting state); they will be skipped"
}

class EmptyAcceptingState : InternalWarningNonBlockingMessage() {
    override val message: String = "Automata built with no accepting states; the corresponding pattern variants were dropped"
}

class MetavarConstraintParsingFailure : UnsupportedFeatureNonBlockingMessage() {
    override val message: String = "Failed to parse metavariable constraint; the constraint will be ignored"
}

class TaintAutomataCreationFailure(causeMessage: String?) : InternalWarningBlockingMessage() {
    override val message: String = "Failed to create taint automata: ${causeMessage ?: "unknown error"}"
}

class EmptyAutomataAfterGeneratedEdgeElimination : InternalWarningBlockingMessage() {
    override val message: String = "Automata became empty after eliminating generated edges; the rule will produce no matches"
}

class UnexpectedAnalysisEndEdge : InternalWarningNonBlockingMessage() {
    override val message: String = "Encountered an unexpected end-of-analysis edge in the taint automata; it will be ignored"
}

class LoopVarAssign : InternalWarningNonBlockingMessage() {
    override val message: String = "Detected a loop variable assignment in taint flow; this pattern is not fully supported and may produce imprecise results"
}

class EdgesWithoutPositivePredicate(count: Int) : InternalWarningNonBlockingMessage() {
    override val message: String = "$count automata edge(s) have no positive predicate and were removed; rule coverage may be reduced"
}

class TaintRuleWithoutSources : InternalWarningNonBlockingMessage() {
    override val message: String = "Taint rule has no pattern-sources defined; it will never propagate taint"
}

class TaintRuleMatchAnything : InternalWarningNonBlockingMessage() {
    override val message: String = "Taint rule sink or source matches any expression without constraint; this may cause excessive false positives"
}

class SinkRequiresWithMetaVarIgnored : UnsupportedFeatureNonBlockingMessage() {
    override val message: String = "Sink 'requires' field is not supported and will be ignored; all taint reaching this sink will be reported"
}

class TaintRuleHasNoLabels : InternalWarningNonBlockingMessage() {
    override val message: String = "Taint rule defines no taint labels; sink 'requires' expressions cannot be evaluated and will be treated as unconditional"
}

class JoinRuleWithUnsupportedOperation(operation: Any) : UnsupportedFeatureBlockingMessage() {
    override val message: String = "Join rule uses unsupported operation '$operation'; only simple equality joins are supported"
}

class JoinRuleWithNoOperations : UnsupportedFeatureBlockingMessage() {
    override val message: String = "Join rule defines no join operations; at least one 'on' condition with an operation is required"
}

class JoinRuleWithChainedOperations : UnsupportedFeatureBlockingMessage() {
    override val message: String = "Join rule with chained operations is not supported; only a single join condition is allowed"
}

class JoinRuleWithMultipleDistinctRightItems : UnsupportedFeatureBlockingMessage() {
    override val message: String = "Join rule references multiple distinct right-hand rules; only a single right-hand rule is supported"
}

class JoinOnTaintRuleWithNonEmptySources : RuleIssueBlockingMessage() {
    override val message: String = "The right-hand rule in a join must not define pattern-sources; taint sources are inherited from the left-hand rule"
}

class LeftTaintRuleShouldNotHaveSinks : RuleIssueBlockingMessage() {
    override val message: String = "The left-hand rule in a join must not define pattern-sinks; sinks are taken from the right-hand rule"
}

class LeftTaintRuleMustHaveSources : RuleIssueBlockingMessage() {
    override val message: String = "The left-hand rule in a join must define at least one pattern-source"
}

class ComplexMetavarInJoin : RuleIssueBlockingMessage() {
    override val message: String = "Join condition references a complex metavariable expression; only simple metavariable names are supported in join conditions"
}

class JoinIsImpossibleNoLabelFound(label: String) : RuleIssueBlockingMessage() {
    override val message: String = "Join is impossible: taint label '$label' required by the join condition was not found in the left-hand rule"
}

class FailedToConvertToTaintRule(causeMessage: String?) : InternalWarningBlockingMessage() {
    override val message: String = "Failed to convert automata to a taint rule: ${causeMessage ?: "unknown error"}"
}

class NonMethodCallCleaner : InternalWarningNonBlockingMessage() {
    override val message: String = "Cleaner pattern is not a method call; only method-call cleaners are supported and this cleaner will be ignored"
}

class PlaceholderMethodName : InternalWarningNonBlockingMessage() {
    override val message: String = "Method name could not be resolved and was substituted with a placeholder; matching precision is reduced"
}

class PlaceholderTypeName : InternalWarningNonBlockingMessage() {
    override val message: String = "Type name could not be resolved and was substituted with a placeholder; matching precision is reduced"
}

class PlaceholderAnnotation : InternalWarningNonBlockingMessage() {
    override val message: String = "Annotation could not be resolved and was substituted with a placeholder; matching precision is reduced"
}

class IgnoredMetavarConstraint(metavar: Any) : InternalWarningNonBlockingMessage() {
    override val message: String = "Constraint on metavariable '$metavar' could not be converted and will be ignored; matches may be broader than intended"
}

class PlaceholderStringValue : InternalWarningNonBlockingMessage() {
    override val message: String = "String value could not be resolved and was substituted with a placeholder; matching precision is reduced"
}

sealed class UnsupportedRuleSinkWithRequires(override val message: String) : UnsupportedFeatureNonBlockingMessage() {

    class RequiresMetaVars : UnsupportedRuleSinkWithRequires(
        "Sink 'requires' with metavariable expressions is not yet supported; the requirement will be ignored"
    )

    class UnparsedRequires(requires: String) : UnsupportedRuleSinkWithRequires(
        "Failed to parse sink 'requires' expression: '$requires'"
    )
}
