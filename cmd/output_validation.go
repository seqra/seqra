package cmd

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"

	"github.com/seqra/seqra/v2/internal/load_trace"
	"github.com/seqra/seqra/v2/internal/sarif"
	"github.com/seqra/seqra/v2/internal/utils/project"
)

func validateProjectModelOutput(outputDir string) (*project.Config, error) {
	outputInfo, err := os.Stat(outputDir)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, fmt.Errorf("output project model directory does not exist: %s", outputDir)
		}
		return nil, fmt.Errorf("failed to access output project model directory %s: %w", outputDir, err)
	}
	if !outputInfo.IsDir() {
		return nil, fmt.Errorf("output project model path is not a directory: %s", outputDir)
	}

	projectModelFile := filepath.Join(outputDir, "project.yaml")
	projectModelInfo, err := os.Stat(projectModelFile)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, fmt.Errorf("project model file does not exist: %s", projectModelFile)
		}
		return nil, fmt.Errorf("failed to access project model file %s: %w", projectModelFile, err)
	}
	if projectModelInfo.IsDir() {
		return nil, fmt.Errorf("project model file path is a directory: %s", projectModelFile)
	}

	config, err := project.LoadConfig(outputDir)
	if err != nil {
		return nil, fmt.Errorf("failed to parse generated project model at %s: %w", projectModelFile, err)
	}

	return config, nil
}

func validateSarifOutput(absSarifPath string) (*sarif.Report, error) {
	info, err := os.Stat(absSarifPath)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, fmt.Errorf("sarif output file does not exist: %s", absSarifPath)
		}
		return nil, fmt.Errorf("failed to access sarif output file %s: %w", absSarifPath, err)
	}
	if info.IsDir() {
		return nil, fmt.Errorf("sarif output path is a directory: %s", absSarifPath)
	}
	if info.Size() == 0 {
		return nil, fmt.Errorf("sarif output file is empty: %s", absSarifPath)
	}

	report := loadSarifReport(absSarifPath)
	if report == nil {
		return nil, fmt.Errorf("failed to parse sarif output file: %s", absSarifPath)
	}
	if len(report.Runs) == 0 {
		return nil, fmt.Errorf("sarif output file has no runs: %s", absSarifPath)
	}

	return report, nil
}

func validateRuleLoadTraceOutput(absSemgrepRuleLoadTracePath string) (*load_trace.SemgrepLoadTrace, error) {
	info, err := os.Stat(absSemgrepRuleLoadTracePath)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, fmt.Errorf("rule load trace file does not exist: %s", absSemgrepRuleLoadTracePath)
		}
		return nil, fmt.Errorf("failed to access rule load trace file %s: %w", absSemgrepRuleLoadTracePath, err)
	}
	if info.IsDir() {
		return nil, fmt.Errorf("rule load trace path is a directory: %s", absSemgrepRuleLoadTracePath)
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
