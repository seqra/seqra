// Package output provides a DSL-like API for producing styled CLI output.
// It uses lipgloss for styling, glamour for markdown rendering, and bubbles
// for interactive components like spinners and progress bars.
package output

import (
	"charm.land/lipgloss/v2"
	"github.com/charmbracelet/colorprofile"
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
func DefaultTheme(profile colorprofile.Profile) *Theme {
	c := lipgloss.Complete(profile)
	red := c(lipgloss.Color("1"), lipgloss.Color("160"), lipgloss.Color("#DC322F"))
	yellow := c(lipgloss.Color("3"), lipgloss.Color("136"), lipgloss.Color("#B58900"))
	green := c(lipgloss.Color("2"), lipgloss.Color("64"), lipgloss.Color("#859900"))
	blue := c(lipgloss.Color("4"), lipgloss.Color("33"), lipgloss.Color("#268BD2"))
	cyan := c(lipgloss.Color("6"), lipgloss.Color("37"), lipgloss.Color("#2AA198"))
	magenta := c(lipgloss.Color("5"), lipgloss.Color("125"), lipgloss.Color("#D33682"))
	muted := c(lipgloss.Color("8"), lipgloss.Color("66"), lipgloss.Color("#586E75"))
	title := c(lipgloss.Color("5"), lipgloss.Color("61"), lipgloss.Color("#6C71C4"))

	return &Theme{
		Error:   lipgloss.NewStyle().Foreground(red).Bold(true),
		Warning: lipgloss.NewStyle().Foreground(yellow).Bold(true),
		Note:    lipgloss.NewStyle().Foreground(cyan),
		Success: lipgloss.NewStyle().Foreground(green).Bold(true),
		Info:    lipgloss.NewStyle(),
		Debug:   lipgloss.NewStyle().Foreground(magenta),
		Muted:   lipgloss.NewStyle().Foreground(muted),

		HeaderTitle:  lipgloss.NewStyle().Bold(true).Foreground(title),
		HeaderBorder: lipgloss.NewStyle().Faint(true),

		TreeBranch: lipgloss.NewStyle().Faint(true),
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

		SnippetBorder:    lipgloss.NewStyle().Faint(true),
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
