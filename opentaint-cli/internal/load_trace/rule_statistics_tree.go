package load_trace

import (
	"fmt"

	"github.com/seqra/opentaint/v2/internal/globals"
	"github.com/seqra/opentaint/v2/internal/output"
	"github.com/seqra/opentaint/v2/internal/sarif"
)

func PrintRuleStatisticsTree(out *output.Printer, ruleLoadErrorsResult *RuleLoadErrorsResult, absSemgrepRuleLoadTracePath string, sarifSummary sarif.Summary) {
	parsingNode := buildRuleParsingIssuesNode(out, ruleLoadErrorsResult, absSemgrepRuleLoadTracePath)
	executionNode := out.GroupItem("Rule execution",
		out.FieldItem("Rules executed", sarifSummary.TotalRulesExecuted),
		out.FieldItem("Rules triggered", sarifSummary.TotalRulesTriggered),
	)

	out.Section("Rule Statistics").
		Child(parsingNode, executionNode).
		Render()
	out.Blank()
}

func buildRuleParsingIssuesNode(out *output.Printer, result *RuleLoadErrorsResult, absSemgrepRuleLoadTracePath string) any {
	if result == nil {
		return out.GroupItem("Rule parsing issues", "No rule parsing data available")
	}

	if result.Error != nil {
		return out.GroupItem("Rule parsing issues",
			out.GroupItem("Unable to retrieve rule load failures info",
				fmt.Sprintf("Error: %s", result.Error)))
	}

	s := result.Summary
	isDebug := globals.Config.Log.Verbosity == "debug"

	var children []any

	if !isDebug {
		if s.TotalAffectedFiles == 0 && s.TotalAffectedRules == 0 {
			children = append(children, "No issues found")
		} else if s.TotalAffectedRules > 0 {
			children = append(children, fmt.Sprintf("%d rules affected", s.TotalAffectedRules))
		} else {
			children = append(children, fmt.Sprintf("%d files affected", s.TotalAffectedFiles))
		}
	} else {
		children = append(children, out.GroupItem("File-level",
			fmt.Sprintf("Files with syntax errors: %d", s.FileErrorTypes[SyntaxError]),
			fmt.Sprintf("Files with unsupported constructs: %d", s.FileErrorTypes[Unsupported]),
			fmt.Sprintf("Total affected files: %d", s.TotalAffectedFiles),
		))
		children = append(children, out.GroupItem("Rule-level",
			fmt.Sprintf("Rules with syntax errors: %d", s.RuleErrorTypes[SyntaxError]),
			fmt.Sprintf("Rules with unsupported constructs: %d", s.RuleErrorTypes[Unsupported]),
			fmt.Sprintf("Total affected rules: %d", s.TotalAffectedRules),
		))
	}

	var detailChildren []any
	if absSemgrepRuleLoadTracePath != "" {
		detailChildren = append(detailChildren, fmt.Sprintf("Rule load trace: %s", absSemgrepRuleLoadTracePath))
	}
	if isDebug || s.FileErrorTypes[Unsupported] > 0 || s.RuleErrorTypes[Unsupported] > 0 {
		detailChildren = append(detailChildren, "Report issues: https://github.com/seqra/opentaint/issues")
	}
	if len(detailChildren) > 0 {
		children = append(children, out.GroupItem("More details", detailChildren...))
	}

	return out.GroupItem("Rule parsing issues", children...)
}
