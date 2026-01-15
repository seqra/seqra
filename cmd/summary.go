package cmd

import (
	"os"

	"github.com/seqra/seqra/v2/internal/sarif"
	"github.com/seqra/seqra/v2/internal/utils/formatters"
	"github.com/seqra/seqra/v2/internal/utils/log"
	"github.com/sirupsen/logrus"
	"github.com/spf13/cobra"
)

// summaryCmd represents the summary command
var summaryCmd = &cobra.Command{
	Use:   "summary sarif",
	Short: "Print summary of your sarif",
	Args:  cobra.ExactArgs(1), // require exactly one argument
	Long: `Print summary of your sarif file

Arguments:
  sarif  - Path to a sarif file
`,

	Run: func(cmd *cobra.Command, args []string) {
		absSarifPath := log.AbsPathOrExit(args[0], "sarif path")
		report := loadSarifReport(absSarifPath)
		if report == nil {
			return
		}
		printer := formatters.NewTreePrinter()
		printSarifSummary(report, absSarifPath)
		printer.Print()
	},
}

var showFindings bool
var showCodeSnippets bool

func init() {
	rootCmd.AddCommand(summaryCmd)

	summaryCmd.Flags().BoolVar(&showFindings, "show-findings", false, "Show all issues from Sarif file")
	summaryCmd.Flags().BoolVar(&showCodeSnippets, "show-code-snippets", false, "Show finding related code snippets")
	_ = summaryCmd.PersistentFlags().MarkHidden("show-code-snippets")
}

func printSarifSummary(report *sarif.Report, absSarifPath string) {
	if showFindings {
		report.PrintAll(showCodeSnippets)
		logrus.Info()
	}

	report.PrintSummary(absSarifPath)
}

func loadSarifReport(absSarifPath string) *sarif.Report {
	// Read the SARIF file
	data, err := os.ReadFile(absSarifPath)
	if err != nil {
		logrus.Errorf("Failed to read SARIF report: %v", err)
		return nil
	}
	// Parse the SARIF report
	report, err := sarif.UnmarshalReport(data)
	if err != nil {
		logrus.Errorf("Failed to parse SARIF report: %v", err)
		return nil
	}
	return &report
}
