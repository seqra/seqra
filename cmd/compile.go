package cmd

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/seqra/seqra/v2/internal/validation"
	"github.com/spf13/cobra"

	"github.com/seqra/seqra/v2/internal/globals"
	"github.com/seqra/seqra/v2/internal/utils"
	"github.com/seqra/seqra/v2/internal/utils/java"
	"github.com/seqra/seqra/v2/internal/utils/log"

	"github.com/seqra/seqra/v2/internal/output"
)

type CompileCaller int

const (
	External CompileCaller = iota
	Internal
)

var OutputProjectModelPath string
var ProjectPath string
var DryRunCompile bool

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
	Run: func(cmd *cobra.Command, args []string) {
		ProjectPath = args[0]

		projectRoot := filepath.Clean(ProjectPath)
		absProjectRoot := log.AbsPathOrExit(projectRoot, "project path")

		outputProjectModelPath := filepath.Clean(OutputProjectModelPath)
		absOutputProjectModelPath := log.AbsPathOrExit(outputProjectModelPath, "output")

		sb := out.Section("Seqra Compile")
		addConfigFields(cmd, sb)
		if globals.Config.Log.Verbosity == "debug" {
			sb.Line()
		}
		sb.Field("Project", absProjectRoot).
			Field("Output project model", absOutputProjectModelPath).
			Render()
		out.Blank()

		if DryRunCompile {
			failOnInvalidInputs(func() error { return validation.ValidateCompileInputs(absProjectRoot, absOutputProjectModelPath) })
			runDryRun("Compilation")
			return
		}

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
			return compile(absProjectRoot, absOutputProjectModelPath, autobuilderJarPath, compileJavaRunner, External)
		}); err == nil {
			out.Blank()
			printCompileSummary(absOutputProjectModelPath)
			suggest("To scan project run", utils.BuildScanCommandFromCompile(projectRoot, absOutputProjectModelPath))
		} else {
			out.Fatalf("Native compile has failed: %s", err)
		}
	},
}

func init() {
	rootCmd.AddCommand(compileCmd)

	compileCmd.Flags().StringVarP(&OutputProjectModelPath, "output", "o", "", `Path to the result project model`)
	_ = compileCmd.MarkFlagRequired("output")
	compileCmd.Flags().BoolVar(&DryRunCompile, "dry-run", false, "Validate inputs and show what would run without compiling")
}

func ensureAutobuilderAvailable() (string, error) {
	autobuilderJarPath, err := utils.GetAutobuilderJarPath(globals.Config.Autobuilder.Version)
	if err != nil {
		return "", fmt.Errorf("failed to construct path to the autobuilder: %w", err)
	}

	if err = ensureArtifactAvailable("autobuilder", globals.Config.Autobuilder.Version, autobuilderJarPath, func() error {
		return utils.DownloadGithubReleaseAsset(globals.Config.Owner, globals.AutobuilderRepoName, globals.Config.Autobuilder.Version, globals.AutobuilderAssetName, autobuilderJarPath, globals.Config.Github.Token, globals.Config.SkipVerify, out)
	}); err != nil {
		return "", err
	}

	return autobuilderJarPath, nil
}

func compile(absProjectRoot, absOutputProjectModelPath, autobuilderJarPath string, javaRunner java.JavaRunner, caller CompileCaller) error {
	if err := validation.ValidateCompileInputs(absProjectRoot, absOutputProjectModelPath); err != nil {
		return err
	}

	if err := compileProject(absOutputProjectModelPath, absProjectRoot, autobuilderJarPath, javaRunner); err != nil {
		return err
	}

	if _, err := validation.ValidateProjectModelOutput(absOutputProjectModelPath); err != nil {
		validationErr := fmt.Errorf("output validation failed after compile: %w", err)
		output.LogInfo(validationErr)
		if caller == External {
			suggest("If native compilation fails due to missing required Java, set JAVA_HOME according to the project's requirements or try Docker-based compilation:", utils.BuildCompileCommandWithDocker(ProjectPath, OutputProjectModelPath))
		}
		return fmt.Errorf("there was a problem during the compile step, check the full logs: %s", globals.LogPath)
	}

	return nil
}

func printCompileSummary(absOutputProjectModelPath string) {
	out.Section("Compile Summary").
		Field("Project model written to", absOutputProjectModelPath).
		Render()
}

func compileProject(absOutputProjectModelPath, absProjectRoot, autobuilderJarPath string, javaRunner java.JavaRunner) error {
	var err error
	tempLogsDir, err := os.MkdirTemp("", "seqra-*")
	if err != nil {
		return fmt.Errorf("failed to create temporary directory: %w", err)
	}
	tempLogsFile := filepath.Join(tempLogsDir, "autobuild.log")

	builder := NewAutobuilderBuilder().
		SetProjectRootDir(absProjectRoot).
		SetBuildMode("portable").
		SetResultDir(absOutputProjectModelPath).
		SetLogsFile(tempLogsFile).
		SetJarPath(autobuilderJarPath)

	autobuilderCommand := builder.BuildNativeCommand()

	commandSucceeded := func(_ error) bool {
		if _, err = os.Stat(absOutputProjectModelPath); err != nil {
			output.LogInfof("Output project model path does not exist after autobuilder execution: %s", absOutputProjectModelPath)
			output.LogInfo("Autobuilder failed to compile the project")
			return false
		}
		return true
	}
	// Execute the command using JavaRunner
	err = javaRunner.ExecuteJavaCommand(autobuilderCommand, commandSucceeded)
	if err != nil {
		output.LogInfof("Native compilation has failed: %s", err)
		return fmt.Errorf("native compilation has failed: %w", err)
	}

	return nil
}
