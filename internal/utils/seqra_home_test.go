package utils

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/seqra/seqra/v2/internal/globals"
)

func TestIsInstallCurrent_NoMarker(t *testing.T) {
	t.Setenv("HOME", t.TempDir())

	if IsInstallCurrent() {
		t.Error("expected false when no marker file exists")
	}
}

func TestWriteAndIsInstallCurrent(t *testing.T) {
	t.Setenv("HOME", t.TempDir())

	if err := WriteInstallVersionMarker(); err != nil {
		t.Fatalf("WriteInstallVersionMarker() error = %v", err)
	}

	if !IsInstallCurrent() {
		t.Error("expected true after writing marker with current versions")
	}
}

func TestIsInstallCurrent_StaleMarker(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)

	installDir := filepath.Join(home, ".seqra", "install")
	if err := os.MkdirAll(installDir, 0o755); err != nil {
		t.Fatal(err)
	}
	// Write a marker with different content
	if err := os.WriteFile(filepath.Join(installDir, ".versions"), []byte("old-content"), 0o644); err != nil {
		t.Fatal(err)
	}

	if IsInstallCurrent() {
		t.Error("expected false when marker content differs from embedded versions")
	}
}

func TestCleanInstallDir(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)

	installDir := filepath.Join(home, ".seqra", "install")
	libDir := filepath.Join(installDir, "lib")
	jreDir := filepath.Join(installDir, "jre")

	// Create install dirs with content
	createTestFile(t, filepath.Join(libDir, "artifact.jar"), 100)
	createTestFile(t, filepath.Join(jreDir, "bin", "java"), 50)
	if err := os.WriteFile(filepath.Join(installDir, ".versions"), []byte("marker"), 0o644); err != nil {
		t.Fatal(err)
	}

	if err := CleanInstallDir(); err != nil {
		t.Fatalf("CleanInstallDir() error = %v", err)
	}

	// Verify lib, jre, and .versions are removed
	for _, p := range []string{libDir, jreDir, filepath.Join(installDir, ".versions")} {
		if _, err := os.Stat(p); !os.IsNotExist(err) {
			t.Errorf("expected %s to be removed", p)
		}
	}

	// install dir itself should still exist (not removed)
	if _, err := os.Stat(installDir); os.IsNotExist(err) {
		t.Error("install dir itself should not be removed")
	}
}

func TestGetInstallDir(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)

	got := GetInstallDir()
	want := filepath.Join(home, ".seqra", "install")
	if got != want {
		t.Errorf("GetInstallDir() = %q, want %q", got, want)
	}
}

func TestCleanInstallDir_NoDir(t *testing.T) {
	t.Setenv("HOME", t.TempDir())

	// Should not error when install dir doesn't exist
	if err := CleanInstallDir(); err != nil {
		t.Fatalf("CleanInstallDir() error = %v", err)
	}
}

func TestWriteInstallVersionMarker_Content(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)

	if err := WriteInstallVersionMarker(); err != nil {
		t.Fatalf("WriteInstallVersionMarker() error = %v", err)
	}

	markerPath := filepath.Join(home, ".seqra", "install", ".versions")
	data, err := os.ReadFile(markerPath)
	if err != nil {
		t.Fatalf("failed to read marker: %v", err)
	}

	expected := globals.GetVersionsYAML()
	if string(data) != string(expected) {
		t.Errorf("marker content = %q, want %q", data, expected)
	}
}
