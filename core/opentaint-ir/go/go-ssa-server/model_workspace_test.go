package server

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"golang.org/x/tools/go/packages"
)

func TestCreateModelWorkspaceUsesNewestModuleGoVersion(t *testing.T) {
	modelDir := t.TempDir()
	projectDir := t.TempDir()
	writeFile(t, filepath.Join(modelDir, "go.mod"), "module opentaint\ngo 1.98.1\n")
	writeFile(t, filepath.Join(projectDir, "go.mod"), "module example.com/project\ngo 1.99.2\n")
	projectInfo := map[string]*packages.Package{
		"example.com/project": {
			Module: &packages.Module{
				Path:      "example.com/project",
				GoVersion: "1.99.2",
				Main:      true,
				Dir:       projectDir,
			},
		},
	}

	workspace, cleanup, err := createModelWorkspace(modelDir, projectInfo, nil)
	if err != nil {
		t.Fatal(err)
	}
	defer cleanup()
	data, err := os.ReadFile(workspace)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(string(data), "go 1.99.2\n") {
		t.Fatalf("workspace does not use the newest Go version:\n%s", data)
	}
}
