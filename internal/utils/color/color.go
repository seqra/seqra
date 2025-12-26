package color

import (
	"fmt"
	"strings"

	"github.com/sirupsen/logrus"
)

type Color string

const (
	Default Color = ""
	Red     Color = "red"
	Green   Color = "green"
	Yellow  Color = "yellow"
	Blue    Color = "blue"
	Magenta Color = "magenta"
	Cyan    Color = "cyan"
	White   Color = "white"
	Reset   Color = "reset"
)

func (c Color) Code() string {
	switch strings.ToLower(string(c)) {
	case "red":
		return "31"
	case "green":
		return "32"
	case "yellow":
		return "33"
	case "blue":
		return "34"
	case "magenta":
		return "35"
	case "cyan":
		return "36"
	case "white":
		return "37"
	default:
		return "0"
	}
}

func (c Color) String() string {
	return string(c)
}

func Colorize(text string, color Color) string {
	if color == "" {
		return text
	}
	return fmt.Sprintf("\x1b[%sm%s\x1b[0m", color.Code(), text)
}

func LogWithColor(text string, color Color) {
	if color != "" {
		logrus.WithField("color", color.String()).Info(text)
	} else {
		logrus.Info(text)
	}
}

func LogWithMixedColors(parts ...ColoredPart) {
	if len(parts) == 0 {
		return
	}

	var result strings.Builder
	hasColors := false

	for _, part := range parts {
		if part.Color != "" {
			hasColors = true
			result.WriteString(Colorize(part.Text, part.Color))
		} else {
			result.WriteString(part.Text)
		}
	}

	if hasColors {
		logrus.Info(result.String())
	} else {
		logrus.Info(result.String())
	}
}

type ColoredPart struct {
	Text  string
	Color Color
}

func NewColoredPart(text string, color Color) ColoredPart {
	return ColoredPart{Text: text, Color: color}
}
