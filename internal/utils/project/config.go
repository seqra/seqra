package project

import (
	"fmt"
	"os"
	"path/filepath"

	"gopkg.in/yaml.v2"
)

type Config struct {
	SourceRoot    string   `yaml:"sourceRoot"`
	JavaToolchain string   `yaml:"javaToolchain,omitempty"`
	Modules       []Module `yaml:"modules"`
	Dependencies  []string `yaml:"dependencies,omitempty"`
}

type Module struct {
	ModuleSourceRoot string   `yaml:"moduleSourceRoot"`
	Packages         []string `yaml:"packages"`
	ModuleClasses    []string `yaml:"moduleClasses"`
}

func LoadConfig(projectModelPath string) (*Config, error) {
	projectYamlPath := filepath.Join(projectModelPath, "project.yaml")
	yamlData, err := os.ReadFile(projectYamlPath)
	if err != nil {
		return nil, fmt.Errorf("failed to read project.yaml: %w", err)
	}

	var config Config
	if err := yaml.Unmarshal(yamlData, &config); err != nil {
		return nil, fmt.Errorf("failed to parse project.yaml: %w", err)
	}

	return &config, nil
}

func SaveConfig(config *Config, outputPath string) error {
	yamlData, err := yaml.Marshal(config)
	if err != nil {
		return fmt.Errorf("failed to marshal YAML: %w", err)
	}

	if err := os.WriteFile(outputPath, yamlData, 0644); err != nil {
		return fmt.Errorf("failed to write project.yaml: %w", err)
	}

	return nil
}

func GetSourceRoot(projectModelPath string) (string, error) {
	config, err := LoadConfig(projectModelPath)
	if err != nil {
		return "", err
	}

	if filepath.IsAbs(config.SourceRoot) {
		return config.SourceRoot, nil
	}

	return filepath.Join(projectModelPath, config.SourceRoot), nil
}
