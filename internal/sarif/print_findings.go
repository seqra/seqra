package sarif

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/seqra/seqra/v2/internal/utils/formatters"
	"github.com/sirupsen/logrus"
)

// FindingOutputBuilder provides a fluent interface for formatting security findings
type FindingOutputBuilder struct {
	showHeader    bool
	showRule      bool
	showMessage   bool
	showSnippets  bool
	showDataflow  bool
	headerFormat  string
	ruleFormat    string
	messageFormat string
	sourcePrefix  string
	sinkPrefix    string
	defaultLevel  string
}

// NewFindingOutputBuilder creates a new FindingOutputBuilder with default settings
func NewFindingOutputBuilder(showSnippets bool) *FindingOutputBuilder {
	return &FindingOutputBuilder{
		showHeader:    true,
		showRule:      true,
		showMessage:   true,
		showSnippets:  showSnippets,
		showDataflow:  true,
		headerFormat:  "%s",
		ruleFormat:    "Rule: %s",
		messageFormat: "Message: %s",
		sourcePrefix:  "[SOURCE]",
		sinkPrefix:    "[SINK]",
		defaultLevel:  "[UNKNOWN]",
	}
}

// ShowSnippets controls whether to display code snippets
func (fob *FindingOutputBuilder) ShowSnippets(show bool) *FindingOutputBuilder {
	fob.showSnippets = show
	return fob
}

// ShowDataflow controls whether to display dataflow information
func (fob *FindingOutputBuilder) ShowDataflow(show bool) *FindingOutputBuilder {
	fob.showDataflow = show
	return fob
}

// SourcePrefix sets the prefix for source locations
func (fob *FindingOutputBuilder) SourcePrefix(prefix string) *FindingOutputBuilder {
	fob.sourcePrefix = prefix
	return fob
}

// SinkPrefix sets the prefix for sink locations
func (fob *FindingOutputBuilder) SinkPrefix(prefix string) *FindingOutputBuilder {
	fob.sinkPrefix = prefix
	return fob
}

// SnippetBuilder provides a fluent interface for loading and formatting code snippets
type SnippetBuilder struct {
	radius     int64
	showBorder bool
	lineMarker string
	borderChar string
	lineFormat string
}

// NewSnippetBuilder creates a new SnippetBuilder with default settings
func NewSnippetBuilder() *SnippetBuilder {
	return &SnippetBuilder{
		radius:     3,
		showBorder: true,
		lineMarker: ">>",
		borderChar: "—",
		lineFormat: "\t│ %2s %4d %s",
	}
}

// Radius sets the number of lines to show around the target line
func (sb *SnippetBuilder) Radius(radius int64) *SnippetBuilder {
	sb.radius = radius
	return sb
}

// LoadSnippet loads and formats a code snippet
func (sb *SnippetBuilder) LoadSnippet(filePath string, centerLine int64) (string, error) {
	data, err := os.ReadFile(filePath)
	if err != nil {
		return "", err
	}

	lines := strings.Split(string(data), "\n")
	start := centerLine - sb.radius - 1
	end := centerLine + sb.radius - 1

	if start < 0 {
		start = 0
	}
	if end >= int64(len(lines)) {
		end = int64(len(lines) - 1)
	}

	var out strings.Builder

	for i := start; i <= end; i++ {
		marker := "  "
		if i+1 == centerLine {
			marker = sb.lineMarker
		}
		out.WriteString(fmt.Sprintf(sb.lineFormat+"\n", marker, i+1, lines[i]))
	}

	return out.String(), nil
}

func (report *Report) printFinding(result *Result, runIdx int, showCodeSnippets bool) {
	absProjectPath, err := report.projectPath(runIdx)

	if err != nil {
		logrus.Errorf("Project path lookup failed: %v", err)
		return
	}

	if len(result.Locations) == 0 || result.Locations[0].PhysicalLocation == nil {
		logrus.Warn("No primary location for finding")
		return
	}

	lvl := Level("unknown")
	if result.Level != nil {
		lvl = *result.Level
	} else {
		logrus.Warn("Finding has nil level; defaulting to 'unknown'")
	}
	indicator, indicatorColor := levelIndicator(lvl)
	logrus.Info(formatters.FormatTreeHeaderColorized(indicator, indicatorColor))

	rule := "<unknown>"
	if result.RuleID != nil {
		rule = *result.RuleID
	} else {
		logrus.Warn("Finding has nil ruleId")
	}
	msg := ""
	if result.Message.Text != nil {
		msg = *result.Message.Text
	} else {
		logrus.Warn("Finding has nil message.text")
	}

	printer := formatters.NewTreePrinter()
	printer.AddNode(fmt.Sprintf("Rule: %s", rule))
	printer.AddNodeWrapped(fmt.Sprintf("Message: %s", msg))

	loc := result.Locations[0]
	locStr := printLoc(loc.extractNodeLoc(), absProjectPath)
	printer.AddNode(fmt.Sprintf("Location: %s", locStr))

	taintFlow, err := classifyTaintFlow(result)
	if err != nil {
		logrus.Debugf("No source/sink: %s", err)

		snippetBuilder := NewSnippetBuilder()

		resultPath := extractAbsolutePath(&loc, absProjectPath, "Result")

		if resultPath != "" && showCodeSnippets {
			snippet := printSnippetWithBuilder(resultPath, loc.extractNodeLoc(), snippetBuilder)
			if snippet != "" {
				printer.AddNodeAtLevelDefault(snippet, 1)
			}
		}

		printer.Print()
		return
	}

	printer.AddNode("")
	printer.AddNode("Code Flow")

	builder := NewFlowStepBuilder()
	for i, cs := range taintFlow {
		mainLine, locationLine := builder.FormatStep(cs, absProjectPath)
		printer.AddNodeAtLevelWrapped(mainLine, 1)
		printer.AddNodeAtLevelDefault(locationLine, 2)

		if i != 0 && i != len(taintFlow)-1 {
			printer.AddNodeAtLevelDefault("", 1)
			continue
		}

		loc := cs.Step.Location
		locPath := extractAbsolutePath(loc, absProjectPath, "Flow")

		if locPath != "" && showCodeSnippets {
			snippetBuilder := NewSnippetBuilder()
			snippet := printSnippetWithBuilder(locPath, loc.extractNodeLoc(), snippetBuilder)
			if snippet != "" {
				printer.AddNodeAtLevelDefault(snippet, 1)
				continue
			}
		}

		if i == 0 {
			printer.AddNodeAtLevelDefault("", 1)
		}
	}

	printer.Print()
}

func printLoc(loc nodeLoc, absProjectPath string) string {
	link := formatters.FormatFilePathHyperLink(absProjectPath, loc.relFilePath, loc.relFilePath, loc.line)
	return link
}

// extractAbsolutePath safely extracts the absolute path from a location
func extractAbsolutePath(location *Location, absProjectPath, locationName string) string {
	if location != nil &&
		location.PhysicalLocation != nil &&
		location.PhysicalLocation.ArtifactLocation != nil &&
		location.PhysicalLocation.ArtifactLocation.URI != nil {
		return filepath.Join(absProjectPath, *location.PhysicalLocation.ArtifactLocation.URI)
	}
	logrus.Warnf("%s location has no URI; snippet path will be empty", locationName)
	return ""
}

// printSnippetWithBuilder prints a code snippet using the SnippetBuilder
func printSnippetWithBuilder(
	snippetPath string, loc nodeLoc, sb *SnippetBuilder) string {
	snippet, err := sb.LoadSnippet(snippetPath, loc.line)
	if err != nil {
		logrus.Warnf("Failed to load code snippet: %v", err)
		return ""
	}

	if sb.showBorder {
		snippet = "\n" + snippet
	}

	return snippet
}

// printSnippet prints a code snippet (backward compatibility)
// func printSnippet(prefix string, snippetPath string, loc flowNodeLoc, absProjectPath string) {
// 	printSnippetWithBuilder(prefix, snippetPath, loc, absProjectPath, NewSnippetBuilder())
// }

func (report *Report) projectPath(runIdx int) (string, error) {
	run := report.Runs[runIdx]

	if base, ok := run.OriginalURIBaseIDS["%SRCROOT%"]; ok {
		if base.URI == nil {
			logrus.Warn("%SRCROOT% base has nil URI")
			return "", fmt.Errorf("%%SRCROOT%% URI is nil")
		}
		return *base.URI, nil
	}
	return "", fmt.Errorf("%%SRCROOT%% not found in OriginalURIBaseIDS")
}
