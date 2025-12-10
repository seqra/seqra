package cmd

import (
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
			compileType: "docker",
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
				t.Errorf("NewCompileCommand() = %v, want %v", cmd, tt.expected)
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
		rulesetPath          string
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
			scanType:             "docker",
			compileType:          "docker",
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
			scanType:             "docker",
			compileType:          "docker",
			expectTimeout:        true,
			expectScanType:       false,
			expectSemgrep:        false,
		},
		{
			name:                 "scan with ruleset",
			projectPath:          "/path/to/project",
			outputPath:           "/path/to/output.sarif",
			timeout:              defaultTimeout,
			rulesetPath:          "/path/to/ruleset",
			rulesetLoadErrors:    "/path/to/errors.json",
			semgrepCompatibility: false,
			scanType:             "native",
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

// Helper function to check if a string contains a substring
func contains(s, substr string) bool {
	return len(s) >= len(substr) && (s == substr || len(s) > len(substr) && containsSubstring(s, substr))
}

func containsSubstring(s, substr string) bool {
	for i := 0; i <= len(s)-len(substr); i++ {
		if s[i:i+len(substr)] == substr {
			return true
		}
	}
	return false
}
