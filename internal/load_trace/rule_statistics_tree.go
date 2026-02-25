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
	b.printer.Push()
	defer b.printer.Pop()

	if b.ruleLoadErrorsResult == nil {
		b.printer.AddNode("No rule parsing data available")
		return
	}

	if b.ruleLoadErrorsResult.Error != nil {
		b.printer.AddNode("Unable to retrieve rule load failures info")
		b.printer.Push()
		b.printer.AddNode(fmt.Sprintf("Error: %s", b.ruleLoadErrorsResult.Error))
		b.printer.Pop()
		return
	}

	s := b.ruleLoadErrorsResult.Summary
	isDebug := globals.Config.Log.Verbosity == "debug"

	if !isDebug && s.TotalAffectedFiles == 0 && s.TotalAffectedRules == 0 {
		b.printer.AddNode("No issues found")
		return
	}

	b.printer.AddNode("File-level")
	b.printer.Push()
	b.printer.AddNode(fmt.Sprintf("Files with syntax errors: %d", s.FileErrorTypes[SyntaxError]))
	b.printer.AddNode(fmt.Sprintf("Files with unsupported constructs: %d", s.FileErrorTypes[Unsupported]))
	b.printer.AddNode(fmt.Sprintf("Total affected files: %d", s.TotalAffectedFiles))
	b.printer.Pop()

	b.printer.AddNode("Rule-level")
	b.printer.Push()
	b.printer.AddNode(fmt.Sprintf("Rules with syntax errors: %d", s.RuleErrorTypes[SyntaxError]))
	b.printer.AddNode(fmt.Sprintf("Rules with unsupported constructs: %d", s.RuleErrorTypes[Unsupported]))
	b.printer.AddNode(fmt.Sprintf("Total affected rules: %d", s.TotalAffectedRules))
	b.printer.Pop()

	if isDebug || s.TotalAffectedFiles > 0 || s.TotalAffectedRules > 0 {
		b.printer.AddNode("More details")
		b.printer.Push()
		if b.absSemgrepRuleLoadTracePath != "" {
			b.printer.AddNode(fmt.Sprintf("See Rule load trace: %s", b.absSemgrepRuleLoadTracePath))
		}
		if isDebug || s.FileErrorTypes[Unsupported] > 0 || s.RuleErrorTypes[Unsupported] > 0 {
			b.printer.AddNode("Report issues here: https://github.com/seqra/seqra/issues")
		}
		b.printer.Pop()
	}
}

func (b *RuleStatisticsTreeBuilder) addRuleExecution() {
	b.printer.AddNode("Rule Execution")
	b.printer.Push()
	b.printer.AddNode(fmt.Sprintf("Rules executed: %d", b.sarifSummary.TotalRulesExecuted))
	b.printer.AddNode(fmt.Sprintf("Rules triggered: %d", b.sarifSummary.TotalRulesTriggered))
	b.printer.Pop()
}

func PrintRuleStatisticsTree(ruleLoadErrorsResult *RuleLoadErrorsResult, sarifSummary sarif.Summary, absSemgrepRuleLoadTracePath string) {
	logrus.Info(formatters.FormatTreeHeader("Rule Statistics"))

	printer := formatters.NewTreePrinter()

	// Rule Parsing Issues section
	printer.AddNode("Rule Parsing Issues")
	printer.Push()

	if ruleLoadErrorsResult == nil {
		printer.AddNode("No rule parsing data available")
	} else if ruleLoadErrorsResult.Error != nil {
		printer.AddNode("Unable to retrieve rule load failures info")
		printer.Push()
		printer.AddNode(fmt.Sprintf("Error: %s", ruleLoadErrorsResult.Error))
		printer.Pop()
	} else {
		s := ruleLoadErrorsResult.Summary
		isDebug := globals.Config.Log.Verbosity == "debug"

		if !isDebug && s.TotalAffectedFiles == 0 && s.TotalAffectedRules == 0 {
			printer.AddNode("No issues found")
		} else {
			printer.AddNode("File-level")
			printer.Push()
			printer.AddNode(fmt.Sprintf("Files with syntax errors: %d", s.FileErrorTypes[SyntaxError]))
			printer.AddNode(fmt.Sprintf("Files with unsupported constructs: %d", s.FileErrorTypes[Unsupported]))
			printer.AddNode(fmt.Sprintf("Total affected files: %d", s.TotalAffectedFiles))
			printer.Pop()

			printer.AddNode("Rule-level")
			printer.Push()
			printer.AddNode(fmt.Sprintf("Rules with syntax errors: %d", s.RuleErrorTypes[SyntaxError]))
			printer.AddNode(fmt.Sprintf("Rules with unsupported constructs: %d", s.RuleErrorTypes[Unsupported]))
			printer.AddNode(fmt.Sprintf("Total affected rules: %d", s.TotalAffectedRules))
			printer.Pop()

			if isDebug || s.TotalAffectedFiles > 0 || s.TotalAffectedRules > 0 {
				printer.AddNode("More details")
				printer.Push()
				if absSemgrepRuleLoadTracePath != "" {
					printer.AddNode(fmt.Sprintf("See Rule load trace: %s", absSemgrepRuleLoadTracePath))
				}
				if isDebug || s.FileErrorTypes[Unsupported] > 0 || s.RuleErrorTypes[Unsupported] > 0 {
					printer.AddNode("Report issues here: https://github.com/seqra/seqra/issues")
				}
				printer.Pop()
			}
		}
	}
	printer.Pop()

	// Rule Execution section
	printer.AddNode("Rule Execution")
	printer.Push()
	printer.AddNode(fmt.Sprintf("Rules executed: %d", sarifSummary.TotalRulesExecuted))
	printer.AddNode(fmt.Sprintf("Rules triggered: %d", sarifSummary.TotalRulesTriggered))
	printer.Pop()

	printer.Print()
	logrus.Info()
}
