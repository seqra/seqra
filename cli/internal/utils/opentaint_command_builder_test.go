package utils

import (
	"strings"
	"testing"
	"time"
)

func TestNewCompileCommand(t *testing.T) {
	tests := []struct {
		name        string
		projectPath string
		outputPath  string
		compileType string
		expected    string
	}{
		{
			name:        "basic compile command",
			projectPath: "/path/to/project",
			outputPath:  "/path/to/output",
			compileType: "native",
			expected:    "opentaint compile /path/to/project --output /path/to/output",
		},
		{
			name:        "native compile command",
			projectPath: "/path/to/project",
			outputPath:  "/path/to/output",
			compileType: "native",
			expected:    "opentaint compile /path/to/project --output /path/to/output",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cmd := NewCompileCommand(tt.projectPath).
				WithOutput(tt.outputPath).
				WithCompileType(tt.compileType).
				Build()

			if !contains(cmd, tt.expected) {
				t.Errorf("NewCompileCommand() = %q, want it to contain %q", cmd, tt.expected)
			}
		})
	}
}

func TestNewScanCommand(t *testing.T) {
	tests := []struct {
		name                 string
		sourcePath           string
		projectModel         string
		outputPath           string
		timeout              time.Duration
		rulesetPath          []string
		rulesetLoadErrors    string
		semgrepCompatibility bool
		scanType             string
		compileType          string
		expectTimeout        bool
		expectScanType       bool
		expectSemgrep        bool
		expectProjectModel   bool
	}{
		{
			name:                 "basic scan command with source path",
			sourcePath:           "/path/to/project",
			outputPath:           "/path/to/output.sarif",
			timeout:              defaultTimeout,
			semgrepCompatibility: true,
			scanType:             "native",
			compileType:          "native",
			expectTimeout:        false,
			expectScanType:       false,
			expectSemgrep:        false,
		},
		{
			name:                 "scan with project model",
			projectModel:         "/path/to/model",
			outputPath:           "/path/to/output.sarif",
			timeout:              defaultTimeout,
			semgrepCompatibility: true,
			scanType:             "native",
			compileType:          "native",
			expectProjectModel:   true,
		},
		{
			name:                 "scan with custom timeout",
			sourcePath:           "/path/to/project",
			outputPath:           "/path/to/output.sarif",
			timeout:              1200 * time.Second,
			semgrepCompatibility: true,
			scanType:             "native",
			compileType:          "native",
			expectTimeout:        true,
			expectScanType:       false,
			expectSemgrep:        false,
		},
		{
			name:                 "scan with ruleset",
			sourcePath:           "/path/to/project",
			outputPath:           "/path/to/output.sarif",
			timeout:              defaultTimeout,
			rulesetPath:          []string{"/path/to/ruleset"},
			rulesetLoadErrors:    "/path/to/errors.json",
			semgrepCompatibility: false,
			scanType:             "docker",
			compileType:          "docker",
			expectTimeout:        false,
			expectScanType:       true,
			expectSemgrep:        true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cmd := NewScanCommand(tt.sourcePath).
				WithProjectModel(tt.projectModel).
				WithOutput(tt.outputPath).
				WithTimeout(tt.timeout).
				WithRuleset(tt.rulesetPath).
				WithRulesetLoadErrors(tt.rulesetLoadErrors).
				WithSemgrepCompatibility(tt.semgrepCompatibility).
				WithScanType(tt.scanType).
				WithCompileType(tt.compileType).
				Build()

			// Check required parts
			if !contains(cmd, "opentaint scan") {
				t.Errorf("Command should contain 'opentaint scan'")
			}
			if tt.sourcePath != "" && !contains(cmd, tt.sourcePath) {
				t.Errorf("Command should contain source path: %s", tt.sourcePath)
			}
			if tt.outputPath != "" && (!contains(cmd, "--output") || !contains(cmd, tt.outputPath)) {
				t.Errorf("Command should contain output flag and path")
			}

			// Check conditional parts
			if tt.expectTimeout && !contains(cmd, "--timeout") {
				t.Errorf("Command should contain --timeout flag")
			}
			if tt.expectScanType && !contains(cmd, "--scan-type") {
				t.Errorf("Command should contain --scan-type flag")
			}
			if tt.expectSemgrep && !contains(cmd, "--semgrep-compatibility-sarif=false") {
				t.Errorf("Command should contain --semgrep-compatibility-sarif=false")
			}
			if tt.expectProjectModel && !contains(cmd, "--project-model") {
				t.Errorf("Command should contain --project-model flag")
			}
			if tt.expectProjectModel && !contains(cmd, tt.projectModel) {
				t.Errorf("Command should contain project model path: %s", tt.projectModel)
			}
		})
	}
}

func TestNewSummaryCommand(t *testing.T) {
	tests := []struct {
		name             string
		sarifPath        string
		showFindings     bool
		verboseFlow      bool
		showCodeSnippets bool
		expected         string
	}{
		{
			name:      "basic summary command",
			sarifPath: "/path/to/report.sarif",
			expected:  "opentaint summary /path/to/report.sarif",
		},
		{
			name:         "summary with show-findings",
			sarifPath:    "/path/to/report.sarif",
			showFindings: true,
			expected:     "opentaint summary /path/to/report.sarif --show-findings",
		},
		{
			name:             "summary with all flags",
			sarifPath:        "/path/to/report.sarif",
			showFindings:     true,
			verboseFlow:      true,
			showCodeSnippets: true,
			expected:         "opentaint summary /path/to/report.sarif --show-code-snippets --show-findings --verbose-flow",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			builder := NewSummaryCommand(tt.sarifPath)
			if tt.showFindings {
				builder.WithShowFindings()
			}
			if tt.verboseFlow {
				builder.WithVerboseFlow()
			}
			if tt.showCodeSnippets {
				builder.WithShowCodeSnippets()
			}
			cmd := builder.Build()

			if cmd != tt.expected {
				t.Errorf("NewSummaryCommand() = %q, want %q", cmd, tt.expected)
			}
		})
	}
}

func TestScanCommandWithoutArgument(t *testing.T) {
	cmd := NewScanCommand("").WithProjectModel("/path/to/model").Build()
	expected := "opentaint scan --project-model /path/to/model"
	if cmd != expected {
		t.Errorf("NewScanCommand(\"\") = %q, want %q", cmd, expected)
	}
}

func TestBuildCompileCommandWithDocker(t *testing.T) {
	base := NewCompileCommand("").WithOutput("/path/to/output")
	cmd := BuildCompileCommandWithDocker(base, "/path/to/project", "/path/to/output")
	expected := "docker run --rm -v /path/to/project:/project -v /path/to:/database ghcr.io/seqra/opentaint:latest opentaint compile /project --output /database/output"
	if cmd != expected {
		t.Errorf("BuildCompileCommandWithDocker() = %q, want %q", cmd, expected)
	}
}

func TestBuildScanCommandWithDocker(t *testing.T) {
	tests := []struct {
		name            string
		base            *OpentaintCommandBuilder
		projectPath     string
		sarifReportPath string
		rulesetPaths    []string
		expected        string
	}{
		{
			name:            "default options",
			base:            NewScanCommand("").WithTimeout(defaultTimeout).WithSemgrepCompatibility(true),
			projectPath:     "/path/to/project",
			sarifReportPath: "/path/to/output/results.sarif",
			rulesetPaths:    []string{"builtin"},
			expected:        "docker run --rm -v /path/to/project:/project -v /path/to/output:/output ghcr.io/seqra/opentaint:latest opentaint scan /project --output /output/results.sarif",
		},
		{
			name:            "custom timeout and semgrep compatibility disabled",
			base:            NewScanCommand("").WithTimeout(1200 * time.Second).WithSemgrepCompatibility(false),
			projectPath:     "/path/to/project",
			sarifReportPath: "/path/to/output/results.sarif",
			rulesetPaths:    []string{"builtin"},
			expected:        "docker run --rm -v /path/to/project:/project -v /path/to/output:/output ghcr.io/seqra/opentaint:latest opentaint scan /project --output /output/results.sarif --timeout 20m0s --semgrep-compatibility-sarif=false",
		},
		{
			name:            "custom ruleset with volume mount",
			base:            NewScanCommand("").WithTimeout(defaultTimeout).WithSemgrepCompatibility(true),
			projectPath:     "/path/to/project",
			sarifReportPath: "/path/to/output/results.sarif",
			rulesetPaths:    []string{"builtin", "/path/to/custom-rules.yaml"},
			expected:        "docker run --rm -v /path/to/project:/project -v /path/to/output:/output -v /path/to/custom-rules.yaml:/rules/ruleset1 ghcr.io/seqra/opentaint:latest opentaint scan /project --output /output/results.sarif --ruleset builtin --ruleset /rules/ruleset1",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cmd := BuildScanCommandWithDocker(tt.base, tt.projectPath, tt.sarifReportPath, tt.rulesetPaths)
			if cmd != tt.expected {
				t.Errorf("BuildScanCommandWithDocker() = %q, want %q", cmd, tt.expected)
			}
		})
	}
}

func TestCopyFlagsFrom(t *testing.T) {
	base := NewScanCommand("/original").
		WithTimeout(1200 * time.Second).
		WithSemgrepCompatibility(false).
		WithOutput("/original/output.sarif")

	// CopyFlagsFrom should carry non-path flags, then overrides replace paths
	result := NewScanCommand("/docker/project").
		CopyFlagsFrom(base).
		WithOutput("/docker/output.sarif").
		Build()

	expected := "opentaint scan /docker/project --output /docker/output.sarif --timeout 20m0s --semgrep-compatibility-sarif=false"
	if result != expected {
		t.Errorf("CopyFlagsFrom() = %q, want %q", result, expected)
	}
}

func TestCopyFlagsFromDoesNotMutateSource(t *testing.T) {
	base := NewScanCommand("").WithTimeout(1200 * time.Second)
	baseBefore := base.Build()

	NewScanCommand("/other").
		CopyFlagsFrom(base).
		WithOutput("/new/output.sarif")

	baseAfter := base.Build()
	if baseBefore != baseAfter {
		t.Errorf("CopyFlagsFrom mutated source: before=%q, after=%q", baseBefore, baseAfter)
	}
}

// Helper function to check if a string contains a substring
func contains(s, substr string) bool {
	return strings.Contains(s, substr)
}
