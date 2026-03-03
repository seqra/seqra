package cmd

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"time"

	"github.com/seqra/seqra/v2/internal/load_trace"
	"github.com/seqra/seqra/v2/internal/sarif"
	"github.com/seqra/seqra/v2/internal/version"

	"github.com/seqra/seqra/v2/internal/utils/project"
	"github.com/spf13/cobra"
	"github.com/spf13/viper"

	"github.com/seqra/seqra/v2/internal/globals"
	"github.com/seqra/seqra/v2/internal/output"
	"github.com/seqra/seqra/v2/internal/utils"
	"github.com/seqra/seqra/v2/internal/utils/java"
	"github.com/seqra/seqra/v2/internal/utils/log"
)

var (
	UserProjectPath           string
	SarifReportPath           string
	SemgrepCompatibilitySarif bool
	Severity                  []string
	Ruleset                   []string
	DryRunScan                bool
)

type RulesetType struct {
	Path    string
	Builtin bool
}

type ScanMode int

const (
	Scan ScanMode = iota
	CompileAndScan
)

const (
	dryRunScanProjectModelPath  = "seqra-scan-dry-run/project-model"
	dryRunRuleLoadTraceFileName = "seqra-rule-load-trace.dry-run.json"
)

func (m ScanMode) String() string {
	switch m {
	case Scan:
		return "Seqra Scan"
	case CompileAndScan:
		return "Seqra Compile and Scan"
	default:
		return "Unknown"
	}
}

// scanCmd represents the scan command
var scanCmd = &cobra.Command{
	Use:   "scan project",
	Short: "Scan your Java or Kotlin project",
	Args:  cobra.ExactArgs(1), // require exactly one argument
	Long: `This command automatically detects Java/Kotlin build systems, builds the project, and analyzes it

Arguments:
  project  - Path to a project or a project model (required)
`,
	Annotations: map[string]string{"PrintConfig": "true"},
	Run: func(cmd *cobra.Command, args []string) {
		UserProjectPath = args[0]
		scan(cmd)
	},
}

func init() {
	rootCmd.AddCommand(scanCmd)

	scanCmd.Flags().DurationVarP(&globals.Config.Scan.Timeout, "timeout", "t", 900*time.Second, "Timeout for analysis")
	_ = viper.BindPFlag("scan.timeout", scanCmd.Flags().Lookup("timeout"))

	scanCmd.Flags().StringArrayVar(&Ruleset, "ruleset", []string{"builtin"}, "YAML rules file, directory of YAML rules files ending in .yml or .yaml, or `builtin` to scan with built-in rules")
	_ = viper.BindPFlag("scan.ruleset", scanCmd.Flags().Lookup("ruleset"))

	scanCmd.Flags().BoolVar(&SemgrepCompatibilitySarif, "semgrep-compatibility-sarif", true, "Use Semgrep compatible ruleId")
	scanCmd.Flags().StringVarP(&SarifReportPath, "output", "o", "", "Path to the SARIF-report output file")
	_ = scanCmd.MarkFlagRequired("output")
	scanCmd.Flags().StringArrayVar(&Severity, "severity", []string{"warning", "error"}, "Report findings only from rules matching the supplied severity level. By default only warning and error rules are run (note, warning, error)")
	scanCmd.Flags().StringVar(&globals.Config.Scan.MaxMemory, "max-memory", "8G", "Maximum memory for the analyzer (e.g., 1024m, 8G, 81920k, 83886080)")
	_ = viper.BindPFlag("scan.max_memory", scanCmd.Flags().Lookup("max-memory"))
	scanCmd.Flags().Int64Var(&globals.Config.Scan.CodeFlowLimit, "code-flow-limit", 0, "Maximum number of code flows to include in the report (0 = unlimited)")
	_ = scanCmd.PersistentFlags().MarkHidden("code-flow-limit")
	_ = viper.BindPFlag("scan.code_flow_limit", scanCmd.Flags().Lookup("code-flow-limit"))
	scanCmd.Flags().BoolVar(&DryRunScan, "dry-run", false, "Validate inputs and show what would run without compiling or scanning")
}

func scan(cmd *cobra.Command) {
	var absProjectModelPath string
	var tempLogsDir string // Store the temp directory name for cleanup

	userProjectPath := UserProjectPath
	userProjectPath = filepath.Clean(userProjectPath)
	absUserProjectRoot := log.AbsPathOrExit(userProjectPath, "project path")

	tempProjectModel := false
	var tempProjectModelPath string

	if !utils.IsSupportedArch() {
		out.Fatalf("Unsupported architecture found: %s! Only arm64 and amd64 are supported.", utils.GetArch())
	}

	// Resolve project type
	var scanMode ScanMode
	output.LogDebugf("Trying to define %v is a project model or a project", absUserProjectRoot)
	if _, err := os.Stat(filepath.Join(absUserProjectRoot, "project.yaml")); err == nil {
		scanMode = Scan
		absProjectModelPath = absUserProjectRoot
	} else if errors.Is(err, os.ErrNotExist) {
		tempProjectModel = true
		scanMode = CompileAndScan
		if DryRunScan {
			tempProjectModelPath = filepath.Join(os.TempDir(), dryRunScanProjectModelPath)
		} else {
			tempLogsDir, err = os.MkdirTemp("", "seqra-*")
			if err != nil {
				out.Fatalf("Failed to create temporary directory: %s", err)
			}
			tempProjectModelPath = filepath.Join(tempLogsDir, "project-model")
		}
		absProjectModelPath = tempProjectModelPath
	} else {
		out.Fatalf("Unexpected error occurred while checking the project: %s", err)
	}

	var absRuleSetPaths = []RulesetType{}
	var userRuleSetPath = Ruleset

	for _, ruleset := range userRuleSetPath {
		switch ruleset {
		case "builtin":
			rulesPath, err := utils.GetRulesPath(globals.Config.Rules.Version)
			if err != nil {
				out.Fatalf("Unexpected error occurred while trying to construct path to the ruleset: %s", err)
			}

			absRuleSetPaths = append(absRuleSetPaths, RulesetType{Path: rulesPath, Builtin: true})
		default:
			rulesPath := log.AbsPathOrExit(ruleset, "ruleset")
			absRuleSetPaths = append(absRuleSetPaths, RulesetType{Path: rulesPath, Builtin: false})
		}

	}

	var absSarifReportPath string
	if SarifReportPath != "" {
		absSarifReportPath = log.AbsPathOrExit(SarifReportPath, "output")
	} else {
		absSarifReportPath = filepath.Join(os.TempDir(), "seqra-scan.sarif.temp")
	}

	sarifReportName := filepath.Base(absSarifReportPath)

	localVersion := globals.Config.Analyzer.Version
	localSemanticVersion := version.GetVersion()

	var sourceRoot string
	if !tempProjectModel {
		if parsedSourceRoot, err := project.GetSourceRoot(absProjectModelPath); err != nil {
			out.Fatalf("Failed to parse sourceRoot from project.yaml: %v", err)
		} else {
			sourceRoot = parsedSourceRoot
		}
	} else {
		sourceRoot = absUserProjectRoot
	}

	uriBase := fmt.Sprintf("%s%s", sourceRoot, string(filepath.Separator))

	var absSemgrepRuleLoadTracePath string
	if DryRunScan {
		absSemgrepRuleLoadTracePath = filepath.Join(os.TempDir(), dryRunRuleLoadTraceFileName)
	} else {
		absSemgrepRuleLoadTracePath = setupSemgrepRuleLoadTrace()
	}

	// Display scan information in tree format
	printScanInfo(cmd, scanMode, absProjectModelPath, absSemgrepRuleLoadTracePath, tempProjectModel, absUserProjectRoot, absRuleSetPaths)

	if DryRunScan {
		runDryRun(validateScanInputs, "Compilation and analysis")
		return
	}

	maxMemory := ""
	if globals.Config.Scan.MaxMemory != "" {
		parsedMaxMemory, err := utils.ParseMemoryValue(globals.Config.Scan.MaxMemory)
		if err != nil {
			out.Fatalf("Invalid max-memory value: %s", err)
		}
		maxMemory = parsedMaxMemory
	}

	for _, ruleSetPath := range absRuleSetPaths {
		if !ruleSetPath.Builtin {
			continue
		}
		if _, err := os.Stat(ruleSetPath.Path); err == nil {
			continue
		}
		if err := out.RunWithSpinner(fmt.Sprintf("Downloading rules %s", globals.Config.Rules.Version), func() error {
			return utils.DownloadAndUnpackGithubReleaseAsset(globals.Config.Owner, globals.RulesRepoName, globals.Config.Rules.Version, globals.RulesAssetName, ruleSetPath.Path, globals.Config.Github.Token, globals.Config.SkipVerify, out)
		}); err != nil {
			out.Fatalf("Unexpected error occurred while trying to download ruleset: %s", err)
		}
	}

	if tempProjectModel {
		autobuilderJarPath, err := ensureAutobuilderAvailable()
		if err != nil {
			out.Fatalf("Native compile preparation failed: %s", err)
		}

		compileJavaRunner := java.NewJavaRunner().
			WithSkipVerify(globals.Config.SkipVerify).
			WithDebugOutput(out.DebugStream("Autobuilder")).
			TrySystem().
			TrySpecificVersion(globals.Config.Java.Version)
		if _, err := compileJavaRunner.EnsureJava(); err != nil {
			out.Fatalf("Failed to resolve Java for compilation: %s", err)
		}

		if err := out.RunWithSpinner("Compiling project model", func() error {
			return compile(absUserProjectRoot, tempProjectModelPath, autobuilderJarPath, compileJavaRunner, Internal)
		}); err != nil {
			suggest("If native compilation fails due to missing required Java, set JAVA_HOME according to the project's requirements or try Docker-based scan:", utils.BuildScanCommandWithDocker(absUserProjectRoot, absSarifReportPath, Ruleset, globals.Config.Scan.Timeout, SemgrepCompatibilitySarif))
			out.Fatalf("Native compile has failed: %s", err)
		}
		out.Blank()
		printCompileSummary(tempProjectModelPath)
	}

	// Update builder with native paths for native execution
	nativeProjectPath := filepath.Join(absProjectModelPath, "project.yaml")
	nativeOutputDir := filepath.Dir(absSarifReportPath)
	nativeBuilder := NewAnalyzerBuilder().
		SetProject(nativeProjectPath).
		SetOutputDir(nativeOutputDir).
		SetSarifFileName(sarifReportName).
		SetSarifCodeFlowLimit(globals.Config.Scan.CodeFlowLimit).
		SetSarifToolVersion(localVersion).
		SetSarifToolSemanticVersion(localSemanticVersion).
		SetSarifUriBase(uriBase).
		SetIfdsAnalysisTimeout(int64(globals.Config.Scan.Timeout / time.Second)).
		SetRuleLoadTracePath(absSemgrepRuleLoadTracePath).
		EnablePartialFingerprints()
	if SemgrepCompatibilitySarif {
		nativeBuilder.EnableSemgrepCompatibility()
	}
	for _, severity := range Severity {
		nativeBuilder.AddSeverity(severity)
	}
	for _, absRuleSetPath := range absRuleSetPaths {
		nativeBuilder.AddRuleSet(absRuleSetPath.Path)
	}
	if maxMemory != "" {
		nativeBuilder.SetMaxMemory(maxMemory)
	}

	analyzerJarPath, err := ensureAnalyzerAvailable()
	if err != nil {
		out.Fatalf("Native scan preparation failed: %s", err)
	}
	nativeBuilder.SetJarPath(analyzerJarPath)

	analyzerJavaRunner := java.NewJavaRunner().
		WithSkipVerify(globals.Config.SkipVerify).
		WithDebugOutput(out.DebugStream("Analyzer")).
		WithImageType(java.AdoptiumImageJRE).
		TrySpecificVersion(globals.DefaultJavaVersion)
	if _, err := analyzerJavaRunner.EnsureJava(); err != nil {
		out.Fatalf("Failed to resolve Java for analyzer: %s", err)
	}

	if err := out.RunWithSpinner("Analyzing project", func() error {
		return scanProject(nativeBuilder, analyzerJavaRunner)
	}); err != nil {
		out.Fatalf("Native scan has failed: %s", err)
	}

	if _, err := os.Stat(absSarifReportPath); err != nil {
		out.Fatalf("There was a problem during the scan step, check the full logs: %s", globals.LogPath)
	}

	out.Blank()

	el := deserializeSemgrepRuleLoadTrace(absSemgrepRuleLoadTracePath)

	var nonBuiltinRulesetPaths []string
	for _, r := range absRuleSetPaths {
		if !r.Builtin {
			nonBuiltinRulesetPaths = append(nonBuiltinRulesetPaths, r.Path)
		}
	}
	ruleLoadTraceSummary := load_trace.CollectRuleLoadTraceSummary(el, nonBuiltinRulesetPaths)

	res := load_trace.CollectRulesetLoadErrorsSummary(ruleLoadTraceSummary)
	ruleLoadErrorsResult := &res

	report := loadSarifReport(absSarifReportPath)
	if report == nil {
		return
	}

	sarifSummary := sarif.GenerateSummary(report)
	load_trace.PrintRuleStatisticsTree(out, ruleLoadErrorsResult, absSemgrepRuleLoadTracePath, sarifSummary)

	load_trace.PrintSyntaxErrorReport(out, ruleLoadTraceSummary)

	// Process the generated SARIF report if it exists
	printSarifSummary(report, absSarifReportPath)

	if SarifReportPath == "" {
		utils.RemoveIfExistsOrExit(absSarifReportPath)
	} else {
		suggest("To view findings run", fmt.Sprintf("seqra summary --show-findings %s", absSarifReportPath))
	}

	// Clean up temporary directory if it was created
	if tempProjectModel && tempLogsDir != "" {
		if err := os.RemoveAll(filepath.Dir(absProjectModelPath)); err != nil {
			output.LogInfof("Failed to remove temporary directory %s: %v", filepath.Dir(absProjectModelPath), err)
		} else {
			output.LogDebugf("Removed temporary directory: %s", filepath.Dir(absProjectModelPath))
		}
	}
}

func printScanInfo(cmd *cobra.Command, mode ScanMode, absProjectModelPath string, absSemgrepRuleLoadTracePath string, tempProjectModel bool, absUserProjectRoot string, absRuleSetPaths []RulesetType) {
	sb := out.Section(mode.String())
	addConfigFields(cmd, sb)
	if globals.Config.Log.Verbosity == "debug" {
		sb.Field("Rule load trace", absSemgrepRuleLoadTracePath)
		sb.Line()
	}
	if tempProjectModel {
		sb.Field("Project", absUserProjectRoot).
			Field("Temporary project model", absProjectModelPath)
	} else {
		sb.Field("Project model", absProjectModelPath)
	}
	for _, r := range absRuleSetPaths {
		if r.Builtin {
			sb.Field("Bundled ruleset", globals.Config.Rules.Version)
		} else {
			sb.Field("User ruleset", r.Path)
		}
	}
	sb.Render()
}

func validateScanInputs() error {
	if globals.Config.Scan.MaxMemory != "" {
		if _, err := utils.ParseMemoryValue(globals.Config.Scan.MaxMemory); err != nil {
			return fmt.Errorf("invalid max-memory value: %w", err)
		}
	}
	for _, severity := range Severity {
		switch severity {
		case "error", "warning", "note":
		default:
			return fmt.Errorf(`each "severity" flag should be one of note, warning, or error`)
		}
	}
	return nil
}

func setupSemgrepRuleLoadTrace() string {
	absSemgrepRuleLoadTracePath, err := load_trace.GenerateSemgrepRuleLoadTraceFilePath()
	if err != nil {
		out.Fatalf("Failed to generate rule load trace file path: \"%s\": %v", absSemgrepRuleLoadTracePath, err)
	}

	if err = utils.RemoveIfExists(absSemgrepRuleLoadTracePath); err != nil {
		out.Fatalf("Failed to remove existing rule load trace file: \"%s\": %v", absSemgrepRuleLoadTracePath, err)
	}

	// Rule load trace path is now displayed in the tree format
	return absSemgrepRuleLoadTracePath
}

func deserializeSemgrepRuleLoadTrace(absSemgrepRuleLoadTracePath string) *load_trace.SemgrepLoadTrace {
	data, err := os.ReadFile(absSemgrepRuleLoadTracePath)
	if err != nil {
		output.LogInfof("Failed to read rule load trace file \"%s\": %v", absSemgrepRuleLoadTracePath, err)
		return nil
	}

	var el load_trace.SemgrepLoadTrace
	if err := json.Unmarshal(data, &el); err != nil {
		output.LogInfof("Failed to deserialize load trace file \"%s\": %v", absSemgrepRuleLoadTracePath, err)
		return nil
	}
	return &el
}

func ensureAnalyzerAvailable() (string, error) {
	analyzerJarPath, err := utils.GetAnalyzerJarPath(globals.Config.Analyzer.Version)
	if err != nil {
		return "", fmt.Errorf("failed to construct path to the analyzer: %w", err)
	}

	if err := ensureArtifactAvailable("analyzer", globals.Config.Analyzer.Version, analyzerJarPath, func() error {
		return utils.DownloadGithubReleaseAsset(globals.Config.Owner, globals.AnalyzerRepoName, globals.Config.Analyzer.Version, globals.AnalyzerAssetName, analyzerJarPath, globals.Config.Github.Token, globals.Config.SkipVerify, out)
	}); err != nil {
		return "", err
	}

	return analyzerJarPath, nil
}

func scanProject(analyzerBuilder *AnalyzerBuilder, javaRunner java.JavaRunner) error {
	analyzerCommand := analyzerBuilder.BuildNativeCommand()

	commandSucceeded := func(err error) bool {
		if err != nil {
			output.LogDebugf("Analyzer failed: %v", err)
			return false
		}
		return true
	}
	// Execute the command using JavaRunner
	err := javaRunner.ExecuteJavaCommand(analyzerCommand, commandSucceeded)

	return err
}
