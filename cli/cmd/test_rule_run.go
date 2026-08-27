package cmd

import (
	"fmt"
	"os"
	"path/filepath"
	"time"

	"github.com/seqra/opentaint/internal/analyzer"
	"github.com/seqra/opentaint/internal/utils"
	"github.com/seqra/opentaint/internal/utils/log"
	projectutil "github.com/seqra/opentaint/internal/utils/project"
	"github.com/spf13/cobra"
)

var (
	testRulesRuleset     []string
	testRulesOutputDir   string
	testRulesTimeout     time.Duration
	testRulesMaxMemory   string
	testRulesRuleID      []string
	testRulesDataflow    []string
	testRulesGoModels    []string
	testRulesPassthrough []string
)

var testRuleRunCmd = &cobra.Command{
	Use:   "run <project-model>",
	Short: "Run detection-rule tests on a compiled project model",
	Long: `Run detection rules against the samples specified in rule-test.yaml in the
compiled project model.

` + testExitCodesHelp("All rule tests passed"),
	Args: cobra.ExactArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		runTestProject(args[0], testProjectOptions{
			label:               "Rule tests",
			tempDir:             "opentaint-test-rules-*",
			rulesets:            testRulesRuleset,
			outputDir:           testRulesOutputDir,
			timeout:             testRulesTimeout,
			maxMemory:           testRulesMaxMemory,
			ruleIDs:             testRulesRuleID,
			dataflowApprox:      testRulesDataflow,
			goModels:            testRulesGoModels,
			passthroughApprox:   testRulesPassthrough,
			includeBuiltinRules: true,
		})
	},
}

type testProjectOptions struct {
	label               string
	tempDir             string
	rulesets            []string
	outputDir           string
	timeout             time.Duration
	maxMemory           string
	ruleIDs             []string
	dataflowApprox      []string
	goModels            []string
	passthroughApprox   []string
	includeBuiltinRules bool
}

func runTestProject(projectModelArg string, opts testProjectOptions) {
	projectPath := log.AbsPathOrExit(projectModelArg, "project-model")
	nativeProjectPath := filepath.Join(projectPath, "project.yaml")

	if _, err := os.Stat(nativeProjectPath); err != nil {
		if os.IsNotExist(err) {
			out.Fatalf("Project model not found: %s", nativeProjectPath)
		}
		out.Fatalf("Cannot access project model %s: %s", nativeProjectPath, err)
	}

	maxMemory, err := utils.ParseMemoryValue(opts.maxMemory)
	if err != nil {
		out.Fatalf("Invalid --max-memory value: %s", err)
	}

	outputDir := opts.outputDir
	if outputDir == "" {
		tmpDir, err := os.MkdirTemp("", opts.tempDir)
		if err != nil {
			out.Fatalf("Failed to create temp dir: %s", err)
		}
		outputDir = tmpDir
	} else {
		outputDir = log.AbsPathOrExit(outputDir, "output")
		if err := os.MkdirAll(outputDir, 0o755); err != nil {
			out.Fatalf("Failed to create output directory: %s", err)
		}
	}

	timeoutSeconds := int64(opts.timeout / time.Second)
	if timeoutSeconds <= 0 {
		timeoutSeconds = 600
	}

	builder := NewAnalyzerBuilder().
		SetProject(nativeProjectPath).
		SetOutputDir(outputDir).
		SetSarifFileName("test-results.sarif").
		SetIfdsAnalysisTimeout(timeoutSeconds).
		EnableRunRuleTests()

	if opts.includeBuiltinRules {
		rulesPath, err := utils.EnsureRulesPath(out)
		if err != nil {
			out.Fatalf("Failed to prepare built-in rules: %s", err)
		}
		builder.AddRuleSet(rulesPath)
	}

	if maxMemory != "" {
		builder.SetMaxMemory(maxMemory)
	}

	for _, rs := range opts.rulesets {
		absPath := log.AbsPathOrExit(rs, "ruleset")
		builder.AddRuleSet(absPath)
	}

	for _, ruleID := range opts.ruleIDs {
		builder.AddRuleID(ruleID)
	}

	analyzerJarPath, err := ensureAnalyzerAvailable()
	if err != nil {
		out.Fatalf("Failed to resolve analyzer: %s", err)
	}
	builder.SetJarPath(analyzerJarPath)

	addDataflowApproximations(builder, opts.dataflowApprox, analyzerJarPath)
	addGoModels(builder, opts.goModels)
	addPassthroughApproximations(builder, opts.passthroughApprox)

	projectConfig, err := projectutil.LoadConfig(projectPath)
	if err != nil {
		out.Fatalf("Failed to read the project model: %s", err)
	}
	goServerPath := ""
	if len(projectConfig.GoProjects) > 0 {
		goServerPath, err = ensureGoServerAvailable()
		if err != nil {
			out.Fatalf("Failed to resolve the Go server: %s", err)
		}
	}

	javaRunner := newAnalyzerJavaRunner(goServerPath)
	if _, err := javaRunner.EnsureJava(); err != nil {
		out.Fatalf("Failed to resolve Java for analyzer: %s", err)
	}

	cmdErr, err := scanProject(builder, javaRunner)
	if err != nil {
		out.Fatalf("%s failed: %s", opts.label, err)
	}
	analyzerFail := analyzer.Classify(cmdErr)
	if analyzerFail != nil {
		out.Error(analyzerFail.Message)
	}

	resultPath := filepath.Join(outputDir, "test-result.json")
	fmt.Printf("Results directory: %s\n", outputDir)
	fmt.Printf("Test results:     %s\n", resultPath)

	if analyzerFail != nil {
		os.Exit(analyzerFail.ExitCode)
	}

	tr, err := analyzer.LoadTestResult(resultPath)
	if err != nil {
		out.Fatalf("%s produced no readable test-result.json: %s", opts.label, err)
	}
	fmt.Printf("Passed: %d, failed: %d (false negatives: %d, false positives: %d, skipped: %d), disabled: %d\n",
		len(tr.Success), tr.Failed(), len(tr.FalseNegative), len(tr.FalsePositive), len(tr.Skipped), len(tr.Disabled))
	if tr.Failed() > 0 {
		out.Error(fmt.Sprintf("%s failed", opts.label))
		os.Exit(2)
	}

	fmt.Printf("%s completed successfully\n", opts.label)
}

func init() {
	testRuleCmd.AddCommand(testRuleRunCmd)

	testRuleRunCmd.Flags().StringArrayVar(&testRulesRuleset, "ruleset", nil, "Ruleset file or directory to test (repeatable)")
	addTestRunFlags(
		testRuleRunCmd,
		&testRulesOutputDir,
		&testRulesTimeout,
		&testRulesMaxMemory,
		&testRulesDataflow,
		&testRulesGoModels,
	)
	testRuleRunCmd.Flags().StringArrayVar(&testRulesRuleID, "rule-id", nil, "Run only rules with this ID (repeatable)")
	testRuleRunCmd.Flags().StringArrayVar(&testRulesPassthrough, "passthrough-approximations", nil, "Pass-through approximation YAML file or directory (repeatable)")
}
