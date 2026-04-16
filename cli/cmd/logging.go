package cmd

import (
	"github.com/seqra/opentaint/internal/globals"
	"github.com/seqra/opentaint/internal/output"
	"github.com/seqra/opentaint/internal/utils"
	"github.com/seqra/opentaint/internal/utils/log"
)

// activateLogging opens the log file and configures the output printer's log writer.
// If logFilePath is set, it uses that directly. Otherwise, it writes a timestamped
// log file into logDir. If both are empty, no log file is opened.
func activateLogging(logFilePath string, logDir string) {
	var logPath string
	var err error

	if logFilePath != "" {
		logPath = log.AbsPathOrExit(logFilePath, "log file")
		if _, err = log.OpenLogFileAt(logPath); err != nil {
			out.Fatalf("Failed to open log file: %s", err)
		}
	} else if logDir != "" {
		logPath, err = log.OpenProjectLog(logDir)
		if err != nil {
			out.Fatalf("Failed to open project log file: %s", err)
		}
	}

	if logPath != "" {
		globals.LogPath = logPath
		out.SetLogWriter(log.LogWriter())
	}
}

// activateLoggingForProject resolves the project log directory from projectPath,
// then activates logging. Logs are written to ~/.opentaint/logs/<slug-hash>/.
func activateLoggingForProject(logFilePath string, projectPath string) {
	logPath, err := utils.GetProjectLogPath(projectPath)
	if err != nil {
		output.LogInfof("Failed to resolve project log path: %v", err)
	}
	activateLogging(logFilePath, logPath)
}
