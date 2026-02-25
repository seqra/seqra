package ui

import (
	"errors"
	"fmt"
	"io"
	"os"
	"strings"
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

func IsSpinnerTerminal() bool {
	return !globals.Config.Quiet && term.IsTerminal(int(os.Stdout.Fd()))
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
	if !IsSpinnerTerminal() {
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

// CopyWithProgress copies src to dst while displaying a progress bar on interactive terminals.
// Falls back to plain io.Copy in non-interactive environments or when total is unknown.
func CopyWithProgress(dst io.Writer, src io.Reader, total int64, label string) (int64, error) {
	verbosity := strings.ToLower(strings.TrimSpace(globals.Config.Log.Verbosity))
	if !IsSpinnerTerminal() || total <= 0 || verbosity == "debug" || verbosity == "trace" {
		return io.Copy(dst, src)
	}

	const (
		barWidth    = 28
		updateEvery = 100 * time.Millisecond
		bytesPerKiB = int64(1024)
		bytesPerMiB = 1024 * bytesPerKiB
		bytesPerGiB = 1024 * bytesPerMiB
	)

	formatBytes := func(n int64) string {
		switch {
		case n >= bytesPerGiB:
			return fmt.Sprintf("%.1f GiB", float64(n)/float64(bytesPerGiB))
		case n >= bytesPerMiB:
			return fmt.Sprintf("%.1f MiB", float64(n)/float64(bytesPerMiB))
		case n >= bytesPerKiB:
			return fmt.Sprintf("%.1f KiB", float64(n)/float64(bytesPerKiB))
		default:
			return fmt.Sprintf("%d B", n)
		}
	}

	started := false
	printProgress := func(written int64, force bool, lastPrinted *time.Time) {
		now := time.Now()
		if !force && !lastPrinted.IsZero() && now.Sub(*lastPrinted) < updateEvery {
			return
		}
		if !started {
			fmt.Print("\n")
			started = true
		}
		*lastPrinted = now

		if written > total {
			written = total
		}
		percent := float64(written) / float64(total)
		filled := int(percent * barWidth)
		if filled > barWidth {
			filled = barWidth
		}
		bar := strings.Repeat("=", filled) + strings.Repeat("-", barWidth-filled)
		fmt.Printf("\r[%s] %s %3.0f%% (%s/%s)", bar, label, percent*100, formatBytes(written), formatBytes(total))
	}

	buf := make([]byte, 32*1024)
	var written int64
	var lastPrinted time.Time

	for {
		nr, readErr := src.Read(buf)
		if nr > 0 {
			nw, writeErr := dst.Write(buf[:nr])
			if nw > 0 {
				written += int64(nw)
				printProgress(written, false, &lastPrinted)
			}
			if writeErr != nil {
				fmt.Print("\n")
				return written, writeErr
			}
			if nw < nr {
				fmt.Print("\n")
				return written, io.ErrShortWrite
			}
		}

		if readErr != nil {
			if errors.Is(readErr, io.EOF) {
				printProgress(written, true, &lastPrinted)
				fmt.Print("\n")
				return written, nil
			}
			fmt.Print("\n")
			return written, readErr
		}
	}
}
