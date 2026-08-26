package cmd

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/seqra/opentaint/internal/approximation"
	"github.com/seqra/opentaint/internal/autobuilder"
	"github.com/seqra/opentaint/internal/output"
)

// approximationBuilder compiles a model project with the autobuilder.
// The model project supplies the compile dependencies.
type approximationBuilder struct {
	analyzerJarPath string
	apiJar          []byte
}

func newApproximationBuilder(analyzerJarPath string) approximation.Builder {
	return &approximationBuilder{analyzerJarPath: analyzerJarPath}
}

// Prepare updates the approximation API jar in the model project.
// The builder reads the jar from the analyzer only once.
func (b *approximationBuilder) Prepare(projectDir string) error {
	if b.apiJar == nil {
		apiJar, err := approximation.ExtractApiJar(b.analyzerJarPath)
		if err != nil {
			return err
		}
		b.apiJar = apiJar
	}
	return approximation.WriteApiJar(projectDir, b.apiJar)
}

func (b *approximationBuilder) Compile(projectDir, projectModelDir string) error {
	autobuilderJarPath, err := ensureAutobuilderAvailable()
	if err != nil {
		return fmt.Errorf("failed to resolve autobuilder: %w", err)
	}

	javaRunner := newAutobuilderJavaRunner()
	if _, err := javaRunner.EnsureJava(); err != nil {
		return fmt.Errorf("failed to resolve Java for approximation compilation: %w", err)
	}

	logsDir, err := os.MkdirTemp("", "opentaint-approximation-logs-*")
	if err != nil {
		return fmt.Errorf("failed to create temp directory for approximation build logs: %w", err)
	}
	logsFile := filepath.Join(logsDir, "approximation-build.log")

	// Show the spinner for direct and automatic builds.
	output.LogInfof("Compiling approximation project %s", projectDir)
	compile := func() error {
		return compileProjectWithLogs(projectModelDir, projectDir, autobuilderJarPath, logsFile, true, javaRunner)
	}
	if err := out.RunWithSpinner("Compiling approximation project "+filepath.Base(projectDir), compile); err != nil {
		// Show compiler errors when they are available.
		// Keep the full log if no compiler error is available.
		if diagnostics := autobuilder.CompilerDiagnostics(logsFile); diagnostics != "" {
			return fmt.Errorf("approximation project %s failed to compile:\n%s", projectDir, diagnostics)
		}
		return fmt.Errorf("failed to compile approximation project %s (build log: %s): %w", projectDir, logsFile, err)
	}

	_ = os.RemoveAll(logsDir)
	return nil
}
