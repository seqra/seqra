package utils

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestProjectPathSlugHash(t *testing.T) {
	t.Run("deterministic output", func(t *testing.T) {
		result1 := ProjectPathSlugHash("/Users/me/my-project")
		result2 := ProjectPathSlugHash("/Users/me/my-project")
		if result1 != result2 {
			t.Errorf("expected deterministic output, got %q and %q", result1, result2)
		}
	})

	t.Run("format is slug-hash", func(t *testing.T) {
		result := ProjectPathSlugHash("/Users/me/my-project")
		// Should be lowercase slug + 8 hex chars
		if len(result) == 0 {
			t.Fatal("expected non-empty result")
		}
		// The slug part should contain "users-me-my-project"
		if result[:len(result)-9] != "users-me-my-project" {
			t.Errorf("unexpected slug portion: %q", result)
		}
		// Should end with dash + 8 hex chars
		if result[len(result)-9] != '-' {
			t.Errorf("expected dash before hash, got %q", result)
		}
	})

	t.Run("different paths produce different hashes", func(t *testing.T) {
		r1 := ProjectPathSlugHash("/Users/me/project-a")
		r2 := ProjectPathSlugHash("/Users/me/project-b")
		if r1 == r2 {
			t.Errorf("expected different hashes for different paths, both got %q", r1)
		}
	})

	t.Run("special characters replaced", func(t *testing.T) {
		result := ProjectPathSlugHash("/tmp/my project (copy)")
		for _, c := range result {
			if c != '-' && (c < 'a' || c > 'z') && (c < '0' || c > '9') {
				t.Errorf("unexpected character %q in slug-hash %q", string(c), result)
			}
		}
	})

	t.Run("leading and trailing dashes trimmed", func(t *testing.T) {
		result := ProjectPathSlugHash("/tmp/project")
		if result[0] == '-' {
			t.Errorf("slug-hash should not start with dash: %q", result)
		}
		if result[len(result)-1] == '-' {
			t.Errorf("slug-hash should not end with dash: %q", result)
		}
	})
}

func TestGetModelCacheDir(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)

	dir, err := GetModelCacheDir()
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	expected := filepath.Join(home, ".opentaint", "models")
	if dir != expected {
		t.Errorf("GetModelCacheDir() = %q, want %q", dir, expected)
	}

	// Directory should be created
	info, err := os.Stat(dir)
	if err != nil {
		t.Fatalf("directory not created: %v", err)
	}
	if !info.IsDir() {
		t.Error("expected directory, got file")
	}
}

func TestGetProjectCachePath(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)

	projectPath := filepath.Join(home, "my-project")
	if err := os.MkdirAll(projectPath, 0o755); err != nil {
		t.Fatal(err)
	}

	cachePath, err := GetProjectCachePath(projectPath)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	// Should be under ~/.opentaint/models/
	modelsDir := filepath.Join(home, ".opentaint", "models")
	if !strings.HasPrefix(cachePath, modelsDir) {
		t.Errorf("cache path %q should be under %q", cachePath, modelsDir)
	}

	// Directory should be created
	info, err := os.Stat(cachePath)
	if err != nil {
		t.Fatalf("directory not created: %v", err)
	}
	if !info.IsDir() {
		t.Error("expected directory, got file")
	}
}

func TestGetProjectCachePath_SymlinksResolved(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)

	// Create real dir and symlink to it
	realDir := filepath.Join(home, "real-project")
	if err := os.MkdirAll(realDir, 0o755); err != nil {
		t.Fatal(err)
	}
	symlinkDir := filepath.Join(home, "link-project")
	if err := os.Symlink(realDir, symlinkDir); err != nil {
		t.Fatal(err)
	}

	path1, err := GetProjectCachePath(realDir)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	path2, err := GetProjectCachePath(symlinkDir)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if path1 != path2 {
		t.Errorf("symlink and real path should resolve to same cache path: %q vs %q", path1, path2)
	}
}

func TestCreateStagingDir(t *testing.T) {
	cacheDir := t.TempDir()

	stagingPath, err := CreateStagingDir(cacheDir)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	// Should be under cacheDir
	if !strings.HasPrefix(stagingPath, cacheDir) {
		t.Errorf("staging path %q should be under %q", stagingPath, cacheDir)
	}

	// Directory name should start with .staging-
	dirName := filepath.Base(stagingPath)
	if !strings.HasPrefix(dirName, ".staging-") {
		t.Errorf("expected .staging- prefix, got %q", dirName)
	}

	// Staging dir itself should exist
	info, err := os.Stat(stagingPath)
	if err != nil {
		t.Fatalf("staging dir not created: %v", err)
	}
	if !info.IsDir() {
		t.Error("expected directory")
	}
}

func TestPromoteStagingToCache(t *testing.T) {
	cacheDir := t.TempDir()

	// Create a staging dir with content
	stagingPath, err := CreateStagingDir(cacheDir)
	if err != nil {
		t.Fatal(err)
	}
	// Add a file to project-model to simulate compilation output
	createTestFile(t, filepath.Join(stagingPath, "project-model", "project.yaml"), 10)

	// Promote
	err = PromoteStagingToCache(cacheDir, stagingPath)
	if err != nil {
		t.Fatalf("PromoteStagingToCache() error = %v", err)
	}

	// project-model symlink should exist and point to a timestamped dir
	symlinkPath := filepath.Join(cacheDir, "project-model")
	target, err := os.Readlink(symlinkPath)
	if err != nil {
		t.Fatalf("expected symlink at %s: %v", symlinkPath, err)
	}
	if !strings.HasPrefix(target, "project-model-") {
		t.Errorf("symlink target should start with project-model-, got %q", target)
	}

	// The file should be accessible through the symlink
	data, err := os.ReadFile(filepath.Join(symlinkPath, "project.yaml"))
	if err != nil {
		t.Fatalf("failed to read through symlink: %v", err)
	}
	if len(data) != 10 {
		t.Errorf("expected 10 bytes, got %d", len(data))
	}

	// Staging dir should be cleaned up
	if _, err := os.Stat(stagingPath); !os.IsNotExist(err) {
		t.Error("staging dir should be removed after promotion")
	}
}

func TestPromoteStagingToCache_ReplacesExisting(t *testing.T) {
	cacheDir := t.TempDir()

	// First promotion
	staging1, err := CreateStagingDir(cacheDir)
	if err != nil {
		t.Fatal(err)
	}
	createTestFile(t, filepath.Join(staging1, "project-model", "project.yaml"), 10)
	if err := PromoteStagingToCache(cacheDir, staging1); err != nil {
		t.Fatal(err)
	}

	oldTarget, _ := os.Readlink(filepath.Join(cacheDir, "project-model"))

	// Second promotion
	staging2, err := CreateStagingDir(cacheDir)
	if err != nil {
		t.Fatal(err)
	}
	createTestFile(t, filepath.Join(staging2, "project-model", "project.yaml"), 20)
	if err := PromoteStagingToCache(cacheDir, staging2); err != nil {
		t.Fatal(err)
	}

	newTarget, _ := os.Readlink(filepath.Join(cacheDir, "project-model"))

	// Symlink should point to new target
	if oldTarget == newTarget {
		t.Error("symlink should point to different target after second promotion")
	}

	// Old timestamped dir should be removed
	if _, err := os.Stat(filepath.Join(cacheDir, oldTarget)); !os.IsNotExist(err) {
		t.Error("old timestamped dir should be removed")
	}

	// New content should be accessible
	data, err := os.ReadFile(filepath.Join(cacheDir, "project-model", "project.yaml"))
	if err != nil {
		t.Fatalf("failed to read through symlink: %v", err)
	}
	if len(data) != 20 {
		t.Errorf("expected 20 bytes from second promotion, got %d", len(data))
	}
}

func TestCleanupStagingDir(t *testing.T) {
	cacheDir := t.TempDir()

	stagingPath, err := CreateStagingDir(cacheDir)
	if err != nil {
		t.Fatal(err)
	}
	createTestFile(t, filepath.Join(stagingPath, "project-model", "file.txt"), 5)

	CleanupStagingDir(stagingPath)

	if _, err := os.Stat(stagingPath); !os.IsNotExist(err) {
		t.Error("staging dir should be removed after cleanup")
	}
}
