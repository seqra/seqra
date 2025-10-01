package log

import (
	"bytes"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"sort"
	"strings"
	"time"

	"github.com/seqra/seqra/internal/utils"
	"github.com/sirupsen/logrus"
	"github.com/spf13/cobra"
	"golang.org/x/term"
)

var (
	logFile *os.File
)

// OpenLogFile creates and returns a file for logging at the specified path.
// It creates the directory structure if it doesn't exist.
// The file handle is stored in a global variable and can be closed with CloseLogFile().
func OpenLogFile() (*os.File, string, error) {
	seqraHomePath, err := utils.GetSeqraHome()
	cobra.CheckErr(err)
	logDir := filepath.Join(seqraHomePath, "logs")
	// Create log file with timestamp
	logPath := filepath.Join(logDir, time.Now().Format("2006-01-02_15-04-05.log"))

	// Ensure the directory exists
	if err := os.MkdirAll(filepath.Dir(logPath), 0755); err != nil {
		return nil, "", err
	}

	// Open or create the log file
	logFile, err = os.OpenFile(logPath, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)
	if err != nil {
		return nil, "", err
	}

	return logFile, logPath, nil
}

// colorMessageFormatter wraps the whole message with a color based on level.
type colorMessageFormatter struct {
	Enabled bool
}

func (f *colorMessageFormatter) Format(entry *logrus.Entry) ([]byte, error) {
	if !f.Enabled {
		return []byte(entry.Message + "\n"), nil
	}

	color := levelColor(entry.Level)
	reset := "\x1b[0m"
	return []byte(color + entry.Message + reset + "\n"), nil
}

func levelColor(lvl logrus.Level) string {
	// Basic, widely supported ANSI colors (no 256-color/truecolor to keep compatibility high)
	switch lvl {
	case logrus.PanicLevel, logrus.FatalLevel, logrus.ErrorLevel:
		return "\x1b[31m" // red
	case logrus.WarnLevel:
		return "\x1b[33m" // yellow
	case logrus.DebugLevel:
		return "\x1b[36m" // cyan
	case logrus.TraceLevel:
		return "\x1b[35m" // magenta
	default:
		return "" // no color
	}
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

// SetUpLogs configures logging with the specified output and level.
// 'out' is typically the log file writer. Logs will go to both the console and 'out'.
func SetUpLogs(out io.Writer, level string) error {
	// Parse log level
	consoleLevel, err := logrus.ParseLevel(level)
	if err != nil {
		return err
	}

	// File formatter (with per-line timestamp/level/etc.)
	fileFormatter := &blockTextFormatter{
		TimestampFormat: "2006-01-02 15:04:05",
		Indent:          "    ", // 4 spaces (change to "\t" if you prefer tabs)
	}

	// Two writers: one for file, one for console
	logrus.SetOutput(io.Discard) // avoid default stdout
	logrus.SetLevel(logrus.TraceLevel)

	// Console formatter with conditional color
	consoleFormatter := &colorMessageFormatter{Enabled: colorSupported(os.Stdout)}

	logrus.AddHook(&writerHook{
		Writer:    os.Stdout,
		Formatter: consoleFormatter,
		LogLevels: allowedLevels(consoleLevel),
	})

	logrus.AddHook(&writerHook{
		Writer:    out,
		Formatter: fileFormatter,
		LogLevels: logrus.AllLevels,
	})

	return nil
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

// colorSupported determines if ANSI colors should be used for the given writer.
func colorSupported(w io.Writer) bool {
	// Respect NO_COLOR (https://no-color.org/)
	if os.Getenv("NO_COLOR") != "" {
		return false
	}
	// Allow explicit override
	if os.Getenv("FORCE_COLOR") != "" {
		return true
	}

	f, ok := w.(*os.File)
	if !ok {
		return false
	}

	// Only colorize if attached to a TTY
	if !term.IsTerminal(int(f.Fd())) {
		return false
	}

	// Basic Windows handling: modern Windows terminals support ANSI,
	// but if TERM isn't set we err on the safe side and disable.
	if runtime.GOOS == "windows" {
		if os.Getenv("TERM") == "" && os.Getenv("WT_SESSION") == "" && os.Getenv("ANSICON") == "" {
			return false
		}
	}

	return true
}
