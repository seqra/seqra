package cmd

import (
	"fmt"
	"os"
	"path/filepath"
	"time"

	"github.com/seqra/opentaint/internal/globals"
	"github.com/seqra/opentaint/internal/utils"
	"github.com/seqra/opentaint/internal/utils/java"
	"github.com/seqra/opentaint/internal/utils/log"
	"github.com/spf13/cobra"
)

var (
	testRulesRuleset   []string
	testRulesOutputDir string
	testRulesTimeout   time.Duration
	testRulesMaxMemory string
	testRulesRuleID    []string
)

var agentTestRulesCmd = &cobra.Command{
	Use:   "test-rules <project-model>",
	Short: "Run rule tests against annotated test samples",
	Long: `Run rule tests against annotated test samples in the given project model.

Exit codes:
  0    All rule tests passed
  1    General failure (configuration or infrastructure error)
  252  Unhandled analyzer exception
  253  Out of memory (try increasing --max-memory)
  254  Analysis timed out (try increasing --timeout)
  255  Project configuration error`,
	Args: cobra.ExactArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		projectPath := log.AbsPathOrExit(args[0], "project-model")
		nativeProjectPath := filepath.Join(projectPath, "project.yaml")

		if _, err := os.Stat(nativeProjectPath); os.IsNotExist(err) {
			out.Fatalf("Project model not found: %s", nativeProjectPath)
		}

		// Validate max-memory
		maxMemory, err := utils.ParseMemoryValue(testRulesMaxMemory)
		if err != nil {
			out.Fatalf("Invalid --max-memory value: %s", err)
		}

		// Resolve output directory
		outputDir := testRulesOutputDir
		if outputDir == "" {
			tmpDir, err := os.MkdirTemp("", "opentaint-test-rules-*")
			if err != nil {
				out.Fatalf("Failed to create temp dir: %s", err)
			}
			outputDir = tmpDir
			// Note: temp dir is NOT cleaned up so results remain accessible to the agent.
			// The agent should always specify -o to control the output location.
		} else {
			outputDir = log.AbsPathOrExit(outputDir, "output")
			if err := os.MkdirAll(outputDir, 0755); err != nil {
				out.Fatalf("Failed to create output directory: %s", err)
			}
		}

		// Ensure builtin rules are available
		rulesPath, err := utils.GetRulesPath(globals.Config.Rules.Version)
		if err != nil {
			out.Fatalf("Failed to resolve rules path: %s", err)
		}
		if _, err := os.Stat(rulesPath); os.IsNotExist(err) {
			if dlErr := utils.DownloadAndUnpackGithubReleaseAsset(
				globals.Config.Owner, globals.Config.Repo,
				globals.Config.Rules.Version, globals.RulesAssetName,
				rulesPath, globals.Config.Github.Token,
				globals.Config.SkipVerify, out,
			); dlErr != nil {
				out.Fatalf("Failed to download rules: %s", dlErr)
			}
		}

		timeoutSeconds := int64(testRulesTimeout / time.Second)
		if timeoutSeconds <= 0 {
			timeoutSeconds = 600
		}

		builder := NewAnalyzerBuilder().
			SetProject(nativeProjectPath).
			SetOutputDir(outputDir).
			SetSarifFileName("test-results.sarif").
			SetIfdsAnalysisTimeout(timeoutSeconds).
			AddRuleSet(rulesPath).
			EnableRunRuleTests()

		if maxMemory != "" {
			builder.SetMaxMemory(maxMemory)
		}

		// Add user rulesets
		for _, rs := range testRulesRuleset {
			absPath := log.AbsPathOrExit(rs, "ruleset")
			builder.AddRuleSet(absPath)
		}

		// Add rule ID filters
		for _, ruleID := range testRulesRuleID {
			builder.AddRuleID(ruleID)
		}

		analyzerJarPath, err := ensureAnalyzerAvailable()
		if err != nil {
			out.Fatalf("Failed to resolve analyzer: %s", err)
		}
		builder.SetJarPath(analyzerJarPath)

		javaRunner := java.NewJavaRunner().
			WithSkipVerify(globals.Config.SkipVerify).
			WithStreamOutput(globals.Config.Quiet).
			WithDebugOutput(out.DebugStream("Analyzer")).
			WithImageType(java.AdoptiumImageJRE).
			TrySpecificVersion(globals.DefaultJavaVersion)
		if _, err := javaRunner.EnsureJava(); err != nil {
			out.Fatalf("Failed to resolve Java: %s", err)
		}

		cmdErr, err := scanProject(builder, javaRunner)
		if err != nil {
			out.Fatalf("Rule tests failed: %s", err)
		}
		analyzerFail := classifyAnalyzerError(cmdErr)

		// Always print output paths so the agent can inspect partial results
		fmt.Printf("Results directory: %s\n", outputDir)
		fmt.Printf("Test results:     %s\n", filepath.Join(outputDir, "test-result.json"))

		if analyzerFail != nil {
			os.Exit(analyzerFail.exitCode)
		}

		fmt.Printf("Rule tests completed successfully\n")
	},
}

func init() {
	agentCmd.AddCommand(agentTestRulesCmd)

	agentTestRulesCmd.Flags().StringArrayVar(&testRulesRuleset, "ruleset", nil, "Additional ruleset path (repeatable)")
	agentTestRulesCmd.Flags().StringVarP(&testRulesOutputDir, "output", "o", "", "Output directory for test results (test-result.json)")
	agentTestRulesCmd.Flags().DurationVar(&testRulesTimeout, "timeout", 600*time.Second, "Timeout for analysis")
	agentTestRulesCmd.Flags().StringVar(&testRulesMaxMemory, "max-memory", "8G", "Maximum memory for the analyzer (e.g., 8G)")
	agentTestRulesCmd.Flags().StringArrayVar(&testRulesRuleID, "rule-id", nil, "Filter active rules by ID (repeatable)")
}
