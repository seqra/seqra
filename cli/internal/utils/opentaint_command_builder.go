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
// sourcePath is the path to the project sources (positional argument).
// If empty, no positional argument is added.
func NewScanCommand(sourcePath string) *OpentaintCommandBuilder {
	var args []string
	if sourcePath != "" {
		args = []string{sourcePath}
	}
	return &OpentaintCommandBuilder{
		command:    "opentaint scan",
		args:       args,
		flags:      make(map[string]string),
		arrayFlags: make(map[string][]string),
		boolFlags:  make(map[string]bool),
	}
}

// NewSummaryCommand creates a new CommandBuilder for the summary command.
// sarifPath is the path to the SARIF report (positional argument).
func NewSummaryCommand(sarifPath string) *OpentaintCommandBuilder {
	return &OpentaintCommandBuilder{
		command:    "opentaint summary",
		args:       []string{sarifPath},
		flags:      make(map[string]string),
		arrayFlags: make(map[string][]string),
		boolFlags:  make(map[string]bool),
	}
}

// WithProjectModel sets the project-model flag.
func (cb *OpentaintCommandBuilder) WithProjectModel(path string) *OpentaintCommandBuilder {
	if path != "" {
		cb.flags["project-model"] = path
	}
	return cb
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
	if len(rulesets) == 1 && rulesets[0] == "builtin" {
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

// WithShowFindings sets the show-findings flag.
func (cb *OpentaintCommandBuilder) WithShowFindings() *OpentaintCommandBuilder {
	cb.boolFlags["show-findings"] = true
	return cb
}

// WithVerboseFlow sets the verbose-flow flag.
func (cb *OpentaintCommandBuilder) WithVerboseFlow() *OpentaintCommandBuilder {
	cb.boolFlags["verbose-flow"] = true
	return cb
}

// WithShowCodeSnippets sets the show-code-snippets flag.
func (cb *OpentaintCommandBuilder) WithShowCodeSnippets() *OpentaintCommandBuilder {
	cb.boolFlags["show-code-snippets"] = true
	return cb
}

// CopyFlagsFrom copies all flags (regular, array, and boolean) from the source builder,
// overwriting any existing flags with the same name. The command and positional args
// are not copied — only flags. Use this to create a variant of an existing command
// (e.g. Docker) that automatically inherits all user-specified options.
func (cb *OpentaintCommandBuilder) CopyFlagsFrom(source *OpentaintCommandBuilder) *OpentaintCommandBuilder {
	for k, v := range source.flags {
		cb.flags[k] = v
	}
	for k, v := range source.arrayFlags {
		copied := make([]string, len(v))
		copy(copied, v)
		cb.arrayFlags[k] = copied
	}
	for k, v := range source.boolFlags {
		cb.boolFlags[k] = v
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
		if cb.boolFlags[flag] {
			parts = append(parts, fmt.Sprintf("--%s", flag))
		} else {
			parts = append(parts, fmt.Sprintf("--%s=false", flag))
		}
	}

	return strings.Join(parts, " ")
}

// BuildCompileCommandWithDocker builds a docker run command string for compiling a project
// using the opentaint Docker image. The base builder carries all user-specified compile flags
// so that new flags added to the builder factory are automatically included.
// Path-based flags (output) are remapped to container paths.
func BuildCompileCommandWithDocker(base *OpentaintCommandBuilder, projectPath, outputPath string) string {
	absProjectPath, _ := filepath.Abs(projectPath)
	absOutputPath, _ := filepath.Abs(outputPath)
	outputDir := filepath.Dir(absOutputPath)
	outputName := filepath.Base(absOutputPath)

	compileCmd := NewCompileCommand("/project").
		CopyFlagsFrom(base).
		WithOutput("/database/" + outputName).
		Build()

	return fmt.Sprintf("docker run --rm -v %s:/project -v %s:/database ghcr.io/seqra/opentaint:latest %s",
		absProjectPath, outputDir, compileCmd)
}

// BuildScanCommandWithDocker builds a docker run command string for scanning a project
// using the opentaint Docker image. The base builder carries all user-specified scan flags
// (timeout, semgrep-compatibility, etc.) so that new flags added to the builder factory
// are automatically included. Path-based flags (output, ruleset) are remapped to container paths.
func BuildScanCommandWithDocker(base *OpentaintCommandBuilder, projectPath, sarifReportPath string, rulesetPaths []string) string {
	absProjectPath, _ := filepath.Abs(projectPath)
	absSarifReportPath, _ := filepath.Abs(sarifReportPath)
	outputDir := filepath.Dir(absSarifReportPath)
	sarifName := filepath.Base(absSarifReportPath)

	volumes := fmt.Sprintf("-v %s:/project -v %s:/output", absProjectPath, outputDir)

	// Map rulesets to container paths; "builtin" is kept as-is, custom paths get volume mounts.
	// Skip rulesets entirely when only "builtin" is specified (it is the default).
	var containerRulesets []string
	hasCustomRuleset := false
	for _, ruleset := range rulesetPaths {
		if ruleset != "builtin" {
			hasCustomRuleset = true
			break
		}
	}
	if hasCustomRuleset {
		for i, ruleset := range rulesetPaths {
			if ruleset == "builtin" {
				containerRulesets = append(containerRulesets, "builtin")
			} else {
				absRuleset, _ := filepath.Abs(ruleset)
				containerPath := fmt.Sprintf("/rules/ruleset%d", i)
				volumes += fmt.Sprintf(" -v %s:%s", absRuleset, containerPath)
				containerRulesets = append(containerRulesets, containerPath)
			}
		}
	}

	// Start with container paths, then inherit all non-path flags from the base builder.
	// WithOutput and WithRuleset called after CopyFlagsFrom override the host paths
	// that were copied, replacing them with the container-mapped equivalents.
	scanCmd := NewScanCommand("/project").
		CopyFlagsFrom(base).
		WithOutput("/output/" + sarifName).
		WithRuleset(containerRulesets).
		Build()

	return fmt.Sprintf("docker run --rm %s ghcr.io/seqra/opentaint:latest %s", volumes, scanCmd)
}

// BuildScanCommandFromCompile builds a scan command string for use after compile,
// suggesting the next step with the compiled project model.
func BuildScanCommandFromCompile(projectPath, projectModelPath string) string {
	// Suggest output path based on project path
	outputPath := filepath.Join(projectPath, "opentaint.sarif")

	return NewScanCommand("").
		WithProjectModel(projectModelPath).
		WithOutput(outputPath).
		Build()
}
