package project

import (
	"fmt"
	"os"
	"path/filepath"

	"gopkg.in/yaml.v2"
)

// WriteGoProjectModel creates an OpenTaint project model for a Go module or workspace.
func WriteGoProjectModel(projectRoot, outputDir string) error {
	config := Config{
		ProjectRoot: projectRoot,
		GoProjects: []GoProject{
			{ProjectDir: projectRoot},
		},
	}
	data, err := yaml.Marshal(config)
	if err != nil {
		return fmt.Errorf("encode Go project model: %w", err)
	}
	if err := os.MkdirAll(outputDir, 0o755); err != nil {
		return fmt.Errorf("create Go project model directory: %w", err)
	}
	if err := os.WriteFile(filepath.Join(outputDir, "project.yaml"), data, 0o644); err != nil {
		return fmt.Errorf("write Go project model: %w", err)
	}
	return nil
}
