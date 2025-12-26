package formatters

import (
	"strings"
	"unicode/utf8"

	"github.com/seqra/seqra/v2/internal/utils/color"
)

const (
	DefaultBranchSymbol = "├── "
	DefaultEndSymbol    = "└── "
	DefaultLineSymbol   = "│   "
	DefaultSpaceSymbol  = "    "
	DefaultIndent       = "  "
	DefaultMaxWidth     = 80
)

type TreeNode struct {
	text      string
	level     int
	textColor color.Color
	wrapped   bool
}

type TreePrinter struct {
	indent      string
	branchChar  string
	endChar     string
	lineChar    string
	spaceChar   string
	maxWidth    int
	textColor   color.Color
	symbolColor color.Color
	nodes       []TreeNode
}

func NewTreePrinter() *TreePrinter {
	return &TreePrinter{
		indent:      DefaultIndent,
		branchChar:  DefaultBranchSymbol,
		endChar:     DefaultEndSymbol,
		lineChar:    DefaultLineSymbol,
		spaceChar:   DefaultSpaceSymbol,
		maxWidth:    DefaultMaxWidth,
		textColor:   "",
		symbolColor: "",
		nodes:       make([]TreeNode, 0),
	}
}

func (p *TreePrinter) AddNode(text string) {
	p.AddNodeAtLevel(text, 0, p.textColor, false)
}

func (p *TreePrinter) AddNodeWrapped(text string) {
	p.AddNodeAtLevel(text, 0, p.textColor, true)
}

func (p *TreePrinter) Print() {
	for i, node := range p.nodes {
		isLast := p.isLastAtLevel(i, node.level)
		parentLast := p.calculateParentLast(i, node.level)
		p.printNodeAtLevel(node.text, node.level, parentLast, isLast, node.textColor, node.wrapped)
	}
}

func (p *TreePrinter) isLastAtLevel(index int, level int) bool {
	for i := index + 1; i < len(p.nodes); i++ {
		if p.nodes[i].level == level {
			return false
		}
		if p.nodes[i].level < level {
			return true
		}
	}
	return true
}

func (p *TreePrinter) calculateParentLast(index int, level int) []bool {
	if level <= 0 {
		return []bool{}
	}

	parentLast := make([]bool, level)
	for parentLevel := 0; parentLevel < level; parentLevel++ {
		parentLast[parentLevel] = p.isParentLastAtLevel(index, parentLevel)
	}
	return parentLast
}

func (p *TreePrinter) isParentLastAtLevel(index int, parentLevel int) bool {
	// Find the parent node at the specified level
	parentIndex := -1
	for i := index - 1; i >= 0; i-- {
		if p.nodes[i].level == parentLevel {
			parentIndex = i
			break
		}
		if p.nodes[i].level < parentLevel {
			break
		}
	}

	if parentIndex == -1 {
		return false
	}

	// Check if this parent is the last node at its level
	return p.isLastAtLevel(parentIndex, parentLevel)
}

func (p *TreePrinter) AddNodeAtLevelDefault(text string, level int) {
	p.AddNodeAtLevel(text, level, p.textColor, false)
}

func (p *TreePrinter) AddNodeAtLevelWrapped(text string, level int) {
	p.AddNodeAtLevel(text, level, p.textColor, true)
}

func (p *TreePrinter) AddNodeAtLevel(text string, level int, textColor color.Color, wrapped bool) {
	node := TreeNode{
		text:      text,
		level:     level,
		textColor: textColor,
		wrapped:   wrapped,
	}
	p.nodes = append(p.nodes, node)
}

func (p *TreePrinter) printNodeAtLevel(text string, level int, parentLast []bool, isLast bool, textColor color.Color, wrapped bool) {
	if level == 0 {
		if text == "" {
			symbol := p.lineChar
			if isLast {
				symbol = p.endChar
			}
			color.LogWithColor(p.indent+symbol, "")
		} else if wrapped {
			p.printWrappedNodeAtLevel(text, level, parentLast, isLast, textColor)
		} else {
			symbol := p.branchChar
			if isLast {
				symbol = p.endChar
			}
			parts := []color.ColoredPart{
				color.NewColoredPart(p.indent+symbol, ""),
				color.NewColoredPart(text, textColor),
			}
			color.LogWithMixedColors(parts...)
		}
		return
	}

	if text == "" {
		parentPrefix, _ := p.getSymbols(level, parentLast, isLast)
		color.LogWithColor(parentPrefix+p.lineChar, "")
		return
	}

	if wrapped {
		p.printWrappedNodeAtLevel(text, level, parentLast, isLast, textColor)
	} else {
		parentPrefix, currentPrefix := p.getSymbols(level, parentLast, isLast)
		p.printLine(parentPrefix, currentPrefix, text, textColor)
	}
}

func (p *TreePrinter) printWrappedNodeAtLevel(text string, level int, parentLast []bool, isLast bool, textColor color.Color) {
	parentPrefix, currentPrefix := p.getSymbols(level, parentLast, isLast)
	parentContinuation, currentContinuation := p.getContinuationSymbols(level, parentLast, isLast)
	lines := p.wrapText(text)
	for i, line := range lines {
		if i == 0 {
			p.printLine(parentPrefix, currentPrefix, line, textColor)
		} else {
			p.printLine(parentContinuation, currentContinuation, line, textColor)
		}
	}
}

func (p *TreePrinter) getSymbols(level int, parentLast []bool, isLast bool) (string, string) {
	// if level == 0 {
	// 	return "", ""
	// }

	var prefixParts []string
	prefixParts = append(prefixParts, p.indent)

	for i := 0; i < level; i++ {
		if i < len(parentLast) && parentLast[i] {
			prefixParts = append(prefixParts, p.spaceChar)
		} else {
			prefixParts = append(prefixParts, p.lineChar)
		}
	}

	var currentPrefix string
	if isLast {
		currentPrefix = p.endChar
	} else {
		currentPrefix = p.branchChar
	}

	return strings.Join(prefixParts, ""), currentPrefix
}

func (p *TreePrinter) getContinuationSymbols(level int, parentLast []bool, isLast bool) (string, string) {
	// if level == 0 {
	// 	return "", ""
	// }

	var continuationParts []string
	continuationParts = append(continuationParts, p.indent)

	for i := 0; i < level; i++ {
		if i < len(parentLast) && parentLast[i] {
			continuationParts = append(continuationParts, p.spaceChar)
		} else {
			continuationParts = append(continuationParts, p.lineChar)
		}
	}

	var currentContinuation string
	if isLast {
		currentContinuation = p.spaceChar
	} else {
		currentContinuation = p.lineChar
	}

	return strings.Join(continuationParts, ""), currentContinuation
}

func (p *TreePrinter) printLine(parentPrefix, currentPrefix, text string, c color.Color) {
	parts := []color.ColoredPart{
		color.NewColoredPart(parentPrefix, ""),
		color.NewColoredPart(currentPrefix, ""),
		color.NewColoredPart(text, c),
	}
	color.LogWithMixedColors(parts...)
}

func (p *TreePrinter) wrapText(text string) []string {
	if utf8.RuneCountInString(text) <= p.maxWidth {
		return []string{text}
	}

	words := strings.Fields(text)
	if len(words) == 0 {
		return []string{text}
	}

	var lines []string
	currentLine := words[0]
	for _, word := range words[1:] {
		if utf8.RuneCountInString(currentLine+" "+word) <= p.maxWidth {
			currentLine += " " + word
		} else {
			lines = append(lines, currentLine)
			currentLine = word
		}
	}
	lines = append(lines, currentLine)
	return lines
}
