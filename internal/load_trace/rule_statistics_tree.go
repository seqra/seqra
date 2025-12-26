package load_trace

import (
	"fmt"

	"github.com/seqra/seqra/v2/internal/globals"
	"github.com/seqra/seqra/v2/internal/sarif"
	"github.com/seqra/seqra/v2/internal/utils/formatters"
	"github.com/sirupsen/logrus"
)

type RuleStatisticsTreeBuilder struct {
	printer                     *formatters.TreePrinter
	ruleLoadErrorsResult        *RuleLoadErrorsResult
	sarifSummary                sarif.Summary
	absSemgrepRuleLoadTracePath string
}

func NewRuleStatisticsTreeBuilder() *RuleStatisticsTreeBuilder {
	return &RuleStatisticsTreeBuilder{
		printer: formatters.NewTreePrinter(),
	}
}

func (b *RuleStatisticsTreeBuilder) WithRuleLoadErrors(result *RuleLoadErrorsResult) *RuleStatisticsTreeBuilder {
	b.ruleLoadErrorsResult = result
	return b
}

func (b *RuleStatisticsTreeBuilder) WithSarifSummary(summary sarif.Summary) *RuleStatisticsTreeBuilder {
	b.sarifSummary = summary
	return b
}

func (b *RuleStatisticsTreeBuilder) WithRuleLoadTracePath(path string) *RuleStatisticsTreeBuilder {
	b.absSemgrepRuleLoadTracePath = path
	return b
}

func (b *RuleStatisticsTreeBuilder) Build() *formatters.TreePrinter {
	b.addRuleParsingIssues()
	b.addRuleExecution()
	return b.printer
}

func (b *RuleStatisticsTreeBuilder) addRuleParsingIssues() {
	b.printer.AddNode("Rule Parsing Issues")

	if b.ruleLoadErrorsResult == nil {
		b.printer.AddNodeAtLevel("No rule parsing data available", 1, "", false)
		return
	}

	if b.ruleLoadErrorsResult.Error != nil {
		b.printer.AddNodeAtLevel("Unable to retrieve rule load failures info", 1, "", false)
		b.printer.AddNodeAtLevel(fmt.Sprintf("Error: %s", b.ruleLoadErrorsResult.Error), 2, "", false)
		return
	}

	s := b.ruleLoadErrorsResult.Summary
	isDebug := globals.Config.Log.Verbosity == "debug"

	if !isDebug && s.TotalAffectedFiles == 0 && s.TotalAffectedRules == 0 {
		b.printer.AddNodeAtLevel("No issues found", 1, "", false)
		return
	}

	b.printer.AddNodeAtLevel("File-level", 1, "", false)
	b.printer.AddNodeAtLevel(fmt.Sprintf("Files with syntax errors: %d", s.FileErrorTypes[SyntaxError]), 2, "", false)
	b.printer.AddNodeAtLevel(fmt.Sprintf("Files with unsupported constructs: %d", s.FileErrorTypes[Unsupported]), 2, "", false)
	b.printer.AddNodeAtLevel(fmt.Sprintf("Total affected files: %d", s.TotalAffectedFiles), 2, "", false)

	b.printer.AddNodeAtLevel("Rule-level", 1, "", false)
	b.printer.AddNodeAtLevel(fmt.Sprintf("Rules with syntax errors: %d", s.RuleErrorTypes[SyntaxError]), 2, "", false)
	b.printer.AddNodeAtLevel(fmt.Sprintf("Rules with unsupported constructs: %d", s.RuleErrorTypes[Unsupported]), 2, "", false)
	b.printer.AddNodeAtLevel(fmt.Sprintf("Total affected rules: %d", s.TotalAffectedRules), 2, "", false)

	if isDebug || s.TotalAffectedFiles > 0 || s.TotalAffectedRules > 0 {
		b.printer.AddNodeAtLevel("More details", 1, "", false)
		if b.absSemgrepRuleLoadTracePath != "" {
			b.printer.AddNodeAtLevel(fmt.Sprintf("See Rule load trace: %s", b.absSemgrepRuleLoadTracePath), 2, "", false)
		}
		if isDebug || s.FileErrorTypes[Unsupported] > 0 || s.RuleErrorTypes[Unsupported] > 0 {
			b.printer.AddNodeAtLevel("Report issues here: https://github.com/seqra/seqra/issues", 2, "", false)
		}
	}
}

func (b *RuleStatisticsTreeBuilder) addRuleExecution() {
	b.printer.AddNode("Rule Execution")
	b.printer.AddNodeAtLevel(fmt.Sprintf("Rules executed: %d", b.sarifSummary.TotalRulesExecuted), 1, "", false)
	b.printer.AddNodeAtLevel(fmt.Sprintf("Rules triggered: %d", b.sarifSummary.TotalRulesTriggered), 1, "", false)
}

func PrintRuleStatisticsTree(ruleLoadErrorsResult *RuleLoadErrorsResult, sarifSummary sarif.Summary, absSemgrepRuleLoadTracePath string) {
	logrus.Info(formatters.FormatTreeHeader("Rule Statistics"))

	printer := formatters.NewTreePrinter()

	// Rule Parsing Issues section
	printer.AddNode("Rule Parsing Issues")

	if ruleLoadErrorsResult == nil {
		printer.AddNodeAtLevel("No rule parsing data available", 1, "", false)
	} else if ruleLoadErrorsResult.Error != nil {
		printer.AddNodeAtLevel("Unable to retrieve rule load failures info", 1, "", false)
		printer.AddNodeAtLevel(fmt.Sprintf("Error: %s", ruleLoadErrorsResult.Error), 2, "", false)
	} else {
		s := ruleLoadErrorsResult.Summary
		isDebug := globals.Config.Log.Verbosity == "debug"

		if !isDebug && s.TotalAffectedFiles == 0 && s.TotalAffectedRules == 0 {
			printer.AddNodeAtLevel("No issues found", 1, "", false)
		} else {
			printer.AddNodeAtLevel("File-level", 1, "", false)
			printer.AddNodeAtLevel(fmt.Sprintf("Files with syntax errors: %d", s.FileErrorTypes[SyntaxError]), 2, "", false)
			printer.AddNodeAtLevel(fmt.Sprintf("Files with unsupported constructs: %d", s.FileErrorTypes[Unsupported]), 2, "", false)
			printer.AddNodeAtLevel(fmt.Sprintf("Total affected files: %d", s.TotalAffectedFiles), 2, "", false)

			printer.AddNodeAtLevel("Rule-level", 1, "", false)
			printer.AddNodeAtLevel(fmt.Sprintf("Rules with syntax errors: %d", s.RuleErrorTypes[SyntaxError]), 2, "", false)
			printer.AddNodeAtLevel(fmt.Sprintf("Rules with unsupported constructs: %d", s.RuleErrorTypes[Unsupported]), 2, "", false)
			printer.AddNodeAtLevel(fmt.Sprintf("Total affected rules: %d", s.TotalAffectedRules), 2, "", false)

			if isDebug || s.TotalAffectedFiles > 0 || s.TotalAffectedRules > 0 {
				printer.AddNodeAtLevel("More details", 1, "", false)
				if absSemgrepRuleLoadTracePath != "" {
					printer.AddNodeAtLevel(fmt.Sprintf("See Rule load trace: %s", absSemgrepRuleLoadTracePath), 2, "", false)
				}
				if isDebug || s.FileErrorTypes[Unsupported] > 0 || s.RuleErrorTypes[Unsupported] > 0 {
					printer.AddNodeAtLevel("Report issues here: https://github.com/seqra/seqra/issues", 2, "", false)
				}
			}
		}
	}

	// Rule Execution section
	printer.AddNode("Rule Execution")
	printer.AddNodeAtLevel(fmt.Sprintf("Rules executed: %d", sarifSummary.TotalRulesExecuted), 1, "", false)
	printer.AddNodeAtLevel(fmt.Sprintf("Rules triggered: %d", sarifSummary.TotalRulesTriggered), 1, "", false)

	printer.Print()
	logrus.Info()
}
