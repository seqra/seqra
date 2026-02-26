package load_trace

import (
	"fmt"

	"charm.land/lipgloss/v2/tree"

	"github.com/seqra/seqra/v2/internal/globals"
	"github.com/seqra/seqra/v2/internal/output"
)

type RuleStatisticsTreeBuilder struct {
	ruleLoadErrorsResult        *RuleLoadErrorsResult
	absSemgrepRuleLoadTracePath string
}

func NewRuleStatisticsTreeBuilder() *RuleStatisticsTreeBuilder {
	return &RuleStatisticsTreeBuilder{}
}

func (b *RuleStatisticsTreeBuilder) WithRuleLoadErrors(result *RuleLoadErrorsResult) *RuleStatisticsTreeBuilder {
	b.ruleLoadErrorsResult = result
	return b
}

func (b *RuleStatisticsTreeBuilder) WithRuleLoadTracePath(path string) *RuleStatisticsTreeBuilder {
	b.absSemgrepRuleLoadTracePath = path
	return b
}

func (b *RuleStatisticsTreeBuilder) Build() []any {
	return []any{b.buildRuleParsingIssues()}
}

func (b *RuleStatisticsTreeBuilder) buildRuleParsingIssues() *tree.Tree {
	node := tree.Root("Rule parsing issues")

	if b.ruleLoadErrorsResult == nil {
		node.Child("No rule parsing data available")
		return node
	}

	if b.ruleLoadErrorsResult.Error != nil {
		errNode := tree.Root("Unable to retrieve rule load failures info").
			Child(fmt.Sprintf("Error: %s", b.ruleLoadErrorsResult.Error))
		node.Child(errNode)
		return node
	}

	s := b.ruleLoadErrorsResult.Summary
	isDebug := globals.Config.Log.Verbosity == "debug"

	if !isDebug {
		if s.TotalAffectedFiles == 0 && s.TotalAffectedRules == 0 {
			node.Child("No issues found")
		} else if s.TotalAffectedRules > 0 {
			node.Child(fmt.Sprintf("%d rules affected", s.TotalAffectedRules))
		} else {
			node.Child(fmt.Sprintf("%d files affected", s.TotalAffectedFiles))
		}
	} else {
		fileLevel := tree.Root("File-level").
			Child(fmt.Sprintf("Files with syntax errors: %d", s.FileErrorTypes[SyntaxError])).
			Child(fmt.Sprintf("Files with unsupported constructs: %d", s.FileErrorTypes[Unsupported])).
			Child(fmt.Sprintf("Total affected files: %d", s.TotalAffectedFiles))
		node.Child(fileLevel)

		ruleLevel := tree.Root("Rule-level").
			Child(fmt.Sprintf("Rules with syntax errors: %d", s.RuleErrorTypes[SyntaxError])).
			Child(fmt.Sprintf("Rules with unsupported constructs: %d", s.RuleErrorTypes[Unsupported])).
			Child(fmt.Sprintf("Total affected rules: %d", s.TotalAffectedRules))
		node.Child(ruleLevel)
	}

	details := tree.Root("More details")
	if b.absSemgrepRuleLoadTracePath != "" {
		details.Child(fmt.Sprintf("Rule load trace: %s", b.absSemgrepRuleLoadTracePath))
	}
	if isDebug || s.FileErrorTypes[Unsupported] > 0 || s.RuleErrorTypes[Unsupported] > 0 {
		details.Child("Report issues: https://github.com/seqra/seqra/issues")
	}
	node.Child(details)

	return node
}

func PrintRuleStatisticsTree(out *output.Printer, ruleLoadErrorsResult *RuleLoadErrorsResult, absSemgrepRuleLoadTracePath string) {
	builder := NewRuleStatisticsTreeBuilder().
		WithRuleLoadErrors(ruleLoadErrorsResult).
		WithRuleLoadTracePath(absSemgrepRuleLoadTracePath)

	nodes := builder.Build()

	out.Section("Rule Statistics").
		Child(nodes...).
		Render()
	out.Blank()
}
