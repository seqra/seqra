package log

import (
	"bytes"
	"strings"
	"testing"

	"github.com/sirupsen/logrus"
)

// SetUpLogs must wire the file hook so that Debug-level entries are
// always captured. JAR output is emitted at Debug; it must land in the
// log file regardless of any user-facing console mode.
func TestSetUpLogsFileHookAlwaysCapturesDebug(t *testing.T) {
	var buf bytes.Buffer
	LogWriter().Swap(&buf)
	t.Cleanup(func() { LogWriter().Swap(nopWriter{}) })

	// Reset hooks between cases.
	logrus.StandardLogger().ReplaceHooks(make(logrus.LevelHooks))

	if err := SetUpLogs(); err != nil {
		t.Fatalf("SetUpLogs returned error: %v", err)
	}

	logrus.Debug("captured-debug-line")

	got := buf.String()
	if !strings.Contains(got, "captured-debug-line") {
		t.Fatalf("expected debug line in log file output in default mode, got %q", got)
	}
}

type nopWriter struct{}

func (nopWriter) Write(p []byte) (int, error) { return len(p), nil }
