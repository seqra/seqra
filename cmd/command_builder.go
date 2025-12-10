package cmd

import (
	"fmt"
	"strings"
	"time"
)

// Default values for command flags
const (
	defaultCompileType = "docker"
	defaultScanType    = "docker"
	defaultTimeout     = 900 * time.Second
)

// CommandBuilder provides a fluent API for building seqra CLI commands.
// It handles common flag logic and ensures consistent command formatting.
type CommandBuilder struct {
	command   string
	args      []string
	flags     map[string]string
	boolFlags map[string]bool
}

// NewCompileCommand creates a new CommandBuilder for the compile command.
func NewCompileCommand(projectPath string) *CommandBuilder {
	return &CommandBuilder{
		command:   "seqra compile",
		args:      []string{projectPath},
		flags:     make(map[string]string),
		boolFlags: make(map[string]bool),
	}
}

// NewScanCommand creates a new CommandBuilder for the scan command.
func NewScanCommand(projectPath string) *CommandBuilder {
	return &CommandBuilder{
		command:   "seqra scan",
		args:      []string{projectPath},
		flags:     make(map[string]string),
		boolFlags: make(map[string]bool),
	}
}

// WithOutput sets the output path flag.
func (cb *CommandBuilder) WithOutput(path string) *CommandBuilder {
	if path != "" {
		cb.flags["output"] = path
	}
	return cb
}

// WithCompileType sets the compile-type flag.
func (cb *CommandBuilder) WithCompileType(compileType string) *CommandBuilder {
	if compileType != "" && compileType != defaultCompileType {
		cb.flags["compile-type"] = compileType
	}
	return cb
}

// WithScanType sets the scan-type flag if it differs from the default.
func (cb *CommandBuilder) WithScanType(scanType string) *CommandBuilder {
	if scanType != "" && scanType != defaultScanType {
		cb.flags["scan-type"] = scanType
	}
	return cb
}

// WithTimeout sets the timeout flag if it differs from the default.
func (cb *CommandBuilder) WithTimeout(timeout time.Duration) *CommandBuilder {
	if timeout != defaultTimeout {
		cb.flags["timeout"] = timeout.String()
	}
	return cb
}

// WithRuleset sets the ruleset path flag.
func (cb *CommandBuilder) WithRuleset(path string) *CommandBuilder {
	if path != "" {
		cb.flags["ruleset"] = path
	}
	return cb
}

// WithRulesetLoadErrors sets the ruleset-load-errors path flag.
func (cb *CommandBuilder) WithRulesetLoadErrors(path string) *CommandBuilder {
	if path != "" {
		cb.flags["ruleset-load-errors"] = path
	}
	return cb
}

// WithSemgrepCompatibility sets the semgrep-compatibility-sarif flag.
func (cb *CommandBuilder) WithSemgrepCompatibility(enabled bool) *CommandBuilder {
	if !enabled {
		cb.boolFlags["semgrep-compatibility-sarif"] = false
	}
	return cb
}

// Build constructs the final command string.
func (cb *CommandBuilder) Build() string {
	parts := []string{cb.command}
	parts = append(parts, cb.args...)

	// Add regular flags
	for flag, value := range cb.flags {
		parts = append(parts, fmt.Sprintf("--%s", flag), value)
	}

	// Add boolean flags
	for flag, value := range cb.boolFlags {
		parts = append(parts, fmt.Sprintf("--%s=%t", flag, value))
	}

	return strings.Join(parts, " ")
}

// buildCompileCommandWithDocker builds a compile command string with --compile-type docker
// preserving all other options from the original command.
func buildCompileCommandWithDocker(projectPath, outputPath string) string {
	return NewCompileCommand(projectPath).
		WithOutput(outputPath).
		WithCompileType("docker").
		Build()
}

// buildScanCommandWithDocker builds a scan command string with --compile-type docker
// preserving all other options from the original command.
func buildScanCommandWithDocker(projectPath, sarifReportPath, rulesetPath, rulesetLoadErrorsPath string,
	timeout time.Duration, semgrepCompatibility bool, scanType string) string {
	return NewScanCommand(projectPath).
		WithOutput(sarifReportPath).
		WithTimeout(timeout).
		WithRuleset(rulesetPath).
		WithRulesetLoadErrors(rulesetLoadErrorsPath).
		WithSemgrepCompatibility(semgrepCompatibility).
		WithScanType(scanType).
		WithCompileType("docker").
		Build()
}

// buildScanCommandFromCompile builds a scan command string for use after compile,
// suggesting the next step with the compiled project model.
func buildScanCommandFromCompile(projectPath, projectModelPath string) string {
	// Suggest output path based on project path
	outputPath := fmt.Sprintf("%s.sarif", projectPath)

	return NewScanCommand(projectModelPath).
		WithOutput(outputPath).
		Build()
}
