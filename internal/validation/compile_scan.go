package validation

import (
	"fmt"
	"path/filepath"

	"github.com/seqra/seqra/v2/internal/utils"
)

func ValidateCompileInputs(absProjectRoot, absOutputProjectModelPath string) error {
	if err := requireDirExists(absProjectRoot, "project directory"); err != nil {
		return err
	}

	if err := requirePathNotExists(absOutputProjectModelPath, "output directory"); err != nil {
		return err
	}

	if !utils.IsSupportedArch() {
		return fmt.Errorf("unsupported architecture found: %s! only arm64 and amd64 are supported", utils.GetArch())
	}

	return nil
}

func ValidateScanInputs(absUserProjectRoot, absProjectModelPath, absSarifReportPath string, nonBuiltinRulesetPaths, severities []string, maxMemoryValue string, requiresProjectModel bool) (string, error) {
	if err := requireDirExists(absUserProjectRoot, "project path"); err != nil {
		return "", err
	}

	if requiresProjectModel {
		projectModelFile := filepath.Join(absProjectModelPath, "project.yaml")
		if _, err := requireFileExists(projectModelFile, "project model file"); err != nil {
			return "", err
		}
	}

	for _, ruleSetPath := range nonBuiltinRulesetPaths {
		if err := requirePathExists(ruleSetPath, "ruleset path"); err != nil {
			return "", err
		}
	}

	for _, severity := range severities {
		switch severity {
		case "error", "warning", "note":
		default:
			return "", fmt.Errorf(`each "severity" flag should be one of note, warning, or error`)
		}
	}

	if maxMemoryValue == "" {
		return "", nil
	}

	parsedMaxMemory, err := utils.ParseMemoryValue(maxMemoryValue)
	if err != nil {
		return "", fmt.Errorf("invalid max-memory value: %w", err)
	}

	return parsedMaxMemory, nil
}
