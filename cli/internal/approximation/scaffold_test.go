package approximation

import (
	"os"
	"path/filepath"
	"slices"
	"strings"
	"testing"

	"golang.org/x/mod/modfile"
)

func TestScaffoldGoCreatesModelModule(t *testing.T) {
	projectDir := filepath.Join(t.TempDir(), "models")
	dependencies := []string{
		"github.com/acme/library@v1.2.3",
		"example.com/other/v2@v2.0.1",
	}

	if err := ScaffoldGo(projectDir, dependencies); err != nil {
		t.Fatalf("ScaffoldGo: %v", err)
	}

	content, err := os.ReadFile(filepath.Join(projectDir, goModuleFile))
	if err != nil {
		t.Fatal(err)
	}
	file, err := modfile.Parse(goModuleFile, content, nil)
	if err != nil {
		t.Fatalf("generated go.mod is invalid: %v\n%s", err, content)
	}
	if file.Module == nil || file.Module.Mod.Path != "opentaint" {
		t.Fatalf("module path = %v, want opentaint", file.Module)
	}
	if file.Go == nil || file.Go.Version != currentGoVersion() {
		t.Fatalf("Go version = %v, want %s", file.Go, currentGoVersion())
	}

	gotDependencies := make([]string, 0, len(file.Require))
	for _, requirement := range file.Require {
		gotDependencies = append(gotDependencies, requirement.Mod.String())
	}
	wantDependenciesList := slices.Clone(dependencies)
	slices.Sort(wantDependenciesList)
	wantDependencies := strings.Join(wantDependenciesList, "\n")
	if strings.Join(gotDependencies, "\n") != wantDependencies {
		t.Fatalf("dependencies = %v, want %v", gotDependencies, dependencies)
	}
	if !IsGoProject(projectDir) {
		t.Fatal("generated directory is not recognized as a Go approximation project")
	}
}

func TestGoModuleRejectsUnpinnedDependency(t *testing.T) {
	_, err := goModule([]string{"github.com/acme/library"}, "1.25.0")
	if err == nil || !strings.Contains(err.Error(), "module@version") {
		t.Fatalf("goModule error = %v, want module@version guidance", err)
	}
}

func TestGoModuleRejectsInvalidMajorVersion(t *testing.T) {
	_, err := goModule([]string{"example.com/library/v2@v1.0.0"}, "1.25.0")
	if err == nil || !strings.Contains(err.Error(), "should be v2") {
		t.Fatalf("goModule error = %v, want major-version error", err)
	}
}

func TestGoModuleDoesNotWritePartialProject(t *testing.T) {
	projectDir := filepath.Join(t.TempDir(), "models")
	err := ScaffoldGo(projectDir, []string{"not-pinned"})
	if err == nil {
		t.Fatal("ScaffoldGo accepted an unpinned dependency")
	}
	if _, statErr := os.Stat(filepath.Join(projectDir, goModuleFile)); !os.IsNotExist(statErr) {
		t.Fatalf("invalid scaffold wrote go.mod: %v", statErr)
	}
}

func TestScaffoldGoKeepsModelSourcesOnUpdate(t *testing.T) {
	projectDir := filepath.Join(t.TempDir(), "models")
	if err := ScaffoldGo(projectDir, nil); err != nil {
		t.Fatalf("first ScaffoldGo: %v", err)
	}
	modelPath := filepath.Join(projectDir, "net", "http", "model.go")
	writeFile(t, modelPath, "package http\n")

	if err := ScaffoldGo(projectDir, []string{"golang.org/x/text@v0.26.0"}); err != nil {
		t.Fatalf("second ScaffoldGo: %v", err)
	}
	content, err := os.ReadFile(modelPath)
	if err != nil {
		t.Fatalf("read model after update: %v", err)
	}
	if string(content) != "package http\n" {
		t.Fatalf("model changed on update: %q", content)
	}
}

func TestScaffoldGoKeepsGoModAfterInvalidUpdate(t *testing.T) {
	projectDir := filepath.Join(t.TempDir(), "models")
	if err := ScaffoldGo(projectDir, []string{"golang.org/x/text@v0.26.0"}); err != nil {
		t.Fatalf("first ScaffoldGo: %v", err)
	}
	goModPath := filepath.Join(projectDir, goModuleFile)
	before, err := os.ReadFile(goModPath)
	if err != nil {
		t.Fatal(err)
	}

	if err := ScaffoldGo(projectDir, []string{"invalid"}); err == nil {
		t.Fatal("invalid update succeeded")
	}
	after, err := os.ReadFile(goModPath)
	if err != nil {
		t.Fatal(err)
	}
	if string(after) != string(before) {
		t.Fatal("invalid update changed go.mod")
	}
}
