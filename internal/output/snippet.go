package output

import (
	"fmt"
	"os"
	"strings"
)

// ── Code Snippet ─────────────────────────────────────────────────────
// Renders code snippets with line numbers and highlighted target lines.

// SnippetBuilder provides a fluent interface for loading and formatting
// code snippets around a target line.
type SnippetBuilder struct {
	printer    *Printer
	radius     int64
	lineMarker string
}

// Snippet starts building a code snippet renderer.
func (p *Printer) Snippet() *SnippetBuilder {
	return &SnippetBuilder{
		printer:    p,
		radius:     3,
		lineMarker: ">>",
	}
}

// LoadOrEmpty loads a snippet, returning empty string on any error.
func (sb *SnippetBuilder) LoadOrEmpty(filePath string, centerLine int64) string {
	data, err := os.ReadFile(filePath)
	if err != nil {
		return ""
	}

	lines := strings.Split(string(data), "\n")
	start := centerLine - sb.radius - 1
	end := centerLine + sb.radius - 1

	if start < 0 {
		start = 0
	}
	if end >= int64(len(lines)) {
		end = int64(len(lines) - 1)
	}

	th := sb.printer.theme
	var out strings.Builder

	out.WriteString("\n")
	for i := start; i <= end; i++ {
		lineNum := i + 1
		marker := "  "
		lineStyle := th.SnippetLine
		if lineNum == centerLine {
			marker = sb.lineMarker
			lineStyle = th.SnippetHighlight
		}

		numStr := th.SnippetLineNum.Render(fmt.Sprintf("%4d", lineNum))
		border := th.SnippetBorder.Render("│")
		content := lineStyle.Render(lines[i])

		out.WriteString(fmt.Sprintf("\t%s %2s %s %s\n", border, marker, numStr, content))
	}

	return out.String()
}
