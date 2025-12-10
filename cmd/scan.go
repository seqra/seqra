package cmd

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/sirupsen/logrus"
	"github.com/spf13/cobra"
	"github.com/spf13/viper"

	"github.com/docker/docker/api/types/container"

	"github.com/seqra/seqra/internal/container_run"
	"github.com/seqra/seqra/internal/globals"
	"github.com/seqra/seqra/internal/load_errors"
	"github.com/seqra/seqra/internal/sarif"
	"github.com/seqra/seqra/internal/utils"
	"github.com/seqra/seqra/internal/utils/java"
	"github.com/seqra/seqra/internal/utils/log"
	"github.com/seqra/seqra/internal/utils/project"
)

var UserProjectPath string
var SarifReportPath string
var OnlyScan bool
var RuleSetLoadErrorsPath string
var SemgrepCompatibilitySarif bool

// scanCmd represents the scan command
var scanCmd = &cobra.Command{
	Use:   "scan project",
	Short: "Scan your Java project",
	Args:  cobra.MinimumNArgs(1), // require at least one argument
	Long: `This command automatically detects Java build system, build project and analyze it

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
		scan()
	},
}

func init() {
	rootCmd.AddCommand(scanCmd)

	scanCmd.Flags().DurationVarP(&globals.Config.Scan.Timeout, "timeout", "t", 900*time.Second, "Timeout for analysis")
	_ = viper.BindPFlag("scan.timeout", scanCmd.Flags().Lookup("timeout"))

	scanCmd.Flags().StringVar(&globals.Config.Scan.Ruleset, "ruleset", "", "Directory containing YAML rules")
	_ = viper.BindPFlag("scan.ruleset", scanCmd.Flags().Lookup("ruleset"))

	scanCmd.Flags().StringVar(&globals.Config.Compile.Type, "compile-type", "docker", "Environment for run compile command (docker, native)")
	scanCmd.Flags().StringVar(&globals.Config.Scan.Type, "scan-type", "docker", "Environment for run scan command (docker, native)")
	scanCmd.Flags().StringVar(&RuleSetLoadErrorsPath, "ruleset-load-errors", "", "Path to log ruleset load errors")
	scanCmd.Flags().BoolVar(&SemgrepCompatibilitySarif, "semgrep-compatibility-sarif", true, "Use Semgrep compatible ruleId")
	scanCmd.Flags().StringVarP(&SarifReportPath, "output", "o", "", "Path to the SARIF-report output file")
	_ = scanCmd.MarkFlagRequired("output")
	scanCmd.Flags().BoolVar(&OnlyScan, "only-scan", false, "Only scan the project, expecting a project model")
}

const defaultDataPath = "/data"

func scan() {
	var absProjectModelPath string
	var tempDirName string // Store the temp directory name for cleanup

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
	if OnlyScan {
		logrus.Infof("=== Scan only mode===")
		absProjectModelPath = absUserProjectRoot
	} else {
		logrus.Debugf("Trying to define %v is a project model or a project", absUserProjectRoot)
		if _, err := os.Stat(absUserProjectRoot + "/project.yaml"); err == nil {
			logrus.Infof("=== Scan only mode===")
			absProjectModelPath = absUserProjectRoot
		} else if errors.Is(err, os.ErrNotExist) {
			tempProjectModel = true
			logrus.Infof("=== Compile and Scan mode ===")
			tempDirName, err = os.MkdirTemp("", "seqra-*")
			if err != nil {
				logrus.Fatalf("Failed to create temporary directory: %s", err)
			}
			tempProjectModelPath = tempDirName + "/project-model"
			absProjectModelPath = tempProjectModelPath
		} else {
			logrus.Fatalf("Unexpected error occurred while checking the project: %s", err)
		}
	}
	if tempProjectModel {
		logrus.Infof("Project: %s", absUserProjectRoot)
		logrus.Infof("Temporary project model: %s", absProjectModelPath)
	} else {
		logrus.Infof("Project model: %s", absProjectModelPath)
	}

	var sourceRoot string
	if !tempProjectModel {
		if parsedSourceRoot, err := project.GetSourceRoot(absProjectModelPath); err != nil {
			logrus.Fatalf("Failed to parse sourceRoot from project.yaml: %v", err)
		} else {
			sourceRoot = parsedSourceRoot
		}
	}

	var resultbase = defaultDataPath
	var absRuleSetPath string
	var userRuleSetPath = globals.Config.Scan.Ruleset

	if userRuleSetPath != "" {
		absRuleSetPath = log.AbsPathOrExit(userRuleSetPath, "ruleset")
		if strings.HasPrefix(absRuleSetPath, defaultDataPath) {
			resultbase = "/projectData"
		}
		logrus.Infof("User ruleset: %s", absRuleSetPath)
	} else {
		rulesPath, err := utils.GetRulesPath(globals.RulesBindVersion)
		if err != nil {
			logrus.Fatalf("Unexpected error occurred while trying to construct path to the ruleset: %s", err)
		}

		if _, err := os.Stat(rulesPath); errors.Is(err, os.ErrNotExist) {
			logrus.Info("Download seqra-rules")
			err := utils.DownloadAndUnpackGithubReleaseArchive(globals.RepoOwner, globals.RulesRepoName, globals.RulesBindVersion, rulesPath, globals.Config.Github.Token)
			if err != nil {
				logrus.Fatalf("Unexpected error occurred while trying to download ruleset: %s", err)
			}
		}

		absRuleSetPath = rulesPath
		logrus.Infof("Use bundled ruleset: %s", absRuleSetPath)
	}

	dockerProjectPath := resultbase + "/project"
	dockerProjectYamlPath := dockerProjectPath + "/project.yaml"
	dockerOutputDir := resultbase + "/reports"
	dockerSarif := dockerOutputDir + "/report-ifds.sarif"
	dockerRulesetErrors := dockerOutputDir + "/rule-errors.json"

	hostConfig := &container.HostConfig{}

	// Get the current user's UID and GID
	containerUID := fmt.Sprintf("%d", os.Getuid())
	containerGID := fmt.Sprintf("%d", os.Getgid())

	envCont := []string{"CONTAINER_UID=" + containerUID, "CONTAINER_GID=" + containerGID}

	analyzerFlags := []string{
		"--project", dockerProjectYamlPath,
		"--output-dir", dockerOutputDir,
	}

	switch globals.Config.Log.Verbosity {
	case "info":
		analyzerFlags = append(analyzerFlags, "--verbosity=info")
	case "debug":
		analyzerFlags = append(analyzerFlags, "--verbosity=debug")
	}

	analyzerFlags = append(analyzerFlags, fmt.Sprintf("--ifds-analysis-timeout=%d", globals.Config.Scan.Timeout/time.Second))

	var copyToContainer = make(map[string]string)

	copyToContainer[absProjectModelPath] = dockerProjectPath

	var copyFromContainer = make(map[string]string)

	var absSarifReportPath string
	if SarifReportPath != "" {
		absSarifReportPath = log.AbsPathOrExit(SarifReportPath, "output")
	} else {
		absSarifReportPath = filepath.Join(os.TempDir(), "seqra-scan.sarif.temp")
	}

	copyFromContainer[dockerSarif] = absSarifReportPath
	utils.RemoveIfExistsOrExit(absSarifReportPath)

	if absRuleSetPath != "" {
		analyzerFlags = append(analyzerFlags, "--semgrep-rule-set")
		analyzerFlags = append(analyzerFlags, absRuleSetPath)
		copyToContainer[absRuleSetPath] = absRuleSetPath
	}

	var absRulesetLoadErrorsPath = ""
	if RuleSetLoadErrorsPath != "" {
		if absRuleSetPath == "" {
			logrus.Fatalf(`The "ruleset-load-errors" flag requires the "ruleset" flag to be specified.`)
		}

		absRulesetLoadErrorsPath = log.AbsPathOrExit(RuleSetLoadErrorsPath, "ruleset-load-errors")
		logrus.Infof("Load ruleset errors: %s", absRulesetLoadErrorsPath)

		analyzerFlags = append(analyzerFlags, "--semgrep-rule-load-errors")
		analyzerFlags = append(analyzerFlags, dockerRulesetErrors)
		copyFromContainer[dockerRulesetErrors] = absRulesetLoadErrorsPath
		utils.RemoveIfExistsOrExit(absRulesetLoadErrorsPath)
	}

	if tempProjectModel {
		compile(absUserProjectRoot, tempProjectModelPath, globals.Config.Compile.Type)
		// Check if compilation succeeded
		if _, err := os.Stat(tempProjectModelPath); err != nil {
			// Compilation failed, provide scan-specific suggestion
			logrus.Info("")
			logrus.Info("If native compilation fails due to missing required Java, set JAVA_HOME according to the project's requirements or try Docker-based compilation:")
			logrus.Infof("   %s", buildScanCommandWithDocker(
				UserProjectPath,
				SarifReportPath,
				globals.Config.Scan.Ruleset,
				RuleSetLoadErrorsPath,
				globals.Config.Scan.Timeout,
				SemgrepCompatibilitySarif,
				globals.Config.Scan.Type,
			))
			return
		}
	}

	logrus.Infof("Scan mode: %s", globals.Config.Scan.Type)
	switch globals.Config.Scan.Type {
	case "docker":
		scanWithDocker(analyzerFlags, envCont, hostConfig, copyToContainer, copyFromContainer)
	case "native":
		scanWithNative(absProjectModelPath, absSarifReportPath, absRuleSetPath, absRulesetLoadErrorsPath, analyzerFlags)
	default:
		logrus.Fatalf("scan-type must be one of \"docker\", \"native\"")
	}

	// Process the generated SARIF report if it exists
	report := PrintSarifSummary(absSarifReportPath, true)
	if report == nil {
		return
	}
	report.SetToolDriver()
	report.KeepOnlyOneCodeFlowElement()
	report.KeepOnlyFileLocations()

	logrus.Infof("Log file: %s", globals.LogPath)
	if SarifReportPath == "" {
		utils.RemoveIfExistsOrExit(absSarifReportPath)
	} else {
		logrus.Info()
		logrus.Infof("Full report: %s", absSarifReportPath)
		logrus.Infof("You can view findings by run:")
		logrus.Infof("   seqra summary --show-findings %s", absSarifReportPath)

        report.UpdateURIInfo(sourceRoot + "/")

		if SemgrepCompatibilitySarif {
			report.UpdateRuleId(absRuleSetPath, userRuleSetPath)
			// Write the modified SARIF back to the same file
			if err := sarif.WriteFile(report, absSarifReportPath); err != nil {
				logrus.Warnf("Failed to write modified SARIF report: %v", err)
				return
			}
			logrus.Debug("Successfully modified SARIF report")
		}
	}

	if absRulesetLoadErrorsPath != "" && SemgrepCompatibilitySarif {
		data, err := os.ReadFile(absRulesetLoadErrorsPath)
		if err != nil {
			logrus.Errorf("Can't modify semgrep rules load report: %v", err)
		} else {
			var el load_errors.ErrorsList
			err := el.UnmarshalJSON(data)
			if err != nil {
				logrus.Warnf("Can't parse Semgrep rules load report: %v", err)
			} else {
				el.UpdateRuleId(absRuleSetPath, userRuleSetPath)
				// Write the modified SARIF back to the same file
				if err := load_errors.SaveErrorsListToFile(el, absRulesetLoadErrorsPath); err != nil {
					logrus.Warnf("Failed to write modified Semgrep rules load report: %v", err)
					return
				}
				logrus.Debug("Successfully modified Semgrep rules load report")
			}
		}
	}

	// Clean up temporary directory if it was created
	if tempProjectModel && tempDirName != "" {
		if err := os.RemoveAll(filepath.Dir(absProjectModelPath)); err != nil {
			logrus.Warnf("Failed to remove temporary directory %s: %v", filepath.Dir(absProjectModelPath), err)
		} else {
			logrus.Debugf("Removed temporary directory: %s", filepath.Dir(absProjectModelPath))
		}
	}
}

func scanWithDocker(analyzerFlags []string, envCont []string, hostConfig *container.HostConfig, copyToContainer map[string]string, copyFromContainer map[string]string) {
	analyzerImageLink := utils.GetImageLink(globals.Config.Analyzer.Version, globals.AnalyzerDocker)
	container_run.RunGhcrContainer("Scan", analyzerImageLink, analyzerFlags, envCont, hostConfig, copyToContainer, copyFromContainer)
}

func scanWithNative(absProjectModelPath, absSarifReportPath, absRuleSetPath, absRulesetLoadErrorsPath string, analyzerFlags []string) {
	// Get the path to the analyzer JAR
	analyzerJarPath, err := utils.GetAnalyzerJarPath(globals.Config.Analyzer.Version)
	if err != nil {
		logrus.Fatalf("Failed to construct path to the analyzer: %s", err)
	}

	// Download the analyzer JAR if it doesn't exist
	if _, err := os.Stat(analyzerJarPath); errors.Is(err, os.ErrNotExist) {
		logrus.Infof("Downloading analyzer version %s", globals.Config.Analyzer.Version)
		if err := utils.DownloadGithubReleaseAsset(globals.RepoOwner, globals.AnalyzerRepoName, globals.Config.Analyzer.Version, globals.AnalyzerAssetName, analyzerJarPath, globals.Config.Github.Token); err != nil {
			logrus.Fatalf("Failed to download analyzer: %s", err)
		}
		logrus.Infof("Successfully downloaded analyzer to %s", analyzerJarPath)
	}

	// Convert Docker paths to native paths for analyzer flags
	nativeAnalyzerFlags := convertDockerFlagsToNative(analyzerFlags, absProjectModelPath, absSarifReportPath, absRuleSetPath, absRulesetLoadErrorsPath)

	// Build the command with all necessary arguments
	analyzerCommand := []string{
		"-Xmx8G",
		"-Dorg.seqra.ir.impl.storage.defaultBatchSize=2000",
		"-Djdk.util.jar.enableMultiRelease=false",
		"-jar",
		analyzerJarPath,
	}
	analyzerCommand = append(analyzerCommand, nativeAnalyzerFlags...)

	javaRunner := java.NewJavaRunner().TrySpecificVersion(java.DefaultJavaVersion)

	commandSucceeded := func(err error) bool {
		if err != nil {
			logrus.Errorf("Analyzer failed: %v", err)
			return false
		}
		return true
	}
	// Execute the command using JavaRunner
	_, err = javaRunner.ExecuteJavaCommand(analyzerCommand, commandSucceeded)

	if err != nil {
		logrus.Errorf("Native scan has failed: %s", err)
	} else {
		renameSarifReport(absSarifReportPath)
	}
}

func convertDockerFlagsToNative(analyzerFlags []string, absProjectModelPath, absSarifReportPath, absRuleSetPath, absRulesetLoadErrorsPath string) []string {
	nativeFlags := make([]string, 0, len(analyzerFlags))
	for i := 0; i < len(analyzerFlags); i++ {
		flag := analyzerFlags[i]
		switch flag {
		case "--project":
			nativeFlags = append(nativeFlags, "--project", filepath.Join(absProjectModelPath, "project.yaml"))
			i++ // Skip the next argument (Docker path)
		case "--output-dir":
			outputDir := filepath.Dir(absSarifReportPath)
			nativeFlags = append(nativeFlags, "--output-dir", outputDir)
			i++ // Skip the next argument (Docker path)
		case "--semgrep-rule-set":
			if absRuleSetPath != "" {
				nativeFlags = append(nativeFlags, "--semgrep-rule-set", absRuleSetPath)
			}
			i++ // Skip the next argument (Docker path)
		case "--semgrep-rule-load-errors":
			if absRulesetLoadErrorsPath != "" {
				nativeFlags = append(nativeFlags, "--semgrep-rule-load-errors", absRulesetLoadErrorsPath)
			}
			i++ // Skip the next argument (Docker path)
		default:
			nativeFlags = append(nativeFlags, flag)
		}
	}
	return nativeFlags
}

func renameSarifReport(absSarifReportPath string) {
	outputDir := filepath.Dir(absSarifReportPath)
	generatedSarifPath := filepath.Join(outputDir, "report-ifds.sarif")
	if _, err := os.Stat(generatedSarifPath); err == nil {
		if generatedSarifPath != absSarifReportPath {
			if err := os.Rename(generatedSarifPath, absSarifReportPath); err != nil {
				logrus.Debugf("Failed to rename SARIF report from %s to %s: %v", generatedSarifPath, absSarifReportPath, err)
			} else {
				logrus.Debugf("Successfully renamed SARIF report from %s to %s", generatedSarifPath, absSarifReportPath)
			}
		}
	} else {
		logrus.Debugf("Generated SARIF report not found at %s", generatedSarifPath)
	}
}
