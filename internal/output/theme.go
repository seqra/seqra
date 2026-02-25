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

// DefaultTheme returns the default seqra theme adapted for light/dark terminals.
// Colors are specified in truecolor and downsampled by colorprofile.Writer as needed.
func DefaultTheme(hasDarkBackground bool) *Theme {
	lightDark := lipgloss.LightDark(hasDarkBackground)

	red := lightDark(lipgloss.Color("#dc322f"), lipgloss.Color("#ff6b6b"))
	yellow := lightDark(lipgloss.Color("#b58900"), lipgloss.Color("#ffd166"))
	green := lightDark(lipgloss.Color("#859900"), lipgloss.Color("#9ece6a"))
	blue := lightDark(lipgloss.Color("#268bd2"), lipgloss.Color("#7aa2f7"))
	cyan := lightDark(lipgloss.Color("#2aa198"), lipgloss.Color("#7dcfff"))
	muted := lightDark(lipgloss.Color("#586e75"), lipgloss.Color("#6b7280"))
	title := lightDark(lipgloss.Color("#073642"), lipgloss.Color("#e5e7eb"))
	border := lightDark(lipgloss.Color("#93a1a1"), lipgloss.Color("#6b7280"))

	return &Theme{
		Error:   lipgloss.NewStyle().Foreground(red).Bold(true),
		Warning: lipgloss.NewStyle().Foreground(yellow).Bold(true),
		Note:    lipgloss.NewStyle().Foreground(blue),
		Success: lipgloss.NewStyle().Foreground(green).Bold(true),
		Info:    lipgloss.NewStyle(),
		Debug:   lipgloss.NewStyle().Foreground(cyan),
		Muted:   lipgloss.NewStyle().Foreground(muted),

		HeaderTitle:  lipgloss.NewStyle().Bold(true).Foreground(title),
		HeaderBorder: lipgloss.NewStyle().Foreground(border),

		TreeBranch: lipgloss.NewStyle().Foreground(border),
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

		SnippetBorder:    lipgloss.NewStyle().Foreground(border),
		SnippetLine:      lipgloss.NewStyle(),
		SnippetHighlight: lipgloss.NewStyle().Bold(true),
		SnippetLineNum:   lipgloss.NewStyle().Foreground(muted),
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
