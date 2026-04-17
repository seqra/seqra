package cmd

import (
	"fmt"
	"os"
	"path/filepath"
	"time"

	"github.com/seqra/opentaint/internal/load_trace"
	"github.com/seqra/opentaint/internal/sarif"
	"github.com/seqra/opentaint/internal/validation"
	"github.com/seqra/opentaint/internal/version"

	"github.com/seqra/opentaint/internal/utils/project"
	"github.com/spf13/cobra"
	"github.com/spf13/viper"

	"github.com/seqra/opentaint/internal/globals"
	"github.com/seqra/opentaint/internal/output"
	"github.com/seqra/opentaint/internal/utils"
	"github.com/seqra/opentaint/internal/utils/java"
	"github.com/seqra/opentaint/internal/utils/log"
)

var (
	UserProjectPath           string
	ProjectModelPath          string
	SarifReportPath           string
	SemgrepCompatibilitySarif bool
	Severity                  []string
	Ruleset                   []string
	DryRunScan                bool
	Recompile                 bool
	ScanLogFile               string
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
	dryRunScanProjectModelPath  = "opentaint-scan-dry-run/project-model"
	dryRunRuleLoadTraceFileName = "opentaint-rule-load-trace.dry-run.json"
)

func (m ScanMode) String() string {
	switch m {
	case Scan:
		return "OpenTaint Scan"
	case CompileAndScan:
		return "OpenTaint Compile and Scan"
	default:
		return "Unknown"
	}
}

// scanConfig holds the resolved paths and flags for a scan invocation.
type scanConfig struct {
	mode             ScanMode
	absProjectModel  string // absolute path to the project model (always the cache dir when projectCachePath is set)
	projectCachePath string // cache dir for this project (empty for explicit model / dry-run)
	needsCompilation bool   // true when compilation is needed before scanning
	cacheLock        *utils.FileLock
}

// scanCmd represents the scan command
var scanCmd = &cobra.Command{
	Use:   "scan [source-path]",
	Short: "Scan your Java or Kotlin project",
	Args:  cobra.MaximumNArgs(1),
	Long: `This command automatically detects Java/Kotlin build systems, builds the project, and analyzes it

Arguments:
  source-path  - Path to the project sources (default: current directory)

Use --project-model to scan a pre-compiled project model instead of compiling from sources.
`,
	Annotations: map[string]string{"PrintConfig": "true"},
	Run: func(cmd *cobra.Command, args []string) {
		if len(args) > 0 && ProjectModelPath != "" {
			out.Error("Cannot use both a source path argument and --project-model flag")
			suggest("Use either a source path or --project-model",
				utils.NewScanCommand("<source-path>").Build()+"\n  "+utils.NewScanCommand("").WithProjectModel("<model-path>").Build())
			os.Exit(1)
		}
		if Recompile && ProjectModelPath != "" {
			out.Fatalf("Cannot use --recompile with --project-model; the flag only applies when compiling from sources")
		}
		if len(args) > 0 {
			UserProjectPath = args[0]
		} else {
			UserProjectPath = "."
		}
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

	scanCmd.Flags().StringArrayVar(&Severity, "severity", []string{"warning", "error"}, "Report findings only from rules matching the supplied severity level. By default only warning and error rules are run (note, warning, error)")
	scanCmd.Flags().StringVar(&globals.Config.Scan.MaxMemory, "max-memory", "8G", "Maximum memory for the analyzer (e.g., 1024m, 8G, 81920k, 83886080)")
	_ = viper.BindPFlag("scan.max_memory", scanCmd.Flags().Lookup("max-memory"))
	scanCmd.Flags().Int64Var(&globals.Config.Scan.CodeFlowLimit, "code-flow-limit", 0, "Maximum number of code flows to include in the report (0 = unlimited)")
	_ = scanCmd.PersistentFlags().MarkHidden("code-flow-limit")
	_ = viper.BindPFlag("scan.code_flow_limit", scanCmd.Flags().Lookup("code-flow-limit"))
	scanCmd.Flags().BoolVar(&DryRunScan, "dry-run", false, "Validate inputs and show what would run without compiling or scanning")
	scanCmd.Flags().BoolVar(&Recompile, "recompile", false, "Force recompilation even if a cached project model exists")
	scanCmd.Flags().StringVar(&ProjectModelPath, "project-model", "", "Path to a pre-compiled project model (skips compilation)")
	scanCmd.Flags().StringVar(&ScanLogFile, "log-file", "", "Path to the log file (default: <cache-dir>/logs/<timestamp>.log)")
}

// currentScanBuilder returns a builder pre-populated with the user's current scan flags.
// All scan command suggestions should use this as the base to ensure that adding a new
// flag in one place automatically propagates to every suggestion.
func currentScanBuilder(sourcePath string) *utils.OpentaintCommandBuilder {
	return utils.NewScanCommand(sourcePath).
		WithOutput(SarifReportPath).
		WithTimeout(globals.Config.Scan.Timeout).
		WithRuleset(Ruleset).
		WithSemgrepCompatibility(SemgrepCompatibilitySarif)
}

func scan(cmd *cobra.Command) {
	userProjectPath := filepath.Clean(UserProjectPath)
	absUserProjectRoot := log.AbsPathOrExit(userProjectPath, "project path")

	if !utils.IsSupportedArch() {
		out.Fatalf("Unsupported architecture found: %s! Only arm64 and amd64 are supported.", utils.GetArch())
	}

	// When compiling from sources, validate the source folder looks like a Java/Kotlin project
	if ProjectModelPath == "" {
		if err := validation.ValidateSourceProject(absUserProjectRoot); err != nil {
			if validation.IsProjectModel(absUserProjectRoot) {
				out.ErrorErr(err)
				suggest("Use --project-model to scan a pre-compiled model", currentScanBuilder("").WithProjectModel(absUserProjectRoot).Build())
				os.Exit(1)
			}
			out.FatalErr(err)
		}
	}

	cfg := resolveScanConfig(absUserProjectRoot)
	defer func() {
		if cfg.cacheLock != nil {
			cfg.cacheLock.Unlock()
		}
	}()

	// Activate logging
	if !DryRunScan {
		activateLoggingForProject(ScanLogFile, absUserProjectRoot)
	}

	absProjectModelPath := cfg.absProjectModel

	var absRuleSetPaths []RulesetType
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
		absSarifReportPath = utils.DefaultSarifReportPath(absProjectModelPath)
	}

	sarifReportName := filepath.Base(absSarifReportPath)

	localVersion := globals.Config.Analyzer.Version
	localSemanticVersion := version.GetVersion()

	var sourceRoot string
	if !cfg.needsCompilation {
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
	printScanInfo(cmd, cfg, absSemgrepRuleLoadTracePath, absUserProjectRoot, absRuleSetPaths)

	var nonBuiltinRulesetPaths []string
	for _, r := range absRuleSetPaths {
		if !r.Builtin {
			nonBuiltinRulesetPaths = append(nonBuiltinRulesetPaths, r.Path)
		}
	}

	maxMemory, err := validation.ValidateScanInputs(absUserProjectRoot, absProjectModelPath, absSarifReportPath, nonBuiltinRulesetPaths, Severity, globals.Config.Scan.MaxMemory, cfg.mode == Scan)
	if err != nil {
		out.Fatalf("Input validation failed: %s", err)
	}

	if DryRunScan {
		runDryRun("Compilation and analysis")
		return
	}

	for _, ruleSetPath := range absRuleSetPaths {
		if !ruleSetPath.Builtin {
			continue
		}
		if _, err := os.Stat(ruleSetPath.Path); err == nil {
			continue
		}
		if err := utils.DownloadAndUnpackGithubReleaseAsset(globals.Config.Owner, globals.Config.Repo, globals.Config.Rules.Version, globals.RulesAssetName, ruleSetPath.Path, globals.Config.Github.Token, globals.Config.SkipVerify, out); err != nil {
			out.Fatalf("Unexpected error occurred while trying to download ruleset: %s", err)
		}
	}

	if cfg.needsCompilation {
		autobuilderJarPath, err := ensureAutobuilderAvailable()
		if err != nil {
			out.Fatalf("Native compile preparation failed: %s", err)
		}

		compileJavaRunner := java.NewJavaRunner().
			WithSkipVerify(globals.Config.SkipVerify).
			WithStreamOutput(globals.Config.Quiet).
			WithDebugOutput(out.DebugStream("Autobuilder")).
			TrySystem().
			TrySpecificVersion(globals.Config.Java.Version)
		if _, err := compileJavaRunner.EnsureJava(); err != nil {
			out.Fatalf("Failed to resolve Java for compilation: %s", err)
		}

		// Wipe any residue from a prior crashed compile before writing new output.
		if cfg.projectCachePath != "" {
			if err := os.RemoveAll(cfg.absProjectModel); err != nil {
				out.Fatalf("Failed to prepare cache directory: %s", err)
			}
		}

		if err := out.RunWithSpinner("Compiling project model", func() error {
			return compile(absUserProjectRoot, cfg.absProjectModel, autobuilderJarPath, compileJavaRunner, Internal)
		}); err != nil {
			if cfg.projectCachePath != "" {
				_ = os.RemoveAll(cfg.absProjectModel)
			}
			out.Error("Native compile has failed: " + err.Error())
			suggest("If native compilation fails due to missing required Java, set JAVA_HOME according to the project's requirements or try Docker-based scan:", utils.BuildScanCommandWithDocker(currentScanBuilder(""), absUserProjectRoot, absSarifReportPath, Ruleset))
			os.Exit(1)
		}
		out.Blank()

		// Mark the cache as valid, then downgrade to a reader so other scans
		// can run the analyzer against the freshly-compiled model in parallel.
		if cfg.projectCachePath != "" {
			if err := utils.MarkCompileComplete(cfg.projectCachePath); err != nil {
				_ = os.RemoveAll(cfg.absProjectModel)
				out.Fatalf("Failed to mark model complete: %s", err)
			}
			if err := cfg.cacheLock.Downgrade(); err != nil {
				output.LogInfof("Cache lock downgrade failed, continuing under exclusive: %v", err)
			}
		}

		printCompileSummary(absProjectModelPath)
	}

	if err := utils.EnsureParentDir(absSarifReportPath); err != nil {
		out.Fatalf("Failed to create output directory: %s", err)
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
		WithStreamOutput(globals.Config.Quiet).
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

	report, err := validation.ValidateSarifOutput(absSarifReportPath)
	if err != nil {
		output.LogInfof("Scan output validation failed: %v", err)
		out.Fatalf("There was a problem during the scan step, check the full logs: %s", globals.LogPath)
	}

	out.Blank()

	el, err := validation.ValidateRuleLoadTraceOutput(absSemgrepRuleLoadTracePath)
	if err != nil {
		out.Fatalf("Failed to validate rule load trace output: %s", err)
	}
	ruleLoadTraceSummary := load_trace.CollectRuleLoadTraceSummary(el, nonBuiltinRulesetPaths)

	res := load_trace.CollectRulesetLoadErrorsSummary(ruleLoadTraceSummary)
	ruleLoadErrorsResult := &res

	sarifSummary := sarif.GenerateSummary(report)
	load_trace.PrintRuleStatisticsTree(out, ruleLoadErrorsResult, absSemgrepRuleLoadTracePath, sarifSummary)

	load_trace.PrintSyntaxErrorReport(out, ruleLoadTraceSummary)

	// Process the generated SARIF report if it exists
	printSarifSummary(report, absSarifReportPath)

	suggest("To view findings run", utils.NewSummaryCommand(absSarifReportPath).WithShowFindings().Build())
}

func resolveScanConfig(absUserProjectRoot string) scanConfig {
	if ProjectModelPath != "" {
		return scanConfig{
			mode:            Scan,
			absProjectModel: log.AbsPathOrExit(filepath.Clean(ProjectModelPath), "project model path"),
		}
	}

	if DryRunScan {
		dryRunPath := filepath.Join(os.TempDir(), dryRunScanProjectModelPath)
		return scanConfig{
			mode:             CompileAndScan,
			absProjectModel:  dryRunPath,
			needsCompilation: true,
		}
	}

	projectCachePath, err := utils.GetProjectCachePath(absUserProjectRoot)
	if err != nil {
		out.Fatalf("Failed to create model cache directory: %s", err)
	}

	cachedModelPath := utils.CachedProjectModelPath(projectCachePath)
	cacheLockPath := utils.CacheLockPath(projectCachePath)

	// Fast path: if we're not forced to recompile and the cache looks
	// complete on disk, take a shared lock and re-check under the lock.
	if !Recompile && utils.IsCachedModelComplete(projectCachePath) {
		sharedLock, sharedErr := utils.TryLockShared(cacheLockPath)
		if sharedErr == nil {
			if utils.IsCachedModelComplete(projectCachePath) {
				output.LogDebugf("Reusing cached model at: %s", cachedModelPath)
				return scanConfig{
					mode:             Scan,
					absProjectModel:  cachedModelPath,
					projectCachePath: projectCachePath,
					cacheLock:        sharedLock,
				}
			}
			// Marker vanished between the outer check and the lock
			// (writer raced ahead of us). Fall through to compile path.
			sharedLock.Unlock()
		} else if sharedErr != utils.ErrLocked {
			out.Fatalf("Failed to acquire cache read lock: %s", sharedErr)
		}
		// sharedErr == ErrLocked means a writer holds the cache; we're about
		// to ask for exclusive below, which will also fail with ErrLocked and
		// produce the same "compilation already in progress" message.
	}

	cacheLock, lockErr := utils.TryLockExclusive(
		cacheLockPath,
		utils.LockMeta{PID: os.Getpid(), Command: "compile", Project: absUserProjectRoot},
	)
	if lockErr == utils.ErrLocked {
		out.Error("Compilation already in progress for this project")
		suggest("To scan an existing model instead", utils.NewScanCommand("").WithProjectModel("<model-path>").Build())
		os.Exit(1)
	}
	if lockErr != nil {
		out.Fatalf("Failed to acquire cache lock: %s", lockErr)
	}

	return scanConfig{
		mode:             CompileAndScan,
		absProjectModel:  cachedModelPath,
		projectCachePath: projectCachePath,
		needsCompilation: true,
		cacheLock:        cacheLock,
	}
}

func printScanInfo(cmd *cobra.Command, cfg scanConfig, absSemgrepRuleLoadTracePath string, absUserProjectRoot string, absRuleSetPaths []RulesetType) {
	sb := out.Section(cfg.mode.String())
	addConfigFields(cmd, sb)
	if globals.Config.Log.Verbosity == "debug" {
		sb.Field("Rule load trace", absSemgrepRuleLoadTracePath)
		sb.Line()
	}
	if cfg.needsCompilation {
		sb.Field("Project", absUserProjectRoot)
		if cfg.projectCachePath != "" {
			sb.Field("Project model", cfg.absProjectModel)
		}
	} else {
		sb.Field("Project model", cfg.absProjectModel)
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

func ensureAnalyzerAvailable() (string, error) {
	analyzerJarPath, err := utils.GetAnalyzerJarPath(globals.Config.Analyzer.Version)
	if err != nil {
		return "", fmt.Errorf("failed to construct path to the analyzer: %w", err)
	}

	if err := ensureArtifactAvailable("analyzer", globals.Config.Analyzer.Version, analyzerJarPath, func() error {
		return utils.DownloadGithubReleaseAsset(globals.Config.Owner, globals.Config.Repo, globals.Config.Analyzer.Version, globals.AnalyzerAssetName, analyzerJarPath, globals.Config.Github.Token, globals.Config.SkipVerify, out)
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
