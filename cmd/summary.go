package cmd

import (
	"fmt"

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
		report, err := sarif.LoadReport(absSarifPath)
		if err != nil {
			out.Fatalf("Failed to load SARIF report: %s", err)
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
