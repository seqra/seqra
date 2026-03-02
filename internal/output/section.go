package output

import (
	"fmt"
	"strings"

	"charm.land/lipgloss/v2"
	"charm.land/lipgloss/v2/tree"
)

// ── Section ──────────────────────────────────────────────────────────
// A Section is a titled block of output rendered as a tree with a
// header box on top. It provides a fluent DSL for building structured
// CLI output.
//
// Usage:
//
//	out.Section("Seqra Scan").
//	    Field("Project", projectPath).
//	    Field("Output", outputPath).
//	    Group("Rules",
//	        out.Item("builtin"),
//	        out.Item("custom.yaml"),
//	    ).
//	    Render()

// SectionBuilder builds and renders a titled tree section.
type SectionBuilder struct {
	printer *Printer
	title   string
	style   lipgloss.Style
	items   []any // tree children: strings, *tree.Tree, or TreeItem
}

// Section starts building a new titled output section.
func (p *Printer) Section(title string) *SectionBuilder {
	return &SectionBuilder{
		printer: p,
		title:   title,
		style:   lipgloss.NewStyle(),
	}
}

// WithStyle sets a lipgloss style for the section title.
func (sb *SectionBuilder) WithStyle(s lipgloss.Style) *SectionBuilder {
	sb.style = s
	return sb
}

// Field adds a "Key: Value" node to the section.
func (sb *SectionBuilder) Field(key string, value any) *SectionBuilder {
	sb.items = append(sb.items, sb.printer.FieldItem(key, value))
	return sb
}

// StyledField adds a "Key: Value" node with a custom style for the value.
func (sb *SectionBuilder) StyledField(key string, value any, valueStyle lipgloss.Style) *SectionBuilder {
	sb.items = append(sb.items, sb.printer.StyledFieldItem(key, value, valueStyle))
	return sb
}

// Text adds a plain text node.
func (sb *SectionBuilder) Text(text string) *SectionBuilder {
	sb.items = append(sb.items, text)
	return sb
}

// StyledText adds a text node rendered with a given style.
func (sb *SectionBuilder) StyledText(text string, style lipgloss.Style) *SectionBuilder {
	sb.items = append(sb.items, style.Render(text))
	return sb
}

// Line adds a blank line separator.
func (sb *SectionBuilder) Line() *SectionBuilder {
	sb.items = append(sb.items, "")
	return sb
}

// Group adds a named sub-tree with children.
func (sb *SectionBuilder) Group(name string, children ...any) *SectionBuilder {
	sb.items = append(sb.items, sb.printer.GroupItem(name, children...))
	return sb
}

// Child adds any tree node directly (string, *tree.Tree, etc).
func (sb *SectionBuilder) Child(children ...any) *SectionBuilder {
	sb.items = append(sb.items, children...)
	return sb
}

// GroupItem creates a named sub-tree with children and returns it
// for use as a nested child inside Group() or Child() calls.
func (p *Printer) GroupItem(name string, children ...any) *tree.Tree {
	th := p.theme
	sub := tree.Root(name).
		Enumerator(seqraEnumerator).
		Indenter(seqraIndenter).
		EnumeratorStyle(th.TreeBranch).
		IndenterStyle(th.TreeBranch).
		ItemStyle(th.TreeItem)
	for _, child := range children {
		sub.Child(child)
	}
	return sub
}

// Render builds and prints the section.
func (sb *SectionBuilder) Render() {
	p := sb.printer
	th := p.theme

	// Render header
	header := renderHeader(sb.title, sb.style, th)
	fmt.Fprintln(p.w, header) //nolint:errcheck
	p.writeMirroredLine(header)

	// Build tree
	if len(sb.items) > 0 {
		body := indentTreeBlock(sb.buildTree(th).String())
		fmt.Fprintln(p.w, body) //nolint:errcheck
		p.writeMirroredLine(body)
	}
}

// String returns the rendered section as a string without printing.
func (sb *SectionBuilder) String() string {
	th := sb.printer.theme

	var buf strings.Builder

	header := renderHeader(sb.title, sb.style, th)
	buf.WriteString(header)
	buf.WriteString("\n")

	if len(sb.items) > 0 {
		buf.WriteString(indentTreeBlock(sb.buildTree(th).String()))
	}

	return buf.String()
}

func seqraEnumerator(children tree.Children, index int) string {
	isLast := index == children.Length()-1
	isSeparator := children.At(index).Value() == ""

	if isSeparator {
		if isLast {
			return "   "
		}
		return "│  "
	}

	if isLast {
		return "└─ "
	}
	return "├─ "
}

func seqraIndenter(children tree.Children, index int) string {
	if index == children.Length()-1 {
		return "   "
	}
	return "│  "
}

func (sb *SectionBuilder) buildTree(th *Theme) *tree.Tree {
	t := tree.New().
		Enumerator(seqraEnumerator).
		Indenter(seqraIndenter).
		EnumeratorStyle(th.TreeBranch).
		IndenterStyle(th.TreeBranch).
		ItemStyle(th.TreeItem)

	for _, item := range sb.items {
		t.Child(item)
	}

	return t
}

func indentTreeBlock(s string) string {
	if s == "" {
		return s
	}

	lines := strings.Split(s, "\n")
	for i := range lines {
		if lines[i] == "" {
			continue
		}
		lines[i] = "  " + lines[i]
	}

	return strings.Join(lines, "\n")
}

// ── Header rendering ─────────────────────────────────────────────────

func renderHeader(title string, titleStyle lipgloss.Style, th *Theme) string {
	styledTitle := titleStyle.Render(title)
	titleLen := lipgloss.Width(title)
	boxWidth := titleLen + 4
	topLine := th.HeaderBorder.Render("╭─") +
		th.HeaderTitle.Render(styledTitle) +
		th.HeaderBorder.Render(strings.Repeat("─", max(boxWidth-titleLen-4, 0))+"─╮")

	bottomLine := th.HeaderBorder.Render("╰─┬" + strings.Repeat("─", max(boxWidth-4, 0)) + "╯")

	return topLine + "\n" + bottomLine
}

// ── Convenience helpers ──────────────────────────────────────────────

// Item wraps a value as a plain tree child. Useful inside Group() calls.
func (p *Printer) Item(value any) string {
	return fmt.Sprint(value)
}

// FieldItem creates a "Key: Value" string for use inside Group() calls.
func (p *Printer) FieldItem(key string, value any) string {
	th := p.theme
	return th.FieldKey.Render(key+":") + " " + th.FieldValue.Render(fmt.Sprint(value))
}

// StyledFieldItem creates a "Key: Value" string with custom value style.
func (p *Printer) StyledFieldItem(key string, value any, valueStyle lipgloss.Style) string {
	th := p.theme
	return th.FieldKey.Render(key+":") + " " + valueStyle.Render(fmt.Sprint(value))
}

// ── Suggestion ───────────────────────────────────────────────────────

// Suggest prints a suggestion section with a description and optional command.
func (p *Printer) Suggest(description string, command string) {
	th := p.theme

	p.Blank()
	sb := p.Section("Suggestions")
	if command == "" {
		sb.StyledText(description, th.Suggestion)
	} else {
		sb.Child(p.GroupItem(th.Suggestion.Render(description), th.Command.Render(command)))
	}

	sb.Render()
}

// ── Box ──────────────────────────────────────────────────────────────

// BoxBuilder builds a bordered box with a title and key-value fields.
type BoxBuilder struct {
	printer *Printer
	title   string
	fields  []boxField
	width   int
}

type boxField struct {
	key   string
	value string
}

// Box starts building a bordered box.
func (p *Printer) Box(title string) *BoxBuilder {
	return &BoxBuilder{
		printer: p,
		title:   title,
		width:   66,
	}
}

// Width sets the box width.
func (bb *BoxBuilder) Width(w int) *BoxBuilder {
	bb.width = w
	return bb
}

// Field adds a key-value pair inside the box.
func (bb *BoxBuilder) Field(key, value string) *BoxBuilder {
	bb.fields = append(bb.fields, boxField{key: key, value: value})
	return bb
}

// Render prints the box.
func (bb *BoxBuilder) Render() {
	text := bb.String()
	fmt.Fprintln(bb.printer.w, text) //nolint:errcheck
	bb.printer.writeMirroredLine(text)
}

// String returns the rendered box as a string.
func (bb *BoxBuilder) String() string {
	th := bb.printer.theme
	innerWidth := bb.width - 2
	titleLen := lipgloss.Width(bb.title)

	top := th.HeaderBorder.Render("╭─" + bb.title + strings.Repeat("─", max(bb.width-titleLen-3, 0)) + "╮")

	var lines []string
	lines = append(lines, top)

	for _, f := range bb.fields {
		line := f.key + ":  " + f.value
		if visibleWidth(line) > innerWidth {
			line = truncateToWidth(line, innerWidth)
		}
		padding := innerWidth - visibleWidth(line) - 1
		if padding < 0 {
			padding = 0
		}
		lines = append(lines, th.HeaderBorder.Render("│")+" "+line+strings.Repeat(" ", padding)+th.HeaderBorder.Render("│"))
	}

	bottom := th.HeaderBorder.Render("╰" + strings.Repeat("─", bb.width-2) + "╯")
	lines = append(lines, bottom)

	return strings.Join(lines, "\n")
}

func visibleWidth(s string) int {
	plain := ansiEscapePattern.ReplaceAllString(s, "")
	return lipgloss.Width(plain)
}

func truncateToWidth(s string, w int) string {
	if w <= 0 {
		return ""
	}
	runes := []rune(s)
	for len(runes) > 0 && lipgloss.Width(string(runes)) > w {
		runes = runes[:len(runes)-1]
	}
	return string(runes)
}
