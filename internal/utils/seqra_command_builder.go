package utils

import (
	"fmt"
	"strings"
	"time"
)

// Default values for command flags
const (
	defaultCompileType = "native"
	defaultScanType    = "native"
	defaultTimeout     = 900 * time.Second
)

// SeqraCommandBuilder provides a fluent API for building seqra CLI commands.
// It handles common flag logic and ensures consistent command formatting.
type SeqraCommandBuilder struct {
	command    string
	args       []string
	flags      map[string]string
	arrayFlags map[string][]string
	boolFlags  map[string]bool
}

// NewCompileCommand creates a new CommandBuilder for the compile command.
func NewCompileCommand(projectPath string) *SeqraCommandBuilder {
	return &SeqraCommandBuilder{
		command:    "seqra compile",
		args:       []string{projectPath},
		flags:      make(map[string]string),
		arrayFlags: make(map[string][]string),
		boolFlags:  make(map[string]bool),
	}
}

// NewScanCommand creates a new CommandBuilder for the scan command.
func NewScanCommand(projectPath string) *SeqraCommandBuilder {
	return &SeqraCommandBuilder{
		command:    "seqra scan",
		args:       []string{projectPath},
		flags:      make(map[string]string),
		arrayFlags: make(map[string][]string),
		boolFlags:  make(map[string]bool),
	}
}

// WithOutput sets the output path flag.
func (cb *SeqraCommandBuilder) WithOutput(path string) *SeqraCommandBuilder {
	if path != "" {
		cb.flags["output"] = path
	}
	return cb
}

// WithCompileType sets the compile-type flag.
func (cb *SeqraCommandBuilder) WithCompileType(compileType string) *SeqraCommandBuilder {
	if compileType != "" && compileType != defaultCompileType {
		cb.flags["compile-type"] = compileType
	}
	return cb
}

// WithScanType sets the scan-type flag if it differs from the default.
func (cb *SeqraCommandBuilder) WithScanType(scanType string) *SeqraCommandBuilder {
	if scanType != "" && scanType != defaultScanType {
		cb.flags["scan-type"] = scanType
	}
	return cb
}

// WithTimeout sets the timeout flag if it differs from the default.
func (cb *SeqraCommandBuilder) WithTimeout(timeout time.Duration) *SeqraCommandBuilder {
	if timeout != defaultTimeout {
		cb.flags["timeout"] = timeout.String()
	}
	return cb
}

// WithRuleset sets the ruleset path flag.
func (cb *SeqraCommandBuilder) WithRuleset(rulesets []string) *SeqraCommandBuilder {
	if len(rulesets) == 1 && rulesets[0] == "builtint" {
		return cb
	}
	cb.arrayFlags["ruleset"] = []string{}
	for _, ruleset := range rulesets {
		rules := cb.arrayFlags["ruleset"]
		cb.arrayFlags["ruleset"] = append(rules, ruleset)
	}
	return cb
}

// WithRulesetLoadErrors sets the ruleset-load-errors path flag.
func (cb *SeqraCommandBuilder) WithRulesetLoadErrors(path string) *SeqraCommandBuilder {
	if path != "" {
		cb.flags["ruleset-load-errors"] = path
	}
	return cb
}

// WithSemgrepCompatibility sets the semgrep-compatibility-sarif flag.
func (cb *SeqraCommandBuilder) WithSemgrepCompatibility(enabled bool) *SeqraCommandBuilder {
	if !enabled {
		cb.boolFlags["semgrep-compatibility-sarif"] = false
	}
	return cb
}

// Build constructs the final command string.
func (cb *SeqraCommandBuilder) Build() string {
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

// BuildCompileCommandWithDocker builds a compile command string with --compile-type docker
// preserving all other options from the original command.
func BuildCompileCommandWithDocker(projectPath, outputPath string) string {
	return NewCompileCommand(projectPath).
		WithOutput(outputPath).
		WithCompileType("docker").
		Build()
}

// BuildScanCommandWithDocker builds a scan command string with --compile-type docker
// preserving all other options from the original command.
func BuildScanCommandWithDocker(projectPath, sarifReportPath string, rulesetPath []string,
	timeout time.Duration, semgrepCompatibility bool, scanType string) string {
	return NewScanCommand(projectPath).
		WithOutput(sarifReportPath).
		WithTimeout(timeout).
		WithRuleset(rulesetPath).
		WithSemgrepCompatibility(semgrepCompatibility).
		WithScanType(scanType).
		WithCompileType("docker").
		Build()
}

// BuildScanCommandFromCompile builds a scan command string for use after compile,
// suggesting the next step with the compiled project model.
func BuildScanCommandFromCompile(projectPath, projectModelPath string) string {
	// Suggest output path based on project path
	outputPath := fmt.Sprintf("%s/seqra.sarif", projectPath)

	return NewScanCommand(projectModelPath).
		WithOutput(outputPath).
		Build()
}
