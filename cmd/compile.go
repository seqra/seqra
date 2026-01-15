package cmd

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"

	"github.com/seqra/seqra/v2/internal/utils/formatters"
	"github.com/sirupsen/logrus"
	"github.com/spf13/cobra"

	"github.com/seqra/seqra/v2/internal/globals"
	"github.com/seqra/seqra/v2/internal/utils"
	"github.com/seqra/seqra/v2/internal/utils/java"
	"github.com/seqra/seqra/v2/internal/utils/log"
)

type CompileCaller int

const (
	External CompileCaller = iota
	Internal
)

var OutputProjectModelPath string
var ProjectPath string

// compileCmd represents the compile command
var compileCmd = &cobra.Command{
	Use:   "compile project",
	Short: "Compile your Java or Kotlin project",
	Args:  cobra.ExactArgs(1), // require exactly one argument
	Long: `This command takes a required path to the project, automatically detects Java/Kotlin build system, modules and dependencies and compiles project model.

Arguments:
  project  - Path to a project to compile (required)
`,
	Annotations: map[string]string{"PrintConfig": "true"},
	PreRun: func(cmd *cobra.Command, args []string) {
		bindCompileTypeFlag(cmd)
	},
	Run: func(cmd *cobra.Command, args []string) {
		ProjectPath = args[0]

		projectRoot := filepath.Clean(ProjectPath)
		absProjectRoot := log.AbsPathOrExit(projectRoot, "project path")

		outputProjectModelPath := filepath.Clean(OutputProjectModelPath)
		absOutputProjectModelPath := log.AbsPathOrExit(outputProjectModelPath, "output")

		logrus.Info(formatters.FormatTreeHeader("Seqra Compile"))
		printer := formatters.NewTreePrinter()
		printConfig(cmd, printer)
		printer.AddNode("")
		printer.AddNode("Project: " + absProjectRoot)
		printer.AddNode("Output project model: " + absOutputProjectModelPath)
		printer.Print()
		logrus.Info()

		if err := compile(absProjectRoot, absOutputProjectModelPath, External); err == nil {
			suggest("To scan project run", utils.BuildScanCommandFromCompile(projectRoot, absOutputProjectModelPath))
		} else {
			logrus.Fatal()
		}
	},
}

func init() {
	rootCmd.AddCommand(compileCmd)

	compileCmd.Flags().StringVarP(&OutputProjectModelPath, "output", "o", "", `Path to the result project model`)
	_ = compileCmd.MarkFlagRequired("output")
}

func compile(absProjectRoot, absOutputProjectModelPath string, caller CompileCaller) error {
	if _, err := os.Stat(absOutputProjectModelPath); err == nil {
		logrus.Fatalf("Output directory already exists: %s", absOutputProjectModelPath)
	}

	if !utils.IsSupportedArch() {
		logrus.Fatalf("Unsupported architecture found: %s! Only arm64 and amd64 are supported.", utils.GetArch())
	}

	compileProject(absOutputProjectModelPath, absProjectRoot)

	if _, err := os.Stat(absOutputProjectModelPath); err != nil {
		err := fmt.Errorf("there was a problem during the compile step, check the full logs: %s", globals.LogPath)
		logrus.Error(err)
		if caller == External {
			suggest("If native compilation fails due to missing required Java, set JAVA_HOME according to the project's requirements or try Docker-based compilation:", utils.BuildCompileCommandWithDocker(ProjectPath, OutputProjectModelPath))
		}
		return err
	}

	logrus.Info(formatters.FormatTreeHeader("Compile Summary"))
	printer := formatters.NewTreePrinter()

	printer.AddNode(fmt.Sprintf("Project model written to: %s", absOutputProjectModelPath))
	printer.Print()
	return nil
}

func compileProject(absOutputProjectModelPath, absProjectRoot string) {

	autobuilderJarPath, err := utils.GetAutobuilderJarPath(globals.Config.Autobuilder.Version)
	if err != nil {
		logrus.Fatalf("Failed to construct path to the autobuilder: %s", err)
	}

	// Download the autobuilder JAR if it doesn't exist
	if _, err = os.Stat(autobuilderJarPath); errors.Is(err, os.ErrNotExist) {
		logrus.Info()
		logrus.Infof("Downloading autobuilder version %s", globals.Config.Autobuilder.Version)
		if err = utils.DownloadGithubReleaseAsset(globals.RepoOwner, globals.AutobuilderRepoName, globals.Config.Autobuilder.Version, globals.AutobuilderAssetName, autobuilderJarPath, globals.Config.Github.Token); err != nil {
			logrus.Fatalf("Failed to download autobuilder: %s", err)
		}
		logrus.Infof("Successfully downloaded autobuilder to %s", autobuilderJarPath)
	}

	tempLogsDir, err := os.MkdirTemp("", "seqra-*")
	if err != nil {
		logrus.Fatalf("Failed to create temporary directory: %s", err)
	}
	tempLogsFile := filepath.Join(tempLogsDir, "autobuild.log")

	builder := NewAutobuilderBuilder().
		SetProjectRootDir(absProjectRoot).
		SetBuildMode("portable").
		SetResultDir(absOutputProjectModelPath).
		SetLogsFile(tempLogsFile).
		SetJarPath(autobuilderJarPath)

	autobuilderCommand := builder.BuildNativeCommand()

	javaRunner := java.NewJavaRunner().TrySystem().TrySpecificVersion(globals.Config.Java.Version)

	commandSucceeded := func(_ error) bool {
		if _, err = os.Stat(absOutputProjectModelPath); err != nil {
			logrus.Errorf("Output project model path does not exist after autobuilder execution: %s", absOutputProjectModelPath)
			logrus.Error("Autobuilder failed to compile the project")
			return false
		}
		return true
	}
	// Execute the command using JavaRunner
	err = javaRunner.ExecuteJavaCommand(autobuilderCommand, commandSucceeded)
	if err != nil {
		logrus.Errorf("Native compilation has failed: %s", err)
	}
}
