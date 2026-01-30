package cmd

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"time"

	"github.com/seqra/seqra/v2/internal/load_trace"
	"github.com/seqra/seqra/v2/internal/version"

	"github.com/seqra/seqra/v2/internal/utils/formatters"
	"github.com/seqra/seqra/v2/internal/utils/project"
	"github.com/sirupsen/logrus"
	"github.com/spf13/cobra"
	"github.com/spf13/viper"

	"github.com/seqra/seqra/v2/internal/globals"
	"github.com/seqra/seqra/v2/internal/sarif"
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
	Long: `This command automatically detects Java/Kotlin build systems, build project and analyze it

Arguments:
  project  - Path to a project or a project model (required)
`,
	Annotations: map[string]string{"PrintConfig": "true"},
	PreRun: func(cmd *cobra.Command, args []string) {
		bindCompileTypeFlag(cmd)
		bindScanTypeFlag(cmd)
	},
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
}

func scan(cmd *cobra.Command) {
	var absProjectModelPath string
	var tempLogsDir string // Store the temp directory name for cleanup

	userProjectPath := UserProjectPath
	userProjectPath = filepath.Clean(userProjectPath)
	absUserProjectRoot := log.AbsPathOrExit(userProjectPath, "project path")

	logrus.Info()
	tempProjectModel := false
	var tempProjectModelPath string

	if !utils.IsSupportedArch() {
		logrus.Fatalf("Unsupported architecture found: %s! Only arm64 and amd64 are supported.", utils.GetArch())
	}

	// Resolve project type
	var scanMode ScanMode
	logrus.Debugf("Trying to define %v is a project model or a project", absUserProjectRoot)
	if _, err := os.Stat(filepath.Join(absUserProjectRoot, "project.yaml")); err == nil {
		scanMode = Scan
		absProjectModelPath = absUserProjectRoot
	} else if errors.Is(err, os.ErrNotExist) {
		tempProjectModel = true
		scanMode = CompileAndScan
		tempLogsDir, err = os.MkdirTemp("", "seqra-*")
		if err != nil {
			logrus.Fatalf("Failed to create temporary directory: %s", err)
		}
		tempProjectModelPath = filepath.Join(tempLogsDir, "project-model")
		absProjectModelPath = tempProjectModelPath
	} else {
		logrus.Fatalf("Unexpected error occurred while checking the project: %s", err)
	}

	var absRuleSetPaths = []RulesetType{}
	var userRuleSetPath = Ruleset

	for _, ruleset := range userRuleSetPath {
		switch ruleset {
		case "builtin":
			rulesPath, err := utils.GetRulesPath(globals.Config.Rules.Version)
			if err != nil {
				logrus.Fatalf("Unexpected error occurred while trying to construct path to the ruleset: %s", err)
			}

			if _, err := os.Stat(rulesPath); errors.Is(err, os.ErrNotExist) {
				logrus.Info("Downloading seqra-rules")
				err := utils.DownloadAndUnpackGithubReleaseAsset(globals.GetRepoOwner(), globals.RulesRepoName, globals.Config.Rules.Version, globals.RulesAssetName, rulesPath, globals.Config.Github.Token)
				if err != nil {
					logrus.Fatalf("Unexpected error occurred while trying to download ruleset: %s", err)
				}
				logrus.Infof("Successfully downloaded seqra-rules to %s", rulesPath)

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
			logrus.Fatalf("Failed to parse sourceRoot from project.yaml: %v", err)
		} else {
			sourceRoot = parsedSourceRoot
		}
	} else {
		sourceRoot = absUserProjectRoot
	}

	uriBase := fmt.Sprintf("%s%s", sourceRoot, string(filepath.Separator))

	absSemgrepRuleLoadTracePath := setupSemgrepRuleLoadTrace()

	// Display scan information in tree format
	printScanInfo(cmd, scanMode, absProjectModelPath, absRuleSetPaths, absSemgrepRuleLoadTracePath, tempProjectModel, absUserProjectRoot)

	if tempProjectModel {
		if err := compile(absUserProjectRoot, tempProjectModelPath, Internal); err != nil {
			suggest("If native compilation fails due to missing required Java, set JAVA_HOME according to the project's requirements or try Docker-based compilation:", "")
			logrus.Fatal()
		}
	}

	maxMemory := ""
	if globals.Config.Scan.MaxMemory != "" {
		parsedMaxMemory, err := utils.ParseMemoryValue(globals.Config.Scan.MaxMemory)
		if err != nil {
			logrus.Fatalf("Invalid max-memory value: %s", err)
		}
		maxMemory = parsedMaxMemory
	}

	// Update builder with native paths for native execution
	nativeProjectPath := filepath.Join(absProjectModelPath, "project.yaml")
	nativeOutputDir := filepath.Dir(absSarifReportPath)
	nativeBuilder := NewAnalyzerBuilder().
		SetProject(nativeProjectPath).
		SetOutputDir(nativeOutputDir).
		SetSarifFileName(sarifReportName).
		SetSarifThreadFlowLimit("1").
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
		switch severity {
		case "error", "warning", "note":
			nativeBuilder.AddSeverity(severity)
		default:
			logrus.Fatalf(`The each "severity" flag should be one of note, warning, or error.`)
		}
	}
	for _, absRuleSetPath := range absRuleSetPaths {
		nativeBuilder.AddRuleSet(absRuleSetPath.Path)
	}
	if maxMemory != "" {
		nativeBuilder.SetMaxMemory(maxMemory)
	}
	scanProject(nativeBuilder)

	if _, err := os.Stat(absSarifReportPath); err != nil {
		logrus.Fatalf("There was a problem during the scan step, check the full logs: %s", globals.LogPath)
	}

	logrus.Info()

	el := deserializeSemgrepRuleLoadTrace(absSemgrepRuleLoadTracePath)

	ruleLoadTraceSummary := load_trace.CollectRuleLoadTraceSummary(el)

	res := load_trace.CollectRulesetLoadErrorsSummary(ruleLoadTraceSummary)
	ruleLoadErrorsResult := &res

	report := loadSarifReport(absSarifReportPath)
	if report == nil {
		return
	}

	sarifSummary := sarif.GenerateSummary(report)
	load_trace.PrintRuleStatisticsTree(ruleLoadErrorsResult, sarifSummary, absSemgrepRuleLoadTracePath)

	load_trace.PrintSyntaxErrorReport(ruleLoadTraceSummary)

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
			logrus.Warnf("Failed to remove temporary directory %s: %v", filepath.Dir(absProjectModelPath), err)
		} else {
			logrus.Debugf("Removed temporary directory: %s", filepath.Dir(absProjectModelPath))
		}
	}
}

func printScanInfo(cmd *cobra.Command, mode ScanMode, absProjectModelPath string, absRuleSetPaths []RulesetType, absSemgrepRuleLoadTracePath string, tempProjectModel bool, absUserProjectRoot string) {
	logrus.Info(formatters.FormatTreeHeader(mode.String()))

	printer := formatters.NewTreePrinter()
	printConfig(cmd, printer)
	printer.AddNode("")
	if tempProjectModel {
		printer.AddNode("Project: " + absUserProjectRoot)
		printer.AddNode("Temporary project model: " + absProjectModelPath)
	} else {
		printer.AddNode("Project model: " + absProjectModelPath)
	}
	printer.AddNode("")
	for _, absRuleSetPath := range absRuleSetPaths {
		if absRuleSetPath.Builtin {
			printer.AddNode("Bundled ruleset: " + absRuleSetPath.Path)
		} else {
			printer.AddNode("User ruleset: " + absRuleSetPath.Path)
		}
	}
	printer.AddNode("Rule load trace: " + absSemgrepRuleLoadTracePath)

	printer.Print()
}

func setupSemgrepRuleLoadTrace() string {
	absSemgrepRuleLoadTracePath, err := load_trace.GenerateSemgrepRuleLoadTraceFilePath()
	if err != nil {
		logrus.Fatalf("Failed to generate rule load trace file path: \"%s\": %v", absSemgrepRuleLoadTracePath, err)
	}

	if err = utils.RemoveIfExists(absSemgrepRuleLoadTracePath); err != nil {
		logrus.Fatalf("Failed to remove existing rule load trace file: \"%s\": %v", absSemgrepRuleLoadTracePath, err)
	}

	// Rule load trace path is now displayed in the tree format
	return absSemgrepRuleLoadTracePath
}

func deserializeSemgrepRuleLoadTrace(absSemgrepRuleLoadTracePath string) *load_trace.SemgrepLoadTrace {
	data, err := os.ReadFile(absSemgrepRuleLoadTracePath)
	if err != nil {
		logrus.Errorf("Failed to read rule load trace file \"%s\": %v", absSemgrepRuleLoadTracePath, err)
		return nil
	}

	var el load_trace.SemgrepLoadTrace
	if err := json.Unmarshal(data, &el); err != nil {
		logrus.Errorf("Failed to deserialize load trace file \"%s\": %v", absSemgrepRuleLoadTracePath, err)
		return nil
	}
	return &el
}

func scanProject(analyzerBuilder *AnalyzerBuilder) {
	// Get the path to the analyzer JAR
	analyzerJarPath, err := utils.GetAnalyzerJarPath(globals.Config.Analyzer.Version)
	if err != nil {
		logrus.Fatalf("Failed to construct path to the analyzer: %s", err)
	}

	// Download the analyzer JAR if it doesn't exist
	if _, err := os.Stat(analyzerJarPath); errors.Is(err, os.ErrNotExist) {
		logrus.Info()
		logrus.Infof("Downloading analyzer version %s", globals.Config.Analyzer.Version)
		if err := utils.DownloadGithubReleaseAsset(globals.GetRepoOwner(), globals.AnalyzerRepoName, globals.Config.Analyzer.Version, globals.AnalyzerAssetName, analyzerJarPath, globals.Config.Github.Token); err != nil {
			logrus.Fatalf("Failed to download analyzer: %s", err)
		}
		logrus.Infof("Successfully downloaded analyzer to %s", analyzerJarPath)
	}

	// Set the jar path on the builder and build the command
	analyzerCommand := analyzerBuilder.SetJarPath(analyzerJarPath).BuildNativeCommand()

	javaRunner := java.NewJavaRunner().TrySpecificVersion(java.DefaultJavaVersion)

	commandSucceeded := func(err error) bool {
		if err != nil {
			logrus.Errorf("Analyzer failed: %v", err)
			return false
		}
		return true
	}
	// Execute the command using JavaRunner
	err = javaRunner.ExecuteJavaCommand(analyzerCommand, commandSucceeded)

	if err != nil {
		logrus.Errorf("Native scan has failed: %s", err)
	}
}
