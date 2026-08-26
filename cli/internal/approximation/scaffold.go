package approximation

import (
	"archive/zip"
	"bytes"
	"fmt"
	"io"
	"os"
	"path/filepath"

	"github.com/seqra/opentaint/internal/testproject"
)

const gradleProjectName = "approximation-models"

// Scaffold creates a Gradle project for dataflow models.
func Scaffold(projectDir string, dependencies []string, analyzerJarPath string) error {
	apiJar, err := ExtractApiJar(analyzerJarPath)
	if err != nil {
		return err
	}

	localJar := filepath.ToSlash(filepath.Join(libsDirName, apiJarName))
	if err := testproject.BootstrapWithLocalJars(projectDir, gradleProjectName, dependencies, []string{localJar}); err != nil {
		return err
	}
	if err := WriteApiJar(projectDir, apiJar); err != nil {
		return err
	}
	return os.MkdirAll(filepath.Join(projectDir, "src", "main", "java"), 0o755)
}

// ExtractApiJar reads the approximation API jar from the analyzer jar.
func ExtractApiJar(analyzerJarPath string) ([]byte, error) {
	reader, err := zip.OpenReader(analyzerJarPath)
	if err != nil {
		return nil, fmt.Errorf("failed to open analyzer jar %s: %w", analyzerJarPath, err)
	}
	defer func() { _ = reader.Close() }()

	entryName := apiJarResourcePrefix + apiJarName
	for _, file := range reader.File {
		if file.Name != entryName {
			continue
		}
		content, err := file.Open()
		if err != nil {
			return nil, fmt.Errorf("failed to read %s from the analyzer jar: %w", apiJarName, err)
		}
		defer func() { _ = content.Close() }()
		return io.ReadAll(content)
	}
	return nil, fmt.Errorf("analyzer jar %s bundles no %s", analyzerJarPath, apiJarName)
}

// WriteApiJar writes the approximation API jar to a model project.
// It does not write the file when the content is current.
func WriteApiJar(projectDir string, apiJar []byte) error {
	path := apiJarPath(projectDir)
	if current, err := os.ReadFile(path); err == nil && bytes.Equal(current, apiJar) {
		return nil
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return fmt.Errorf("failed to create %s: %w", filepath.Dir(path), err)
	}
	return os.WriteFile(path, apiJar, 0o644)
}
