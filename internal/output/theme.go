// Package output provides a DSL-like API for producing styled CLI output.
// It uses lipgloss for styling, glamour for markdown rendering, and bubbles
// for interactive components like spinners and progress bars.
package output

import (
	"charm.land/lipgloss/v2"
)

// Theme holds all the visual styles used throughout the CLI.
// Changing a theme field changes the appearance everywhere it's referenced.
type Theme struct {
	// Semantic styles for severity levels
	Error   lipgloss.Style
	Warning lipgloss.Style
	Note    lipgloss.Style
	Success lipgloss.Style
	Info    lipgloss.Style
	Debug   lipgloss.Style
	Muted   lipgloss.Style

	// Component styles
	HeaderTitle  lipgloss.Style
	HeaderBorder lipgloss.Style

	TreeBranch lipgloss.Style
	TreeItem   lipgloss.Style

	FieldKey   lipgloss.Style
	FieldValue lipgloss.Style

	Suggestion lipgloss.Style
	Command    lipgloss.Style
	FilePath   lipgloss.Style

	// Spinner symbols
	SpinnerDone  string
	SpinnerFail  string
	SpinnerStyle lipgloss.Style
	DoneStyle    lipgloss.Style
	FailStyle    lipgloss.Style

	// Snippet styles
	SnippetBorder    lipgloss.Style
	SnippetLine      lipgloss.Style
	SnippetHighlight lipgloss.Style
	SnippetLineNum   lipgloss.Style
}

// DefaultTheme returns the default seqra theme.
func DefaultTheme() *Theme {
	red := lipgloss.Color("#FF5F56")
	yellow := lipgloss.Color("#FFBD2E")
	green := lipgloss.Color("#27C93F")
	blue := lipgloss.Color("#2196F3")
	cyan := lipgloss.Color("#56B6C2")
	dim := lipgloss.Color("#666666")
	white := lipgloss.Color("#E5E5E5")

	return &Theme{
		Error:   lipgloss.NewStyle().Foreground(red).Bold(true),
		Warning: lipgloss.NewStyle().Foreground(yellow).Bold(true),
		Note:    lipgloss.NewStyle().Foreground(blue),
		Success: lipgloss.NewStyle().Foreground(green).Bold(true),
		Info:    lipgloss.NewStyle(),
		Debug:   lipgloss.NewStyle().Foreground(cyan),
		Muted:   lipgloss.NewStyle().Foreground(dim),

		HeaderTitle:  lipgloss.NewStyle().Bold(true).Foreground(white),
		HeaderBorder: lipgloss.NewStyle().Foreground(dim),

		TreeBranch: lipgloss.NewStyle().Foreground(dim),
		TreeItem:   lipgloss.NewStyle(),

		FieldKey:   lipgloss.NewStyle().Foreground(cyan),
		FieldValue: lipgloss.NewStyle(),

		Suggestion: lipgloss.NewStyle().Foreground(yellow),
		Command:    lipgloss.NewStyle().Foreground(green),
		FilePath:   lipgloss.NewStyle().Foreground(blue).Underline(true),

		SpinnerDone:  "✓",
		SpinnerFail:  "✗",
		SpinnerStyle: lipgloss.NewStyle().Foreground(cyan),
		DoneStyle:    lipgloss.NewStyle().Foreground(green),
		FailStyle:    lipgloss.NewStyle().Foreground(red),

		SnippetBorder:    lipgloss.NewStyle().Foreground(dim),
		SnippetLine:      lipgloss.NewStyle(),
		SnippetHighlight: lipgloss.NewStyle().Bold(true),
		SnippetLineNum:   lipgloss.NewStyle().Foreground(dim),
	}
}

// PlainTheme returns a theme with no colors or styles (for non-TTY / --color=never).
func PlainTheme() *Theme {
	plain := lipgloss.NewStyle()
	return &Theme{
		Error:   plain,
		Warning: plain,
		Note:    plain,
		Success: plain,
		Info:    plain,
		Debug:   plain,
		Muted:   plain,

		HeaderTitle:  plain,
		HeaderBorder: plain,

		TreeBranch: plain,
		TreeItem:   plain,

		FieldKey:   plain,
		FieldValue: plain,

		Suggestion: plain,
		Command:    plain,
		FilePath:   plain,

		SpinnerDone:  "✓",
		SpinnerFail:  "✗",
		SpinnerStyle: plain,
		DoneStyle:    plain,
		FailStyle:    plain,

		SnippetBorder:    plain,
		SnippetLine:      plain,
		SnippetHighlight: plain,
		SnippetLineNum:   plain,
	}
}
