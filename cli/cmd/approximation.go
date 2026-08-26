package cmd

import (
	"fmt"

	"github.com/seqra/opentaint/internal/approximation"
	"github.com/seqra/opentaint/internal/utils/log"
	"github.com/spf13/cobra"
)

var initApproximationDeps []string

var approximationCmd = &cobra.Command{
	Use:   "approximation",
	Short: "Create dataflow approximation projects",
	Long: `A dataflow approximation project holds code-based models of how taint moves through
methods the analyzer cannot see into, together with the exact dependency versions those
models are written against.

Compile one with 'opentaint compile approximations', and apply it with the
--dataflow-approximations flag of 'opentaint scan' or 'opentaint test approximation run'.`,
}

var approximationInitCmd = &cobra.Command{
	Use:   "init <output-dir>",
	Short: "Create a dataflow approximation project",
	Long: `Create a Gradle project for OpenTaint dataflow approximation models.

The project includes:
  - build.gradle.kts with compile-only dependencies, settings.gradle.kts
  - libs/opentaint-approximations-api.jar, the @Approximate annotations and the
    OpentaintNdUtil / ArgumentTypeContext support types
  - src/main/java/ for the model sources, one @Approximate class per target class

Use --dependency to pin the libraries the models are written against, at the exact versions
the target application uses. Those pins are the models' compile environment: they, and
nothing about the project under analysis, decide what the models compile against.`,
	Args: cobra.ExactArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		projectPath := log.AbsPathOrExit(args[0], "output-dir")

		analyzerJarPath, err := ensureAnalyzerAvailable()
		if err != nil {
			out.Fatalf("Failed to resolve analyzer: %s", err)
		}

		// Init replaces the build file but keeps the model sources.
		// Warn the user because this removes dependency pins that they added manually.
		existing := approximation.IsProject(projectPath)
		if existing {
			out.Warnf("Replacing the build file of the existing approximation project at %s; "+
				"pass every dependency the models need, not only the new ones", projectPath)
		}

		if err := approximation.Scaffold(projectPath, initApproximationDeps, analyzerJarPath); err != nil {
			out.Fatalf("Failed to create approximation project: %s", err)
		}

		if existing {
			fmt.Printf("Approximation project updated at %s\n", projectPath)
		} else {
			fmt.Printf("Approximation project initialized at %s\n", projectPath)
		}
		suggest("To compile the models run", fmt.Sprintf("opentaint compile approximations %s", args[0]))
	},
}

func init() {
	rootCmd.AddCommand(approximationCmd)
	approximationCmd.AddCommand(approximationInitCmd)

	approximationInitCmd.Flags().StringArrayVar(&initApproximationDeps, "dependency", nil,
		"Compile-only Maven dependency coordinates the models are written against (repeatable)")
}
