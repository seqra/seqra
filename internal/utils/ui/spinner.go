package ui

import (
	"fmt"
	"os"
	"sync"
	"time"

	"github.com/seqra/seqra/v2/internal/globals"
	"github.com/seqra/seqra/v2/internal/utils/formatters"
	"golang.org/x/term"
)

// Spinner displays an animated progress indicator on the terminal.
// Each Start/Stop cycle is independent; the Spinner can be reused.
type Spinner struct {
	stopCh chan struct{}
	doneCh chan struct{}
	msg    string
	start  time.Time
	mu     sync.Mutex
}

func NewSpinner() *Spinner {
	return &Spinner{}
}

func (s *Spinner) Start(message string) {
	s.mu.Lock()
	s.stopCh = make(chan struct{})
	s.doneCh = make(chan struct{})
	s.msg = message
	s.start = time.Now()
	s.mu.Unlock()

	go func() {
		frames := []rune{'⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏'}
		i := 0

		ticker := time.NewTicker(100 * time.Millisecond)
		defer ticker.Stop()
		defer close(s.doneCh)

		for {
			select {
			case <-s.stopCh:
				return
			case <-ticker.C:
				s.mu.Lock()
				elapsed := formatters.FormatDuration(time.Since(s.start))
				msg := s.msg
				s.mu.Unlock()

				fmt.Printf("\r[%c] %s %s", frames[i], msg, elapsed)
				i = (i + 1) % len(frames)
			}
		}
	}()
}

func (s *Spinner) Stop(finalMessage string) {
	close(s.stopCh)
	<-s.doneCh
	elapsed := formatters.FormatDuration(time.Since(s.start))
	fmt.Printf("\r[✓] %s in %s\n", finalMessage, elapsed)
}

func (s *Spinner) StopError(finalMessage string) {
	close(s.stopCh)
	<-s.doneCh
	elapsed := formatters.FormatDuration(time.Since(s.start))
	fmt.Printf("\r[✗] %s in %s\n", finalMessage, elapsed)
}

// RunWithSpinner executes run while displaying a spinner on interactive terminals.
// In quiet mode or non-TTY environments, run is executed directly without visual feedback.
func RunWithSpinner(phase string, run func() error) error {
	if globals.Config.Quiet || !term.IsTerminal(int(os.Stdout.Fd())) {
		return run()
	}

	spinner := NewSpinner()
	spinner.Start(phase)
	err := run()
	if err != nil {
		spinner.StopError(phase)
		return err
	}
	spinner.Stop(phase)
	return nil
}
