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
		switch {
		case isCompilerDiagnostic(message):
			diagnostics = append(diagnostics, "  "+message)
		case len(diagnostics) > 0 && compilerErrorCount.MatchString(message):
			// javac closes with a count of what it just reported. On its own the line is
			// only something that reads like one, so it is kept as a trailer and never as
			// the diagnostic.
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
	return strings.Contains(message, ": error: ") || strings.HasPrefix(message, "e: ")
}
