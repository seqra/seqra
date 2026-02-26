package output

import (
	"fmt"
	"io"
	"os"
	"sync"

	"github.com/charmbracelet/colorprofile"
)

// DebugStreamWriter writes labeled and indented debug output to stderr.
// The source label is printed once before the first line.
type DebugStreamWriter struct {
	printer *Printer
	w       io.Writer
	label   string

	mu         sync.Mutex
	headerDone bool
}

// DebugStream creates a writer for streaming debug output for a source.
func (p *Printer) DebugStream(label string) *DebugStreamWriter {
	return &DebugStreamWriter{
		printer: p,
		w:       colorprofile.NewWriter(os.Stderr, os.Environ()),
		label:   label,
	}
}

// WriteLine prints one line of debug output with source grouping.
func (d *DebugStreamWriter) WriteLine(text string) {
	d.mu.Lock()
	defer d.mu.Unlock()

	if !d.headerDone {
		header := d.printer.theme.Debug.Bold(true).Render(d.label)
		fmt.Fprintln(d.w, header)
		d.printer.writeMirroredLine(d.label)
		d.headerDone = true
	}

	line := "    " + text
	fmt.Fprintln(d.w, d.printer.theme.Muted.Render(line))
	d.printer.writeMirroredLine(line)
}
