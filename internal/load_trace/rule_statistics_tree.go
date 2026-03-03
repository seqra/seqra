package load_trace

import (
	"fmt"

	"github.com/seqra/seqra/v2/internal/globals"
	"github.com/seqra/seqra/v2/internal/output"
	"github.com/seqra/seqra/v2/internal/sarif"
)

func PrintRuleStatisticsTree(out *output.Printer, ruleLoadErrorsResult *RuleLoadErrorsResult, absSemgrepRuleLoadTracePath string, sarifSummary sarif.Summary) {
	parsingNode := buildRuleParsingIssuesNode(out, ruleLoadErrorsResult, absSemgrepRuleLoadTracePath)

	out.Section("Rule Statistics").
		Child(parsingNode).
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

	if !isDebug && s.TotalAffectedFiles == 0 && s.TotalAffectedRules == 0 {
		children = append(children, "No issues found")
	} else {
		ruleLevel := out.GroupItem("Rule-level",
			fmt.Sprintf("Rules with syntax issues: %d", s.RuleErrorTypes[SyntaxError]+s.RuleErrorTypes[SyntaxWarning]),
			fmt.Sprintf("Rules with unsupported constructs: %d", s.RuleErrorTypes[UnsupportedError]+s.RuleErrorTypes[UnsupportedWarning]),
		)
		if s.RuleErrorTypes[Internal] > 0 {
			ruleLevel.Child(fmt.Sprintf("Rules with internal issues: %d", s.RuleErrorTypes[Internal]))
		}
		ruleLevel.Child(fmt.Sprintf("Total affected rules: %d", s.TotalAffectedRules))
		children = append(children, ruleLevel)
	}

	if isDebug || s.TotalAffectedFiles > 0 || s.TotalAffectedRules > 0 {
		var detailChildren []any
		if absSemgrepRuleLoadTracePath != "" {
			detailChildren = append(detailChildren, fmt.Sprintf("See Rule load trace: %s", absSemgrepRuleLoadTracePath))
		}
		if isDebug || s.FileErrorTypes[Internal] > 0 || s.RuleErrorTypes[Internal] > 0 {
			detailChildren = append(detailChildren, "Report issues here: https://github.com/seqra/seqra/issues")
		}
		if len(detailChildren) > 0 {
			children = append(children, out.GroupItem("More details", detailChildren...))
		}
	}

	return out.GroupItem("Rule parsing issues", children...)
}
