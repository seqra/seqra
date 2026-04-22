package cmd

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"

	"github.com/seqra/opentaint/internal/globals"
	"github.com/seqra/opentaint/internal/output"
	"github.com/seqra/opentaint/internal/utils"
	"github.com/seqra/opentaint/internal/utils/java"
	"github.com/seqra/opentaint/internal/utils/project"
)

// approxClassesJarPrefix is the path prefix under which the analyzer fat JAR
// bundles approximation support sources (OpentaintNdUtil, ArgumentTypeContext).
const approxClassesJarPrefix = "opentaint-dataflow-approximations/"

// compileApproximationsIfNeeded checks whether a --dataflow-approximations directory
// contains .java source files. If so, it compiles them using javac (with the
// analyzer JAR + project dependencies on the classpath) and returns the path to
// the compiled .class output directory. If the directory already contains only
// .class files (or no .java files at all), it is returned as-is.
//
// projectModelDir is the directory containing project.yaml — used to resolve
// project dependencies for the javac classpath (approximation code may reference
// library types like org.apache.pdfbox.pdmodel.PDDocument).
func compileApproximationsIfNeeded(approxPath string, analyzerJarPath string, projectModelDir string) (string, error) {
	info, err := os.Stat(approxPath)
	if err != nil {
		return "", fmt.Errorf("approximation path does not exist: %w", err)
	}
	if !info.IsDir() {
		return approxPath, nil
	}

	javaFiles, err := collectJavaSources(approxPath)
	if err != nil {
		return "", err
	}
	if len(javaFiles) == 0 {
		return approxPath, nil
	}

	output.LogInfof("Found %d .java file(s) in approximations directory, compiling...", len(javaFiles))

	javacPath, err := resolveJavacPath()
	if err != nil {
		return "", err
	}

	extractedDir, err := os.MkdirTemp("", "opentaint-approx-deps-*")
	if err != nil {
		return "", fmt.Errorf("failed to create temp directory for approximation deps: %w", err)
	}
	defer func() { _ = os.RemoveAll(extractedDir) }()

	if err := utils.ExtractZipPrefix(analyzerJarPath, approxClassesJarPrefix, extractedDir); err != nil {
		return "", fmt.Errorf("failed to extract approximation classes from analyzer JAR: %w", err)
	}

	outputDir, err := os.MkdirTemp("", "opentaint-approx-compiled-*")
	if err != nil {
		return "", fmt.Errorf("failed to create temp directory for compiled approximations: %w", err)
	}

	classpath := buildApproxClasspath(analyzerJarPath, extractedDir, projectModelDir)
	if err := runJavac(javacPath, classpath, outputDir, javaFiles); err != nil {
		_ = os.RemoveAll(outputDir)
		return "", err
	}

	output.LogInfof("Approximation compilation succeeded, output: %s", outputDir)
	return outputDir, nil
}

func collectJavaSources(root string) ([]string, error) {
	var javaFiles []string
	err := filepath.Walk(root, func(path string, fi os.FileInfo, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if !fi.IsDir() && strings.HasSuffix(fi.Name(), ".java") {
			javaFiles = append(javaFiles, path)
		}
		return nil
	})
	if err != nil {
		return nil, fmt.Errorf("failed to walk approximations directory: %w", err)
	}
	return javaFiles, nil
}

func resolveJavacPath() (string, error) {
	javacRunner := java.NewJavaRunner().
		WithSkipVerify(globals.Config.SkipVerify).
		WithImageType(java.AdoptiumImageJDK).
		TrySystem().
		TrySpecificVersion(globals.DefaultJavaVersion)

	javaPath, err := javacRunner.EnsureJava()
	if err != nil {
		return "", fmt.Errorf("failed to resolve Java for approximation compilation: %w", err)
	}

	javacPath := java.DeriveJavacPath(javaPath)
	if _, err := os.Stat(javacPath); err != nil {
		return "", fmt.Errorf("javac not found at %s (resolved from java at %s). A JDK (not JRE) is required to compile approximation sources", javacPath, javaPath)
	}
	return javacPath, nil
}

// buildApproxClasspath assembles the javac classpath for approximation compilation:
//  1. Analyzer JAR — contains @Approximate, @ApproximateByName annotations
//  2. Extracted approximation utilities — OpentaintNdUtil, ArgumentTypeContext
//  3. Project dependencies — library JARs that approximation code may reference
func buildApproxClasspath(analyzerJarPath, extractedDir, projectModelDir string) string {
	parts := []string{analyzerJarPath, extractedDir}
	parts = append(parts, resolveProjectDependencies(projectModelDir)...)
	return strings.Join(parts, string(os.PathListSeparator))
}

func runJavac(javacPath, classpath, outputDir string, javaFiles []string) error {
	args := []string{
		"-source", "8",
		"-target", "8",
		"-cp", classpath,
		"-d", outputDir,
	}
	args = append(args, javaFiles...)

	output.LogDebugf("Running javac: %s %s", javacPath, strings.Join(args, " "))

	cmd := exec.Command(javacPath, args...)
	cmdOutput, cmdErr := cmd.CombinedOutput()
	if cmdErr != nil {
		return fmt.Errorf(
			"approximation compilation failed:\n%s\njavac exited with: %w",
			string(cmdOutput), cmdErr,
		)
	}
	return nil
}

// resolveProjectDependencies reads project.yaml from the project model directory
// and returns absolute paths to the dependency JARs listed there.
func resolveProjectDependencies(projectModelDir string) []string {
	if projectModelDir == "" {
		return nil
	}
	config, err := project.LoadConfig(projectModelDir)
	if err != nil {
		output.LogDebugf("Could not read project config for approximation compilation: %v", err)
		return nil
	}
	var absDeps []string
	for _, dep := range config.Dependencies {
		absPath := dep
		if !filepath.IsAbs(dep) {
			absPath = filepath.Join(projectModelDir, dep)
		}
		if _, err := os.Stat(absPath); err == nil {
			absDeps = append(absDeps, absPath)
		}
	}
	output.LogDebugf("Resolved %d project dependencies for approximation classpath", len(absDeps))
	return absDeps
}
