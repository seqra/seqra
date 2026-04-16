package log

import (
	"io"
	"sync"
)

// SwitchableWriter wraps an io.Writer and allows hot-swapping the underlying
// writer. All writes are forwarded to the current target. Safe for concurrent use.
type SwitchableWriter struct {
	mu sync.Mutex
	w  io.Writer
}

// NewSwitchableWriter creates a SwitchableWriter targeting w.
func NewSwitchableWriter(w io.Writer) *SwitchableWriter {
	return &SwitchableWriter{w: w}
}

// Write forwards the write to the current underlying writer.
func (s *SwitchableWriter) Write(p []byte) (int, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.w.Write(p)
}

// Swap replaces the underlying writer. Subsequent writes go to newW.
func (s *SwitchableWriter) Swap(newW io.Writer) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.w = newW
}
