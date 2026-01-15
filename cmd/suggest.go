package cmd

import (
	"github.com/seqra/seqra/v2/internal/utils/color"
	"github.com/seqra/seqra/v2/internal/utils/formatters"
	"github.com/sirupsen/logrus"
)

func suggest(theme, command string) {
	logrus.Info()
	logrus.Info(formatters.FormatTreeHeader("Suggestions"))
	printer := formatters.NewTreePrinter()

	printer.AddNode(theme)
	if command != "" {
		printer.AddNodeAtLevel(command, 1, color.Default, false)
	}
	printer.Print()
}
