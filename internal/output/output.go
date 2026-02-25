package output

import (
	"fmt"
	"io"
	"os"
	"runtime"
	"strings"

	"charm.land/lipgloss/v2"
	"golang.org/x/term"
)

// Printer is the central output abstraction. All user-facing output goes
// through a Printer instance. It holds the active theme, the output writer,
// and knows whether it's writing to a TTY.
type Printer struct {
	w     io.Writer
	theme *Theme
	isTTY bool
	quiet bool
}

// New creates a Printer that writes to os.Stdout with the default theme.
// Color support is auto-detected based on the terminal.
func New() *Printer {
	w := os.Stdout
	tty := term.IsTerminal(int(w.Fd()))
	return &Printer{
		w:     w,
		theme: DefaultTheme(),
		isTTY: tty,
	}
}

// NewWithWriter creates a Printer that writes to the given writer.
func NewWithWriter(w io.Writer) *Printer {
	tty := false
	if f, ok := w.(*os.File); ok {
		tty = term.IsTerminal(int(f.Fd()))
	}
	return &Printer{
		w:     w,
		theme: DefaultTheme(),
		isTTY: tty,
	}
}

// Configure applies the given options to the Printer.
func (p *Printer) Configure(colorMode string, quiet bool) {
	p.quiet = quiet
	colored := p.resolveColor(colorMode)
	if !colored {
		p.theme = PlainTheme()
	}
}

// Theme returns the active theme.
func (p *Printer) Theme() *Theme {
	return p.theme
}

// IsTTY returns true if the output is an interactive terminal.
func (p *Printer) IsTTY() bool {
	return p.isTTY
}

// IsQuiet returns true if quiet mode is enabled.
func (p *Printer) IsQuiet() bool {
	return p.quiet
}

// IsInteractive returns true if the output is a TTY and not in quiet mode.
func (p *Printer) IsInteractive() bool {
	return p.isTTY && !p.quiet
}

// Writer returns the underlying io.Writer.
func (p *Printer) Writer() io.Writer {
	return p.w
}

// Print writes a styled string followed by a newline.
func (p *Printer) Print(a ...any) {
	if p.quiet {
		return
	}
	fmt.Fprintln(p.w, a...)
}

// Printf writes a formatted string followed by a newline.
func (p *Printer) Printf(format string, a ...any) {
	if p.quiet {
		return
	}
	fmt.Fprintf(p.w, format+"\n", a...)
}

// Styled writes text rendered with the given style.
func (p *Printer) Styled(style lipgloss.Style, a ...any) {
	if p.quiet {
		return
	}
	text := fmt.Sprint(a...)
	fmt.Fprintln(p.w, style.Render(text))
}

// Blank prints an empty line.
func (p *Printer) Blank() {
	if p.quiet {
		return
	}
	fmt.Fprintln(p.w)
}

// Error prints an error-styled message. Not suppressed by quiet mode.
func (p *Printer) Error(a ...any) {
	text := fmt.Sprint(a...)
	fmt.Fprintln(p.w, p.theme.Error.Render(text))
}

// Errorf prints a formatted error message. Not suppressed by quiet mode.
func (p *Printer) Errorf(format string, a ...any) {
	text := fmt.Sprintf(format, a...)
	fmt.Fprintln(p.w, p.theme.Error.Render(text))
}

// Warn prints a warning-styled message.
func (p *Printer) Warn(a ...any) {
	if p.quiet {
		return
	}
	text := fmt.Sprint(a...)
	fmt.Fprintln(p.w, p.theme.Warning.Render(text))
}

// Warnf prints a formatted warning message.
func (p *Printer) Warnf(format string, a ...any) {
	if p.quiet {
		return
	}
	text := fmt.Sprintf(format, a...)
	fmt.Fprintln(p.w, p.theme.Warning.Render(text))
}

// Success prints a success-styled message.
func (p *Printer) Success(a ...any) {
	if p.quiet {
		return
	}
	text := fmt.Sprint(a...)
	fmt.Fprintln(p.w, p.theme.Success.Render(text))
}

// Successf prints a formatted success message.
func (p *Printer) Successf(format string, a ...any) {
	if p.quiet {
		return
	}
	text := fmt.Sprintf(format, a...)
	fmt.Fprintln(p.w, p.theme.Success.Render(text))
}

// resolveColor determines whether colors should be enabled.
func (p *Printer) resolveColor(mode string) bool {
	mode = strings.ToLower(strings.TrimSpace(mode))
	if mode == "" {
		mode = "auto"
	}

	switch mode {
	case "always":
		return true
	case "never":
		return false
	case "auto":
		// continue
	default:
		return false
	}

	if os.Getenv("NO_COLOR") != "" {
		return false
	}
	if os.Getenv("FORCE_COLOR") != "" {
		return true
	}
	if !p.isTTY {
		return false
	}
	if runtime.GOOS == "windows" {
		if os.Getenv("TERM") == "" && os.Getenv("WT_SESSION") == "" && os.Getenv("ANSICON") == "" {
			return false
		}
	}
	return true
}
