package sarif

import (
	"fmt"
	"path"
	"sort"
	"strings"

	"github.com/seqra/opentaint/v2/internal/output"
)

type classifiedStep struct {
	Step ThreadFlowLocation
}

// formatFlowStep formats a single dataflow step into a summary line and a location line.
func formatFlowStep(cs classifiedStep, absProjectPath string) (string, string) {
	step := cs.Step
	loc := step.Location.extractNodeLoc()

	msg := ""
	if step.Location != nil && step.Location.Message != nil && step.Location.Message.Text != nil {
		msg = strings.Join(strings.Fields(*step.Location.Message.Text), " ")
	} else {
		output.LogInfo("ThreadFlowLocation has no message text")
	}

	mainLine := msg

	// Format location as plain text (hyperlinks handled by caller if needed)
	locationLine := fmt.Sprintf("%s:%d", loc.relFilePath, loc.line)

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
	output.LogInfo("Missing executionOrder in taint step; treating as 0")
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
		output.LogInfo("Location has no PhysicalLocation/ArtifactLocation/URI")
		return nodeLoc{}
	}

	// For correct file:/// hyperlinks on Windows
	relFilePath := strings.ReplaceAll(*loc.PhysicalLocation.ArtifactLocation.URI, "\\", "/")
	fileName := path.Base(relFilePath)
	var lineVal int64 = -1
	if loc.PhysicalLocation.Region != nil && loc.PhysicalLocation.Region.StartLine != nil {
		lineVal = *loc.PhysicalLocation.Region.StartLine
	} else {
		output.LogInfo("Region or StartLine is nil")
	}

	method := ""
	if len(loc.LogicalLocations) == 0 {
		output.LogInfo("Logical locations is empty, unable to extract method name")
	} else if logicalLoc := loc.LogicalLocations[0]; logicalLoc.FullyQualifiedName != nil {
		method = " " + *logicalLoc.FullyQualifiedName
	}

	return nodeLoc{relFilePath: relFilePath, fileName: fileName, method: method, line: lineVal}
}


