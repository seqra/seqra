package cmd

import (
	"fmt"
	"os"

	"github.com/seqra/seqra/v2/internal/output"
	"github.com/seqra/seqra/v2/internal/sarif"
	"github.com/seqra/seqra/v2/internal/utils/log"
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
			out.Fatal("Failed to load SARIF report")
		}
		printSarifSummary(report, absSarifPath)
	},
}

var showFindings bool
var showCodeSnippets bool
var verboseFlow bool

func init() {
	rootCmd.AddCommand(summaryCmd)

	summaryCmd.Flags().BoolVar(&showFindings, "show-findings", false, "Show all issues from Sarif file")
	summaryCmd.Flags().BoolVar(&showCodeSnippets, "show-code-snippets", false, "Show finding related code snippets")
	summaryCmd.Flags().BoolVar(&verboseFlow, "verbose-flow", false, "Show full code flow steps for findings")
	_ = summaryCmd.PersistentFlags().MarkHidden("show-code-snippets")
}

func printSarifSummary(report *sarif.Report, absSarifPath string) {
	hasOmittedFlow := false
	if showFindings {
		hasOmittedFlow = report.PrintAll(out, showCodeSnippets, verboseFlow)
		out.Blank()
	}

	report.PrintSummary(out, absSarifPath)

	if showFindings && hasOmittedFlow && !verboseFlow {
		out.Suggest(
			"To see full code flow and code snippets, use:",
			fmt.Sprintf("seqra summary --show-findings --verbose-flow --show-code-snippets %s", absSarifPath),
		)
	}
}

func loadSarifReport(absSarifPath string) *sarif.Report {
	// Read the SARIF file
	data, err := os.ReadFile(absSarifPath)
	if err != nil {
		output.LogInfof("Failed to read SARIF report: %v", err)
		return nil
	}
	// Parse the SARIF report
	report, err := sarif.UnmarshalReport(data)
	if err != nil {
		output.LogInfof("Failed to parse SARIF report: %v", err)
		return nil
	}
	return &report
}
