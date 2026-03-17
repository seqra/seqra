// Package output provides a DSL-like API for producing styled CLI output.
// It uses lipgloss for styling, glamour for markdown rendering, and bubbles
// for interactive components like spinners and progress bars.
package output

import (
	"image/color"
	"os"

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

// DefaultTheme returns the default opentaint theme adapted for light/dark terminals.
// Colors are specified with ANSI 16 indexes so output follows the active terminal theme.
func DefaultTheme(_ colorprofile.Profile) *Theme {
	ld := lipgloss.LightDark(lipgloss.HasDarkBackground(os.Stdin, os.Stdout))
	col := func(light, dark string) color.Color {
		return ld(lipgloss.Color(light), lipgloss.Color(dark))
	}

	return &Theme{
		Error:   lipgloss.NewStyle().Foreground(col("1", "9")).Bold(true),
		Warning: lipgloss.NewStyle().Foreground(col("3", "11")).Bold(true),
		Note:    lipgloss.NewStyle().Foreground(col("6", "14")),
		Success: lipgloss.NewStyle().Foreground(col("2", "10")).Bold(true),
		Info:    lipgloss.NewStyle(),
		Debug:   lipgloss.NewStyle().Foreground(col("5", "13")),
		Muted:   lipgloss.NewStyle().Foreground(lipgloss.Color("8")),

		HeaderTitle:  lipgloss.NewStyle().Bold(true).Foreground(col("2", "13")),
		HeaderBorder: lipgloss.NewStyle().Faint(true),

		TreeBranch: lipgloss.NewStyle().Faint(true),
		TreeItem:   lipgloss.NewStyle(),

		FieldKey:   lipgloss.NewStyle().Foreground(col("4", "12")),
		FieldValue: lipgloss.NewStyle(),

		Suggestion: lipgloss.NewStyle().Foreground(col("4", "10")),
		Command:    lipgloss.NewStyle().Bold(true),
		FilePath:   lipgloss.NewStyle().Foreground(col("6", "14")).Underline(true),

		SpinnerDone:  "✓",
		SpinnerFail:  "✗",
		SpinnerStyle: lipgloss.NewStyle().Foreground(col("4", "12")),
		DoneStyle:    lipgloss.NewStyle().Foreground(col("2", "10")),
		FailStyle:    lipgloss.NewStyle().Foreground(col("1", "9")),

		SnippetBorder:    lipgloss.NewStyle().Faint(true),
		SnippetLine:      lipgloss.NewStyle(),
		SnippetHighlight: lipgloss.NewStyle().Foreground(col("4", "12")).Bold(true),
		SnippetLineNum:   lipgloss.NewStyle().Foreground(lipgloss.Color("8")),
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
