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
	baseW             io.Writer
	w                 io.Writer
	logW              io.Writer
	verbosity         string
	theme             *Theme
	isTTY   bool
	quiet   bool
	profile colorprofile.Profile
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

// IsInteractive returns true if the output is a TTY and not in quiet mode.
func (p *Printer) IsInteractive() bool {
	return p.isTTY && !p.quiet
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
	return p.IsInteractive() && !p.IsDebugVerbosity()
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
