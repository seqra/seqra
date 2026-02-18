package utils

import (
	"archive/tar"
	"compress/gzip"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
)

func TestClassifyExePath(t *testing.T) {
	tests := []struct {
		name     string
		exePath  string
		goPath   string
		expected InstallMethod
	}{
		{
			"homebrew cellar",
			"/usr/local/Cellar/opentaint/1.0/bin/opentaint",
			"/home/user/go",
			InstallMethodHomebrew,
		},
		{
			"homebrew linuxbrew",
			"/home/user/.linuxbrew/Homebrew/bin/opentaint",
			"/home/user/go",
			InstallMethodHomebrew,
		},
		{
			"homebrew cask",
			"/opt/homebrew/Caskroom/opentaint/1.0/opentaint",
			"/home/user/go",
			InstallMethodHomebrew,
		},
		{
			"go install",
			"/home/user/go/bin/opentaint",
			"/home/user/go",
			InstallMethodGoInstall,
		},
		{
			"binary fallback",
			"/usr/local/bin/opentaint",
			"/home/user/go",
			InstallMethodBinary,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := classifyExePath(tt.exePath, tt.goPath)
			if got != tt.expected {
				t.Errorf("classifyExePath(%q, %q) = %d, want %d", tt.exePath, tt.goPath, got, tt.expected)
			}
		})
	}
}

func TestGetArchiveName(t *testing.T) {
	name := getArchiveName()

	ext := "tar.gz"
	if runtime.GOOS == "windows" {
		ext = "zip"
	}
	want := fmt.Sprintf("opentaint_%s_%s.%s", runtime.GOOS, runtime.GOARCH, ext)

	if name != want {
		t.Errorf("getArchiveName() = %q, want %q", name, want)
	}

	if !strings.HasPrefix(name, "opentaint_") {
		t.Errorf("getArchiveName() should start with 'opentaint_', got %q", name)
	}
}

func TestSelfUpdate(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("tar.gz specific test, skipping on Windows")
	}

	// Create a minimal tar.gz archive with a fake binary and lib/jre dirs
	archiveDir := t.TempDir()
	archivePath := filepath.Join(archiveDir, "opentaint_test.tar.gz")

	f, err := os.Create(archivePath)
	if err != nil {
		t.Fatal(err)
	}

	gw := gzip.NewWriter(f)
	tw := tar.NewWriter(gw)

	// Add fake binary
	binaryContent := []byte("#!/bin/sh\necho opentaint-test\n")
	if err := tw.WriteHeader(&tar.Header{
		Name: "opentaint",
		Mode: 0o755,
		Size: int64(len(binaryContent)),
	}); err != nil {
		t.Fatal(err)
	}
	if _, err := tw.Write(binaryContent); err != nil {
		t.Fatal(err)
	}

	// Add lib directory with a file
	if err := tw.WriteHeader(&tar.Header{
		Typeflag: tar.TypeDir,
		Name:     "lib/",
		Mode:     0o755,
	}); err != nil {
		t.Fatal(err)
	}
	libContent := []byte("fake-lib-content")
	if err := tw.WriteHeader(&tar.Header{
		Name: "lib/analyzer.jar",
		Mode: 0o644,
		Size: int64(len(libContent)),
	}); err != nil {
		t.Fatal(err)
	}
	if _, err := tw.Write(libContent); err != nil {
		t.Fatal(err)
	}

	// Add jre directory with a file
	if err := tw.WriteHeader(&tar.Header{
		Typeflag: tar.TypeDir,
		Name:     "jre/",
		Mode:     0o755,
	}); err != nil {
		t.Fatal(err)
	}
	jreContent := []byte("fake-jre-content")
	if err := tw.WriteHeader(&tar.Header{
		Name: "jre/bin/java",
		Mode: 0o755,
		Size: int64(len(jreContent)),
	}); err != nil {
		t.Fatal(err)
	}
	if _, err := tw.Write(jreContent); err != nil {
		t.Fatal(err)
	}

	if err := tw.Close(); err != nil {
		t.Fatal(err)
	}
	if err := gw.Close(); err != nil {
		t.Fatal(err)
	}
	if err := f.Close(); err != nil {
		t.Fatal(err)
	}

	// Create install directory with an existing binary
	installDir := t.TempDir()
	existingBinary := filepath.Join(installDir, "opentaint")
	if err := os.WriteFile(existingBinary, []byte("old-binary"), 0o755); err != nil {
		t.Fatal(err)
	}

	// Run SelfUpdate
	if err := SelfUpdate(archivePath, installDir); err != nil {
		t.Fatalf("SelfUpdate() error = %v", err)
	}

	// Verify binary was updated
	data, err := os.ReadFile(filepath.Join(installDir, "opentaint"))
	if err != nil {
		t.Fatalf("failed to read updated binary: %v", err)
	}
	if string(data) != string(binaryContent) {
		t.Errorf("binary content = %q, want %q", string(data), string(binaryContent))
	}

	// Verify lib directory was placed
	libData, err := os.ReadFile(filepath.Join(installDir, "lib", "analyzer.jar"))
	if err != nil {
		t.Fatalf("failed to read lib/analyzer.jar: %v", err)
	}
	if string(libData) != string(libContent) {
		t.Errorf("lib content = %q, want %q", string(libData), string(libContent))
	}

	// Verify jre directory was placed
	jreData, err := os.ReadFile(filepath.Join(installDir, "jre", "bin", "java"))
	if err != nil {
		t.Fatalf("failed to read jre/bin/java: %v", err)
	}
	if string(jreData) != string(jreContent) {
		t.Errorf("jre content = %q, want %q", string(jreData), string(jreContent))
	}
}
