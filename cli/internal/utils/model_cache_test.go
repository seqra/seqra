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
		// The slug part should be the last path segment only
		if result[:len(result)-9] != "my-project" {
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

	expected := filepath.Join(home, ".opentaint", "cache")
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

	// Should be under ~/.opentaint/cache/
	modelsDir := filepath.Join(home, ".opentaint", "cache")
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

func TestGetLogCacheDirPath(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)

	logDir, err := GetLogCacheDirPath()
	if err != nil {
		t.Fatalf("GetLogCacheDirPath() error = %v", err)
	}
	expected := filepath.Join(home, ".opentaint", "logs")
	if logDir != expected {
		t.Errorf("got %q, want %q", logDir, expected)
	}
}

func TestGetProjectLogPath(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)

	// Use a real existing path so EvalSymlinks works.
	// projectPath is passed as-is; GetProjectLogPath canonicalizes it internally.
	// The slug-hash is computed from the resolved path, so we resolve here too.
	projectPath := home
	resolvedProjectPath, err := filepath.EvalSymlinks(projectPath)
	if err != nil {
		t.Fatalf("EvalSymlinks projectPath: %v", err)
	}

	logPath, err := GetProjectLogPath(projectPath)
	if err != nil {
		t.Fatalf("GetProjectLogPath() error = %v", err)
	}

	// GetOpenTaintHomePath uses os.UserHomeDir() which returns HOME as-is (unresolved).
	// So the logs base is HOME/.opentaint/logs, not resolved-HOME/.opentaint/logs.
	slugHash := ProjectPathSlugHash(resolvedProjectPath)
	expected := filepath.Join(home, ".opentaint", "logs", slugHash)
	if logPath != expected {
		t.Errorf("got %q, want %q", logPath, expected)
	}
}

func TestCompileCompleteMarkerPath(t *testing.T) {
	cacheDir := "/home/user/.opentaint/cache/my-project-abc12345"
	got := CompileCompleteMarkerPath(cacheDir)
	expected := "/home/user/.opentaint/cache/my-project-abc12345/project-model/.compile-complete"
	if got != expected {
		t.Errorf("got %q, want %q", got, expected)
	}
}

func TestIsCachedModelComplete(t *testing.T) {
	t.Run("false for non-existent cache", func(t *testing.T) {
		cacheDir := t.TempDir()
		if IsCachedModelComplete(cacheDir) {
			t.Error("empty cache dir should not be complete")
		}
	})

	t.Run("false when project.yaml missing", func(t *testing.T) {
		cacheDir := t.TempDir()
		// Only the marker, no project.yaml.
		createTestFile(t, filepath.Join(cacheDir, "project-model", ".compile-complete"), 0)
		if IsCachedModelComplete(cacheDir) {
			t.Error("missing project.yaml should make cache incomplete")
		}
	})

	t.Run("false when marker missing", func(t *testing.T) {
		cacheDir := t.TempDir()
		// Only project.yaml, no marker.
		createTestFile(t, filepath.Join(cacheDir, "project-model", "project.yaml"), 10)
		if IsCachedModelComplete(cacheDir) {
			t.Error("missing marker should make cache incomplete")
		}
	})

	t.Run("true when both present", func(t *testing.T) {
		cacheDir := t.TempDir()
		createTestFile(t, filepath.Join(cacheDir, "project-model", "project.yaml"), 10)
		createTestFile(t, filepath.Join(cacheDir, "project-model", ".compile-complete"), 0)
		if !IsCachedModelComplete(cacheDir) {
			t.Error("both files present should mean complete")
		}
	})
}

func TestMarkCompileComplete(t *testing.T) {
	cacheDir := t.TempDir()
	// The caller always creates project-model/ before calling.
	if err := os.MkdirAll(filepath.Join(cacheDir, "project-model"), 0o755); err != nil {
		t.Fatal(err)
	}

	if err := MarkCompileComplete(cacheDir); err != nil {
		t.Fatalf("MarkCompileComplete() error = %v", err)
	}

	info, err := os.Stat(CompileCompleteMarkerPath(cacheDir))
	if err != nil {
		t.Fatalf("marker should exist: %v", err)
	}
	if info.Size() != 0 {
		t.Errorf("marker should be empty, got size %d", info.Size())
	}
}
