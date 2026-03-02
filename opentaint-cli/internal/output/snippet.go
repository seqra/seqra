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
	radius     int
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
// centerLine is 1-based.
func (sb *SnippetBuilder) LoadOrEmpty(filePath string, centerLine int64) string {
	lines := sb.LoadLinesOrEmpty(filePath, centerLine)
	if len(lines) == 0 {
		return ""
	}
	return strings.Join(lines, "\n")
}

// LoadLinesOrEmpty loads a snippet and returns it as tree-friendly lines.
// centerLine is 1-based.
func (sb *SnippetBuilder) LoadLinesOrEmpty(filePath string, centerLine int64) []string {
	data, err := os.ReadFile(filePath)
	if err != nil {
		return nil
	}

	lines := strings.Split(string(data), "\n")
	center := int(centerLine)
	start := center - sb.radius - 1
	end := center + sb.radius - 1

	if start < 0 {
		start = 0
	}
	if end >= len(lines) {
		end = len(lines) - 1
	}

	th := sb.printer.theme
	out := make([]string, 0, end-start+1)

	for i := start; i <= end; i++ {
		lineNum := i + 1
		marker := "  "
		lineStyle := th.SnippetLine
		if lineNum == center {
			marker = sb.lineMarker
			lineStyle = th.SnippetHighlight
		}

		numStr := th.SnippetLineNum.Render(fmt.Sprintf("%4d", lineNum))
		content := lineStyle.Render(lines[i])

		var prefix string
		if i == end {
			prefix = th.SnippetBorder.Render("└────")
		} else {
			prefix = th.SnippetBorder.Render("│")
		}

		if i == end {
			out = append(out, fmt.Sprintf("%s%s %s", prefix, numStr, content))
		} else {
			out = append(out, fmt.Sprintf("%s %2s %s %s", prefix, marker, numStr, content))
		}
	}

	return out
}
