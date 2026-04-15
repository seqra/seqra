package log

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestOpenProjectLog(t *testing.T) {
	t.Run("creates log file under cache slug dir", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)

		cacheDir := filepath.Join(home, ".opentaint", "cache", "my-project-abc12345")
		if err := os.MkdirAll(cacheDir, 0o755); err != nil {
			t.Fatal(err)
		}

		logPath, err := OpenProjectLog(cacheDir)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		defer CloseLogFile()

		logsDir := filepath.Join(cacheDir, "logs")
		if !strings.HasPrefix(logPath, logsDir) {
			t.Errorf("log path %q should be under %q", logPath, logsDir)
		}

		if _, err := os.Stat(logPath); err != nil {
			t.Fatalf("log file not created: %v", err)
		}
	})
}
