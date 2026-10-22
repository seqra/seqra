package project

import (
	"os"
	"path/filepath"
	"testing"
)

func TestWriteGoProjectModel(t *testing.T) {
	root := t.TempDir()
	output := filepath.Join(t.TempDir(), "model")

	if err := WriteGoProjectModel(root, output); err != nil {
		t.Fatal(err)
	}

	config, err := LoadConfig(output)
	if err != nil {
		t.Fatal(err)
	}
	if config.ProjectRoot != root {
		t.Fatalf("project root = %q, want %q", config.ProjectRoot, root)
	}
	if len(config.GoProjects) != 1 || config.GoProjects[0].ProjectDir != root {
		t.Fatalf("Go projects = %#v, want root project", config.GoProjects)
	}
	if info, err := os.Stat(filepath.Join(output, "project.yaml")); err != nil || info.Size() == 0 {
		t.Fatalf("project.yaml is missing or empty: %v", err)
	}
}
