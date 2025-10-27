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
	"github.com/seqra/seqra/internal/utils/log"
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

	analyzerImageLink := utils.GetImageLink(globals.Config.Analyzer.Version, globals.AnalyzerDocker)

	if tempProjectModel {
		compile(absUserProjectRoot, tempProjectModelPath, globals.Config.Compile.Type)
	}

	container_run.RunGhcrContainer("Scan", analyzerImageLink, analyzerFlags, envCont, hostConfig, copyToContainer, copyFromContainer)

	// Process the generated SARIF report if it exists
	report := PrintSarifSummary(absSarifReportPath, true)
	if report == nil {
		return
	}

	logrus.Infof("Log file: %s", globals.LogPath)
	if SarifReportPath == "" {
		utils.RemoveIfExistsOrExit(absSarifReportPath)
	} else {
		logrus.Info()
		logrus.Infof("Full report: %s", absSarifReportPath)
		logrus.Infof("You can view findings by run: seqra summary --show-findings %s", absSarifReportPath)

		if tempProjectModel {
			report.UpdateURIInfo(absUserProjectRoot + "/")
		} else {
			report.UpdateURIInfo(absProjectModelPath + "/sources/")
		}

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
