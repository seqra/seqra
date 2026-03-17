package utils

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/seqra/opentaint/v2/internal/globals"
)

func TestDeleteArtifacts(t *testing.T) {
	t.Run("removes files and dirs", func(t *testing.T) {
		tmpDir := t.TempDir()

		f1 := filepath.Join(tmpDir, "file1.jar")
		createTestFile(t, f1, 10)

		d1 := filepath.Join(tmpDir, "dir1")
		if err := os.MkdirAll(d1, 0o755); err != nil {
			t.Fatal(err)
		}
		createTestFile(t, filepath.Join(d1, "inner.txt"), 5)

		artifacts := []StaleArtifact{
			{Path: f1, Size: 10, Kind: StaleKindAnalyzer},
			{Path: d1, Size: 5, Kind: StaleKindRules},
		}

		if err := DeleteArtifacts(artifacts); err != nil {
			t.Fatalf("DeleteArtifacts() error = %v", err)
		}

		for _, a := range artifacts {
			if _, err := os.Stat(a.Path); !os.IsNotExist(err) {
				t.Errorf("expected %s to be removed", a.Path)
			}
		}
	})

	t.Run("empty slice is no-op", func(t *testing.T) {
		if err := DeleteArtifacts(nil); err != nil {
			t.Fatalf("DeleteArtifacts(nil) error = %v", err)
		}
	})
}

func TestScanForStaleArtifacts(t *testing.T) {
	// Save and restore globals
	origAnalyzer := globals.AnalyzerBindVersion
	origAutobuilder := globals.AutobuilderBindVersion
	origRules := globals.RulesBindVersion
	origJava := globals.DefaultJavaVersion
	t.Cleanup(func() {
		globals.AnalyzerBindVersion = origAnalyzer
		globals.AutobuilderBindVersion = origAutobuilder
		globals.RulesBindVersion = origRules
		globals.DefaultJavaVersion = origJava
	})

	globals.AnalyzerBindVersion = "1.0.0"
	globals.AutobuilderBindVersion = "1.0.0"
	globals.RulesBindVersion = "v1.0.0"
	globals.DefaultJavaVersion = 21

	t.Run("empty home", func(t *testing.T) {
		t.Setenv("HOME", t.TempDir())
		result, err := ScanForStaleArtifacts(false)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(result.Stale) != 0 {
			t.Errorf("expected 0 stale, got %d", len(result.Stale))
		}
	})

	t.Run("stale analyzer", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		opentaintHome := filepath.Join(home, ".opentaint")
		createTestFile(t, filepath.Join(opentaintHome, "analyzer_0.9.0.jar"), 100)

		result, err := ScanForStaleArtifacts(false)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(result.Stale) != 1 {
			t.Fatalf("expected 1 stale, got %d", len(result.Stale))
		}
		if result.Stale[0].Kind != StaleKindAnalyzer {
			t.Errorf("expected kind=%s, got %q", StaleKindAnalyzer, result.Stale[0].Kind)
		}
	})

	t.Run("current analyzer", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		opentaintHome := filepath.Join(home, ".opentaint")
		createTestFile(t, filepath.Join(opentaintHome, "analyzer_1.0.0.jar"), 100)

		result, err := ScanForStaleArtifacts(false)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(result.Stale) != 0 {
			t.Errorf("expected 0 stale, got %d", len(result.Stale))
		}
	})

	t.Run("stale autobuilder", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		opentaintHome := filepath.Join(home, ".opentaint")
		createTestFile(t, filepath.Join(opentaintHome, "autobuilder_0.9.0.jar"), 100)

		result, err := ScanForStaleArtifacts(false)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(result.Stale) != 1 {
			t.Fatalf("expected 1 stale, got %d", len(result.Stale))
		}
		if result.Stale[0].Kind != StaleKindAutobuilder {
			t.Errorf("expected kind=%s, got %q", StaleKindAutobuilder, result.Stale[0].Kind)
		}
	})

	t.Run("stale rules", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		opentaintHome := filepath.Join(home, ".opentaint")
		rulesDir := filepath.Join(opentaintHome, "rules_v0.9.0")
		createTestFile(t, filepath.Join(rulesDir, "rule.yaml"), 50)

		result, err := ScanForStaleArtifacts(false)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(result.Stale) != 1 {
			t.Fatalf("expected 1 stale, got %d", len(result.Stale))
		}
		if result.Stale[0].Kind != StaleKindRules {
			t.Errorf("expected kind=%s, got %q", StaleKindRules, result.Stale[0].Kind)
		}
	})

	t.Run("stale JDK", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		opentaintHome := filepath.Join(home, ".opentaint")
		jdkDir := filepath.Join(opentaintHome, "jdk", "temurin-17-jdk+35")
		createTestFile(t, filepath.Join(jdkDir, "bin", "java"), 50)

		result, err := ScanForStaleArtifacts(false)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(result.Stale) != 1 {
			t.Fatalf("expected 1 stale, got %d", len(result.Stale))
		}
		if result.Stale[0].Kind != StaleKindJDK {
			t.Errorf("expected kind=%s, got %q", StaleKindJDK, result.Stale[0].Kind)
		}
	})

	t.Run("stale JRE", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		opentaintHome := filepath.Join(home, ".opentaint")
		jreDir := filepath.Join(opentaintHome, "jre", "temurin-17-jre+35")
		createTestFile(t, filepath.Join(jreDir, "bin", "java"), 50)

		result, err := ScanForStaleArtifacts(false)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(result.Stale) != 1 {
			t.Fatalf("expected 1 stale, got %d", len(result.Stale))
		}
		if result.Stale[0].Kind != StaleKindJRE {
			t.Errorf("expected kind=%s, got %q", StaleKindJRE, result.Stale[0].Kind)
		}
	})

	t.Run("current JRE", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		opentaintHome := filepath.Join(home, ".opentaint")
		jreDir := filepath.Join(opentaintHome, "jre", "temurin-21-jre+35")
		createTestFile(t, filepath.Join(jreDir, "bin", "java"), 50)

		result, err := ScanForStaleArtifacts(false)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(result.Stale) != 0 {
			t.Errorf("expected 0 stale, got %d", len(result.Stale))
		}
	})

	t.Run("logs includeLogs=true", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		opentaintHome := filepath.Join(home, ".opentaint")
		createTestFile(t, filepath.Join(opentaintHome, "logs", "app.log"), 200)

		result, err := ScanForStaleArtifacts(true)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(result.Stale) != 1 {
			t.Fatalf("expected 1 stale, got %d", len(result.Stale))
		}
		if result.Stale[0].Kind != StaleKindLog {
			t.Errorf("expected kind=%s, got %q", StaleKindLog, result.Stale[0].Kind)
		}
	})

	t.Run("logs includeLogs=false", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		opentaintHome := filepath.Join(home, ".opentaint")
		createTestFile(t, filepath.Join(opentaintHome, "logs", "app.log"), 200)

		result, err := ScanForStaleArtifacts(false)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(result.Stale) != 0 {
			t.Errorf("expected 0 stale, got %d", len(result.Stale))
		}
	})

	t.Run("hidden files skipped", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		opentaintHome := filepath.Join(home, ".opentaint")
		createTestFile(t, filepath.Join(opentaintHome, ".config"), 10)
		createTestFile(t, filepath.Join(opentaintHome, ".last-update-check"), 10)

		result, err := ScanForStaleArtifacts(false)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(result.Stale) != 0 {
			t.Errorf("expected 0 stale, got %d", len(result.Stale))
		}
	})

	t.Run("mixed old and current", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		opentaintHome := filepath.Join(home, ".opentaint")

		// Current (should not be stale)
		createTestFile(t, filepath.Join(opentaintHome, "analyzer_1.0.0.jar"), 100)
		createTestFile(t, filepath.Join(opentaintHome, "autobuilder_1.0.0.jar"), 100)

		// Old (should be stale)
		createTestFile(t, filepath.Join(opentaintHome, "analyzer_0.8.0.jar"), 100)
		createTestFile(t, filepath.Join(opentaintHome, "autobuilder_0.8.0.jar"), 100)

		result, err := ScanForStaleArtifacts(false)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(result.Stale) != 2 {
			t.Fatalf("expected 2 stale, got %d", len(result.Stale))
		}
		for _, s := range result.Stale {
			if s.Kind != StaleKindAnalyzer && s.Kind != StaleKindAutobuilder {
				t.Errorf("unexpected kind %q", s.Kind)
			}
		}
	})

	t.Run("stale install-lib no marker", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		installLib := filepath.Join(home, ".opentaint", "install", "lib")
		createTestFile(t, filepath.Join(installLib, "artifact.jar"), 100)

		result, err := ScanForStaleArtifacts(false)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		found := false
		for _, s := range result.Stale {
			if s.Kind == StaleKindInstallLib {
				found = true
			}
		}
		if !found {
			t.Error("expected install-lib to be flagged as stale when no version marker exists")
		}
	})

	t.Run("current install-lib with marker", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		installLib := filepath.Join(home, ".opentaint", "install", "lib")
		createTestFile(t, filepath.Join(installLib, "artifact.jar"), 100)

		// Write current marker
		if err := WriteInstallVersionMarker(); err != nil {
			t.Fatal(err)
		}

		result, err := ScanForStaleArtifacts(false)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		for _, s := range result.Stale {
			if s.Kind == StaleKindInstallLib {
				t.Error("expected install-lib not to be flagged when version marker is current")
			}
		}
	})

	t.Run("stale install-jre no marker", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		installJRE := filepath.Join(home, ".opentaint", "install", "jre")
		createTestFile(t, filepath.Join(installJRE, "bin", "java"), 50)

		result, err := ScanForStaleArtifacts(false)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		found := false
		for _, s := range result.Stale {
			if s.Kind == StaleKindInstallJRE {
				found = true
			}
		}
		if !found {
			t.Error("expected install-jre to be flagged as stale when no version marker exists")
		}
	})
}

func createTestFile(t *testing.T, path string, size int) {
	t.Helper()
	dir := filepath.Dir(path)
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	data := make([]byte, size)
	if err := os.WriteFile(path, data, 0o644); err != nil {
		t.Fatal(err)
	}
}
