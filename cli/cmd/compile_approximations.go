package cmd

import (
	"archive/zip"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strings"

	"github.com/seqra/opentaint/internal/globals"
	"github.com/seqra/opentaint/internal/output"
	"github.com/seqra/opentaint/internal/utils/java"
	"github.com/seqra/opentaint/internal/utils/project"
)

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

	// If it's a single file, return as-is (nothing to compile)
	if !info.IsDir() {
		return approxPath, nil
	}

	// Collect .java files in the directory tree
	var javaFiles []string
	_ = filepath.Walk(approxPath, func(path string, fi os.FileInfo, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if !fi.IsDir() && strings.HasSuffix(fi.Name(), ".java") {
			javaFiles = append(javaFiles, path)
		}
		return nil
	})

	if len(javaFiles) == 0 {
		// No Java sources — directory may contain .class files or be empty; pass through.
		return approxPath, nil
	}

	output.LogInfof("Found %d .java file(s) in approximations directory, compiling...", len(javaFiles))

	// Resolve javac from the managed JDK
	javacRunner := java.NewJavaRunner().
		WithSkipVerify(globals.Config.SkipVerify).
		WithImageType(java.AdoptiumImageJDK).
		TrySystem().
		TrySpecificVersion(globals.DefaultJavaVersion)

	javaPath, err := javacRunner.EnsureJava()
	if err != nil {
		return "", fmt.Errorf("failed to resolve Java for approximation compilation: %w", err)
	}

	javacPath := deriveJavacPath(javaPath)
	if _, err := os.Stat(javacPath); err != nil {
		return "", fmt.Errorf("javac not found at %s (resolved from java at %s). A JDK (not JRE) is required to compile approximation sources", javacPath, javaPath)
	}

	// Extract approximation support classes from the analyzer JAR.
	// The JAR bundles utility classes (OpentaintNdUtil, ArgumentTypeContext)
	// under "opentaint-dataflow-approximations/" prefix.
	extractedDir, err := extractApproxClassesFromJar(analyzerJarPath)
	if err != nil {
		return "", fmt.Errorf("failed to extract approximation classes from analyzer JAR: %w", err)
	}

	// Create temp output directory for compiled .class files
	outputDir, err := os.MkdirTemp("", "opentaint-approx-compiled-*")
	if err != nil {
		_ = os.RemoveAll(extractedDir)
		return "", fmt.Errorf("failed to create temp directory for compiled approximations: %w", err)
	}

	// Build classpath:
	// 1. Analyzer JAR — contains @Approximate, @ApproximateByName annotations
	// 2. Extracted approximation utilities — OpentaintNdUtil, ArgumentTypeContext
	// 3. Project dependencies — library JARs that approximation code may reference
	cpParts := []string{analyzerJarPath, extractedDir}
	cpParts = append(cpParts, resolveProjectDependencies(projectModelDir)...)
	classpath := strings.Join(cpParts, string(os.PathListSeparator))

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

	// Always clean up extracted dependencies
	_ = os.RemoveAll(extractedDir)

	if cmdErr != nil {
		_ = os.RemoveAll(outputDir)
		return "", fmt.Errorf(
			"approximation compilation failed:\n%s\njavac exited with: %w",
			string(cmdOutput), cmdErr,
		)
	}

	output.LogInfof("Approximation compilation succeeded, output: %s", outputDir)
	return outputDir, nil
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

// extractApproxClassesFromJar extracts bundled approximation support classes
// from the analyzer fat JAR. These are stored under "opentaint-dataflow-approximations/"
// prefix and need standard package structure for javac to find them.
func extractApproxClassesFromJar(jarPath string) (string, error) {
	r, err := zip.OpenReader(jarPath)
	if err != nil {
		return "", fmt.Errorf("failed to open JAR: %w", err)
	}
	defer r.Close()

	extractDir, err := os.MkdirTemp("", "opentaint-approx-deps-*")
	if err != nil {
		return "", err
	}

	const prefix = "opentaint-dataflow-approximations/"
	for _, f := range r.File {
		if !strings.HasPrefix(f.Name, prefix) {
			continue
		}
		if f.FileInfo().IsDir() {
			continue
		}
		relPath := strings.TrimPrefix(f.Name, prefix)
		if relPath == "" {
			continue
		}
		destPath := filepath.Join(extractDir, relPath)
		if err := os.MkdirAll(filepath.Dir(destPath), 0755); err != nil {
			_ = os.RemoveAll(extractDir)
			return "", err
		}
		src, err := f.Open()
		if err != nil {
			_ = os.RemoveAll(extractDir)
			return "", err
		}
		dst, err := os.Create(destPath)
		if err != nil {
			src.Close()
			_ = os.RemoveAll(extractDir)
			return "", err
		}
		_, err = io.Copy(dst, src)
		src.Close()
		dst.Close()
		if err != nil {
			_ = os.RemoveAll(extractDir)
			return "", err
		}
	}

	return extractDir, nil
}

// deriveJavacPath returns the path to javac given the path to java.
func deriveJavacPath(javaPath string) string {
	dir := filepath.Dir(javaPath)
	return filepath.Join(dir, "javac")
}
