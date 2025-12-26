package formatters

import (
	"strings"
	"unicode/utf8"

	"github.com/sirupsen/logrus"
)

type ScanResultBuilder struct {
	width int
}

func NewScanResultBuilder() *ScanResultBuilder {
	return &ScanResultBuilder{
		width: 66,
	}
}

func (srb *ScanResultBuilder) Width(w int) *ScanResultBuilder {
	srb.width = w
	return srb
}

type Field struct {
	Key   string
	Value string
}

func (srb *ScanResultBuilder) Build(title string, fields []Field) string {
	innerWidth := srb.width - 2
	titleWidth := utf8.RuneCountInString(title)

	if titleWidth > innerWidth {
		logrus.Fatalf("Title width must be less than %d", innerWidth)
		return title
	}

	top := "╭─" + title + strings.Repeat("─", srb.width-titleWidth-3) + "╮"
	bottom := "╰" + strings.Repeat("─", srb.width-2) + "╯"

	var lines []string
	lines = append(lines, top)

	for _, field := range fields {
		line := field.Key + ":" + strings.Repeat(" ", 2) + field.Value
		lineWidth := utf8.RuneCountInString(line)
		if lineWidth > innerWidth {
			line = line[:innerWidth]
			lineWidth = innerWidth
		}
		padding := innerWidth - lineWidth
		lines = append(lines, "│ "+line+strings.Repeat(" ", padding-1)+"│")
	}

	lines = append(lines, bottom)
	return strings.Join(lines, "\n")
}

type SimpleBoxBuilder struct {
	width int
}

func NewSimpleBoxBuilder() *SimpleBoxBuilder {
	return &SimpleBoxBuilder{
		width: 20,
	}
}

func (sbb *SimpleBoxBuilder) Width(w int) *SimpleBoxBuilder {
	sbb.width = w
	return sbb
}

func (sbb *SimpleBoxBuilder) Build(title string) string {
	titleWidth := utf8.RuneCountInString(title)
	boxWidth := max(titleWidth+3, sbb.width)

	top := "╭─" + title + strings.Repeat("─", boxWidth-titleWidth-2) + "╮"
	bottom := "╰" + strings.Repeat("─", boxWidth-1) + "╯"

	return top + "\n" + bottom
}

func FormatScanResult(project, java, result string) string {
	fields := []Field{
		{"Project", project},
		{"Java", java},
		{"Result", result},
	}
	return NewScanResultBuilder().Build("SEQRA SAST SCAN", fields)
}

func FormatSimpleBox(title string) string {
	return NewSimpleBoxBuilder().Build(title)
}
