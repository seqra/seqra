package cmd

import (
	"github.com/seqra/opentaint/internal/globals"
	"github.com/seqra/opentaint/internal/output"
	"github.com/seqra/opentaint/internal/utils"
	"github.com/seqra/opentaint/internal/utils/log"
)

// activateLogging opens the log file and configures the output printer's log writer.
// If logFilePath is set, it uses that directly. Otherwise, it derives the log path
// from the project cache directory. If both are empty, no log file is opened.
func activateLogging(logFilePath string, projectCachePath string) {
	var logPath string
	var err error

	if logFilePath != "" {
		logPath = log.AbsPathOrExit(logFilePath, "log file")
		if _, err = log.OpenLogFileAt(logPath); err != nil {
			out.Fatalf("Failed to open log file: %s", err)
		}
	} else if projectCachePath != "" {
		logPath, err = log.OpenProjectLog(projectCachePath)
		if err != nil {
			out.Fatalf("Failed to open project log file: %s", err)
		}
	}

	if logPath != "" {
		globals.LogPath = logPath
		out.SetLogWriter(log.LogWriter())
	}
}

// activateLoggingForProject resolves the project cache path from projectPath,
// then activates logging. Used by compile and project commands that share the
// same "resolve cache path → activate logging" pattern.
func activateLoggingForProject(logFilePath string, projectPath string) {
	cachePath, err := utils.GetProjectCachePath(projectPath)
	if err != nil {
		output.LogInfof("Failed to resolve project cache path for logging: %v", err)
	}
	activateLogging(logFilePath, cachePath)
}
