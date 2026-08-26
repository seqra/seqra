// Package autobuilder reads autobuilder output.
package autobuilder

import (
	"os"
	"regexp"
	"strings"
)

// logLineSeparator precedes the build-tool output in an autobuilder log line.
const logLineSeparator = " - "

// compilerErrorCount matches a javac error summary.
var compilerErrorCount = regexp.MustCompile(`^\d+ errors?$`)

// CompilerDiagnostics returns compiler errors from an autobuilder log.
// It returns an empty string if the log has no compiler error.
func CompilerDiagnostics(logPath string) string {
	log, err := os.ReadFile(logPath)
	if err != nil {
		return ""
	}

	var diagnostics []string
	for _, line := range strings.Split(string(log), "\n") {
		message := buildOutput(line)
		if isCompilerDiagnostic(message) {
			diagnostics = append(diagnostics, "  "+message)
		}
	}
	return strings.Join(diagnostics, "\n")
}

func buildOutput(logLine string) string {
	message := logLine
	if _, after, found := strings.Cut(logLine, logLineSeparator); found {
		message = after
	}
	return strings.TrimRight(message, "\r")
}

func isCompilerDiagnostic(message string) bool {
	// Match javac and kotlinc error formats.
	return strings.Contains(message, ": error: ") ||
		strings.HasPrefix(message, "e: ") ||
		compilerErrorCount.MatchString(message)
}
