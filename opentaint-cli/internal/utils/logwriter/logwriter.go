package logwriter

// LogWriter implements io.Writer and forwards all writes to a channel
// for processing by the logrus logger
type LogWriter struct {
	ch chan<- string
}

// New creates a new LogWriter that forwards to the given channel
func New(ch chan<- string) *LogWriter {
	return &LogWriter{ch: ch}
}

// Write implements the io.Writer interface
func (w *LogWriter) Write(p []byte) (n int, err error) {
	// Remove trailing newline if present
	line := string(p)
	if len(line) > 0 && line[len(line)-1] == '\n' {
		line = line[:len(line)-1]
	}
	w.ch <- line
	return len(p), nil
}
