package output

import (
	"fmt"
	"io"
	"os"
	"regexp"
	"runtime"
	"strings"

	"github.com/charmbracelet/colorprofile"
	"golang.org/x/term"
)

// Printer is the central output abstraction. All user-facing output goes
// through a Printer instance. It holds the active theme, the output writer,
// and knows whether it's writing to a TTY.
type Printer struct {
	baseW     io.Writer
	w         io.Writer
	debugW    io.Writer
	logW      io.Writer
	verbosity string
	theme     *Theme
	isTTY     bool
	quiet     bool
	profile   colorprofile.Profile
}

var ansiEscapePattern = regexp.MustCompile(`\x1b\[[0-9;?]*[ -/]*[@-~]`)

// New creates a Printer that writes to os.Stdout with the default theme.
// Color support is auto-detected based on the terminal.
func New() *Printer {
	w := os.Stdout
	tty := term.IsTerminal(int(w.Fd()))
	cw := colorprofile.NewWriter(w, os.Environ())
	return &Printer{
		baseW:   w,
		w:       cw,
		debugW:  colorprofile.NewWriter(os.Stderr, os.Environ()),
		theme:   DefaultTheme(cw.Profile),
		isTTY:   tty,
		profile: cw.Profile,
	}
}

// NewWithWriter creates a Printer that writes to the given writer.
func NewWithWriter(w io.Writer) *Printer {
	tty := false
	if f, ok := w.(*os.File); ok {
		tty = term.IsTerminal(int(f.Fd()))
	}
	cw := colorprofile.NewWriter(w, os.Environ())
	return &Printer{
		baseW:   w,
		w:       cw,
		debugW:  colorprofile.NewWriter(os.Stderr, os.Environ()),
		theme:   DefaultTheme(cw.Profile),
		isTTY:   tty,
		profile: cw.Profile,
	}
}

// NewConfigured creates a Printer and applies color/quiet configuration.
func NewConfigured(colorMode string, quiet bool) *Printer {
	p := New()
	p.Configure(colorMode, quiet)
	return p
}

// Configure applies the given options to the Printer.
func (p *Printer) Configure(colorMode string, quiet bool) {
	p.quiet = quiet
	mode := strings.ToLower(strings.TrimSpace(colorMode))
	if mode == "" {
		mode = "auto"
	}

	switch mode {
	case "never":
		p.profile = colorprofile.NoTTY
		p.w = &colorprofile.Writer{Forward: p.baseW, Profile: p.profile}
		p.theme = PlainTheme()
		return
	case "always":
		p.profile = colorprofile.TrueColor
		p.w = &colorprofile.Writer{Forward: p.baseW, Profile: p.profile}
		p.theme = DefaultTheme(p.profile)
		return
	default:
		cw := colorprofile.NewWriter(p.baseW, os.Environ())
		p.profile = cw.Profile
		p.w = cw
	}

	colored := p.resolveColor(colorMode)
	if !colored {
		p.theme = PlainTheme()
		return
	}
	p.theme = DefaultTheme(p.profile)
}

// Theme returns the active theme.
func (p *Printer) Theme() *Theme {
	return p.theme
}

// IsInteractive returns true if the output is a TTY.
func (p *Printer) IsInteractive() bool {
	return p.isTTY
}

// SetLogWriter configures an additional writer for plain-text output mirroring.
// Mirrored output has ANSI escapes stripped and is useful for writing user-facing
// section output to log files.
func (p *Printer) SetLogWriter(w io.Writer) {
	p.logW = w
}

// SetVerbosity stores the configured log verbosity to control
// interactive UI elements like spinners and progress bars.
func (p *Printer) SetVerbosity(level string) {
	p.verbosity = strings.ToLower(strings.TrimSpace(level))
}

// IsDebugVerbosity returns true for debug-like verbosity modes.
func (p *Printer) IsDebugVerbosity() bool {
	return p.verbosity == "debug"
}

// IsInteractiveUI returns true when interactive UI components
// (spinners/progress bars) should be rendered.
func (p *Printer) IsInteractiveUI() bool {
	return p.IsInteractive() && !p.IsDebugVerbosity() && !p.quiet
}

func (p *Printer) writeMirroredLine(line string) {
	if p.logW == nil {
		return
	}
	line = strings.ReplaceAll(line, "\r", "")
	line = ansiEscapePattern.ReplaceAllString(line, "")
	if !strings.HasSuffix(line, "\n") {
		line += "\n"
	}
	_, _ = io.WriteString(p.logW, line)
}

// Print writes a styled string followed by a newline.
func (p *Printer) Print(a ...any) {
	fmt.Fprintln(p.w, a...) //nolint:errcheck
}

// Printf writes a formatted string followed by a newline.
func (p *Printer) Printf(format string, a ...any) {
	fmt.Fprintf(p.w, format+"\n", a...) //nolint:errcheck
}

// Blank prints an empty line.
func (p *Printer) Blank() {
	fmt.Fprintln(p.w) //nolint:errcheck
}

// InteractiveBlank prints an empty line only when interactive UI
// components are enabled.
func (p *Printer) InteractiveBlank() {
	if p.IsInteractiveUI() {
		p.Blank()
	}
}

// Error prints an error-styled message.
func (p *Printer) Error(a ...any) {
	text := fmt.Sprint(a...)
	fmt.Fprintln(p.w, p.theme.Error.Render(text)) //nolint:errcheck
}

// Warn prints a warning-styled message.
func (p *Printer) Warn(a ...any) {
	text := fmt.Sprint(a...)
	fmt.Fprintln(p.w, p.theme.Warning.Render(text)) //nolint:errcheck
}

// Warnf prints a formatted warning message.
func (p *Printer) Warnf(format string, a ...any) {
	text := fmt.Sprintf(format, a...)
	fmt.Fprintln(p.w, p.theme.Warning.Render(text)) //nolint:errcheck
}

// Successf prints a formatted success message.
func (p *Printer) Successf(format string, a ...any) {
	text := fmt.Sprintf(format, a...)
	fmt.Fprintln(p.w, p.theme.Success.Render(text)) //nolint:errcheck
}

// Fatal prints an error-styled message and terminates the process with exit code 1.
func (p *Printer) Fatal(a ...any) {
	p.Error(a...)
	os.Exit(1)
}

// Fatalf prints a formatted error-styled message and terminates the process with exit code 1.
func (p *Printer) Fatalf(format string, a ...any) {
	p.Error(fmt.Sprintf(format, a...))
	os.Exit(1)
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

// WrapText wraps text to fit within the given column width, preserving
// existing newlines and splitting only on whitespace boundaries.
func WrapText(text string, width int) string {
	if width <= 0 {
		return text
	}
	var out strings.Builder
	for i, paragraph := range strings.Split(text, "\n") {
		if i > 0 {
			out.WriteByte('\n')
		}
		words := strings.Fields(paragraph)
		if len(words) == 0 {
			continue
		}
		lineLen := 0
		for j, word := range words {
			wLen := len([]rune(word))
			if j == 0 {
				out.WriteString(word)
				lineLen = wLen
			} else if lineLen+1+wLen > width {
				out.WriteByte('\n')
				out.WriteString(word)
				lineLen = wLen
			} else {
				out.WriteByte(' ')
				out.WriteString(word)
				lineLen += 1 + wLen
			}
		}
	}
	return out.String()
}
