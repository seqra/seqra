package load_trace

import (
	"github.com/seqra/opentaint/v2/internal/output"
)

func PrintSyntaxErrorReport(out *output.Printer, loadTraceSummary RuleLoadTraceSummary) {
	filesWithErrors := filterFilesWithSyntaxErrors(loadTraceSummary.Files)
	if len(filesWithErrors) == 0 {
		return
	}

	sb := out.Section("Rule Syntax Errors")
	for _, f := range filesWithErrors {
		children := buildFileChildren(out, f)
		if len(children) > 0 {
			sb.Group("File: "+f.Path, children...)
		}
	}
	sb.Render()
	out.Blank()
}

func buildFileChildren(out *output.Printer, file fileSummary) []any {
	fileSyntaxErrors := filterSyntaxErrors(file.Errors)
	rulesWithErrors := filterRulesWithSyntaxErrors(file.Rules)

	if len(fileSyntaxErrors) == 0 && len(rulesWithErrors) == 0 {
		return nil
	}

	th := out.Theme()
	var children []any

	for _, err := range fileSyntaxErrors {
		children = append(children, th.Error.Render("Error "+err.Message))
	}

	for _, rule := range rulesWithErrors {
		ruleChildren := buildRuleChildren(out, rule)
		if len(ruleChildren) > 0 {
			children = append(children, out.GroupItem("Rule: "+rule.RuleID, ruleChildren...))
		}
	}

	return children
}

func buildRuleChildren(out *output.Printer, rule ruleSummary) []any {
	ruleSyntax := filterSyntaxErrors(rule.Errors)
	stepSyntax := collectStepSyntaxErrors(rule.Steps)

	if len(ruleSyntax) == 0 && len(stepSyntax) == 0 {
		return nil
	}

	th := out.Theme()
	allErrors := append(ruleSyntax, stepSyntax...)
	children := make([]any, 0, len(allErrors))
	for _, err := range allErrors {
		children = append(children, th.Error.Render("Error "+err.Message))
	}

	return children
}

func filterFilesWithSyntaxErrors(files []fileSummary) []fileSummary {
	var result []fileSummary
	for _, f := range files {
		if hasSyntaxErrors(f) {
			result = append(result, f)
		}
	}
	return result
}

func hasSyntaxErrors(file fileSummary) bool {
	return len(filterSyntaxErrors(file.Errors)) > 0 || len(filterRulesWithSyntaxErrors(file.Rules)) > 0
}

func filterSyntaxErrors(errs []errorEntry) []errorEntry {
	var result []errorEntry
	for _, err := range errs {
		if err.Type == SyntaxError {
			result = append(result, err)
		}
	}
	return result
}

func collectStepSyntaxErrors(steps []stepSummary) []errorEntry {
	var result []errorEntry
	for _, step := range steps {
		result = append(result, filterSyntaxErrors(step.Errors)...)
	}
	return result
}

func filterRulesWithSyntaxErrors(rules []ruleSummary) []ruleSummary {
	var result []ruleSummary
	for _, r := range rules {
		if len(filterSyntaxErrors(r.Errors)) > 0 || len(collectStepSyntaxErrors(r.Steps)) > 0 {
			result = append(result, r)
		}
	}
	return result
}
