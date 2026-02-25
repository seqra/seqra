package output

import (
	"github.com/charmbracelet/glamour"
)

// RenderMarkdown renders a markdown string to styled terminal output.
// Falls back to plain text if rendering fails.
func (p *Printer) RenderMarkdown(markdown string) string {
	if !p.isTTY {
		return markdown
	}

	style := "dark"
	r, err := glamour.NewTermRenderer(
		glamour.WithStylePath(style),
		glamour.WithWordWrap(80),
	)
	if err != nil {
		return markdown
	}

	rendered, err := r.Render(markdown)
	if err != nil {
		return markdown
	}

	return rendered
}
