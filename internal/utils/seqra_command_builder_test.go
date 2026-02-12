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
			expected:    "seqra compile /path/to/project --output /path/to/output",
		},
		{
			name:        "native compile command",
			projectPath: "/path/to/project",
			outputPath:  "/path/to/output",
			compileType: "native",
			expected:    "seqra compile /path/to/project --output /path/to/output",
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
		projectPath          string
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
	}{
		{
			name:                 "basic scan command",
			projectPath:          "/path/to/project",
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
			name:                 "scan with custom timeout",
			projectPath:          "/path/to/project",
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
			projectPath:          "/path/to/project",
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
			cmd := NewScanCommand(tt.projectPath).
				WithOutput(tt.outputPath).
				WithTimeout(tt.timeout).
				WithRuleset(tt.rulesetPath).
				WithRulesetLoadErrors(tt.rulesetLoadErrors).
				WithSemgrepCompatibility(tt.semgrepCompatibility).
				WithScanType(tt.scanType).
				WithCompileType(tt.compileType).
				Build()

			// Check required parts
			if !contains(cmd, "seqra scan") {
				t.Errorf("Command should contain 'seqra scan'")
			}
			if !contains(cmd, tt.projectPath) {
				t.Errorf("Command should contain project path: %s", tt.projectPath)
			}
			if !contains(cmd, "--output") || !contains(cmd, tt.outputPath) {
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
		})
	}
}

func TestBuildCompileCommandWithDocker(t *testing.T) {
	cmd := BuildCompileCommandWithDocker("/path/to/project", "/path/to/output")
	expected := "docker run --rm -v /path/to/project:/project -v /path/to:/database ghcr.io/seqra/seqra:latest seqra compile /project --output /database/output"
	if cmd != expected {
		t.Errorf("BuildCompileCommandWithDocker() = %q, want %q", cmd, expected)
	}
}

func TestBuildScanCommandWithDocker(t *testing.T) {
	tests := []struct {
		name                 string
		projectPath          string
		sarifReportPath      string
		rulesetPaths         []string
		timeout              time.Duration
		semgrepCompatibility bool
		expected             string
	}{
		{
			name:                 "default options",
			projectPath:          "/path/to/project",
			sarifReportPath:      "/path/to/output/results.sarif",
			rulesetPaths:         []string{"builtin"},
			timeout:              defaultTimeout,
			semgrepCompatibility: true,
			expected:             "docker run --rm -v /path/to/project:/project -v /path/to/output:/output ghcr.io/seqra/seqra:latest seqra scan /project --output /output/results.sarif",
		},
		{
			name:                 "custom timeout and semgrep compatibility disabled",
			projectPath:          "/path/to/project",
			sarifReportPath:      "/path/to/output/results.sarif",
			rulesetPaths:         []string{"builtin"},
			timeout:              1200 * time.Second,
			semgrepCompatibility: false,
			expected:             "docker run --rm -v /path/to/project:/project -v /path/to/output:/output ghcr.io/seqra/seqra:latest seqra scan /project --output /output/results.sarif --timeout 20m0s --semgrep-compatibility-sarif=false",
		},
		{
			name:                 "custom ruleset with volume mount",
			projectPath:          "/path/to/project",
			sarifReportPath:      "/path/to/output/results.sarif",
			rulesetPaths:         []string{"builtin", "/path/to/custom-rules.yaml"},
			timeout:              defaultTimeout,
			semgrepCompatibility: true,
			expected:             "docker run --rm -v /path/to/project:/project -v /path/to/output:/output -v /path/to/custom-rules.yaml:/rules/ruleset1 ghcr.io/seqra/seqra:latest seqra scan /project --output /output/results.sarif --ruleset builtin --ruleset /rules/ruleset1",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			cmd := BuildScanCommandWithDocker(tt.projectPath, tt.sarifReportPath, tt.rulesetPaths, tt.timeout, tt.semgrepCompatibility)
			if cmd != tt.expected {
				t.Errorf("BuildScanCommandWithDocker() = %q, want %q", cmd, tt.expected)
			}
		})
	}
}

// Helper function to check if a string contains a substring
func contains(s, substr string) bool {
	return strings.Contains(s, substr)
}
