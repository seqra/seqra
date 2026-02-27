package load_trace

import (
	"fmt"

	"github.com/seqra/seqra/v2/internal/globals"
	"github.com/seqra/seqra/v2/internal/output"
)

type RuleStatisticsTreeBuilder struct {
	out                         *output.Printer
	ruleLoadErrorsResult        *RuleLoadErrorsResult
	absSemgrepRuleLoadTracePath string
}

func NewRuleStatisticsTreeBuilder(out *output.Printer) *RuleStatisticsTreeBuilder {
	return &RuleStatisticsTreeBuilder{out: out}
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
	return b.buildRuleParsingIssues()
}

func (b *RuleStatisticsTreeBuilder) buildRuleParsingIssues() []any {
	if b.ruleLoadErrorsResult == nil {
		return []any{b.out.GroupItem("Rule parsing issues", "No rule parsing data available")}
	}

	if b.ruleLoadErrorsResult.Error != nil {
		errNode := b.out.GroupItem("Unable to retrieve rule load failures info",
			fmt.Sprintf("Error: %s", b.ruleLoadErrorsResult.Error))
		return []any{b.out.GroupItem("Rule parsing issues", errNode)}
	}

	s := b.ruleLoadErrorsResult.Summary
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
		fileLevel := b.out.GroupItem("File-level",
			fmt.Sprintf("Files with syntax errors: %d", s.FileErrorTypes[SyntaxError]),
			fmt.Sprintf("Files with unsupported constructs: %d", s.FileErrorTypes[Unsupported]),
			fmt.Sprintf("Total affected files: %d", s.TotalAffectedFiles),
		)
		children = append(children, fileLevel)

		ruleLevel := b.out.GroupItem("Rule-level",
			fmt.Sprintf("Rules with syntax errors: %d", s.RuleErrorTypes[SyntaxError]),
			fmt.Sprintf("Rules with unsupported constructs: %d", s.RuleErrorTypes[Unsupported]),
			fmt.Sprintf("Total affected rules: %d", s.TotalAffectedRules),
		)
		children = append(children, ruleLevel)
	}

	var detailChildren []any
	if b.absSemgrepRuleLoadTracePath != "" {
		detailChildren = append(detailChildren, fmt.Sprintf("Rule load trace: %s", b.absSemgrepRuleLoadTracePath))
	}
	if isDebug || s.FileErrorTypes[Unsupported] > 0 || s.RuleErrorTypes[Unsupported] > 0 {
		detailChildren = append(detailChildren, "Report issues: https://github.com/seqra/seqra/issues")
	}
	if len(detailChildren) > 0 {
		children = append(children, b.out.GroupItem("More details", detailChildren...))
	}

	return []any{b.out.GroupItem("Rule parsing issues", children...)}
}

func PrintRuleStatisticsTree(out *output.Printer, ruleLoadErrorsResult *RuleLoadErrorsResult, absSemgrepRuleLoadTracePath string) {
	builder := NewRuleStatisticsTreeBuilder(out).
		WithRuleLoadErrors(ruleLoadErrorsResult).
		WithRuleLoadTracePath(absSemgrepRuleLoadTracePath)

	nodes := builder.Build()

	out.Section("Rule Statistics").
		Child(nodes...).
		Render()
	out.Blank()
}
