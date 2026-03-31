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

var agentTestRulesCmd = &cobra.Command{
	Use:   "test-rules <project-model>",
	Short: "Run rule tests against annotated test samples",
	Args:  cobra.ExactArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		projectPath := log.AbsPathOrExit(args[0], "project-model")
		nativeProjectPath := filepath.Join(projectPath, "project.yaml")

		if _, err := os.Stat(nativeProjectPath); os.IsNotExist(err) {
			out.Fatalf("Project model not found: %s", nativeProjectPath)
		}

		// Ensure rules are available
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

		outputDir, err := os.MkdirTemp("", "opentaint-test-rules-*")
		if err != nil {
			out.Fatalf("Failed to create temp dir: %s", err)
		}
		defer os.RemoveAll(outputDir)

		builder := NewAnalyzerBuilder().
			SetProject(nativeProjectPath).
			SetOutputDir(outputDir).
			SetSarifFileName("test-results.sarif").
			SetIfdsAnalysisTimeout(int64(600)).
			AddRuleSet(rulesPath).
			EnableRunRuleTests()

		// Add user rulesets from scan-level flags if present
		for _, ruleID := range RuleID {
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

		_ = time.Second // ensure time import used
		if err := scanProject(builder, javaRunner); err != nil {
			out.Fatalf("Rule tests failed: %s", err)
		}

		fmt.Println("Rule tests completed successfully")
	},
}

func init() {
	agentCmd.AddCommand(agentTestRulesCmd)
}
