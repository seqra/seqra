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
// Colors are specified with ANSI 16 indexes so output follows the active terminal theme.
func DefaultTheme(hasDarkBackground bool) *Theme {
	lightDark := lipgloss.LightDark(hasDarkBackground)

	red := lightDark(lipgloss.Color("1"), lipgloss.Color("9"))
	yellow := lightDark(lipgloss.Color("3"), lipgloss.Color("11"))
	green := lightDark(lipgloss.Color("2"), lipgloss.Color("10"))
	blue := lightDark(lipgloss.Color("4"), lipgloss.Color("12"))
	cyan := lightDark(lipgloss.Color("6"), lipgloss.Color("14"))
	magenta := lightDark(lipgloss.Color("5"), lipgloss.Color("13"))
	muted := lightDark(lipgloss.Color("8"), lipgloss.Color("7"))
	title := lightDark(lipgloss.Color("4"), lipgloss.Color("14"))

	return &Theme{
		Error:   lipgloss.NewStyle().Foreground(red).Bold(true),
		Warning: lipgloss.NewStyle().Foreground(yellow).Bold(true),
		Note:    lipgloss.NewStyle().Foreground(cyan),
		Success: lipgloss.NewStyle().Foreground(green).Bold(true),
		Info:    lipgloss.NewStyle(),
		Debug:   lipgloss.NewStyle().Foreground(magenta),
		Muted:   lipgloss.NewStyle().Foreground(muted),

		HeaderTitle:  lipgloss.NewStyle().Bold(true).Foreground(title),
		HeaderBorder: lipgloss.NewStyle(),

		TreeBranch: lipgloss.NewStyle(),
		TreeItem:   lipgloss.NewStyle(),

		FieldKey:   lipgloss.NewStyle().Foreground(blue),
		FieldValue: lipgloss.NewStyle(),

		Suggestion: lipgloss.NewStyle().Foreground(cyan),
		Command:    lipgloss.NewStyle().Foreground(green).Bold(true),
		FilePath:   lipgloss.NewStyle().Foreground(cyan).Underline(true),

		SpinnerDone:  "✓",
		SpinnerFail:  "✗",
		SpinnerStyle: lipgloss.NewStyle().Foreground(blue),
		DoneStyle:    lipgloss.NewStyle().Foreground(green),
		FailStyle:    lipgloss.NewStyle().Foreground(red),

		SnippetBorder:    lipgloss.NewStyle(),
		SnippetLine:      lipgloss.NewStyle(),
		SnippetHighlight: lipgloss.NewStyle().Foreground(blue).Bold(true),
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
