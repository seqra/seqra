package log

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestOpenProjectLog(t *testing.T) {
	t.Run("creates log file directly inside logDir", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)

		logDir := filepath.Join(home, ".opentaint", "logs", "my-project-abc12345")
		if err := os.MkdirAll(logDir, 0o755); err != nil {
			t.Fatal(err)
		}

		logPath, err := OpenProjectLog(logDir)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		defer func() { _ = CloseLogFile() }()

		if !strings.HasPrefix(logPath, logDir) {
			t.Errorf("log path %q should be directly under %q", logPath, logDir)
		}

		// Ensure no extra "logs/" subdirectory was created
		if strings.HasPrefix(logPath, filepath.Join(logDir, "logs")) {
			t.Errorf("log path %q should NOT be under %q/logs — logDir is already the logs dir", logPath, logDir)
		}

		if _, err := os.Stat(logPath); err != nil {
			t.Fatalf("log file not created: %v", err)
		}
	})
}
