package output

import (
	"fmt"
	"io"
	"os"
	"regexp"
	"runtime"
	"strconv"
	"strings"

	"charm.land/lipgloss/v2"
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
	isTTY             bool
	quiet             bool
	hasDarkBackground bool
}

var ansiEscapePattern = regexp.MustCompile(`\x1b\[[0-9;?]*[ -/]*[@-~]`)

// New creates a Printer that writes to os.Stdout with the default theme.
// Color support is auto-detected based on the terminal.
func New() *Printer {
	w := os.Stdout
	tty := term.IsTerminal(int(w.Fd()))
	hasDark := detectDarkBackground(tty)
	return &Printer{
		baseW:             w,
		w:                 colorprofile.NewWriter(w, os.Environ()),
		theme:             DefaultTheme(hasDark),
		isTTY:             tty,
		hasDarkBackground: hasDark,
	}
}

// NewWithWriter creates a Printer that writes to the given writer.
func NewWithWriter(w io.Writer) *Printer {
	tty := false
	if f, ok := w.(*os.File); ok {
		tty = term.IsTerminal(int(f.Fd()))
	}
	hasDark := detectDarkBackground(tty)
	return &Printer{
		baseW:             w,
		w:                 colorprofile.NewWriter(w, os.Environ()),
		theme:             DefaultTheme(hasDark),
		isTTY:             tty,
		hasDarkBackground: hasDark,
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
	p.hasDarkBackground = detectDarkBackground(p.isTTY)
	mode := strings.ToLower(strings.TrimSpace(colorMode))
	if mode == "" {
		mode = "auto"
	}

	switch mode {
	case "never":
		p.w = &colorprofile.Writer{Forward: p.baseW, Profile: colorprofile.NoTTY}
		p.theme = PlainTheme()
		return
	case "always":
		p.w = &colorprofile.Writer{Forward: p.baseW, Profile: colorprofile.TrueColor}
		p.theme = DefaultTheme(p.hasDarkBackground)
		return
	default:
		p.w = colorprofile.NewWriter(p.baseW, os.Environ())
	}

	colored := p.resolveColor(colorMode)
	if !colored {
		p.theme = PlainTheme()
		return
	}
	p.theme = DefaultTheme(p.hasDarkBackground)
}

func detectDarkBackground(isTTY bool) bool {
	if mode := strings.ToLower(strings.TrimSpace(os.Getenv("SEQRA_THEME"))); mode == "light" {
		return false
	} else if mode == "dark" {
		return true
	}

	if val := strings.TrimSpace(os.Getenv("COLORFGBG")); val != "" {
		parts := strings.Split(val, ";")
		for i := len(parts) - 1; i >= 0; i-- {
			bg, err := strconv.Atoi(strings.TrimSpace(parts[i]))
			if err != nil {
				continue
			}
			if bg >= 0 {
				return bg < 8
			}
		}
	}

	if isTTY {
		return lipgloss.HasDarkBackground(os.Stdin, os.Stdout)
	}

	return true
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
