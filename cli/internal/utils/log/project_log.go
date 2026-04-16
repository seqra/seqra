package log

import (
	"path/filepath"
	"time"
)

// OpenProjectLog opens a timestamped log file under <cacheDir>/logs/ and swaps
// the global SwitchableWriter to write to it. Returns the log file path.
func OpenProjectLog(cacheDir string) (string, error) {
	logPath := filepath.Join(cacheDir, "logs", time.Now().Format("2006-01-02_15-04-05.log"))
	if _, err := OpenLogFileAt(logPath); err != nil {
		return "", err
	}
	return logPath, nil
}
