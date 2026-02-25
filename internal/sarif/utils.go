package sarif

import (
	"encoding/json"
	"fmt"
	"sort"

	"github.com/seqra/seqra/v2/internal/globals"
	"github.com/seqra/seqra/v2/internal/utils/color"
	"github.com/seqra/seqra/v2/internal/utils/formatters"
	"github.com/sirupsen/logrus"
)

// Summary represents a summary of SARIF findings
type Summary struct {
	TotalRulesExecuted  int
	TotalRulesTriggered int
	TotalFindings       int
	FindingsByLevel     map[Level]int
}

// Parse parses SARIF data using standard json package
func Parse(data []byte) (*Report, error) {
	var report Report
	if err := json.Unmarshal(data, &report); err != nil {
		return nil, fmt.Errorf("failed to parse SARIF: %w", err)
	}
	return &report, nil
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
		return Level("note")
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
			case Level("error"):
				rs.Errors++
			case Level("warning"):
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

func (report *Report) printFindingsOverview() {
	ruleSummary := generateRuleSummary(report)
	if len(ruleSummary) == 0 {
		return
	}

	logrus.Info(formatters.FormatTreeHeader("Findings Overview"))
	printer := formatters.NewTreePrinter()

	for _, item := range ruleSummary {
		printer.AddNodeAtLevelWrapped(
			fmt.Sprintf("%s: %d findings (errors: %d, warnings: %d, notes: %d)",
				item.RuleID,
				item.Total,
				item.Errors,
				item.Warnings,
				item.Notes,
			),
			0,
		)
	}

	printer.Print()
	logrus.Info()
}

func printReportsInfo(printer *formatters.TreePrinter, absSarifReportPath string) {
	printer.AddNode("Reports")
	printer.AddNodeAtLevel(fmt.Sprintf("Log: %s", globals.LogPath), 1, color.Default, false)
	printer.AddNodeAtLevel(fmt.Sprintf("SARIF: %s", absSarifReportPath), 1, color.Default, false)
}

// PrintSummary prints a human-readable summary of the SARIF report
func (report *Report) PrintSummary(absSarifReportPath string) {
	summary := GenerateSummary(report)

	logrus.Info(formatters.FormatTreeHeader("Scan Summary"))
	printer := formatters.NewTreePrinter()

	printer.AddNode("Findings")
	printer.AddNodeAtLevel(fmt.Sprintf("Total: %d", summary.TotalFindings), 1, color.Default, false)
	printer.AddNodeAtLevel(fmt.Sprintf("Errors: %d", summary.FindingsByLevel["error"]), 1, color.Red, false)
	printer.AddNodeAtLevel(fmt.Sprintf("Warnings: %d", summary.FindingsByLevel["warning"]), 1, color.Yellow, false)
	printer.AddNodeAtLevel(fmt.Sprintf("Notes: %d", summary.FindingsByLevel["note"]), 1, color.Default, false)

	printReportsInfo(printer, absSarifReportPath)
	printer.Print()
}

type PrintableResult struct {
	RuleId    *string
	Message   *string
	Locations *string
	Level     *Level
}

func (report *Report) PrintAll(showCodeSnippets bool, verboseFlow bool) {
	report.printFindingsOverview()

	if len(report.Runs) > 0 && len(report.Runs[0].Results) > 0 {
		logrus.Info(formatters.FormatHeader1("Findings"))
	}

	totalFindings := 0
	for _, run := range report.Runs {
		totalFindings += len(run.Results)
	}

	findingIndex := 0

	for idx, run := range report.Runs {
		for _, result := range run.Results {
			findingIndex++
			report.printFinding(&result, idx, showCodeSnippets, verboseFlow, findingIndex, totalFindings)
			logrus.Info()
		}
	}
}
