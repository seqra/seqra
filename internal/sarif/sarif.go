package sarif

import (
	"encoding/json"
	"fmt"
	"os"
	"strings"
	"unicode"

	"github.com/seqra/seqra/internal/utils/semgrep"
	"github.com/sirupsen/logrus"
)

// Report represents a SARIF report
type Report struct {
	Version *string `json:"version"`
	Schema  *string `json:"$schema"`
	Runs    []*Run  `json:"runs"`
}

// Run represents a single run of a static analysis tool
type Run struct {
	Tool               *Tool                       `json:"tool"`
	Results            []*Result                   `json:"results,omitempty"`
	OriginalUriBaseIds map[string]ArtifactLocation `json:"originalUriBaseIds,omitempty"`
}

// Tool contains information about the analysis tool
type Tool struct {
	Driver *Driver `json:"driver"`
}

// Driver contains information about the tool's driver
type Driver struct {
	Name         *string `json:"name"`
	Organization *string `json:"organization"`
	Version      *string `json:"version"`
	Rules        []*Rule `json:"rules,omitempty"`
}

type DefaultConfiguration struct {
	Level string `json:"level"`
}

type FullDescription struct {
	Text string `json:"text"`
}

type ShortDescription struct {
	Text string `json:"text"`
}

type Help struct {
	Text string `json:"text"`
}

type Properties struct {
	Tags []string `json:"tags"`
}

// Rule represents a rule that was run
type Rule struct {
	ID                   *string               `json:"id"`
	Name                 *string               `json:"name,omitempty"`
	DefaultConfiguration *DefaultConfiguration `json:"defaultConfiguration,omitempty"`
	FullDescription      *FullDescription      `json:"fullDescription,omitempty"`
	ShortDescription     *ShortDescription     `json:"shortDescription,omitempty"`
	Properties           *Properties           `json:"properties,omitempty"`
}

// Result represents a single result produced by the tool
type Result struct {
	Level     string      `json:"level"`
	Message   *Message    `json:"message,omitempty"`
	RuleId    string      `json:"ruleId"`
	Locations []*Location `json:"locations,omitempty"`
	CodeFlows []*CodeFlow `json:"codeFlows,omitempty"`
}

// Message contains the text of a result message
type Message struct {
	Text string `json:"text"`
}

// Location represents a location in source code
type Location struct {
	PhysicalLocation *PhysicalLocation  `json:"physicalLocation"`
	LogicalLocations []*LogicalLocation `json:"logicalLocations,omitempty"`
	Message          *Message           `json:"message,omitempty"`
}

// LogicalLocation represents a logical location in the code, such as a function or class
type LogicalLocation struct {
	FullyQualifiedName *string `json:"fullyQualifiedName,omitempty"`
	DecoratedName      *string `json:"decoratedName,omitempty"`
}

// PhysicalLocation specifies the location of a result
type PhysicalLocation struct {
	ArtifactLocation *ArtifactLocation `json:"artifactLocation"`
	Region           *Region           `json:"region,omitempty"`
}

// Region represents a region of an artifact's content
type Region struct {
	StartLine   int  `json:"startLine"`
	StartColumn *int `json:"startColumn,omitempty"`
	EndLine     *int `json:"endLine,omitempty"`
	EndColumn   *int `json:"endColumn,omitempty"`
}

// ArtifactLocation specifies the location of an artifact
type ArtifactLocation struct {
	URI       string  `json:"uri"`
	URIBaseID *string `json:"uriBaseId,omitempty"`
}

// Summary represents a summary of SARIF findings
type Summary struct {
	TotalFindings       int
	TotalRulesRun       int
	TotalRulesTriggered int
	FindingsByLevel     map[string]int
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
		FindingsByLevel:     make(map[string]int),
		TotalRulesRun:       0,
		TotalRulesTriggered: 0,
	}

	rulesTriggered := make(map[string]bool)
	rulesRun := make(map[string]bool)

	for _, run := range report.Runs {
		for _, rule := range run.Tool.Driver.Rules {
			rulesRun[*rule.ID] = true
		}
		summary.TotalFindings += len(run.Results)

		for _, result := range run.Results {
			level := result.Level
			rulesTriggered[result.RuleId] = true
			if level == "" {
				level = "note" // Default level if not specified
			}
			summary.FindingsByLevel[level]++
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

func LogFindings(summary Summary, level string) {
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
	Level     *string
}

func CapitalizeFirst(s string) string {
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
		"%s %s in file: %s\nRule: %s\nMessage: %s",
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

			ruleId := &result.RuleId
			text := &result.Message.Text
			level := &result.Level
			var nextResult PrintableResult
			if len(result.Locations) > 0 && result.Locations[0].PhysicalLocation != nil {
				nextResult = PrintableResult{ruleId, text, &result.Locations[0].PhysicalLocation.ArtifactLocation.URI, level}
				printableResults = append(printableResults, nextResult.toString())
			}
		}
	}

	logrus.Info(strings.Join(printableResults, "\n\n") + "\n")
}

// CodeFlow represents a code flow in the analysis results
type CodeFlow struct {
	ThreadFlows []ThreadFlow `json:"threadFlows"`
}

// ThreadFlow represents a thread flow in the analysis results
type ThreadFlow struct {
	Locations []ThreadFlowLocation `json:"locations"`
}

// ThreadFlowLocation represents a location in a thread flow
type ThreadFlowLocation struct {
	Location       Location `json:"location"`
	ExecutionOrder int      `json:"executionOrder"`
	Index          int      `json:"index"`
	Kinds          []string `json:"kinds"`
}

func updateLocation(location *Location) {
	srcRoot := "%SRCROOT%"
	if location.PhysicalLocation != nil {
		location.PhysicalLocation.ArtifactLocation.URIBaseID = &srcRoot
	} else {
		logrus.Debug("Location doesn't contain PhysicalLocation")
	}
}

// UpdateURIInfo updates URI information in the SARIF report
func (report *Report) UpdateURIInfo(absProjectPath string) {
	for _, run := range report.Runs {

		// Initialize OriginalUriBaseIds if nil
		if run.OriginalUriBaseIds == nil {
			run.OriginalUriBaseIds = make(map[string]ArtifactLocation)
		}

		// Add or update the SRCROOT URI base
		run.OriginalUriBaseIds["%SRCROOT%"] = ArtifactLocation{
			URI: absProjectPath,
		}

		// Update artifact locations in results
		for _, result := range run.Results {

			// Update locations in the main result
			for _, location := range result.Locations {
				updateLocation(location)
			}

			// Update locations in code flows
			for _, codeFlow := range result.CodeFlows {
				for l := range codeFlow.ThreadFlows {
					threadFlow := &codeFlow.ThreadFlows[l]
					for m := range threadFlow.Locations {
						updateLocation(&threadFlow.Locations[m].Location)
					}
				}
			}
		}
	}
}

func (report *Report) UpdateRuleId(absRulesPath, userRulesPath string) {
	ruleStart := semgrep.GetRuleIdPathStart(userRulesPath)
	for _, run := range report.Runs {
		// Update RuleId in results
		for _, result := range run.Results {
			result.RuleId = semgrep.GetSemgrepRuleId(result.RuleId, absRulesPath, ruleStart)
		}
		if run.Tool != nil && run.Tool.Driver != nil {
			for _, rules := range run.Tool.Driver.Rules {
				*rules.ID = semgrep.GetSemgrepRuleId(*rules.ID, absRulesPath, ruleStart)
				*rules.Name = semgrep.GetSemgrepRuleId(*rules.Name, absRulesPath, ruleStart)
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
