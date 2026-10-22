package cmd

import (
	"fmt"

	"github.com/seqra/opentaint/internal/approximation"
	"github.com/seqra/opentaint/internal/utils/log"
	"github.com/spf13/cobra"
)

var compileApproximationsOutput string

var compileApproximationsCmd = &cobra.Command{
	Use:   "approximations <approximation-project>",
	Short: "Compile a dataflow approximation project",
	Long: `Compile the dataflow approximation models in <approximation-project> against the
dependencies that project pins in its own build file.

This is the model project, not the project to scan: its build.gradle.kts lists the exact
library versions the models are written against, so the same models compile identically
wherever they are applied.

Scanning and testing build an approximation project on demand, so running this is only
needed to compile ahead of time or to see compilation errors on their own.`,
	Args: cobra.ExactArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		projectPath := log.AbsPathOrExit(args[0], "approximation-project")

		if !approximation.IsProject(projectPath) {
			out.Fatalf("%s is not an approximation project: it has no build.gradle.kts declaring the "+
				"dependencies its models are written against.\nCreate one with 'opentaint approximation init %s "+
				"--language java --dependency <group:artifact:version>'", projectPath, args[0])
		}

		outputPath := compileApproximationsOutput
		if outputPath == "" {
			outputPath = approximation.BuildDir(projectPath)
		} else {
			outputPath = log.AbsPathOrExit(outputPath, "output")
		}

		analyzerJarPath, err := ensureAnalyzerAvailable()
		if err != nil {
			out.Fatalf("Failed to resolve analyzer: %s", err)
		}

		if err := approximation.Build(projectPath, outputPath, newApproximationBuilder(analyzerJarPath)); err != nil {
			out.Fatalf("Approximation compilation failed: %s", err)
		}

		out.Section("Approximation Compile Summary").
			Field("Approximation project", projectPath).
			Field("Compiled models written to", approximation.ClassesDir(outputPath)).
			Render()
		suggest("To apply these models in a scan run",
			fmt.Sprintf("opentaint scan <project-model> --dataflow-approximations %s", args[0]))
	},
}

func init() {
	compileCmd.AddCommand(compileApproximationsCmd)

	compileApproximationsCmd.Flags().StringVarP(&compileApproximationsOutput, "output", "o", "",
		"Path to the compiled models (default: <approximation-project>/.opentaint/build)")
}
