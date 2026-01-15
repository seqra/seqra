package utils

import (
	"fmt"
	"path/filepath"
	"sort"
	"strings"
	"time"
)

// Default values for command flags
const (
	defaultCompileType = "native"
	defaultScanType    = "native"
	defaultTimeout     = 900 * time.Second
)

// OpentaintCommandBuilder provides a fluent API for building opentaint CLI commands.
// It handles common flag logic and ensures consistent command formatting.
type OpentaintCommandBuilder struct {
	command    string
	args       []string
	flags      map[string]string
	arrayFlags map[string][]string
	boolFlags  map[string]bool
}

// NewCompileCommand creates a new CommandBuilder for the compile command.
func NewCompileCommand(projectPath string) *OpentaintCommandBuilder {
	return &OpentaintCommandBuilder{
		command:    "opentaint compile",
		args:       []string{projectPath},
		flags:      make(map[string]string),
		arrayFlags: make(map[string][]string),
		boolFlags:  make(map[string]bool),
	}
}

// NewScanCommand creates a new CommandBuilder for the scan command.
func NewScanCommand(projectPath string) *OpentaintCommandBuilder {
	return &OpentaintCommandBuilder{
		command:    "opentaint scan",
		args:       []string{projectPath},
		flags:      make(map[string]string),
		arrayFlags: make(map[string][]string),
		boolFlags:  make(map[string]bool),
	}
}

// WithOutput sets the output path flag.
func (cb *OpentaintCommandBuilder) WithOutput(path string) *OpentaintCommandBuilder {
	if path != "" {
		cb.flags["output"] = path
	}
	return cb
}

// WithCompileType sets the compile-type flag.
func (cb *OpentaintCommandBuilder) WithCompileType(compileType string) *OpentaintCommandBuilder {
	if compileType != "" && compileType != defaultCompileType {
		cb.flags["compile-type"] = compileType
	}
	return cb
}

// WithScanType sets the scan-type flag if it differs from the default.
func (cb *OpentaintCommandBuilder) WithScanType(scanType string) *OpentaintCommandBuilder {
	if scanType != "" && scanType != defaultScanType {
		cb.flags["scan-type"] = scanType
	}
	return cb
}

// WithTimeout sets the timeout flag if it differs from the default.
func (cb *OpentaintCommandBuilder) WithTimeout(timeout time.Duration) *OpentaintCommandBuilder {
	if timeout != defaultTimeout {
		cb.flags["timeout"] = timeout.String()
	}
	return cb
}

// WithRuleset sets the ruleset path flag.
func (cb *OpentaintCommandBuilder) WithRuleset(rulesets []string) *OpentaintCommandBuilder {
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
func (cb *OpentaintCommandBuilder) WithRulesetLoadErrors(path string) *OpentaintCommandBuilder {
	if path != "" {
		cb.flags["ruleset-load-errors"] = path
	}
	return cb
}

// WithSemgrepCompatibility sets the semgrep-compatibility-sarif flag.
func (cb *OpentaintCommandBuilder) WithSemgrepCompatibility(enabled bool) *OpentaintCommandBuilder {
	if !enabled {
		cb.boolFlags["semgrep-compatibility-sarif"] = false
	}
	return cb
}

// Build constructs the final command string.
func (cb *OpentaintCommandBuilder) Build() string {
	parts := []string{cb.command}
	parts = append(parts, cb.args...)

	// Add regular flags in sorted order for consistency
	var flagNames []string
	for flag := range cb.flags {
		flagNames = append(flagNames, flag)
	}
	sort.Strings(flagNames)
	for _, flag := range flagNames {
		parts = append(parts, fmt.Sprintf("--%s", flag), cb.flags[flag])
	}

	// Add array flags in sorted order
	var arrayFlagNames []string
	for flag := range cb.arrayFlags {
		arrayFlagNames = append(arrayFlagNames, flag)
	}
	sort.Strings(arrayFlagNames)
	for _, flag := range arrayFlagNames {
		for _, value := range cb.arrayFlags[flag] {
			parts = append(parts, fmt.Sprintf("--%s", flag), value)
		}
	}

	// Add boolean flags in sorted order
	var boolFlagNames []string
	for flag := range cb.boolFlags {
		boolFlagNames = append(boolFlagNames, flag)
	}
	sort.Strings(boolFlagNames)
	for _, flag := range boolFlagNames {
		parts = append(parts, fmt.Sprintf("--%s=%t", flag, cb.boolFlags[flag]))
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
	outputPath := filepath.Join(projectPath, "opentaint.sarif")

	return NewScanCommand(projectModelPath).
		WithOutput(outputPath).
		Build()
}
