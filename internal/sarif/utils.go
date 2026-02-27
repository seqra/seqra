package sarif

import (
	"fmt"
	"sort"
	"strings"

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
	RuleID      string
	Description string
	CWETags     []string
	Total       int
	Errors      int
	Warnings    int
	Notes       int
}

func findingLevel(result *Result) Level {
	if result == nil || result.Level == nil || *result.Level == "" {
		return Note
	}
	return *result.Level
}

func generateRuleSummary(report *Report) []RuleSummary {
	rulesByID := make(map[string]ReportingDescriptor)
	for _, run := range report.Runs {
		for _, rule := range run.Tool.Driver.Rules {
			if rule.ID == "" {
				continue
			}
			if _, exists := rulesByID[rule.ID]; !exists {
				rulesByID[rule.ID] = rule
			}
		}
	}

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
		if rule, ok := rulesByID[rs.RuleID]; ok {
			rs.Description = ruleDescription(rule)
			rs.CWETags = cweTags(rule)
		}
		out = append(out, *rs)
	}

	sort.Slice(out, func(i, j int) bool {
		if out[i].Errors != out[j].Errors {
			return out[i].Errors > out[j].Errors
		}
		if out[i].Warnings != out[j].Warnings {
			return out[i].Warnings > out[j].Warnings
		}
		if out[i].Notes != out[j].Notes {
			return out[i].Notes > out[j].Notes
		}
		if out[i].Total != out[j].Total {
			return out[i].Total > out[j].Total
		}
		return out[i].RuleID < out[j].RuleID
	})

	return out
}

func ruleDescription(rule ReportingDescriptor) string {
	if rule.ShortDescription != nil && rule.ShortDescription.Text != "" {
		return strings.TrimSpace(rule.ShortDescription.Text)
	}
	if rule.FullDescription != nil && rule.FullDescription.Text != "" {
		return strings.TrimSpace(rule.FullDescription.Text)
	}
	return ""
}

func cweTags(rule ReportingDescriptor) []string {
	if rule.Properties == nil || len(rule.Properties.Tags) == 0 {
		return nil
	}

	var cwes []string
	for _, tag := range rule.Properties.Tags {
		if strings.HasPrefix(strings.ToUpper(tag), "CWE-") {
			cwes = append(cwes, tag)
		}
	}

	sort.Strings(cwes)
	return cwes
}

func findingFiles(report *Report) int {
	files := make(map[string]struct{})

	for _, run := range report.Runs {
		for _, result := range run.Results {
			if len(result.Locations) == 0 {
				continue
			}
			loc := result.Locations[0].extractNodeLoc()
			if loc.relFilePath == "" {
				continue
			}
			files[loc.relFilePath] = struct{}{}
		}
	}

	return len(files)
}

func formatLevelCounts(errors, warnings, notes int) string {
	parts := make([]string, 0, 3)
	if errors > 0 {
		parts = append(parts, fmt.Sprintf("%d errors", errors))
	}
	if warnings > 0 {
		parts = append(parts, fmt.Sprintf("%d warnings", warnings))
	}
	if notes > 0 {
		parts = append(parts, fmt.Sprintf("%d notes", notes))
	}
	if len(parts) == 0 {
		return "0 findings"
	}
	return strings.Join(parts, ", ")
}

// PrintSummary prints a human-readable summary of the SARIF report
func (report *Report) PrintSummary(out *output.Printer, absSarifReportPath string) {
	summary := GenerateSummary(report)
	ruleSummary := generateRuleSummary(report)

	totalLine := formatLevelCounts(
		summary.FindingsByLevel["error"],
		summary.FindingsByLevel["warning"],
		summary.FindingsByLevel["note"],
	)

	var rulesTriggered any
	if summary.TotalRulesTriggered > 0 {
		var ruleNodes []any
		for _, item := range ruleSummary {
			ruleLine := fmt.Sprintf("%s: %s",
				item.RuleID,
				formatLevelCounts(
					item.Errors,
					item.Warnings,
					item.Notes,
				),
			)
			if len(item.CWETags) > 0 {
				ruleLine += " [" + strings.Join(item.CWETags, ", ") + "]"
			}

			if item.Description != "" {
				ruleNodes = append(ruleNodes, out.GroupItem(ruleLine, item.Description))
			} else {
				ruleNodes = append(ruleNodes, ruleLine)
			}
		}
		rulesTriggered = out.GroupItem(out.FieldItem("Rules triggered", summary.TotalRulesTriggered), ruleNodes...)
	} else {
		rulesTriggered = out.FieldItem("Rules triggered", summary.TotalRulesTriggered)
	}

	out.Section("Scan Summary").
		Group("Findings",
			out.FieldItem("Total", totalLine),
			out.FieldItem("Files affected", findingFiles(report)),
			out.FieldItem("Rules executed", summary.TotalRulesExecuted),
			rulesTriggered,
		).
		Group("Output",
			out.FieldItem("Report", absSarifReportPath),
			out.FieldItem("Log", globals.LogPath),
		).
		Render()
}

func (report *Report) PrintAll(out *output.Printer, showCodeSnippets bool, verboseFlow bool) bool {
	totalFindings := 0
	for _, run := range report.Runs {
		totalFindings += len(run.Results)
	}

	if totalFindings == 0 {
		return false
	}

	type findingRef struct {
		runIdx int
		result *Result
		file   string
		line   int64
		order  int
	}

	byFile := make(map[string][]findingRef)
	order := 0

	for runIdx := range report.Runs {
		run := &report.Runs[runIdx]
		for resultIdx := range run.Results {
			order++
			result := &run.Results[resultIdx]
			file := "<unknown>"
			line := int64(-1)
			if len(result.Locations) > 0 {
				loc := result.Locations[0].extractNodeLoc()
				if loc.relFilePath != "" {
					file = loc.relFilePath
				}
				line = loc.line
			}

			byFile[file] = append(byFile[file], findingRef{
				runIdx: runIdx,
				result: result,
				file:   file,
				line:   line,
				order:  order,
			})
		}
	}

	files := make([]string, 0, len(byFile))
	for file := range byFile {
		files = append(files, file)
	}
	sort.Strings(files)

	hasOmitted := false
	for fileIdx, file := range files {
		group := byFile[file]
		sort.Slice(group, func(i, j int) bool {
			if group[i].line != group[j].line {
				li := group[i].line
				lj := group[j].line
				if li < 0 {
					li = 1<<62 - 1
				}
				if lj < 0 {
					lj = 1<<62 - 1
				}
				return li < lj
			}
			return group[i].order < group[j].order
		})

		fileSection := out.Section(fmt.Sprintf("%s [%d]", file, len(group)))
		for findingIdx, finding := range group {
			if findingIdx > 0 {
				fileSection.Line()
			}
			node, omitted := report.buildFindingTree(out, finding.result, finding.runIdx, showCodeSnippets, verboseFlow)
			if node != nil {
				fileSection.Child(node)
			}
			if omitted {
				hasOmitted = true
			}
		}
		fileSection.Render()
		if fileIdx < len(files)-1 {
			out.Blank()
		}
	}

	return hasOmitted
}
