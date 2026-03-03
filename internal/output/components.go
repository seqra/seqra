package output

import (
	"errors"
	"fmt"
	"io"
	"strings"
	"sync"
	"time"

	"charm.land/lipgloss/v2"
	bprogress "github.com/charmbracelet/bubbles/progress"
	bspinner "github.com/charmbracelet/bubbles/spinner"
)

var smoothSpinner = bspinner.Spinner{
	Frames: []string{
		"▱▱▱",
		"▰▱▱",
		"▰▰▱",
		"▰▰▰",
		"▱▰▰",
		"▱▱▰",
		"▱▱▱",
		"▱▱▰",
		"▱▰▰",
		"▰▰▰",
		"▰▰▱",
		"▰▱▱",
	},
	FPS: time.Second / 10,
}

// ── Spinner ──────────────────────────────────────────────────────────
// A spinner shows an animated progress indicator while a long-running
// operation is in progress.

// SpinnerHandle controls a running spinner.
type SpinnerHandle struct {
	printer  *Printer
	stopCh   chan struct{}
	doneCh   chan struct{}
	stopOnce sync.Once
	spinner  bspinner.Model
	msg      string
	start    time.Time
	mu       sync.Mutex
}

const clearLine = "\r\033[K"

// StartSpinner creates and starts a new spinner. Call Stop() or StopError()
// on the returned handle when the operation completes.
func (p *Printer) StartSpinner(message string) *SpinnerHandle {
	h := &SpinnerHandle{
		printer: p,
		stopCh:  make(chan struct{}),
		doneCh:  make(chan struct{}),
		spinner: bspinner.New(bspinner.WithSpinner(smoothSpinner)),
		msg:     message,
		start:   time.Now(),
	}

	if !p.IsInteractiveUI() {
		close(h.doneCh)
		return h
	}

	go func() {
		defer close(h.doneCh)
		fps := h.spinner.Spinner.FPS
		if fps <= 0 {
			fps = 100 * time.Millisecond
		}
		ticker := time.NewTicker(fps)
		defer ticker.Stop()

		for {
			select {
			case <-h.stopCh:
				return
			case <-ticker.C:
				h.mu.Lock()
				h.spinner, _ = h.spinner.Update(h.spinner.Tick())
				frame := h.spinner.View()
				elapsed := formatDuration(time.Since(h.start))
				msg := h.msg
				h.mu.Unlock()

				th := p.theme
				frame = th.SpinnerStyle.Render(frame)
				fmt.Fprintf(p.w, "%s%s %s %s", clearLine, frame, msg, th.Muted.Render(elapsed)) //nolint:errcheck
			}
		}
	}()

	return h
}

// Stop completes the spinner with a success indicator.
func (h *SpinnerHandle) Stop(finalMessage string) {
	h.stopOnce.Do(func() { close(h.stopCh) })
	<-h.doneCh
	elapsed := formatDuration(time.Since(h.start))
	th := h.printer.theme
	done := th.DoneStyle.Render(h.printer.theme.SpinnerDone)
	fmt.Fprintf(h.printer.w, "%s%s %s in %s\n", clearLine, done, finalMessage, th.Muted.Render(elapsed)) //nolint:errcheck
}

// StopError completes the spinner with an error indicator.
func (h *SpinnerHandle) StopError(finalMessage string) {
	h.stopOnce.Do(func() { close(h.stopCh) })
	<-h.doneCh
	elapsed := formatDuration(time.Since(h.start))
	th := h.printer.theme
	fail := th.FailStyle.Render(h.printer.theme.SpinnerFail)
	fmt.Fprintf(h.printer.w, "%s%s %s in %s\n", clearLine, fail, finalMessage, th.Muted.Render(elapsed)) //nolint:errcheck
}

// RunWithSpinner wraps a function with a spinner animation.
// If the terminal is non-interactive, the function runs without visual feedback.
func (p *Printer) RunWithSpinner(phase string, run func() error) error {
	if !p.IsInteractiveUI() {
		return run()
	}

	spinner := p.StartSpinner(phase)
	err := run()
	if err != nil {
		spinner.StopError(phase)
		return err
	}
	spinner.Stop(phase)
	return nil
}

// ── Progress bar ─────────────────────────────────────────────────────

// CopyWithProgress copies src to dst while displaying a progress bar
// on interactive terminals. Falls back to plain io.Copy otherwise.
func (p *Printer) CopyWithProgress(dst io.Writer, src io.Reader, total int64, label string) (int64, error) {
	if !p.IsInteractiveUI() || total <= 0 {
		return io.Copy(dst, src)
	}

	const (
		barWidth           = 28
		progressLabelWidth = 45
		updateEvery        = 100 * time.Millisecond
	)

	paddedLabel := fmt.Sprintf("%-*s", progressLabelWidth, label)
	activeSymbol := p.theme.SpinnerStyle.Render("↓")
	doneSymbol := p.theme.DoneStyle.Render(p.theme.SpinnerDone)
	failSymbol := p.theme.FailStyle.Render(p.theme.SpinnerFail)

	bar := bprogress.New(
		bprogress.WithWidth(barWidth),
		bprogress.WithoutPercentage(),
		bprogress.WithFillCharacters('█', '░'),
	)

	started := false
	printProgress := func(written int64, force bool, lastPrinted *time.Time) {
		now := time.Now()
		if !force && !lastPrinted.IsZero() && now.Sub(*lastPrinted) < updateEvery {
			return
		}
		if !started {
			fmt.Fprint(p.w, "\n") //nolint:errcheck
			started = true
		}
		*lastPrinted = now

		if written > total {
			written = total
		}
		percent := float64(written) / float64(total)
		barView := bar.ViewAs(percent)
		symbol := activeSymbol
		if force && written >= total {
			symbol = doneSymbol
		}
		fmt.Fprintf(p.w, "%s%s %s %s %3.0f%% (%s/%s)", clearLine, symbol, paddedLabel, barView, percent*100, FormatSize(written), FormatSize(total)) //nolint:errcheck
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
				fmt.Fprintf(p.w, "%s%s %s failed\n", clearLine, failSymbol, label) //nolint:errcheck
				return written, writeErr
			}
			if nw < nr {
				fmt.Fprintf(p.w, "%s%s %s failed\n", clearLine, failSymbol, label) //nolint:errcheck
				return written, io.ErrShortWrite
			}
		}

		if readErr != nil {
			if errors.Is(readErr, io.EOF) {
				printProgress(written, true, &lastPrinted)
				fmt.Fprint(p.w, "\n") //nolint:errcheck
				return written, nil
			}
			fmt.Fprintf(p.w, "%s%s %s failed\n", clearLine, failSymbol, label) //nolint:errcheck
			return written, readErr
		}
	}
}

// ── Confirm prompt ───────────────────────────────────────────────────

// Confirm shows a Y/N prompt and returns true if the user accepts.
// If not interactive, returns the default value.
func (p *Printer) Confirm(prompt string, defaultYes bool) bool {
	if !p.IsInteractive() {
		return defaultYes
	}

	suffix := " [y/N] "
	if defaultYes {
		suffix = " [Y/n] "
	}

	fmt.Fprint(p.w, "\n"+prompt+suffix) //nolint:errcheck

	var response string
	if _, err := fmt.Scanln(&response); err != nil {
		return defaultYes
	}
	response = strings.TrimSpace(strings.ToLower(response))
	if response == "y" || response == "yes" {
		return true
	}
	if response == "n" || response == "no" {
		return false
	}
	return defaultYes
}

// ── Duration formatting ──────────────────────────────────────────────

// FormatDuration returns a human-readable duration string.
func FormatDuration(d time.Duration) string {
	return formatDuration(d)
}

func formatDuration(d time.Duration) string {
	d = d.Round(time.Second)
	h := d / time.Hour
	m := (d % time.Hour) / time.Minute
	s := (d % time.Minute) / time.Second

	switch {
	case h > 0:
		return fmt.Sprintf("%dh %dm %ds", h, m, s)
	case m > 0:
		return fmt.Sprintf("%dm %ds", m, s)
	default:
		return fmt.Sprintf("%ds", s)
	}
}

// ── File size formatting ─────────────────────────────────────────────

// FormatSize returns a human-readable byte size string.
func FormatSize(bytes int64) string {
	const (
		kb = 1024
		mb = 1024 * kb
		gb = 1024 * mb
	)
	switch {
	case bytes >= gb:
		return fmt.Sprintf("%.1f GB", float64(bytes)/float64(gb))
	case bytes >= mb:
		return fmt.Sprintf("%.1f MB", float64(bytes)/float64(mb))
	case bytes >= kb:
		return fmt.Sprintf("%.1f KB", float64(bytes)/float64(kb))
	default:
		return fmt.Sprintf("%d B", bytes)
	}
}

// ── Hyperlink ────────────────────────────────────────────────────────

// Hyperlink returns an OSC 8 terminal hyperlink if the terminal supports it,
// otherwise returns plain text.
func (p *Printer) Hyperlink(text, url string) string {
	if !p.isTTY {
		return text
	}
	return lipgloss.NewStyle().Hyperlink(url).Render(text)
}

// FileLink returns a clickable file:// hyperlink for a file path and line.
func (p *Printer) FileLink(absProjectPath, relFilePath, displayName string, line int64) string {
	if !p.isTTY {
		return fmt.Sprintf("%s:%d", displayName, line)
	}

	absProjectPath = strings.ReplaceAll(absProjectPath, "\\", "/")
	if !strings.HasSuffix(absProjectPath, "/") {
		absProjectPath += "/"
	}

	uri := fmt.Sprintf("file://%s%s", absProjectPath, relFilePath)
	link := lipgloss.NewStyle().Hyperlink(uri).Render(displayName)
	return fmt.Sprintf("%s:%d", link, line)
}
