package cmd

import (
	"fmt"
	"strings"

	"github.com/seqra/opentaint/internal/approximation"
	"github.com/seqra/opentaint/internal/utils/log"
	"github.com/spf13/cobra"
)

var (
	initApproximationDeps     []string
	initApproximationLanguage string
)

var approximationCmd = &cobra.Command{
	Use:   "approximation",
	Short: "Create dataflow approximation projects",
	Long: `A dataflow approximation project holds code-based models of how taint moves through
methods the analyzer cannot see into, together with the dependency versions those models
use.

Java projects compile with 'opentaint compile approximations' and use the
--dataflow-approximations flag. Go projects are Go modules and use the --go-models flag.`,
}

var approximationInitCmd = &cobra.Command{
	Use:   "init <output-dir>",
	Short: "Create a dataflow approximation project",
	Long: `Create an OpenTaint dataflow approximation project.

The default language is Java. A Java project includes:
  - build.gradle.kts with compile-only dependencies, settings.gradle.kts
  - libs/opentaint-approximations-api.jar, the @Approximate annotations and the
    OpentaintNdUtil / ArgumentTypeContext support types
  - src/main/java/ for the model sources, one @Approximate class per target class

Use --language go to create a Go module with module path "opentaint". Put each model package
at the target import path below the module root. For example, model net/http in net/http.

Use --dependency to add each library the models use. For Java, use Maven coordinates. For
Go, use module@version. Use the versions from the target application.`,
	Args: cobra.ExactArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		projectPath := log.AbsPathOrExit(args[0], "output-dir")
		language := strings.ToLower(strings.TrimSpace(initApproximationLanguage))

		switch language {
		case "java":
			initJavaApproximation(projectPath, args[0])
		case "go":
			initGoApproximation(projectPath, args[0])
		default:
			out.Fatalf("Unsupported approximation language %q. Use java or go", initApproximationLanguage)
		}
	},
}

func initJavaApproximation(projectPath, projectArg string) {
	if approximation.IsGoProject(projectPath) {
		out.Fatalf("The output directory already contains a Go approximation project: %s", projectPath)
	}

	analyzerJarPath, err := ensureAnalyzerAvailable()
	if err != nil {
		out.Fatalf("Failed to resolve analyzer: %s", err)
	}

	// Init replaces the build file but keeps the model sources.
	// Warn the user because this removes dependency pins that they added manually.
	existing := approximation.IsProject(projectPath)
	if existing {
		out.Warnf("Replacing the build file of the existing approximation project at %s. "+
			"Pass every dependency the models need, not only the new ones", projectPath)
	}

	if err := approximation.Scaffold(projectPath, initApproximationDeps, analyzerJarPath); err != nil {
		out.Fatalf("Failed to create approximation project: %s", err)
	}

	printApproximationInitResult(projectPath, existing)
	suggest("To compile the models run", fmt.Sprintf("opentaint compile approximations %s", projectArg))
}

func initGoApproximation(projectPath, projectArg string) {
	if approximation.IsProject(projectPath) {
		out.Fatalf("The output directory already contains a Java approximation project: %s", projectPath)
	}

	existing := approximation.IsGoProject(projectPath)
	if existing {
		out.Warnf("Replacing go.mod in the existing approximation project at %s. "+
			"Pass every dependency the models need", projectPath)
	}

	if err := approximation.ScaffoldGo(projectPath, initApproximationDeps); err != nil {
		out.Fatalf("Failed to create Go approximation project: %s", err)
	}

	printApproximationInitResult(projectPath, existing)
	suggest("To apply the models run", fmt.Sprintf("opentaint scan <project> --go-models %s", projectArg))
}

func printApproximationInitResult(projectPath string, existing bool) {
	if existing {
		fmt.Printf("Approximation project updated at %s\n", projectPath)
	} else {
		fmt.Printf("Approximation project initialized at %s\n", projectPath)
	}
}

func init() {
	rootCmd.AddCommand(approximationCmd)
	approximationCmd.AddCommand(approximationInitCmd)

	approximationInitCmd.Flags().StringArrayVar(&initApproximationDeps, "dependency", nil,
		"Maven coordinate for Java or module@version for Go (repeatable)")
	approximationInitCmd.Flags().StringVar(&initApproximationLanguage, "language", "java",
		"Model language: java or go")
}
