package log

import (
	"bytes"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"github.com/sirupsen/logrus"
)

var (
	logFile *os.File
)

// OpenLogFile creates and returns a file for logging at the specified path.
// It creates the directory structure if it doesn't exist.
// The file handle is stored in a global variable and can be closed with CloseLogFile().
func OpenLogFile() (*os.File, string, error) {
	logDir := filepath.Join(os.TempDir(), "seqra", "logs")
	// Create log file with timestamp
	logPath := filepath.Join(logDir, time.Now().Format("2006-01-02_15-04-05.log"))

	// Ensure the directory exists
	if err := os.MkdirAll(filepath.Dir(logPath), 0755); err != nil {
		return nil, "", err
	}

	// Open or create the log file
	var err error
	logFile, err = os.OpenFile(logPath, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)
	if err != nil {
		return nil, "", err
	}

	return logFile, logPath, nil
}

// blockTextFormatter keeps multi-line messages as one block.
// Only the first line gets timestamp/level/fields. Subsequent lines are indented.
type blockTextFormatter struct {
	TimestampFormat string
	Indent          string // e.g. "\t" or "    "
}

func (f *blockTextFormatter) Format(entry *logrus.Entry) ([]byte, error) {
	var buf bytes.Buffer

	ts := entry.Time.Format(f.TimestampFormat)
	level := strings.ToUpper(entry.Level.String())[0]

	// Stable ordering for fields
	keys := make([]string, 0, len(entry.Data))
	for k := range entry.Data {
		keys = append(keys, k)
	}
	sort.Strings(keys)

	var fieldsChunk string
	if len(keys) > 0 {
		var parts []string
		for _, k := range keys {
			parts = append(parts, fmt.Sprintf("%s=%v", k, entry.Data[k]))
		}
		fieldsChunk = strings.Join(parts, " ")
	}

	// Split message into lines
	lines := strings.Split(strings.TrimRight(entry.Message, "\n"), "\n")

	// First line with metadata
	if fieldsChunk != "" {
		fmt.Fprintf(&buf, "%s |%c| %s %s\n", ts, level, fieldsChunk, lines[0])
	} else {
		fmt.Fprintf(&buf, "%s |%c| %s\n", ts, level, lines[0])
	}

	// Continuation lines (just indented or raw)
	for _, line := range lines[1:] {
		fmt.Fprintf(&buf, "%s%s\n", f.Indent, line)
	}

	return buf.Bytes(), nil
}

// SetUpLogs configures logging to the given file writer.
// Console output is no longer handled by logrus — it's handled by the output.Printer.
// Logrus is used exclusively for structured file logging.
func SetUpLogs(out io.Writer, level string, colorMode string) error {
	_ = colorMode

	normalizedLevel := strings.ToLower(strings.TrimSpace(level))
	fileLevel := logrus.InfoLevel
	switch normalizedLevel {
	case "", "info":
		fileLevel = logrus.InfoLevel
	case "debug":
		fileLevel = logrus.DebugLevel
	default:
		return fmt.Errorf("invalid verbosity %q: expected one of info, debug", level)
	}

	// File formatter (with per-line timestamp/level/etc.)
	fileFormatter := &blockTextFormatter{
		TimestampFormat: "2006-01-02 15:04:05",
		Indent:          "    ", // 4 spaces
	}

	// Discard default output — all logging goes through hooks
	logrus.SetOutput(io.Discard)
	logrus.SetLevel(logrus.DebugLevel)

	// File logging hook — all levels up to the configured level
	logrus.AddHook(&writerHook{
		Writer:    out,
		Formatter: fileFormatter,
		LogLevels: allowedLevels(fileLevel),
	})

	// Stderr hook for fatal/error — these still go to stderr for exit codes
	logrus.AddHook(&writerHook{
		Writer:    os.Stderr,
		Formatter: &plainMessageFormatter{},
		LogLevels: []logrus.Level{logrus.PanicLevel, logrus.FatalLevel},
	})

	return nil
}

// plainMessageFormatter outputs just the message with no decoration.
// Used for fatal errors that go to stderr.
type plainMessageFormatter struct{}

func (f *plainMessageFormatter) Format(entry *logrus.Entry) ([]byte, error) {
	if entry.Message == "" {
		return []byte("\n"), nil
	}
	return []byte(entry.Message + "\n"), nil
}

// allowedLevels returns all log levels >= given level
func allowedLevels(maxLevel logrus.Level) []logrus.Level {
	levels := []logrus.Level{}
	for _, lvl := range logrus.AllLevels {
		if lvl <= maxLevel {
			levels = append(levels, lvl)
		}
	}
	return levels
}

// writerHook allows different formatters and levels per output
type writerHook struct {
	Writer    io.Writer
	Formatter logrus.Formatter
	LogLevels []logrus.Level
}

func (hook *writerHook) Fire(entry *logrus.Entry) error {
	line, err := hook.Formatter.Format(entry)
	if err != nil {
		return err
	}
	_, err = hook.Writer.Write(line)
	return err
}

func (hook *writerHook) Levels() []logrus.Level {
	return hook.LogLevels
}

// CloseLogFile closes the log file if it's open.
// This should be called when the application is shutting down.
func CloseLogFile() error {
	if logFile != nil {
		return logFile.Close()
	}
	return nil
}
