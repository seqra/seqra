package cmd

import (
	"os"
	"time"

	"github.com/seqra/opentaint/internal/testapprox"
	"github.com/spf13/cobra"
)

var (
	testApproxOutputDir string
	testApproxTimeout   time.Duration
	testApproxMaxMemory string
	testApproxDataflow  []string
	testApproxGoModels  []string
)

var testApproximationRunCmd = &cobra.Command{
	Use:   "run <project-model>",
	Short: "Run dataflow approximation tests on a compiled project model",
	Long: `Run the samples specified in rule-test.yaml with the supplied dataflow approximations applied.

A built-in source-to-sink harness rule is applied automatically; positive samples reference the
approximation-rule.yaml rule with id "approximation-rule".

` + testExitCodesHelp("All approximation tests passed"),
	Args: cobra.ExactArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		ruleDir, err := os.MkdirTemp("", "opentaint-approx-rule-*")
		if err != nil {
			out.Fatalf("Failed to create temp dir for harness rule: %s", err)
		}
		if _, err := testapprox.WriteFixedRule(ruleDir); err != nil {
			out.Fatalf("Failed to materialize harness rule: %s", err)
		}

		runTestProject(args[0], testProjectOptions{
			label:          "Approximation tests",
			tempDir:        "opentaint-test-approximations-*",
			rulesets:       []string{ruleDir},
			outputDir:      testApproxOutputDir,
			timeout:        testApproxTimeout,
			maxMemory:      testApproxMaxMemory,
			dataflowApprox: testApproxDataflow,
			goModels:       testApproxGoModels,
		})
	},
}

func init() {
	testApproximationCmd.AddCommand(testApproximationRunCmd)
	addTestRunFlags(
		testApproximationRunCmd,
		&testApproxOutputDir,
		&testApproxTimeout,
		&testApproxMaxMemory,
		&testApproxDataflow,
		&testApproxGoModels,
	)
}
