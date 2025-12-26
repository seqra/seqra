package load_trace

import (
	"github.com/seqra/seqra/v2/internal/utils/color"
	"github.com/seqra/seqra/v2/internal/utils/formatters"
	"github.com/sirupsen/logrus"
)

func PrintSyntaxErrorReport(loadTraceSummary RuleLoadTraceSummary) {
	filesWithErrors := filterFilesWithSyntaxErrors(loadTraceSummary.Files)
	if len(filesWithErrors) == 0 {
		return
	}

	logrus.Info(formatters.FormatHeader1("Rule Syntax Erros"))
	for _, f := range filesWithErrors {
		printFile(f)
	}
}

func printFile(file fileSummary) {
	fileSyntaxErrors := filterSyntaxErrors(file.Errors)
	rulesWithErrors := filterRulesWithSyntaxErrors(file.Rules)

	if len(fileSyntaxErrors) == 0 && len(rulesWithErrors) == 0 {
		return
	}

	logrus.Info("File: " + file.Path)

	printer := formatters.NewTreePrinter()

	for _, err := range fileSyntaxErrors {
		printer.AddNodeAtLevel("Error "+err.Message, 0, color.Red, true)
	}

	for _, rule := range rulesWithErrors {
		addRuleNodes(printer, rule)
	}

	printer.Print()
	logrus.Info()
}

func addRuleNodes(printer *formatters.TreePrinter, rule ruleSummary) {
	ruleSyntax := filterSyntaxErrors(rule.Errors)
	stepSyntax := collectStepSyntaxErrors(rule.Steps)

	if len(ruleSyntax) == 0 && len(stepSyntax) == 0 {
		return
	}

	printer.AddNodeAtLevel("Rule: "+rule.RuleID, 0, "", false)

	allErrors := append(ruleSyntax, stepSyntax...)
	for _, err := range allErrors {
		printer.AddNodeAtLevel("Error "+err.Message, 1, color.Red, true)
	}
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
