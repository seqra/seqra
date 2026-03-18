package load_trace

import (
	"github.com/seqra/opentaint/internal/output"
)

func PrintSyntaxErrorReport(out *output.Printer, loadTraceSummary RuleLoadTraceSummary) {
	PrintSyntaxErrorCategoryReport(out, loadTraceSummary, SyntaxError, "Rule Syntax Errors")
	PrintSyntaxErrorCategoryReport(out, loadTraceSummary, UnsupportedError, "Rule Unsupported Construction Errors")
	PrintSyntaxErrorCategoryReport(out, loadTraceSummary, SyntaxWarning, "Rule Syntax Warnings")
	PrintSyntaxErrorCategoryReport(out, loadTraceSummary, UnsupportedWarning, "Rule Unsupported Construction Warnings")
}

func PrintSyntaxErrorCategoryReport(out *output.Printer, loadTraceSummary RuleLoadTraceSummary, category errorCategory, title string) {
	filesWithErrors := filterFilesWithSyntaxErrors(loadTraceSummary.Files, category)
	if len(filesWithErrors) == 0 {
		return
	}

	sb := out.Section(title)
	for _, f := range filesWithErrors {
		children := buildFileChildren(out, f, category)
		if len(children) > 0 {
			sb.Group("File: "+f.Path, children...)
		}
	}
	sb.Render()
	out.Blank()
}

func buildFileChildren(out *output.Printer, file fileSummary, category errorCategory) []any {
	fileSyntaxErrors := filterSyntaxErrors(file.Errors, category)
	rulesWithErrors := filterRulesWithSyntaxErrors(file.Rules, category)

	if len(fileSyntaxErrors) == 0 && len(rulesWithErrors) == 0 {
		return nil
	}

	th := out.Theme()
	var children []any

	for _, err := range fileSyntaxErrors {
		children = append(children, th.Error.Render(errorPrefix(category)+err.Message))
	}

	for _, rule := range rulesWithErrors {
		ruleChildren := buildRuleChildren(out, rule, category)
		if len(ruleChildren) > 0 {
			children = append(children, out.GroupItem("Rule: "+rule.RuleID, ruleChildren...))
		}
	}

	return children
}

func buildRuleChildren(out *output.Printer, rule ruleSummary, category errorCategory) []any {
	ruleSyntax := filterSyntaxErrors(rule.Errors, category)
	stepSyntax := collectStepSyntaxErrors(rule.Steps, category)

	if len(ruleSyntax) == 0 && len(stepSyntax) == 0 {
		return nil
	}

	th := out.Theme()
	allErrors := append(ruleSyntax, stepSyntax...)
	children := make([]any, 0, len(allErrors))
	for _, err := range allErrors {
		children = append(children, th.Error.Render(errorPrefix(category)+err.Message))
	}

	return children
}

func errorPrefix(category errorCategory) string {
	switch category {
	case SyntaxWarning, UnsupportedWarning:
		return "Warning "
	case Internal:
		return "Internal "
	default:
		return "Error "
	}
}

func filterFilesWithSyntaxErrors(files []fileSummary, category errorCategory) []fileSummary {
	var result []fileSummary
	for _, f := range files {
		if hasSyntaxErrors(f, category) {
			result = append(result, f)
		}
	}
	return result
}

func hasSyntaxErrors(file fileSummary, category errorCategory) bool {
	return len(filterSyntaxErrors(file.Errors, category)) > 0 || len(filterRulesWithSyntaxErrors(file.Rules, category)) > 0
}

func filterSyntaxErrors(errs []errorEntry, category errorCategory) []errorEntry {
	var result []errorEntry
	for _, err := range errs {
		if err.Type == category {
			result = append(result, err)
		}
	}
	return result
}

func collectStepSyntaxErrors(steps []stepSummary, category errorCategory) []errorEntry {
	var result []errorEntry
	for _, step := range steps {
		result = append(result, filterSyntaxErrors(step.Errors, category)...)
	}
	return result
}

func filterRulesWithSyntaxErrors(rules []ruleSummary, category errorCategory) []ruleSummary {
	var result []ruleSummary
	for _, r := range rules {
		if len(filterSyntaxErrors(r.Errors, category)) > 0 || len(collectStepSyntaxErrors(r.Steps, category)) > 0 {
			result = append(result, r)
		}
	}
	return result
}
