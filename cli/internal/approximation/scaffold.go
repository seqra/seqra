package approximation

import (
	"archive/zip"
	"bytes"
	"fmt"
	"go/version"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"strings"

	"github.com/seqra/opentaint/internal/testproject"
	"github.com/seqra/opentaint/internal/utils"
	"golang.org/x/mod/modfile"
	"golang.org/x/mod/module"
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

// ScaffoldGo creates a Go module for dataflow models.
func ScaffoldGo(projectDir string, dependencies []string) error {
	content, err := goModule(dependencies, currentGoVersion())
	if err != nil {
		return err
	}
	return utils.WriteFiles(map[string][]byte{
		filepath.Join(projectDir, goModuleFile): content,
	})
}

func currentGoVersion() string {
	goVersion := runtime.Version()
	if !version.IsValid(goVersion) {
		goVersion = "go1.25.0"
	}
	return strings.TrimPrefix(goVersion, "go")
}

func goModule(dependencies []string, goVersion string) ([]byte, error) {
	file := new(modfile.File)
	if err := file.AddModuleStmt("opentaint"); err != nil {
		return nil, fmt.Errorf("set Go model module path: %w", err)
	}
	if err := file.AddGoStmt(goVersion); err != nil {
		return nil, fmt.Errorf("set Go model language version: %w", err)
	}

	for _, dependency := range dependencies {
		path, dependencyVersion, ok := strings.Cut(dependency, "@")
		if !ok || path == "" || dependencyVersion == "" || strings.Contains(dependencyVersion, "@") {
			return nil, fmt.Errorf("invalid Go dependency %q: use module@version", dependency)
		}
		if err := module.Check(path, dependencyVersion); err != nil {
			return nil, fmt.Errorf("invalid Go dependency %q: %w", dependency, err)
		}
		if err := file.AddRequire(path, dependencyVersion); err != nil {
			return nil, fmt.Errorf("add Go dependency %q: %w", dependency, err)
		}
	}

	file.SortBlocks()
	content, err := file.Format()
	if err != nil {
		return nil, fmt.Errorf("format Go model module: %w", err)
	}
	return content, nil
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
		if file.Name == entryName {
			return readZipEntry(file)
		}
	}
	return nil, fmt.Errorf("analyzer jar %s bundles no %s", analyzerJarPath, apiJarName)
}

func readZipEntry(file *zip.File) ([]byte, error) {
	content, err := file.Open()
	if err != nil {
		return nil, fmt.Errorf("failed to read %s from the analyzer jar: %w", file.Name, err)
	}
	defer func() { _ = content.Close() }()
	return io.ReadAll(content)
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
