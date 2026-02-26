package sarif

import (
	"fmt"
	"path/filepath"

	"charm.land/lipgloss/v2/tree"

	"github.com/seqra/seqra/v2/internal/output"
	"github.com/sirupsen/logrus"
)

func (report *Report) printFinding(out *output.Printer, result *Result, runIdx int, showCodeSnippets bool, verboseFlow bool, findingIndex int, totalFindings int) {
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
	indicator, indicatorStyle := levelIndicatorStyled(lvl, out.Theme())

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

	loc := result.Locations[0]
	locStr := printLocStyled(loc.extractNodeLoc(), absProjectPath, out)

	sb := out.Section(fmt.Sprintf("Finding %d/%d %s", findingIndex, totalFindings, indicator)).
		WithStyle(indicatorStyle).
		Field("Rule", rule).
		Text(fmt.Sprintf("Message: %s", msg)).
		Field("Location", locStr)

	taintFlow, err := classifyTaintFlow(result)
	if err != nil {
		logrus.Debugf("No source/sink: %s", err)

		resultPath := extractAbsolutePath(&loc, absProjectPath, "Result")
		if resultPath != "" && showCodeSnippets {
			snippet := out.Snippet().LoadOrEmpty(resultPath, loc.extractNodeLoc().line)
			if snippet != "" {
				sb.Text(snippet)
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
	if !verboseFlow && len(taintFlow) > 2 {
		flowSteps = []classifiedStep{taintFlow[0], taintFlow[len(taintFlow)-1]}
		omitted = len(taintFlow) - 2
	}

	for i, cs := range flowSteps {
		mainLine, locationLine := builder.FormatStep(cs, absProjectPath)
		stepNode := tree.Root(mainLine).Child(locationLine)

		if i != 0 && i != len(flowSteps)-1 {
			flowTree.Child(stepNode)
			continue
		}

		stepLoc := cs.Step.Location
		locPath := extractAbsolutePath(stepLoc, absProjectPath, "Flow")
		if locPath != "" && showCodeSnippets {
			snippet := out.Snippet().LoadOrEmpty(locPath, stepLoc.extractNodeLoc().line)
			if snippet != "" {
				stepNode.Child(snippet)
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
