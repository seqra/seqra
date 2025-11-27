package sarif

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"unicode"

	"github.com/seqra/seqra/internal/globals"
	"github.com/seqra/seqra/internal/utils/semgrep"
	"github.com/seqra/seqra/internal/version"
	"github.com/sirupsen/logrus"
)

// isJavaFile checks if the given URI represents a Java file
func isJavaFile(uri *string) bool {
	return filepath.Ext(*uri) == ".java"
}

// isKotlinkFile checks if the given URI represents a Kotlin file
func isKotlinFile(uri *string) bool {
	return filepath.Ext(*uri) == ".kt"
}

func updateLocation(location *Location) {
	srcRoot := "%SRCROOT%"
	if location.PhysicalLocation != nil {
		location.PhysicalLocation.ArtifactLocation.URIBaseID = &srcRoot
	} else {
		logrus.Debug("Location doesn't contain PhysicalLocation")
	}
}

func (report *Report) SetToolDriver() {
	for iRun := range report.Runs {
		run := &report.Runs[iRun]
		run.Tool.Driver.Name = "Seqra"
		localVersion := globals.Config.Analyzer.Version
		run.Tool.Driver.Version = &localVersion

		localSemanticVersion := version.GetVersion()
		run.Tool.Driver.SemanticVersion = &localSemanticVersion
	}
}

func (report *Report) KeepOnlyFileLocations() {
	for iRun := range report.Runs {
		run := &report.Runs[iRun]
		// Update artifact locations in results
		for jResult := range run.Results {
			result := &run.Results[jResult]

			// Update locations in the main result
			var filteredLocations []Location
			for _, location := range result.Locations {
				uri := location.PhysicalLocation.ArtifactLocation.URI
				if location.PhysicalLocation != nil && (isJavaFile(uri) || isKotlinFile(uri)) {
					filteredLocations = append(filteredLocations, location)
				}
			}
			result.Locations = filteredLocations

			// Update locations in code flows
			for _, codeFlow := range result.CodeFlows {
				for l := range codeFlow.ThreadFlows {
					threadFlow := &codeFlow.ThreadFlows[l]
					var filteredThreadFlowLocations []ThreadFlowLocation
					for m := range threadFlow.Locations {
						location := threadFlow.Locations[m].Location
						uri := location.PhysicalLocation.ArtifactLocation.URI
						if location.PhysicalLocation != nil && (isJavaFile(uri) || isKotlinFile(uri)) {
							filteredThreadFlowLocations = append(filteredThreadFlowLocations, threadFlow.Locations[m])
						}
					}
					threadFlow.Locations = filteredThreadFlowLocations
				}
			}
		}
	}
}

// UpdateURIInfo updates URI information in the SARIF report
func (report *Report) UpdateURIInfo(absProjectPath string) {
	for iRun := range report.Runs {
		run := &report.Runs[iRun]

		// Initialize OriginalUriBaseIds if nil
		if run.OriginalURIBaseIDS == nil {
			run.OriginalURIBaseIDS = make(map[string]ArtifactLocation)
		}

		// Add or update the SRCROOT URI base
		run.OriginalURIBaseIDS["%SRCROOT%"] = ArtifactLocation{
			URI: &absProjectPath,
		}

		// Update artifact locations in results
		for jResult := range run.Results {
			result := &run.Results[jResult]

			// Update locations in the main result
			for _, location := range result.Locations {
				updateLocation(&location)
			}

			// Update locations in code flows
			for _, codeFlow := range result.CodeFlows {
				for l := range codeFlow.ThreadFlows {
					threadFlow := &codeFlow.ThreadFlows[l]
					for m := range threadFlow.Locations {
						updateLocation(threadFlow.Locations[m].Location)
					}
				}
			}
		}
	}
}

func (report *Report) UpdateRuleId(absRulesPath, userRulesPath string) {
	ruleStart := semgrep.GetRuleIdPathStart(userRulesPath)
	for iRun := range report.Runs {
		run := &report.Runs[iRun]
		// Update RuleId in results
		for jResult := range run.Results {
			result := &run.Results[jResult]
			*result.RuleID = semgrep.GetSemgrepRuleId(*result.RuleID, absRulesPath, ruleStart)
		}
		for jRules := range run.Tool.Driver.Rules {
			rules := &run.Tool.Driver.Rules[jRules]
			rules.ID = semgrep.GetSemgrepRuleId(rules.ID, absRulesPath, ruleStart)
			*rules.Name = semgrep.GetSemgrepRuleId(*rules.Name, absRulesPath, ruleStart)
		}
	}
}

// KeepOnlyOneCodeFlowElement keeps only the first thread flow in each code flows
func (report *Report) KeepOnlyOneCodeFlowElement() {
	for iRun := range report.Runs {
		run := &report.Runs[iRun]
		for jResult := range run.Results {
			result := &run.Results[jResult]
			for kCodeFlow := range result.CodeFlows {
				codeFlow := &result.CodeFlows[kCodeFlow]
				if len(codeFlow.ThreadFlows) > 0 {
					codeFlow.ThreadFlows = codeFlow.ThreadFlows[:1]
				}
			}
		}
	}
}

// WriteFile writes the SARIF report to a file
func WriteFile(report *Report, filename string) error {
	file, err := os.Create(filename)
	if err != nil {
		return fmt.Errorf("failed to create file: %w", err)
	}
	defer func() {
		_ = file.Close()
	}()

	enc := json.NewEncoder(file)
	enc.SetIndent("", "  ")
	enc.SetEscapeHTML(false)
	if err := enc.Encode(report); err != nil {
		return fmt.Errorf("failed to encode SARIF: %w", err)
	}
	return nil
}

// Summary represents a summary of SARIF findings
type Summary struct {
	TotalFindings       int
	TotalRulesRun       int
	TotalRulesTriggered int
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
		FindingsByLevel:     make(map[Level]int),
		TotalRulesRun:       0,
		TotalRulesTriggered: 0,
	}

	rulesTriggered := make(map[string]bool)
	rulesRun := make(map[string]bool)

	for _, run := range report.Runs {
		for _, rule := range run.Tool.Driver.Rules {
			rulesRun[rule.ID] = true
		}

		for _, result := range run.Results {
			level := result.Level
			rulesTriggered[*result.RuleID] = true
			if *level == "" {
				*level = "note" // Default level if not specified
			}
			summary.FindingsByLevel[*level]++
		}
	}

	summary.TotalRulesTriggered += len(rulesTriggered)
	summary.TotalRulesRun += len(rulesRun)

	return summary
}

// PrintSummary prints a human-readable summary of the SARIF report
func (report *Report) PrintSummary() {
	summary := GenerateSummary(report)

	logrus.Info("=== Scan Results Summary ===")
	logrus.Infof("Total findings: %d", summary.TotalFindings)
	logrus.Infof("Total rules run: %d", summary.TotalRulesRun)
	logrus.Infof("Total rules triggered: %d", summary.TotalRulesTriggered)

	if len(summary.FindingsByLevel) > 0 {
		logrus.Info("Findings by severity:")
		LogFindings(summary, "error")
		LogFindings(summary, "warning")
		LogFindings(summary, "note")
	}
	logrus.Info()
}

func LogFindings(summary Summary, level Level) {
	count, val := summary.FindingsByLevel[level]
	if val {
		logrus.Infof("  %s: %d", level, count)
	} else {
		logrus.Infof("  %s: %d", level, 0)
	}
}

type PrintableResult struct {
	RuleId    *string
	Message   *string
	Locations *string
	Level     *Level
}

func CapitalizeFirst(s Level) string {
	if s == "" {
		return ""
	}

	r := []rune(s)
	// Check if the first character is a letter before capitalizing.
	if unicode.IsLetter(r[0]) {
		r[0] = unicode.ToUpper(r[0])
	}
	return string(r)
}

func (printableResult *PrintableResult) toString() string {
	emoji := ""
	switch *printableResult.Level {
	case "error":
		emoji = "🚩"
	case "warning":
		emoji = "⚠️"
	case "note":
		emoji = "💡"
	default:
		emoji = "❓"
	}
	return fmt.Sprintf(
		"%s %s in file: %s\n   Rule: %s\n   Message: %s",
		emoji,
		CapitalizeFirst(*printableResult.Level),
		*printableResult.Locations,
		*printableResult.RuleId, strings.ReplaceAll(*printableResult.Message, "\n", "\n\t"),
	)
}

func (report *Report) PrintAll() {
	var printableResults []string
	for _, run := range report.Runs {
		for _, result := range run.Results {

			ruleId := result.RuleID
			text := result.Message.Text
			level := result.Level
			var nextResult PrintableResult
			if len(result.Locations) > 0 && result.Locations[0].PhysicalLocation != nil {
				nextResult = PrintableResult{ruleId, text, result.Locations[0].PhysicalLocation.ArtifactLocation.URI, level}
				printableResults = append(printableResults, nextResult.toString())
			}
		}
	}

	logrus.Info(strings.Join(printableResults, "\n\n") + "\n")
}
