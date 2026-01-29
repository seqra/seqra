package cmd

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/seqra/seqra/v2/internal/globals"
)

type CommandBuilder interface {
	SetVerbosity(verbosity string) CommandBuilder
	BuildNativeCommand() []string
}

type BaseCommandBuilder struct {
	verbosity string
}

func (b *BaseCommandBuilder) SetVerbosity(verbosity string) {
	b.verbosity = verbosity
}

func (b *BaseCommandBuilder) appendVerbosityFlag(flags []string) []string {
	switch b.verbosity {
	case "info":
		return append(flags, "--verbosity=info")
	case "debug":
		return append(flags, "--verbosity=debug")
	default:
		return flags
	}
}

func NewAnalyzerBuilder() *AnalyzerBuilder {
	return &AnalyzerBuilder{
		BaseCommandBuilder: &BaseCommandBuilder{
			verbosity: globals.Config.Log.Verbosity,
		},
		maxMemory: "-Xmx8G",
	}
}

func NewAutobuilderBuilder() *AutobuilderBuilder {
	return &AutobuilderBuilder{
		BaseCommandBuilder: &BaseCommandBuilder{
			verbosity: globals.Config.Log.Verbosity,
		},
		maxMemory: "-Xmx1G",
	}
}

type AnalyzerBuilder struct {
	*BaseCommandBuilder
	projectPath              string
	outputDir                string
	logsFile                 string
	sarifFileName            string
	sarifThreadFlowLimit     string
	sarifToolVersion         string
	sarifToolSemanticVersion string
	sarifUriBase             string
	semgrepCompatibility     bool
	partialFingerprints      bool
	ifdsAnalysisTimeout      int64
	severities               []string
	ruleSetPaths             []string
	ruleLoadTracePath        string
	jarPath                  string
	maxMemory                string
}

func (a *AnalyzerBuilder) SetProject(projectPath string) *AnalyzerBuilder {
	a.projectPath = projectPath
	return a
}

func (a *AnalyzerBuilder) SetOutputDir(outputDir string) *AnalyzerBuilder {
	a.outputDir = outputDir
	return a
}

func (a *AnalyzerBuilder) SetLogsFile(logsFile string) *AnalyzerBuilder {
	a.logsFile = logsFile
	return a
}

func (a *AnalyzerBuilder) SetSarifFileName(fileName string) *AnalyzerBuilder {
	a.sarifFileName = fileName
	return a
}

func (a *AnalyzerBuilder) SetSarifThreadFlowLimit(limit string) *AnalyzerBuilder {
	a.sarifThreadFlowLimit = limit
	return a
}

func (a *AnalyzerBuilder) SetSarifToolVersion(version string) *AnalyzerBuilder {
	a.sarifToolVersion = version
	return a
}

func (a *AnalyzerBuilder) SetSarifToolSemanticVersion(version string) *AnalyzerBuilder {
	a.sarifToolSemanticVersion = version
	return a
}

func (a *AnalyzerBuilder) SetSarifUriBase(uriBase string) *AnalyzerBuilder {
	a.sarifUriBase = uriBase
	return a
}

func (a *AnalyzerBuilder) EnableSemgrepCompatibility() *AnalyzerBuilder {
	a.semgrepCompatibility = true
	return a
}

func (a *AnalyzerBuilder) EnablePartialFingerprints() *AnalyzerBuilder {
	a.partialFingerprints = true
	return a
}

func (a *AnalyzerBuilder) SetIfdsAnalysisTimeout(timeout int64) *AnalyzerBuilder {
	a.ifdsAnalysisTimeout = timeout
	return a
}

func (a *AnalyzerBuilder) AddSeverity(severity string) *AnalyzerBuilder {
	a.severities = append(a.severities, severity)
	return a
}

func (a *AnalyzerBuilder) AddRuleSet(ruleSetPath string) *AnalyzerBuilder {
	a.ruleSetPaths = append(a.ruleSetPaths, ruleSetPath)
	return a
}

func (a *AnalyzerBuilder) SetRuleLoadTracePath(tracePath string) *AnalyzerBuilder {
	a.ruleLoadTracePath = tracePath
	return a
}

func (a *AnalyzerBuilder) SetJarPath(jarPath string) *AnalyzerBuilder {
	a.jarPath = jarPath
	return a
}

func (a *AnalyzerBuilder) SetMaxMemory(maxMemory string) *AnalyzerBuilder {
	a.maxMemory = maxMemory
	return a
}

func (a *AnalyzerBuilder) SetVerbosity(verbosity string) CommandBuilder {
	a.BaseCommandBuilder.SetVerbosity(verbosity)
	return a
}

func (a *AnalyzerBuilder) BuildNativeCommand() []string {
	// For native execution, create a temporary logs directory
	tempLogsDir, err := os.MkdirTemp("", "seqra-*")
	if err != nil {
		return []string{}
	}
	tempLogsFile := filepath.Join(tempLogsDir, "analyzer.log")

	command := []string{
		a.maxMemory,
		"-Dorg.seqra.ir.impl.storage.defaultBatchSize=2000",
		"-Djdk.util.jar.enableMultiRelease=false",
		"-jar",
	}

	// Add jar path if it's set
	if a.jarPath != "" {
		command = append(command, a.jarPath)
	}

	flags := []string{
		"--project", a.projectPath,
		"--output-dir", a.outputDir,
		"--logs-file", tempLogsFile,
		"--sarif-file-name", a.sarifFileName,
	}

	if a.sarifThreadFlowLimit != "" {
		flags = append(flags, "--sarif-thread-flow-limit="+a.sarifThreadFlowLimit)
	}

	if a.sarifToolVersion != "" {
		flags = append(flags, "--sarif-tool-version", a.sarifToolVersion)
	}

	if a.sarifToolSemanticVersion != "" {
		flags = append(flags, "--sarif-tool-semantic-version", a.sarifToolSemanticVersion)
	}

	if a.sarifUriBase != "" {
		flags = append(flags, "--sarif-uri-base", a.sarifUriBase)
	}

	if a.semgrepCompatibility {
		flags = append(flags, "--sarif-semgrep-style-id")
	}

	if a.partialFingerprints {
		flags = append(flags, "--sarif-generate-fingerprint")
	}

	flags = a.appendVerbosityFlag(flags)

	for _, severity := range a.severities {
		flags = append(flags, fmt.Sprintf("--semgrep-rule-severity=%s", severity))
	}

	if a.ifdsAnalysisTimeout > 0 {
		flags = append(flags, fmt.Sprintf("--ifds-analysis-timeout=%d", a.ifdsAnalysisTimeout))
	}

	for _, ruleSetPath := range a.ruleSetPaths {
		flags = append(flags, "--semgrep-rule-set", ruleSetPath)
	}

	if a.ruleLoadTracePath != "" {
		flags = append(flags, "--semgrep-rule-load-trace", a.ruleLoadTracePath)
	}

	return append(command, flags...)
}

type AutobuilderBuilder struct {
	*BaseCommandBuilder
	projectRootDir string
	resultDir      string
	logsFile       string
	buildType      string
	buildMode      string
	classpaths     []string
	packages       []string
	maxMemory      string
	jarPath        string
}

func (a *AutobuilderBuilder) SetProjectRootDir(projectRootDir string) *AutobuilderBuilder {
	a.projectRootDir = projectRootDir
	return a
}

func (a *AutobuilderBuilder) SetResultDir(resultDir string) *AutobuilderBuilder {
	a.resultDir = resultDir
	return a
}

func (a *AutobuilderBuilder) SetLogsFile(logsFile string) *AutobuilderBuilder {
	a.logsFile = logsFile
	return a
}

func (a *AutobuilderBuilder) SetBuildType(buildType string) *AutobuilderBuilder {
	a.buildType = buildType
	return a
}

func (a *AutobuilderBuilder) SetBuildMode(buildMode string) *AutobuilderBuilder {
	a.buildMode = buildMode
	return a
}

func (a *AutobuilderBuilder) AddClasspath(classpath string) *AutobuilderBuilder {
	a.classpaths = append(a.classpaths, classpath)
	return a
}

func (a *AutobuilderBuilder) AddPackage(pkg string) *AutobuilderBuilder {
	a.packages = append(a.packages, pkg)
	return a
}

func (a *AutobuilderBuilder) SetMaxMemory(maxMemory string) *AutobuilderBuilder {
	a.maxMemory = maxMemory
	return a
}

func (a *AutobuilderBuilder) SetJarPath(jarPath string) *AutobuilderBuilder {
	a.jarPath = jarPath
	return a
}

func (a *AutobuilderBuilder) SetVerbosity(verbosity string) CommandBuilder {
	a.BaseCommandBuilder.SetVerbosity(verbosity)
	return a
}

func (a *AutobuilderBuilder) BuildDockerFlags() []string {
	flags := []string{
		"--project-root-dir", a.projectRootDir,
		"--result-dir", a.resultDir,
	}

	if a.buildMode != "" {
		flags = append(flags, "--build", a.buildMode)
	}

	if a.logsFile != "" {
		flags = append(flags, "--logs-file", a.logsFile)
	}

	flags = a.appendVerbosityFlag(flags)

	if a.buildType != "" {
		flags = append(flags, "--build-type", a.buildType)
		for _, cp := range a.classpaths {
			flags = append(flags, "--cp", cp)
		}
		for _, pkg := range a.packages {
			flags = append(flags, "--pkg", pkg)
		}
	}

	return flags
}

func (a *AutobuilderBuilder) BuildNativeCommand() []string {

	flags := []string{
		"--project-root-dir", a.projectRootDir,
		"--result-dir", a.resultDir,
	}

	if a.buildMode != "" {
		flags = append(flags, "--build", a.buildMode)
	}

	if a.logsFile != "" {
		flags = append(flags, "--logs-file", a.logsFile)
	}

	flags = a.appendVerbosityFlag(flags)

	if a.buildType != "" {
		flags = append(flags, "--build-type", a.buildType)
		for _, cp := range a.classpaths {
			flags = append(flags, "--cp", cp)
		}
		for _, pkg := range a.packages {
			flags = append(flags, "--pkg", pkg)
		}
	}

	command := []string{
		a.maxMemory,
		"-jar",
	}

	// Add jar path if it's set
	if a.jarPath != "" {
		command = append(command, a.jarPath)
	}

	return append(command, flags...)
}
