package ui

import (
	"fmt"
	"sync"
	"time"

	"github.com/seqra/seqra/v2/internal/utils/formatters"
)

type SpinnerInterface interface {
	Start(message string)
	Stop(finalMessage string)
	StopError(finalMessage string)
}

type Spinner struct {
	stopCh chan struct{}
	doneCh chan struct{}
	msg    string
	start  time.Time
	mu     sync.Mutex
}

func NewSpinner() *Spinner {
	return &Spinner{
		stopCh: make(chan struct{}),
		doneCh: make(chan struct{}),
	}
}

func (s *Spinner) Start(message string) {
	s.mu.Lock()
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
	// Tell Start goroutine to stop
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
