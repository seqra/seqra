package cmd

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"

	"github.com/docker/docker/api/types/container"
	"github.com/sirupsen/logrus"
	"github.com/spf13/cobra"

	"github.com/seqra/seqra/internal/container_run"
	"github.com/seqra/seqra/internal/globals"
	"github.com/seqra/seqra/internal/utils"
	"github.com/seqra/seqra/internal/utils/java"
	"github.com/seqra/seqra/internal/utils/log"
)

var OutputProjectModelPath string
var ProjectPath string

// compileCmd represents the compile command
var compileCmd = &cobra.Command{
	Use:   "compile project",
	Short: "Compile your Java project",
	Args:  cobra.MinimumNArgs(1), // require at least one argument
	Long: `This command takes a required path to the project, automatically detects Java build system, modules and dependencies and compile project model.

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

		logrus.Info()
		logrus.Infof("=== Compile only mode ===")
		logrus.Infof("Project: %s", absProjectRoot)
		logrus.Infof("Project model write to: %s", absOutputProjectModelPath)

		compile(absProjectRoot, absOutputProjectModelPath, globals.Config.Compile.Type)
	},
}

func init() {
	rootCmd.AddCommand(compileCmd)

	compileCmd.Flags().StringVarP(&OutputProjectModelPath, "output", "o", "", `Path to the result project model`)
	_ = compileCmd.MarkFlagRequired("output")

	compileCmd.Flags().StringVar(&globals.Config.Compile.Type, "compile-type", "docker", "Environment for run compile command (docker, native)")
}

func compile(absProjectRoot, absOutputProjectModelPath, compileType string) {
	if _, err := os.Stat(absOutputProjectModelPath); err == nil {
		logrus.Fatalf("Output directory already exist: %s", absOutputProjectModelPath)
	}

	if !utils.IsSupportedArch() {
		logrus.Fatalf("Unsupported architecture found: %s! Only arm64 and amd64 are supported.", utils.GetArch())
	}

	appendFlags := []string{}

	switch globals.Config.Log.Verbosity {
	case "info":
		appendFlags = append(appendFlags, "--verbosity=info")
	case "debug":
		appendFlags = append(appendFlags, "--verbosity=debug")
	}

	logrus.Infof("Compile mode: %s", compileType)
	switch compileType {
	case "docker":
		compileWithDocker(absOutputProjectModelPath, absProjectRoot, appendFlags)
	case "native":
		compileWithNative(absOutputProjectModelPath, absProjectRoot, appendFlags)
	default:
		logrus.Fatalf("compile-type must be one of \"docker\", \"native\"")
	}

	if _, err := os.Stat(absOutputProjectModelPath); err != nil {
		logrus.Errorf("There was a problem during the compile step, check the full logs: %s", globals.LogPath)
		if ProjectPath != "" {
			// Called from compile command - show suggestion
			logrus.Info("")
			logrus.Info("If native compilation fails due to missing required Java, set JAVA_HOME according to the project's requirements or try Docker-based compilation:")
			logrus.Infof("   %s", buildCompileCommandWithDocker(ProjectPath, OutputProjectModelPath))
		}
		return
	}

	if ProjectPath != "" {
		logrus.Info("")
		logrus.Info("Compilation successful! Next step: run security scan")
		logrus.Infof("   %s", buildScanCommandFromCompile(ProjectPath, absOutputProjectModelPath))
	}
}

func compileWithDocker(absOutputProjectModelPath, absProjectRoot string, appendFlags []string) {
	autobuilderFlags := []string{
		"--project-root-dir", "/data/project",
		"--build", "portable",
		"--result-dir", "/data/build",
	}

	autobuilderFlags = append(autobuilderFlags, appendFlags...)

	hostConfig := &container.HostConfig{}

	// Get the current user's UID and GID
	containerUID := fmt.Sprintf("%d", os.Getuid())
	containerGID := fmt.Sprintf("%d", os.Getgid())

	envCont := []string{"CONTAINER_UID=" + containerUID, "CONTAINER_GID=" + containerGID}

	var copyToContainer = make(map[string]string)
	copyToContainer[absProjectRoot] = "/data/project"

	var copyFromContainer = make(map[string]string)
	copyFromContainer["/data/build"] = absOutputProjectModelPath

	autobuilderImageLink := utils.GetImageLink(globals.Config.Autobuilder.Version, globals.AutobuilderDocker)
	container_run.RunGhcrContainer("Compile", autobuilderImageLink, autobuilderFlags, envCont, hostConfig, copyToContainer, copyFromContainer)
}

func compileWithNative(absOutputProjectModelPath, absProjectRoot string, appendFlags []string) {
	// Get the path to the autobuilder JAR
	autobuilderJarPath, err := utils.GetAutobuilderJarPath(globals.Config.Autobuilder.Version)
	if err != nil {
		logrus.Fatalf("Failed to construct path to the autobuilder: %s", err)
	}

	// Download the autobuilder JAR if it doesn't exist
	if _, err = os.Stat(autobuilderJarPath); errors.Is(err, os.ErrNotExist) {
		logrus.Infof("Downloading autobuilder version %s", globals.Config.Autobuilder.Version)
		if err = utils.DownloadGithubReleaseAsset(globals.RepoOwner, globals.AutobuilderRepoName, globals.Config.Autobuilder.Version, globals.AutobuilderAssetName, autobuilderJarPath, globals.Config.Github.Token); err != nil {
			logrus.Fatalf("Failed to download autobuilder: %s", err)
		}
		logrus.Infof("Successfully downloaded autobuilder to %s", autobuilderJarPath)
	}

	// Build the command with all necessary arguments
	autobuilderCommand := []string{
		"-Xmx1G",
		"-jar",
		autobuilderJarPath,
		"--project-root-dir", absProjectRoot,
		"--build", "portable",
		"--result-dir", absOutputProjectModelPath,
	}
	autobuilderCommand = append(autobuilderCommand, appendFlags...)

	javaRunner := java.NewJavaRunner().TrySystem().TrySpecificVersion(java.DefaultJavaVersion).TrySpecificVersion(java.LegacyJavaVersion)

	commandSucceeded := func(_ error) bool {
		if _, err = os.Stat(absOutputProjectModelPath); err != nil {
			logrus.Errorf("Output project model path does not exist after autobuilder execution: %s", absOutputProjectModelPath)
			logrus.Error("Autobuilder failed to compile the project")
			return false
		}
		return true
	}
	// Execute the command using JavaRunner
	_, err = javaRunner.ExecuteJavaCommand(autobuilderCommand, commandSucceeded)
	if err != nil {
		logrus.Errorf("Native compilation has failed: %s", err)
	}
}
