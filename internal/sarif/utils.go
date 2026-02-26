package sarif

import (
	"fmt"
	"sort"

	"github.com/seqra/seqra/v2/internal/globals"
	"github.com/seqra/seqra/v2/internal/output"
)

// Summary represents a summary of SARIF findings
type Summary struct {
	TotalRulesExecuted  int
	TotalRulesTriggered int
	TotalFindings       int
	FindingsByLevel     map[Level]int
}

// GenerateSummary generates a summary of the SARIF report
func GenerateSummary(report *Report) Summary {
	summary := Summary{
		TotalFindings:       0,
		FindingsByLevel:     make(map[Level]int),
		TotalRulesExecuted:  0,
		TotalRulesTriggered: 0,
	}

	rulesTriggered := make(map[string]bool)
	rulesExecuted := make(map[string]bool)

	for _, run := range report.Runs {
		for _, rule := range run.Tool.Driver.Rules {
			rulesExecuted[rule.ID] = true
		}

		for _, result := range run.Results {
			if result.RuleID != nil {
				rulesTriggered[*result.RuleID] = true
			}

			level := findingLevel(&result)
			summary.FindingsByLevel[level]++
			summary.TotalFindings++
		}
	}

	summary.TotalRulesTriggered += len(rulesTriggered)
	summary.TotalRulesExecuted += len(rulesExecuted)

	return summary
}

type RuleSummary struct {
	RuleID   string
	Total    int
	Errors   int
	Warnings int
	Notes    int
}

func findingLevel(result *Result) Level {
	if result == nil || result.Level == nil || *result.Level == "" {
		return Note
	}
	return *result.Level
}

func generateRuleSummary(report *Report) []RuleSummary {
	byRule := make(map[string]*RuleSummary)

	for _, run := range report.Runs {
		for _, result := range run.Results {
			ruleID := "<unknown>"
			if result.RuleID != nil && *result.RuleID != "" {
				ruleID = *result.RuleID
			}

			rs, ok := byRule[ruleID]
			if !ok {
				rs = &RuleSummary{RuleID: ruleID}
				byRule[ruleID] = rs
			}

			rs.Total++
			switch findingLevel(&result) {
			case Error:
				rs.Errors++
			case Warning:
				rs.Warnings++
			default:
				rs.Notes++
			}
		}
	}

	out := make([]RuleSummary, 0, len(byRule))
	for _, rs := range byRule {
		out = append(out, *rs)
	}

	sort.Slice(out, func(i, j int) bool {
		if out[i].Total == out[j].Total {
			return out[i].RuleID < out[j].RuleID
		}
		return out[i].Total > out[j].Total
	})

	return out
}

func (report *Report) printFindingsOverview(out *output.Printer) {
	ruleSummary := generateRuleSummary(report)
	if len(ruleSummary) == 0 {
		return
	}

	sb := out.Section("Findings Overview")
	for _, item := range ruleSummary {
		sb.Text(fmt.Sprintf("%s: %d findings (errors: %d, warnings: %d, notes: %d)",
			item.RuleID,
			item.Total,
			item.Errors,
			item.Warnings,
			item.Notes,
		))
	}
	sb.Render()
	out.Blank()
}

func reportsGroup(out *output.Printer, absSarifReportPath string) []any {
	return []any{
		out.FieldItem("Log", globals.LogPath),
		out.FieldItem("SARIF", absSarifReportPath),
	}
}

// PrintSummary prints a human-readable summary of the SARIF report
func (report *Report) PrintSummary(out *output.Printer, absSarifReportPath string) {
	summary := GenerateSummary(report)
	th := out.Theme()

	out.Section("Scan Summary").
		Group("Findings",
			out.FieldItem("Total", summary.TotalFindings),
			out.StyledFieldItem("Errors", summary.FindingsByLevel["error"], th.Error),
			out.StyledFieldItem("Warnings", summary.FindingsByLevel["warning"], th.Warning),
			out.FieldItem("Notes", summary.FindingsByLevel["note"]),
		).
		Group("Reports", reportsGroup(out, absSarifReportPath)...).
		Render()
}

func (report *Report) PrintAll(out *output.Printer, showCodeSnippets bool, verboseFlow bool) {
	report.printFindingsOverview(out)

	totalFindings := 0
	for _, run := range report.Runs {
		totalFindings += len(run.Results)
	}

	if totalFindings > 0 {
		out.Section("Findings").Render()
	}

	findingIndex := 0

	for idx, run := range report.Runs {
		for _, result := range run.Results {
			findingIndex++
			report.printFinding(out, &result, idx, showCodeSnippets, verboseFlow, findingIndex, totalFindings)
			out.Blank()
		}
	}
}
