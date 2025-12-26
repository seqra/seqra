package sarif

import (
	"fmt"
	"path"
	"sort"
	"strings"

	"github.com/seqra/seqra/v2/internal/utils/color"

	"github.com/sirupsen/logrus"
)

type flowRole string

type classifiedStep struct {
	Step ThreadFlowLocation
}

// FlowStepBuilder provides a fluent interface for formatting dataflow steps
type FlowStepBuilder struct {
	indent     string
	iconStyle  map[flowRole]string
	linkFormat string
}

// NewFlowStepBuilder creates a new FlowStepBuilder with default settings
func NewFlowStepBuilder() *FlowStepBuilder {
	return &FlowStepBuilder{
		indent:     "",
		linkFormat: "        └─%s(%s)",
	}
}

// Indent sets the indentation for flow steps
func (fsb *FlowStepBuilder) Indent(indent string) *FlowStepBuilder {
	fsb.indent = indent
	return fsb
}

// IconStyle sets custom icons for flow roles
func (fsb *FlowStepBuilder) IconStyle(style map[flowRole]string) *FlowStepBuilder {
	fsb.iconStyle = style
	return fsb
}

// LinkFormat sets the format string for location links
func (fsb *FlowStepBuilder) LinkFormat(format string) *FlowStepBuilder {
	fsb.linkFormat = format
	return fsb
}

// FormatStep formats a single dataflow step
func (fsb *FlowStepBuilder) FormatStep(cs classifiedStep, absProjectPath string) (string, string) {
	step := cs.Step
	loc := step.Location.extractNodeLoc()

	msg := ""
	if step.Location != nil && step.Location.Message != nil && step.Location.Message.Text != nil {
		msg = *step.Location.Message.Text
	} else {
		logrus.Warn("ThreadFlowLocation has no message text")
	}

	mainLine := fmt.Sprintf("%s%s", fsb.indent, msg)

	locationLine := printLoc(loc, absProjectPath)

	return mainLine, locationLine
}

// classifyTaintFlow returns ordered taint steps: source → propagation → sink.
func classifyTaintFlow(result *Result) ([]classifiedStep, error) {
	if len(result.CodeFlows) == 0 {
		return nil, fmt.Errorf("result has no codeFlows")
	}

	cf := result.CodeFlows[0]
	if len(cf.ThreadFlows) == 0 {
		return nil, fmt.Errorf("result has codeFlows but no threadFlows")
	}

	tf := cf.ThreadFlows[0]
	if len(tf.Locations) == 0 {
		return nil, fmt.Errorf("threadFlow has no locations")
	}

	steps := tf.Locations

	// Sort by execution order
	sort.Slice(steps, func(i, j int) bool {
		li := getExecutionOrder(steps[i])
		lj := getExecutionOrder(steps[j])
		return li < lj
	})

	var out []classifiedStep
	for _, step := range steps {
		out = append(out, classifiedStep{Step: step})
	}

	return out, nil
}

// getExecutionOrder safely extracts execution order from a step
func getExecutionOrder(step ThreadFlowLocation) int64 {
	if step.ExecutionOrder != nil {
		return *step.ExecutionOrder
	}
	logrus.Warn("Missing executionOrder in taint step; treating as 0")
	return 0
}

type nodeLoc struct {
	relFilePath string
	fileName    string
	method      string
	line        int64
}

func (loc Location) extractNodeLoc() nodeLoc {
	if loc.PhysicalLocation == nil || loc.PhysicalLocation.ArtifactLocation == nil || loc.PhysicalLocation.ArtifactLocation.URI == nil {
		logrus.Warnf("Location has no PhysicalLocation/ArtifactLocation/URI")
		return nodeLoc{}
	}

	// For correct file:/// hyperlinks on Windows
	relFilePath := strings.ReplaceAll(*loc.PhysicalLocation.ArtifactLocation.URI, "\\", "/")
	fileName := path.Base(relFilePath)
	var lineVal int64 = -1
	if loc.PhysicalLocation.Region != nil && loc.PhysicalLocation.Region.StartLine != nil {
		lineVal = *loc.PhysicalLocation.Region.StartLine
	} else {
		logrus.Warnf("Region or StartLine is nil")
	}

	method := ""
	if len(loc.LogicalLocations) == 0 {
		logrus.Warnf("Logical locations is empty, unable to extract method name")
	} else if logicalLoc := loc.LogicalLocations[0]; logicalLoc.FullyQualifiedName != nil {
		method = " " + *logicalLoc.FullyQualifiedName
	}

	return nodeLoc{relFilePath: relFilePath, fileName: fileName, method: method, line: lineVal}
}

// levelIndicator returns a text-based indicator for the given level
func levelIndicator(level Level) (string, color.Color) {
	switch strings.ToLower(string(level)) {
	case "error":
		return "[ERROR]", color.Red
	case "warning":
		return "[WARNING]", color.Yellow
	case "note":
		return "[NOTE]", color.Default
	default:
		return "[UNKNOWN]", color.Default
	}
}
