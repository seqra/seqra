package load_trace

import (
	"charm.land/lipgloss/v2/tree"

	"github.com/seqra/seqra/v2/internal/output"
)

func PrintSyntaxErrorReport(out *output.Printer, loadTraceSummary RuleLoadTraceSummary) {
	filesWithErrors := filterFilesWithSyntaxErrors(loadTraceSummary.Files)
	if len(filesWithErrors) == 0 {
		return
	}

	sb := out.Section("Rule Syntax Errors")
	for _, f := range filesWithErrors {
		fileNode := buildFileNode(out, f)
		if fileNode != nil {
			sb.Child(fileNode)
		}
	}
	sb.Render()
}

func buildFileNode(out *output.Printer, file fileSummary) *tree.Tree {
	fileSyntaxErrors := filterSyntaxErrors(file.Errors)
	rulesWithErrors := filterRulesWithSyntaxErrors(file.Rules)

	if len(fileSyntaxErrors) == 0 && len(rulesWithErrors) == 0 {
		return nil
	}

	th := out.Theme()
	node := tree.Root("File: " + file.Path)

	for _, err := range fileSyntaxErrors {
		node.Child(th.Error.Render("Error " + err.Message))
	}

	for _, rule := range rulesWithErrors {
		ruleNode := buildRuleNode(out, rule)
		if ruleNode != nil {
			node.Child(ruleNode)
		}
	}

	return node
}

func buildRuleNode(out *output.Printer, rule ruleSummary) *tree.Tree {
	ruleSyntax := filterSyntaxErrors(rule.Errors)
	stepSyntax := collectStepSyntaxErrors(rule.Steps)

	if len(ruleSyntax) == 0 && len(stepSyntax) == 0 {
		return nil
	}

	th := out.Theme()
	node := tree.Root("Rule: " + rule.RuleID)

	allErrors := append(ruleSyntax, stepSyntax...)
	for _, err := range allErrors {
		node.Child(th.Error.Render("Error " + err.Message))
	}

	return node
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
