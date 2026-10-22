package cmd

import (
	"time"

	"github.com/spf13/cobra"
)

var testCmd = &cobra.Command{
	Use:   "test",
	Short: "Create and run rule and approximation tests",
	Long:  `Tools for creating test projects, running annotated rule and approximation tests, and debugging rule reachability.`,
}

var testRuleCmd = &cobra.Command{
	Use:   "rule",
	Short: "Create, run, and debug detection-rule tests",
}

var testApproximationCmd = &cobra.Command{
	Use:   "approximation",
	Short: "Create and run dataflow-approximation tests",
}

func init() {
	rootCmd.AddCommand(testCmd)
	testCmd.AddCommand(testRuleCmd)
	testCmd.AddCommand(testApproximationCmd)
}

func testExitCodesHelp(passedLine string) string {
	return `Exit codes:
  0    ` + passedLine + `
  1    General failure (configuration or infrastructure error)
  2    One or more tests failed (false negatives/positives or skipped samples)
  252  Unhandled analyzer exception
  253  Out of memory (try increasing --max-memory)
  254  Analysis timed out (try increasing --timeout)
  255  Project configuration error`
}

func addTestRunFlags(
	cmd *cobra.Command,
	outputDir *string,
	timeout *time.Duration,
	maxMemory *string,
	dataflow, goModels *[]string,
) {
	cmd.Flags().StringVarP(outputDir, "output", "o", "", "Directory for test-result.json and test-results.sarif")
	cmd.Flags().DurationVar(timeout, "timeout", 600*time.Second, "Analysis timeout")
	cmd.Flags().StringVar(maxMemory, "max-memory", "8G", "Maximum analyzer heap size (e.g., 8G)")
	cmd.Flags().StringArrayVar(dataflow, "dataflow-approximations", nil, "Dataflow approximation project, build output, or class directory (repeatable)")
	cmd.Flags().StringArrayVar(goModels, "go-models", nil, "Go model source directory using opentaint/<target-package> package paths, with each target package in only one directory (repeatable)")
}
