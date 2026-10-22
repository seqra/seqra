package cmd

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/seqra/opentaint/internal/validation"
	"github.com/spf13/cobra"

	"github.com/seqra/opentaint/internal/globals"
	"github.com/seqra/opentaint/internal/utils"
	"github.com/seqra/opentaint/internal/utils/java"
	"github.com/seqra/opentaint/internal/utils/log"
	projectutil "github.com/seqra/opentaint/internal/utils/project"

	"github.com/seqra/opentaint/internal/output"
)

var OutputProjectModelPath string
var ProjectPath string
var DryRunCompile bool
var CompileLogFile string

// currentCompileBuilder returns a builder pre-populated with the user's current compile flags.
// All compile command suggestions should use this as the base to ensure that adding a new
// flag in one place automatically propagates to every suggestion.
func currentCompileBuilder(projectPath string) *utils.OpentaintCommandBuilder {
	return utils.NewCompileCommand(projectPath).
		WithOutput(OutputProjectModelPath)
}

// dockerCompileSuggestion builds the "try Docker-based compilation" fallback hint.
func dockerCompileSuggestion() output.Suggestion {
	return output.Suggestion{
		Description: dockerFallbackHintPrefix + "compilation:",
		Command:     utils.BuildCompileCommandWithDocker(currentCompileBuilder(""), ProjectPath, OutputProjectModelPath),
	}
}

// compileCmd represents the compile command
var compileCmd = &cobra.Command{
	Use:   "compile project",
	Short: "Compile your Java, Kotlin, or Go project",
	Args:  cobra.ExactArgs(1), // require exactly one argument
	Long: `This command takes a required path to the project. It detects a Java, Kotlin, or Go project and compiles a project model.

Arguments:
  project  - Path to a project to compile (required)
`,
	Annotations: map[string]string{"PrintConfig": "true"},
	Run: func(cmd *cobra.Command, args []string) {
		ProjectPath = args[0]

		projectRoot := filepath.Clean(ProjectPath)
		absProjectRoot := log.AbsPathOrExit(projectRoot, "project path")

		if err := validation.ValidateSourceProjectForCompile(absProjectRoot); err != nil {
			out.FatalErr(err)
		}

		// Activate logging
		if !DryRunCompile {
			activateLoggingForProject(CompileLogFile, absProjectRoot)
		}

		outputProjectModelPath := filepath.Clean(OutputProjectModelPath)
		absOutputProjectModelPath := log.AbsPathOrExit(outputProjectModelPath, "output")

		sb := out.Section("OpenTaint Compile")
		addConfigFields(cmd, sb)
		if globals.Config.Output.Debug {
			sb.Line()
		}
		sb.FieldNode("Project", absProjectRoot).
			FieldNode("Output project model", absOutputProjectModelPath)
		if validation.IsGoSourceProject(absProjectRoot) {
			sb.FieldNode("Language", "Go")
		} else {
			sb.FieldNode("Autobuilder", utils.ArtifactVersionWithPath(globals.ArtifactByKind("autobuilder")))
		}
		sb.Render()

		if DryRunCompile {
			out.Blank()
			failOnInvalidInputs(func() error { return validation.ValidateCompileInputs(absProjectRoot, absOutputProjectModelPath) })
			runDryRun("Compilation")
			return
		}

		var autobuilderJarPath string
		var compileJavaRunner java.JavaRunner
		if !validation.IsGoSourceProject(absProjectRoot) {
			var err error
			autobuilderJarPath, err = ensureAutobuilderAvailable()
			if err != nil {
				out.Fatalf("Native compile preparation failed: %s", err)
			}

			compileJavaRunner = newAutobuilderJavaRunner()
			if _, err := compileJavaRunner.EnsureJava(); err != nil {
				out.Fatalf("Failed to resolve Java for compilation: %s", err)
			}
		}

		if err := out.RunWithSpinner("Compiling project model", func() error {
			return compile(absProjectRoot, absOutputProjectModelPath, autobuilderJarPath, compileJavaRunner)
		}); err == nil {
			out.Blank()
			printCompileSummary(absOutputProjectModelPath)
			suggest("To scan project run", utils.BuildScanCommandFromCompile(projectRoot, absOutputProjectModelPath))
		} else {
			out.InteractiveBlank()
			failWith(1, fmt.Sprintf("Native compile has failed: %s", err), dockerCompileSuggestion())
		}
	},
}

func init() {
	rootCmd.AddCommand(compileCmd)

	compileCmd.Flags().StringVarP(&OutputProjectModelPath, "output", "o", "", `Path to the result project model`)
	_ = compileCmd.MarkFlagRequired("output")
	compileCmd.Flags().BoolVar(&DryRunCompile, "dry-run", false, "Validate inputs and show what would run without compiling")
	compileCmd.Flags().StringVar(&CompileLogFile, "log-file", "", "Path to the log file (default: <cache-dir>/logs/<timestamp>.log)")
}

func compile(absProjectRoot, absOutputProjectModelPath, autobuilderJarPath string, javaRunner java.JavaRunner) error {
	if err := validation.ValidateCompileInputs(absProjectRoot, absOutputProjectModelPath); err != nil {
		return err
	}

	if err := utils.EnsureParentDir(absOutputProjectModelPath); err != nil {
		return err
	}

	if validation.IsGoSourceProject(absProjectRoot) {
		if err := projectutil.WriteGoProjectModel(absProjectRoot, absOutputProjectModelPath); err != nil {
			return err
		}
	} else {
		if err := compileProject(absOutputProjectModelPath, absProjectRoot, autobuilderJarPath, javaRunner); err != nil {
			return err
		}
	}

	if _, err := validation.ValidateProjectModelOutput(absOutputProjectModelPath); err != nil {
		output.LogInfo(fmt.Errorf("output validation failed after compile: %w", err))
		return fmt.Errorf("there was a problem during the compile step")
	}

	return nil
}

func printCompileSummary(absOutputProjectModelPath string) {
	out.Section("Compile Summary").
		Field("Project model written to", absOutputProjectModelPath).
		Render()
}

func compileProject(absOutputProjectModelPath, absProjectRoot, autobuilderJarPath string, javaRunner java.JavaRunner) error {
	tempLogsDir, err := os.MkdirTemp("", "opentaint-*")
	if err != nil {
		return fmt.Errorf("failed to create temporary directory: %w", err)
	}
	return compileProjectWithLogs(absOutputProjectModelPath, absProjectRoot, autobuilderJarPath,
		filepath.Join(tempLogsDir, "autobuild.log"), false, javaRunner)
}

// compileProjectWithLogs writes the autobuilder log to logsFile.
// captureBuildOutput adds the output from the build tool to this log.
func compileProjectWithLogs(absOutputProjectModelPath, absProjectRoot, autobuilderJarPath, tempLogsFile string, captureBuildOutput bool, javaRunner java.JavaRunner) error {
	var err error

	builder := NewAutobuilderBuilder().
		SetProjectRootDir(absProjectRoot).
		SetBuildMode("portable").
		SetResultDir(absOutputProjectModelPath).
		SetLogsFile(tempLogsFile).
		SetJarPath(autobuilderJarPath)

	if captureBuildOutput {
		builder.ForceDebugVerbosity()
	}

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
	cmdErr, err := javaRunner.ExecuteJavaCommand(autobuilderCommand, commandSucceeded)
	if err != nil {
		output.LogInfof("Native compilation has failed: %s", err)
		return fmt.Errorf("native compilation has failed: %w", err)
	}
	if cmdErr != nil {
		output.LogInfof("Native compilation has failed: %s", cmdErr)
		return fmt.Errorf("native compilation has failed: %w", cmdErr)
	}

	return nil
}
