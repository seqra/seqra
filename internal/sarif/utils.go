package sarif

import (
	"encoding/json"
	"fmt"

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
			level := result.Level
			rulesTriggered[*result.RuleID] = true
			if *level == "" {
				*level = "note" // Default level if not specified
			}
			summary.FindingsByLevel[*level]++
			summary.TotalFindings++
		}
	}

	summary.TotalRulesTriggered += len(rulesTriggered)
	summary.TotalRulesExecuted += len(rulesExecuted)

	return summary
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

func (report *Report) PrintAll(showCodeSnippets bool) {
	if len(report.Runs) > 0 && len(report.Runs[0].Results) > 0 {
		logrus.Info(formatters.FormatHeader1("Findings"))
	}

	for idx, run := range report.Runs {
		for _, result := range run.Results {
			report.printFinding(&result, idx, showCodeSnippets)
			logrus.Info()
		}
	}
}
