package utils

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/seqra/opentaint/internal/globals"
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

	t.Run("cleans up empty parent dirs for model artifacts", func(t *testing.T) {
		tmpDir := t.TempDir()
		cacheDir := filepath.Join(tmpDir, "cache")
		projectDir := filepath.Join(cacheDir, "my-project-a1b2c3d4")
		pmDir := filepath.Join(projectDir, "project-model")
		createTestFile(t, filepath.Join(pmDir, "project.yaml"), 50)

		artifacts := []StaleArtifact{
			{Path: pmDir, Size: 50, Kind: StaleKindModel},
		}

		if err := DeleteArtifacts(artifacts); err != nil {
			t.Fatalf("DeleteArtifacts() error = %v", err)
		}

		// project-model/ should be gone
		if _, err := os.Stat(pmDir); !os.IsNotExist(err) {
			t.Errorf("expected project-model dir to be removed")
		}
		// Empty project dir should be cleaned up
		if _, err := os.Stat(projectDir); !os.IsNotExist(err) {
			t.Errorf("expected empty project cache dir to be removed")
		}
		// Empty cache dir should be cleaned up
		if _, err := os.Stat(cacheDir); !os.IsNotExist(err) {
			t.Errorf("expected empty cache dir to be removed")
		}
	})

	t.Run("preserves non-empty parent dirs for model artifacts", func(t *testing.T) {
		tmpDir := t.TempDir()
		cacheDir := filepath.Join(tmpDir, "cache")
		projectDir := filepath.Join(cacheDir, "my-project-a1b2c3d4")
		pmDir := filepath.Join(projectDir, "project-model")
		createTestFile(t, filepath.Join(pmDir, "project.yaml"), 50)
		// Another project still exists in cache/
		createTestFile(t, filepath.Join(cacheDir, "other-project-deadbeef", "project-model", "p.yaml"), 10)

		artifacts := []StaleArtifact{
			{Path: pmDir, Size: 50, Kind: StaleKindModel},
		}

		if err := DeleteArtifacts(artifacts); err != nil {
			t.Fatalf("DeleteArtifacts() error = %v", err)
		}

		// Empty project dir should be cleaned up
		if _, err := os.Stat(projectDir); !os.IsNotExist(err) {
			t.Errorf("expected empty project cache dir to be removed")
		}
		// cache dir should remain (has other project)
		if _, err := os.Stat(cacheDir); os.IsNotExist(err) {
			t.Errorf("expected cache dir to remain (has other projects)")
		}
	})

	t.Run("empty slice is no-op", func(t *testing.T) {
		if err := DeleteArtifacts(nil); err != nil {
			t.Fatalf("DeleteArtifacts(nil) error = %v", err)
		}
	})
}

func setupPruneTestGlobals(t *testing.T) {
	t.Helper()
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
}

func assertHasKind(t *testing.T, result *PruneResult, kind string) {
	t.Helper()
	for _, s := range result.Stale {
		if s.Kind == kind {
			return
		}
	}
	t.Errorf("expected stale entry with kind %q, found none", kind)
}

func assertNoKind(t *testing.T, result *PruneResult, kind string) {
	t.Helper()
	for _, s := range result.Stale {
		if s.Kind == kind {
			t.Errorf("expected no stale entry with kind %q, but found one at %s", kind, s.Path)
			return
		}
	}
}

func TestScanForStaleArtifacts(t *testing.T) {
	setupPruneTestGlobals(t)

	t.Run("empty home", func(t *testing.T) {
		t.Setenv("HOME", t.TempDir())
		result, err := ScanForStaleArtifacts(PruneCategoriesDefault)
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

		result, err := ScanForStaleArtifacts(PruneCategoriesDefault)
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

		result, err := ScanForStaleArtifacts(PruneCategoriesDefault)
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

		result, err := ScanForStaleArtifacts(PruneCategoriesDefault)
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

		result, err := ScanForStaleArtifacts(PruneCategoriesDefault)
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

		result, err := ScanForStaleArtifacts(PruneCategoriesDefault)
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

		result, err := ScanForStaleArtifacts(PruneCategoriesDefault)
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

		result, err := ScanForStaleArtifacts(PruneCategoriesDefault)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(result.Stale) != 0 {
			t.Errorf("expected 0 stale, got %d", len(result.Stale))
		}
	})


	t.Run("logs dir skipped in home scan", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		logsDir := filepath.Join(home, ".opentaint", "logs", "my-project-a1b2c3d4")
		createTestFile(t, filepath.Join(logsDir, "app.log"), 200)

		result, err := ScanForStaleArtifacts(PruneCategoriesDefault)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		assertNoKind(t, result, StaleKindLog)
	})

	t.Run("hidden files skipped", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		opentaintHome := filepath.Join(home, ".opentaint")
		createTestFile(t, filepath.Join(opentaintHome, ".config"), 10)
		createTestFile(t, filepath.Join(opentaintHome, ".last-update-check"), 10)

		result, err := ScanForStaleArtifacts(PruneCategoriesDefault)
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

		result, err := ScanForStaleArtifacts(PruneCategoriesDefault)
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

	t.Run("install-lib not pruned without all flag", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		installLib := filepath.Join(home, ".opentaint", "install", "lib")
		createTestFile(t, filepath.Join(installLib, "artifact.jar"), 100)

		result, err := ScanForStaleArtifacts(PruneCategoriesDefault)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		assertNoKind(t, result, StaleKindInstallLib)
	})

	t.Run("current install-lib pruned with all flag", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		installLib := filepath.Join(home, ".opentaint", "install", "lib")
		createTestFile(t, filepath.Join(installLib, "artifact.jar"), 100)

		// Write current marker
		if err := WriteInstallVersionMarker(); err != nil {
			t.Fatal(err)
		}

		result, err := ScanForStaleArtifacts(PruneCategoriesAll)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		assertHasKind(t, result, StaleKindInstallLib)
	})

	t.Run("install-jre not pruned without all flag", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		installJRE := filepath.Join(home, ".opentaint", "install", "jre")
		createTestFile(t, filepath.Join(installJRE, "bin", "java"), 50)

		result, err := ScanForStaleArtifacts(PruneCategoriesDefault)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		assertNoKind(t, result, StaleKindInstallJRE)
	})

	t.Run("install-jre pruned with all flag", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		installJRE := filepath.Join(home, ".opentaint", "install", "jre")
		createTestFile(t, filepath.Join(installJRE, "bin", "java"), 50)

		result, err := ScanForStaleArtifacts(PruneCategoriesAll)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		assertHasKind(t, result, StaleKindInstallJRE)
	})
}

func TestScanForStaleArtifacts_CachedModels(t *testing.T) {
	setupPruneTestGlobals(t)

	t.Run("cached model is prunable without all", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		projectDir := filepath.Join(home, ".opentaint", "cache", "my-project-a1b2c3d4")
		pmPath := filepath.Join(projectDir, "project-model", "project.yaml")
		createTestFile(t, pmPath, 50)

		result, err := ScanForStaleArtifacts(PruneCategoriesDefault)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		assertHasKind(t, result, StaleKindModel)
		// Should target project-model/ specifically, not the parent dir
		for _, s := range result.Stale {
			if s.Kind == StaleKindModel {
				expected := filepath.Join(projectDir, "project-model")
				if s.Path != expected {
					t.Errorf("expected path %q, got %q", expected, s.Path)
				}
			}
		}
	})

	t.Run("logs preserved without all flag", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		projectDir := filepath.Join(home, ".opentaint", "cache", "my-project-a1b2c3d4")
		createTestFile(t, filepath.Join(projectDir, "project-model", "project.yaml"), 50)
		// Logs are now in ~/.opentaint/logs/, not in cache dirs
		logsDir := filepath.Join(home, ".opentaint", "logs", "my-project-a1b2c3d4")
		createTestFile(t, filepath.Join(logsDir, "2026-01-01.log"), 100)

		result, err := ScanForStaleArtifacts(PruneCategoriesDefault)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		assertHasKind(t, result, StaleKindModel)
		assertNoKind(t, result, StaleKindLog)
		// Only project-model should be listed, not logs
		for _, s := range result.Stale {
			if s.Kind == StaleKindModel && s.Size != 50 {
				t.Errorf("expected model size 50 (excluding logs), got %d", s.Size)
			}
		}
	})

	t.Run("no size double-counting with all flag", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		projectDir := filepath.Join(home, ".opentaint", "cache", "my-project-a1b2c3d4")
		createTestFile(t, filepath.Join(projectDir, "project-model", "project.yaml"), 50)
		// Logs live in their own top-level directory, counted separately
		logsDir := filepath.Join(home, ".opentaint", "logs", "my-project-a1b2c3d4")
		createTestFile(t, filepath.Join(logsDir, "2026-01-01.log"), 100)

		result, err := ScanForStaleArtifacts(PruneCategoriesAll)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		// Model entry targets project-model/ subdir
		modelCount := 0
		for _, s := range result.Stale {
			if s.Kind == StaleKindModel {
				modelCount++
			}
		}
		if modelCount != 1 {
			t.Errorf("expected 1 model entry, got %d", modelCount)
		}
		// Log entry is separate, targeting ~/.opentaint/logs/<slug>/
		logCount := 0
		for _, s := range result.Stale {
			if s.Kind == StaleKindLog {
				logCount++
			}
		}
		if logCount != 1 {
			t.Errorf("expected 1 log entry, got %d", logCount)
		}
	})

	t.Run("empty models dir produces no stale", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		modelsDir := filepath.Join(home, ".opentaint", "cache")
		if err := os.MkdirAll(modelsDir, 0o755); err != nil {
			t.Fatal(err)
		}

		result, err := ScanForStaleArtifacts(PruneCategoriesDefault)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		assertNoKind(t, result, StaleKindModel)
	})
}

func TestScanForStaleArtifacts_Categories(t *testing.T) {
	setupPruneTestGlobals(t)

	t.Run("artifacts-only prunes jars not rules", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		opentaintHome := filepath.Join(home, ".opentaint")
		createTestFile(t, filepath.Join(opentaintHome, "analyzer_0.9.0.jar"), 100)
		rulesDir := filepath.Join(opentaintHome, "rules_v0.9.0")
		createTestFile(t, filepath.Join(rulesDir, "rule.yaml"), 50)

		result, err := ScanForStaleArtifacts(PruneCategoryArtifacts)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		assertHasKind(t, result, StaleKindAnalyzer)
		assertNoKind(t, result, StaleKindRules)
	})

	t.Run("rules-only prunes rules not jars", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		opentaintHome := filepath.Join(home, ".opentaint")
		createTestFile(t, filepath.Join(opentaintHome, "analyzer_0.9.0.jar"), 100)
		rulesDir := filepath.Join(opentaintHome, "rules_v0.9.0")
		createTestFile(t, filepath.Join(rulesDir, "rule.yaml"), 50)

		result, err := ScanForStaleArtifacts(PruneCategoryRules)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		assertHasKind(t, result, StaleKindRules)
		assertNoKind(t, result, StaleKindAnalyzer)
	})

	t.Run("logs-only scans logs dir", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		logsDir := filepath.Join(home, ".opentaint", "logs", "my-project-a1b2c3d4")
		createTestFile(t, filepath.Join(logsDir, "2026-01-01.log"), 100)

		result, err := ScanForStaleArtifacts(PruneCategoryLogs)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		assertHasKind(t, result, StaleKindLog)
		assertNoKind(t, result, StaleKindModel)
		assertNoKind(t, result, StaleKindAnalyzer)
	})

	t.Run("default categories match expected behavior", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		opentaintHome := filepath.Join(home, ".opentaint")
		// Artifact (should be pruned)
		createTestFile(t, filepath.Join(opentaintHome, "analyzer_0.9.0.jar"), 100)
		// Rules (should be pruned)
		rulesDir := filepath.Join(opentaintHome, "rules_v0.9.0")
		createTestFile(t, filepath.Join(rulesDir, "rule.yaml"), 50)
		// JDK (should be pruned)
		jdkDir := filepath.Join(opentaintHome, "jdk", "temurin-17-jdk+35")
		createTestFile(t, filepath.Join(jdkDir, "bin", "java"), 50)
		// Model (should be pruned)
		pmPath := filepath.Join(opentaintHome, "cache", "my-project-a1b2c3d4", "project-model", "project.yaml")
		createTestFile(t, pmPath, 50)
		// Logs (should NOT be pruned with default)
		logsDir := filepath.Join(opentaintHome, "logs", "my-project-a1b2c3d4")
		createTestFile(t, filepath.Join(logsDir, "2026-01-01.log"), 100)
		// Install (should NOT be pruned with default)
		installLib := filepath.Join(opentaintHome, "install", "lib")
		createTestFile(t, filepath.Join(installLib, "artifact.jar"), 100)

		result, err := ScanForStaleArtifacts(PruneCategoriesDefault)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		assertHasKind(t, result, StaleKindAnalyzer)
		assertHasKind(t, result, StaleKindRules)
		assertHasKind(t, result, StaleKindJDK)
		assertHasKind(t, result, StaleKindModel)
		assertNoKind(t, result, StaleKindLog)
		assertNoKind(t, result, StaleKindInstallLib)
	})

	t.Run("all categories include logs and install", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		opentaintHome := filepath.Join(home, ".opentaint")
		logsDir := filepath.Join(opentaintHome, "logs", "my-project-a1b2c3d4")
		createTestFile(t, filepath.Join(logsDir, "2026-01-01.log"), 100)
		installLib := filepath.Join(opentaintHome, "install", "lib")
		createTestFile(t, filepath.Join(installLib, "artifact.jar"), 100)

		result, err := ScanForStaleArtifacts(PruneCategoriesAll)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		assertHasKind(t, result, StaleKindLog)
		assertHasKind(t, result, StaleKindInstallLib)
	})
}

func TestScanForStaleArtifacts_LockedModelSkipped(t *testing.T) {
	setupPruneTestGlobals(t)

	t.Run("locked project is skipped during model scan", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		projectDir := filepath.Join(home, ".opentaint", "cache", "my-project-a1b2c3d4")
		createTestFile(t, filepath.Join(projectDir, "project-model", "project.yaml"), 50)

		// Hold the cache lock exclusively (simulate in-progress compile).
		lockPath := CacheLockPath(projectDir)
		lock, err := TryLockExclusive(lockPath, LockMeta{PID: 99999, Command: "compile"})
		if err != nil {
			t.Fatalf("failed to acquire cache lock: %v", err)
		}
		defer lock.Unlock()

		result, err := ScanForStaleArtifacts(PruneCategoryModels)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		assertNoKind(t, result, StaleKindModel)
		if len(result.Skipped) != 1 {
			t.Fatalf("expected 1 skipped, got %d", len(result.Skipped))
		}
		if result.Skipped[0].Meta.PID != 99999 {
			t.Errorf("expected PID 99999, got %d", result.Skipped[0].Meta.PID)
		}
	})
}

func TestPruneResult_AddSkipped(t *testing.T) {
	result := &PruneResult{}
	result.AddSkipped(SkippedProject{
		Path: "/home/user/.opentaint/cache/my-project-abc12345",
		Meta: LockMeta{PID: 12345, Command: "compile"},
	})

	if len(result.Skipped) != 1 {
		t.Fatalf("expected 1 skipped, got %d", len(result.Skipped))
	}
	if result.Skipped[0].Meta.PID != 12345 {
		t.Errorf("expected PID 12345, got %d", result.Skipped[0].Meta.PID)
	}
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
