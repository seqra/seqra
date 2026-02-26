package sarif

import (
	"fmt"
	"path/filepath"
	"sort"
	"strings"

	"charm.land/lipgloss/v2/tree"

	"github.com/seqra/seqra/v2/internal/output"
)

func (report *Report) printFinding(out *output.Printer, result *Result, runIdx int, showCodeSnippets bool, verboseFlow bool, findingIndex int, totalFindings int) {
	absProjectPath, err := report.projectPath(runIdx)
	if err != nil {
		output.LogInfof("Project path lookup failed: %v", err)
		return
	}

	if len(result.Locations) == 0 || result.Locations[0].PhysicalLocation == nil {
		output.LogInfo("No primary location for finding")
		return
	}

	lvl := Level("unknown")
	if result.Level != nil {
		lvl = *result.Level
	} else {
		output.LogInfo("Finding has nil level; defaulting to 'unknown'")
	}
	indicator, indicatorStyle := levelIndicatorStyled(lvl, out.Theme())

	rule := "<unknown>"
	if result.RuleID != nil {
		rule = *result.RuleID
	} else {
		output.LogInfo("Finding has nil ruleId")
	}

	var ruleDesc string
	var cwe []string
	if ruleMeta := report.ruleByID(runIdx, rule); ruleMeta != nil {
		ruleDesc = ruleDescription(*ruleMeta)
		cwe = cweTags(*ruleMeta)
	}
	if len(cwe) > 0 {
		rule += " [" + strings.Join(cwe, ", ") + "]"
	}

	msg := ""
	if result.Message.Text != nil {
		msg = *result.Message.Text
	} else {
		output.LogInfo("Finding has nil message.text")
	}

	showMessage := strings.TrimSpace(msg) != "" && strings.TrimSpace(msg) != strings.TrimSpace(ruleDesc)

	loc := result.Locations[0]
	locStr := printLocStyled(loc.extractNodeLoc(), absProjectPath, out)

	sb := out.Section(fmt.Sprintf("Finding %d/%d %s", findingIndex, totalFindings, indicator)).
		WithStyle(indicatorStyle).
		Field("Rule", rule).
		Field("Location", locStr)

	if showMessage {
		sb.Text(fmt.Sprintf("Message: %s", msg))
	}

	endpoints := findingEndpoints(result)
	if len(endpoints) > 0 {
		sb.Field("Endpoint", endpoints[0])
		for _, endpoint := range endpoints[1:] {
			sb.Text(fmt.Sprintf("Endpoint: %s", endpoint))
		}
	}

	taintFlow, err := classifyTaintFlow(result)
	if err != nil {
		output.LogDebugf("No source/sink: %s", err)

		resultPath := extractAbsolutePath(&loc, absProjectPath, "Result")
		if resultPath != "" && showCodeSnippets {
			snippetLines := out.Snippet().LoadLinesOrEmpty(resultPath, loc.extractNodeLoc().line)
			if len(snippetLines) > 0 {
				snippetItems := make([]any, 0, len(snippetLines))
				for _, line := range snippetLines {
					snippetItems = append(snippetItems, line)
				}
				sb.Group("Snippet", snippetItems...)
			}
		}

		sb.Render()
		return
	}

	// Build code flow sub-tree
	flowTree := tree.Root("Code Flow")

	builder := NewFlowStepBuilder()
	flowSteps := taintFlow
	omitted := 0
	shownSnippets := make(map[string]struct{})
	if !verboseFlow && len(taintFlow) > 2 {
		flowSteps = []classifiedStep{taintFlow[0], taintFlow[len(taintFlow)-1]}
		omitted = len(taintFlow) - 2
	}

	for i, cs := range flowSteps {
		mainLine, locationLine := builder.FormatStep(cs, absProjectPath)
		stepNode := tree.Root(mainLine).Child(locationLine)

		isEdgeStep := i == 0 || i == len(flowSteps)-1
		if !verboseFlow && !isEdgeStep {
			flowTree.Child(stepNode)
			continue
		}

		stepLoc := cs.Step.Location
		locPath := extractAbsolutePath(stepLoc, absProjectPath, "Flow")
		if locPath != "" && showCodeSnippets {
			line := stepLoc.extractNodeLoc().line
			snippetKey := fmt.Sprintf("%s:%d", locPath, line)
			if _, alreadyShown := shownSnippets[snippetKey]; !alreadyShown {
				snippetLines := out.Snippet().LoadLinesOrEmpty(locPath, line)
				if len(snippetLines) > 0 {
					snippetNode := tree.Root("Snippet")
					for _, snippetLine := range snippetLines {
						snippetNode.Child(snippetLine)
					}
					stepNode.Child(snippetNode)
					shownSnippets[snippetKey] = struct{}{}
				}
			}
		}

		flowTree.Child(stepNode)

		if omitted > 0 && i == 0 {
			flowTree.Child(fmt.Sprintf("... %d intermediate steps omitted (use --verbose-flow)", omitted))
		}
	}

	sb.Line().Child(flowTree).Render()
}

func printLocStyled(loc nodeLoc, absProjectPath string, out *output.Printer) string {
	return out.FileLink(absProjectPath, loc.relFilePath, loc.relFilePath, loc.line)
}

func (report *Report) ruleByID(runIdx int, ruleID string) *ReportingDescriptor {
	if ruleID == "" || runIdx < 0 || runIdx >= len(report.Runs) {
		return nil
	}

	run := report.Runs[runIdx]
	for i := range run.Tool.Driver.Rules {
		if run.Tool.Driver.Rules[i].ID == ruleID {
			return &run.Tool.Driver.Rules[i]
		}
	}

	for runIndex := range report.Runs {
		for i := range report.Runs[runIndex].Tool.Driver.Rules {
			if report.Runs[runIndex].Tool.Driver.Rules[i].ID == ruleID {
				return &report.Runs[runIndex].Tool.Driver.Rules[i]
			}
		}
	}

	return nil
}

func findingEndpoints(result *Result) []string {
	if result == nil || len(result.RelatedLocations) == 0 {
		return nil
	}

	seen := make(map[string]struct{})
	endpoints := make([]string, 0)

	for _, related := range result.RelatedLocations {
		for _, logical := range related.LogicalLocations {
			endpoint := ""
			if logical.FullyQualifiedName != nil {
				endpoint = strings.TrimSpace(*logical.FullyQualifiedName)
			}
			if endpoint == "" {
				if logical.Name != nil {
					endpoint = strings.TrimSpace(*logical.Name)
				}
			}
			if endpoint == "" {
				continue
			}

			parts := []string{endpoint}
			method := ""
			if logical.Name != nil {
				method = strings.TrimSpace(*logical.Name)
			}
			if method != "" && method != endpoint {
				parts = append(parts, method)
			}

			tags := []string{}
			if logical.Properties != nil {
				tags = logical.Properties.Tags
			}
			if len(tags) > 0 {
				tagText := strings.Join(tags, ", ")
				parts = append(parts, "params: "+tagText)
			}

			formatted := strings.Join(parts, " | ")
			if _, exists := seen[formatted]; exists {
				continue
			}
			seen[formatted] = struct{}{}
			endpoints = append(endpoints, formatted)
		}
	}

	sort.Strings(endpoints)
	return endpoints
}

// extractAbsolutePath safely extracts the absolute path from a location
func extractAbsolutePath(location *Location, absProjectPath, locationName string) string {
	if location != nil &&
		location.PhysicalLocation != nil &&
		location.PhysicalLocation.ArtifactLocation != nil &&
		location.PhysicalLocation.ArtifactLocation.URI != nil {
		return filepath.Join(absProjectPath, *location.PhysicalLocation.ArtifactLocation.URI)
	}
	output.LogInfof("%s location has no URI; snippet path will be empty", locationName)
	return ""
}

func (report *Report) projectPath(runIdx int) (string, error) {
	run := report.Runs[runIdx]

	if base, ok := run.OriginalURIBaseIDS["%SRCROOT%"]; ok {
		if base.URI == nil {
			output.LogInfo("%SRCROOT% base has nil URI")
			return "", fmt.Errorf("%%SRCROOT%% URI is nil")
		}
		return *base.URI, nil
	}
	return "", fmt.Errorf("%%SRCROOT%% not found in OriginalURIBaseIDS")
}
