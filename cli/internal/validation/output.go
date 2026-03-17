package validation

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"

	"github.com/seqra/opentaint/v2/internal/load_trace"
	"github.com/seqra/opentaint/v2/internal/sarif"
	"github.com/seqra/opentaint/v2/internal/utils/project"
)

func ValidateProjectModelOutput(outputDir string) (*project.Config, error) {
	if err := requireDirExists(outputDir, "output project model directory"); err != nil {
		return nil, err
	}

	projectModelFile := filepath.Join(outputDir, "project.yaml")
	if _, err := requireFileExists(projectModelFile, "project model file"); err != nil {
		return nil, err
	}

	config, err := project.LoadConfig(outputDir)
	if err != nil {
		return nil, fmt.Errorf("failed to parse generated project model at %s: %w", projectModelFile, err)
	}

	return config, nil
}

func ValidateSarifOutput(absSarifPath string) (*sarif.Report, error) {
	info, err := requireFileExists(absSarifPath, "sarif output file")
	if err != nil {
		return nil, err
	}
	if info.Size() == 0 {
		return nil, fmt.Errorf("sarif output file is empty: %s", absSarifPath)
	}

	report, err := sarif.LoadReport(absSarifPath)
	if err != nil {
		return nil, fmt.Errorf("failed to parse sarif output file %s: %w", absSarifPath, err)
	}
	if len(report.Runs) == 0 {
		return nil, fmt.Errorf("sarif output file has no runs: %s", absSarifPath)
	}

	return report, nil
}

func ValidateRuleLoadTraceOutput(absSemgrepRuleLoadTracePath string) (*load_trace.SemgrepLoadTrace, error) {
	if _, err := requireFileExists(absSemgrepRuleLoadTracePath, "rule load trace file"); err != nil {
		return nil, err
	}

	data, err := os.ReadFile(absSemgrepRuleLoadTracePath)
	if err != nil {
		return nil, fmt.Errorf("failed to read rule load trace file %s: %w", absSemgrepRuleLoadTracePath, err)
	}

	var trace load_trace.SemgrepLoadTrace
	if err := json.Unmarshal(data, &trace); err != nil {
		return nil, fmt.Errorf("failed to parse rule load trace file %s: %w", absSemgrepRuleLoadTracePath, err)
	}

	return &trace, nil
}
