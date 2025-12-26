package formatters

import (
	"strings"
	"unicode/utf8"

	"github.com/seqra/seqra/v2/internal/utils/color"
	"github.com/sirupsen/logrus"
)

type HeaderStyle int

const (
	BoxedStyle HeaderStyle = iota // Boxed header with borders
	LineStyle                     // Simple line-based header
	TreeStyle                     // Tree-style header with connector
)

type HeaderBuilder struct {
	width   int
	style   HeaderStyle
	padChar string
	color   color.Color
}

func NewHeaderBuilder() *HeaderBuilder {
	return &HeaderBuilder{
		width:   0, // Will be calculated based on header length
		style:   BoxedStyle,
		padChar: " ",
		color:   color.Default,
	}
}

func (hb *HeaderBuilder) Color(c color.Color) *HeaderBuilder {
	hb.color = c
	return hb
}

func (hb *HeaderBuilder) Width(w int) *HeaderBuilder {
	hb.width = w
	return hb
}

func (hb *HeaderBuilder) Style(s HeaderStyle) *HeaderBuilder {
	hb.style = s
	return hb
}

func (hb *HeaderBuilder) PadChar(char string) *HeaderBuilder {
	hb.padChar = char
	return hb
}

func (hb *HeaderBuilder) Build(header string) string {
	// utf8.RuneCountInString handles Unicode characters correctly
	textWidth := utf8.RuneCountInString(header)

	switch hb.style {
	case BoxedStyle:
		return hb.buildBoxedHeader(header, textWidth)
	case LineStyle:
		return hb.buildLineHeader(header, textWidth)
	case TreeStyle:
		return hb.buildTreeHeader(header, textWidth)
	default:
		return hb.buildBoxedHeader(header, textWidth)
	}
}

func (hb *HeaderBuilder) buildBoxedHeader(header string, textWidth int) string {
	var boxWidth int
	if hb.width == 0 {
		boxWidth = textWidth + 4 // Auto-calculate: header + ╭─ + ─╮
	} else {
		boxWidth = max(textWidth+4, hb.width)
	}

	top := "╭─" + header + strings.Repeat("─", boxWidth-textWidth-4) + "─╮"
	bottom := "╰" + strings.Repeat("─", boxWidth-2) + "╯"

	return top + "\n" + bottom
}

func (hb *HeaderBuilder) buildLineHeader(header string, textWidth int) string {
	var lineWidth int
	if hb.width == 0 {
		lineWidth = textWidth + 10 // Auto-calculate: header + some padding
	} else {
		lineWidth = hb.width
		if textWidth >= lineWidth {
			logrus.Fatalf("Header \"%s\" width must be less than or equal to %d", header, lineWidth)
			return header
		}
	}

	padding := (lineWidth - textWidth) / 2
	left := strings.Repeat(hb.padChar, padding)
	right := strings.Repeat(hb.padChar, lineWidth-textWidth-padding)

	return "\n" + left + header + right + "\n"
}

func (hb *HeaderBuilder) buildTreeHeader(header string, textWidth int) string {
	var boxWidth int
	if hb.width == 0 {
		boxWidth = textWidth + 4 // Auto-calculate: header + ╭─ + ─╮
	} else {
		boxWidth = max(textWidth+4, hb.width)
	}

	colorizedHeader := color.Colorize(header, hb.color)

	top := "╭─" + colorizedHeader + strings.Repeat("─", boxWidth-textWidth-4) + "─╮"
	// Bottom: ╰──┬ + remaining dashes + ─╯
	remainingDashes := max(
		// Total width minus ╰─┬╯ (5 chars)
		boxWidth-4, 0)
	bottom := "╰─┬" + strings.Repeat("─", remainingDashes) + "╯"

	return top + "\n" + bottom
}

func FormatHeader1(header string) string {
	return NewHeaderBuilder().Style(BoxedStyle).Build(header)
}

func FormatHeader2(header string) string {
	return NewHeaderBuilder().Style(LineStyle).PadChar("─").Build(header)
}

func FormatTreeHeaderColorized(header string, color color.Color) string {
	return NewHeaderBuilder().Style(TreeStyle).Color(color).Build(header)
}

func FormatTreeHeader(header string) string {
	return NewHeaderBuilder().Style(TreeStyle).Build(header)
}
