package cmd

import (
	"os"

	"github.com/seqra/seqra/internal/sarif"
	"github.com/seqra/seqra/internal/utils/log"
	"github.com/sirupsen/logrus"
	"github.com/spf13/cobra"
)

// summaryCmd represents the summary command
var summaryCmd = &cobra.Command{
	Use:   "summary sarif",
	Short: "Print summary of your sarif",
	Args:  cobra.MinimumNArgs(1), // require at least one argument
	Long: `Print summary of your sarif file

Arguments:
  sarif  - Path to a sarif file
`,

	Run: func(cmd *cobra.Command, args []string) {
		absSarifPath := log.AbsPathOrExit(args[0], "sarif path")
		PrintSarifSummary(absSarifPath, false)
	},
}

var showFindings bool

func init() {
	rootCmd.AddCommand(summaryCmd)

	summaryCmd.Flags().BoolVar(&showFindings, "show-findings", false, "Show all issues from Sarif file")
}

func PrintSarifSummary(absSarifpath string, printEmptyLine bool) *sarif.Report {
	// Read the SARIF file
	data, err := os.ReadFile(absSarifpath)
	if err != nil {
		logrus.Warnf("Failed to read SARIF report: %v", err)
		return nil
	}
	// Parse the SARIF report
	report, err := sarif.UnmarshalReport(data)
	if err != nil {
		logrus.Warnf("Failed to parse SARIF report: %v", err)
		return nil
	}

	if printEmptyLine {
		logrus.Info()
	}

	if showFindings {
		logrus.Infof("=== Findings ===")
		report.PrintAll()
		logrus.Info()
	}

	// Print the summary
	report.PrintSummary()

	return &report
}
