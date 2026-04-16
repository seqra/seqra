# Prune: Lockfiles & Granular Flags Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add cross-platform file locking and granular category flags to the `opentaint prune` command, and move logs to a separate top-level directory.

**Architecture:** Introduce a `lock.go` module wrapping `github.com/gofrs/flock` for cross-platform advisory file locks. Refactor `ScanForStaleArtifacts` to accept a category set instead of a single `all` bool. Move log writing from `cache/<slug>/logs/` to `logs/<slug>/`. Replace `HasStagingDir` heuristic with flock-based compile locks.

**Tech Stack:** Go 1.25, `github.com/gofrs/flock`, Cobra CLI framework

**Spec:** `docs/superpowers/specs/2026-04-16-prune-lockfiles-granular-flags-design.md`

---

### Task 1: Add `github.com/gofrs/flock` dependency

**Files:**
- Modify: `cli/go.mod`
- Modify: `cli/go.sum`

- [ ] **Step 1: Add the dependency**

```bash
cd cli && go get github.com/gofrs/flock
```

- [ ] **Step 2: Verify it compiles**

```bash
cd cli && go build ./...
```
Expected: no errors

- [ ] **Step 3: Commit**

```bash
git add cli/go.mod cli/go.sum
git commit -m "chore: add github.com/gofrs/flock dependency"
```

---

### Task 2: Implement `lock.go` — cross-platform file locking

**Files:**
- Create: `cli/internal/utils/lock.go`
- Create: `cli/internal/utils/lock_test.go`

- [ ] **Step 1: Write failing tests for lock acquisition**

Create `cli/internal/utils/lock_test.go` with tests:

```go
package utils

import (
	"os"
	"path/filepath"
	"testing"
)

func TestTryLock(t *testing.T) {
	t.Run("acquires lock on new file", func(t *testing.T) {
		lockPath := filepath.Join(t.TempDir(), "test.lock")
		lock, err := TryLock(lockPath, LockMeta{PID: os.Getpid(), Command: "test"})
		if err != nil {
			t.Fatalf("TryLock() error = %v", err)
		}
		defer lock.Unlock()
	})

	t.Run("second lock on same file returns ErrLocked", func(t *testing.T) {
		lockPath := filepath.Join(t.TempDir(), "test.lock")
		lock1, err := TryLock(lockPath, LockMeta{PID: os.Getpid(), Command: "first"})
		if err != nil {
			t.Fatalf("first TryLock() error = %v", err)
		}
		defer lock1.Unlock()

		_, err = TryLock(lockPath, LockMeta{PID: os.Getpid(), Command: "second"})
		if err != ErrLocked {
			t.Fatalf("expected ErrLocked, got %v", err)
		}
	})

	t.Run("lock released after Unlock allows re-acquisition", func(t *testing.T) {
		lockPath := filepath.Join(t.TempDir(), "test.lock")
		lock1, err := TryLock(lockPath, LockMeta{PID: os.Getpid(), Command: "first"})
		if err != nil {
			t.Fatalf("first TryLock() error = %v", err)
		}
		lock1.Unlock()

		lock2, err := TryLock(lockPath, LockMeta{PID: os.Getpid(), Command: "second"})
		if err != nil {
			t.Fatalf("second TryLock() error = %v", err)
		}
		defer lock2.Unlock()
	})
}

func TestReadLockMeta(t *testing.T) {
	t.Run("reads PID and command from lock file", func(t *testing.T) {
		lockPath := filepath.Join(t.TempDir(), "test.lock")
		meta := LockMeta{PID: 12345, Command: "compile", Project: "/tmp/my-project"}
		lock, err := TryLock(lockPath, meta)
		if err != nil {
			t.Fatalf("TryLock() error = %v", err)
		}
		defer lock.Unlock()

		got, err := ReadLockMeta(lockPath)
		if err != nil {
			t.Fatalf("ReadLockMeta() error = %v", err)
		}
		if got.PID != 12345 {
			t.Errorf("PID = %d, want 12345", got.PID)
		}
		if got.Command != "compile" {
			t.Errorf("Command = %q, want %q", got.Command, "compile")
		}
		if got.Project != "/tmp/my-project" {
			t.Errorf("Project = %q, want %q", got.Project, "/tmp/my-project")
		}
	})

	t.Run("returns error for missing file", func(t *testing.T) {
		_, err := ReadLockMeta(filepath.Join(t.TempDir(), "missing.lock"))
		if err == nil {
			t.Fatal("expected error for missing file")
		}
	})
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd cli && go test ./internal/utils/ -run "TestTryLock|TestReadLockMeta" -v
```
Expected: FAIL — `TryLock`, `LockMeta`, `ErrLocked`, `ReadLockMeta` undefined

- [ ] **Step 3: Implement `lock.go`**

Create `cli/internal/utils/lock.go`:

```go
package utils

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/gofrs/flock"
)

// ErrLocked is returned when a lock file is already held by another process.
var ErrLocked = errors.New("lock is held by another process")

// LockMeta holds diagnostic information written into lock files.
type LockMeta struct {
	PID     int
	Command string
	Project string
}

// FileLock wraps a flock.Flock with its path for cleanup.
type FileLock struct {
	flock *flock.Flock
	path  string
}

// Unlock releases the advisory lock and removes the lock file.
func (l *FileLock) Unlock() {
	_ = l.flock.Unlock()
	_ = os.Remove(l.path)
}

// TryLock attempts a non-blocking exclusive lock on the given path.
// On success it writes meta into the file and returns a FileLock.
// On failure because the lock is held, it returns ErrLocked.
func TryLock(lockPath string, meta LockMeta) (*FileLock, error) {
	if err := os.MkdirAll(filepath.Dir(lockPath), 0o755); err != nil {
		return nil, fmt.Errorf("failed to create lock directory: %w", err)
	}

	fl := flock.New(lockPath)
	locked, err := fl.TryLock()
	if err != nil {
		return nil, fmt.Errorf("failed to acquire lock: %w", err)
	}
	if !locked {
		return nil, ErrLocked
	}

	content := fmt.Sprintf("pid=%d\ncommand=%s\n", meta.PID, meta.Command)
	if meta.Project != "" {
		content += fmt.Sprintf("project=%s\n", meta.Project)
	}
	_ = os.WriteFile(lockPath, []byte(content), 0o644)

	return &FileLock{flock: fl, path: lockPath}, nil
}

// ReadLockMeta reads diagnostic metadata from a lock file.
func ReadLockMeta(lockPath string) (LockMeta, error) {
	data, err := os.ReadFile(lockPath)
	if err != nil {
		return LockMeta{}, err
	}
	var meta LockMeta
	for _, line := range strings.Split(string(data), "\n") {
		key, value, ok := strings.Cut(line, "=")
		if !ok {
			continue
		}
		switch key {
		case "pid":
			meta.PID, _ = strconv.Atoi(value)
		case "command":
			meta.Command = value
		case "project":
			meta.Project = value
		}
	}
	return meta, nil
}

// PruneLockPath returns the path to the global prune lock: ~/.opentaint/.prune.lock
func PruneLockPath() (string, error) {
	home, err := GetOpenTaintHomePath()
	if err != nil {
		return "", err
	}
	return filepath.Join(home, ".prune.lock"), nil
}

// CompileLockPath returns the path to a per-project compile lock:
// ~/.opentaint/cache/<slug-hash>/.compile.lock
func CompileLockPath(projectCachePath string) string {
	return filepath.Join(projectCachePath, ".compile.lock")
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd cli && go test ./internal/utils/ -run "TestTryLock|TestReadLockMeta" -v
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add cli/internal/utils/lock.go cli/internal/utils/lock_test.go
git commit -m "feat: add cross-platform file locking with gofrs/flock"
```

---

### Task 3: Move log writing to `~/.opentaint/logs/<slug-hash>/`

**Files:**
- Modify: `cli/internal/utils/log/project_log.go`
- Modify: `cli/internal/utils/model_cache.go` (add `GetLogCacheDirPath` / `GetProjectLogPath`)
- Modify: `cli/cmd/logging.go` (use new log path)
- Modify: `cli/cmd/scan.go` (pass log path instead of cache path)

- [ ] **Step 1: Write failing test for new log path helper**

Add to `cli/internal/utils/model_cache_test.go` (or create it):

```go
package utils

import (
	"path/filepath"
	"testing"
)

func TestGetProjectLogPath(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)

	projectPath := "/Users/dev/my-project"
	logPath, err := GetProjectLogPath(projectPath)
	if err != nil {
		t.Fatalf("GetProjectLogPath() error = %v", err)
	}

	slugHash := ProjectPathSlugHash(projectPath)
	expected := filepath.Join(home, ".opentaint", "logs", slugHash)
	if logPath != expected {
		t.Errorf("got %q, want %q", logPath, expected)
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
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd cli && go test ./internal/utils/ -run "TestGetProjectLogPath|TestGetLogCacheDirPath" -v
```
Expected: FAIL — undefined functions

- [ ] **Step 3: Add log path helpers to `model_cache.go`**

Add to `cli/internal/utils/model_cache.go`:

```go
const logsCacheDir = "logs"

// GetLogCacheDirPath returns ~/.opentaint/logs/ without creating it.
func GetLogCacheDirPath() (string, error) {
	opentaintHome, err := GetOpenTaintHomePath()
	if err != nil {
		return "", err
	}
	return filepath.Join(opentaintHome, logsCacheDir), nil
}

// GetProjectLogPath returns ~/.opentaint/logs/<slug-hash>/ for a project path,
// without creating the directory. The project path is canonicalized before hashing.
func GetProjectLogPath(projectPath string) (string, error) {
	absPath, err := filepath.Abs(projectPath)
	if err != nil {
		return "", fmt.Errorf("failed to resolve absolute path: %w", err)
	}
	absPath, err = filepath.EvalSymlinks(absPath)
	if err != nil {
		return "", fmt.Errorf("failed to resolve symlinks: %w", err)
	}

	logsDir, err := GetLogCacheDirPath()
	if err != nil {
		return "", err
	}

	return filepath.Join(logsDir, ProjectPathSlugHash(absPath)), nil
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd cli && go test ./internal/utils/ -run "TestGetProjectLogPath|TestGetLogCacheDirPath" -v
```
Expected: PASS

- [ ] **Step 5: Update `project_log.go` to accept any directory**

Modify `cli/internal/utils/log/project_log.go` — the function already takes a `cacheDir` parameter, so the caller just needs to pass the new logs dir. No change needed to `project_log.go` itself.

- [ ] **Step 6: Update `logging.go` to use the new log directory**

Modify `cli/cmd/logging.go`. Change `activateLogging` to accept a `logDir` parameter (the `logs/<slug>` path) instead of `projectCachePath`:

```go
func activateLogging(logFilePath string, logDir string) {
	var logPath string
	var err error

	if logFilePath != "" {
		logPath = log.AbsPathOrExit(logFilePath, "log file")
		if _, err = log.OpenLogFileAt(logPath); err != nil {
			out.Fatalf("Failed to open log file: %s", err)
		}
	} else if logDir != "" {
		logPath, err = log.OpenProjectLog(logDir)
		if err != nil {
			out.Fatalf("Failed to open project log file: %s", err)
		}
	}

	if logPath != "" {
		globals.LogPath = logPath
		out.SetLogWriter(log.LogWriter())
	}
}

func activateLoggingForProject(logFilePath string, projectPath string) {
	logPath, err := utils.GetProjectLogPath(projectPath)
	if err != nil {
		output.LogInfof("Failed to resolve project log path: %v", err)
	}
	activateLogging(logFilePath, logPath)
}
```

- [ ] **Step 7: Update `scan.go` to pass log path**

In `cli/cmd/scan.go`, in function `scan()` around line 164-169, change the logging block:

Replace:
```go
if !DryRunScan {
	if cfg.projectCachePath != "" {
		activateLogging(ScanLogFile, cfg.projectCachePath)
	} else {
		activateLoggingForProject(ScanLogFile, absUserProjectRoot)
	}
}
```

With:
```go
if !DryRunScan {
	activateLoggingForProject(ScanLogFile, absUserProjectRoot)
}
```

The `activateLoggingForProject` function now resolves the log path via `GetProjectLogPath` which uses `logs/<slug-hash>/`, so the `projectCachePath` branch is no longer needed.

- [ ] **Step 8: Verify everything compiles and existing tests pass**

```bash
cd cli && go build ./... && go test ./...
```
Expected: all pass

- [ ] **Step 9: Commit**

```bash
git add cli/internal/utils/model_cache.go cli/internal/utils/model_cache_test.go cli/cmd/logging.go cli/cmd/scan.go cli/internal/utils/log/project_log.go
git commit -m "refactor: move project logs to ~/.opentaint/logs/<slug-hash>/"
```

---

### Task 4: Introduce `PruneCategories` and refactor `ScanForStaleArtifacts`

**Files:**
- Modify: `cli/internal/utils/prune.go`
- Modify: `cli/internal/utils/prune_test.go`

- [ ] **Step 1: Define `PruneCategories` type and constants**

Add to the top of `cli/internal/utils/prune.go`, replacing the existing `ScanForStaleArtifacts` signature. First, add the categories type:

```go
// PruneCategory represents a class of artifacts that can be selectively pruned.
type PruneCategory int

const (
	PruneCategoryArtifacts PruneCategory = 1 << iota
	PruneCategoryRules
	PruneCategoryJDK
	PruneCategoryModels
	PruneCategoryLogs
	PruneCategoryInstall
)

// PruneCategoriesDefault is the set pruned with no flags: artifacts + rules + jdk + models.
const PruneCategoriesDefault = PruneCategoryArtifacts | PruneCategoryRules | PruneCategoryJDK | PruneCategoryModels

// PruneCategoriesAll is the set pruned with --all.
const PruneCategoriesAll = PruneCategoryArtifacts | PruneCategoryRules | PruneCategoryJDK | PruneCategoryModels | PruneCategoryLogs | PruneCategoryInstall
```

- [ ] **Step 2: Write failing tests for category-based scanning**

Add new tests to `cli/internal/utils/prune_test.go`:

```go
func TestScanForStaleArtifacts_Categories(t *testing.T) {
	setupPruneTestGlobals(t)

	t.Run("artifacts-only prunes jars not rules", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		opentaintHome := filepath.Join(home, ".opentaint")
		createTestFile(t, filepath.Join(opentaintHome, "analyzer_0.9.0.jar"), 100)
		createTestFile(t, filepath.Join(opentaintHome, "rules_v0.9.0", "rule.yaml"), 50)

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
		createTestFile(t, filepath.Join(opentaintHome, "rules_v0.9.0", "rule.yaml"), 50)

		result, err := ScanForStaleArtifacts(PruneCategoryRules)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		assertNoKind(t, result, StaleKindAnalyzer)
		assertHasKind(t, result, StaleKindRules)
	})

	t.Run("logs-only scans logs dir", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		logsDir := filepath.Join(home, ".opentaint", "logs", "my-project-a1b2c3d4")
		createTestFile(t, filepath.Join(logsDir, "2026-01-01.log"), 200)

		result, err := ScanForStaleArtifacts(PruneCategoryLogs)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		assertHasKind(t, result, StaleKindLog)
	})

	t.Run("default categories match old false behavior", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("HOME", home)
		opentaintHome := filepath.Join(home, ".opentaint")
		createTestFile(t, filepath.Join(opentaintHome, "analyzer_0.9.0.jar"), 100)
		createTestFile(t, filepath.Join(opentaintHome, "rules_v0.9.0", "rule.yaml"), 50)
		jdkDir := filepath.Join(opentaintHome, "jdk", "temurin-17-jdk+35")
		createTestFile(t, filepath.Join(jdkDir, "bin", "java"), 50)
		pmPath := filepath.Join(opentaintHome, "cache", "proj-abc12345", "project-model", "project.yaml")
		createTestFile(t, pmPath, 50)
		logsDir := filepath.Join(opentaintHome, "logs", "proj-abc12345")
		createTestFile(t, filepath.Join(logsDir, "app.log"), 100)

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
		createTestFile(t, filepath.Join(opentaintHome, "analyzer_0.9.0.jar"), 100)
		installLib := filepath.Join(opentaintHome, "install", "lib")
		createTestFile(t, filepath.Join(installLib, "artifact.jar"), 100)
		logsDir := filepath.Join(opentaintHome, "logs", "proj-abc12345")
		createTestFile(t, filepath.Join(logsDir, "app.log"), 100)

		result, err := ScanForStaleArtifacts(PruneCategoriesAll)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		assertHasKind(t, result, StaleKindAnalyzer)
		assertHasKind(t, result, StaleKindLog)
		assertHasKind(t, result, StaleKindInstallLib)
	})
}
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
cd cli && go test ./internal/utils/ -run "TestScanForStaleArtifacts_Categories" -v
```
Expected: FAIL — `ScanForStaleArtifacts` signature mismatch (expects `bool`, now called with `PruneCategory`)

- [ ] **Step 4: Refactor `ScanForStaleArtifacts` to accept `PruneCategory`**

Rewrite `cli/internal/utils/prune.go`. The `ScanForStaleArtifacts` function changes its parameter from `all bool` to `categories PruneCategory`. Key changes:

```go
// has reports whether the given category is included in the set.
func (c PruneCategory) has(cat PruneCategory) bool {
	return c&cat != 0
}

func ScanForStaleArtifacts(categories PruneCategory) (*PruneResult, error) {
	opentaintHome, err := GetOpenTaintHomePath()
	if err != nil {
		return nil, fmt.Errorf("failed to get opentaint home: %w", err)
	}

	result := &PruneResult{}

	entries, err := os.ReadDir(opentaintHome)
	if os.IsNotExist(err) {
		return result, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to read opentaint home: %w", err)
	}

	if categories.has(PruneCategoryArtifacts) || categories.has(PruneCategoryRules) {
		artifacts := globals.Artifacts()
		for _, entry := range entries {
			name := entry.Name()
			fullPath := filepath.Join(opentaintHome, name)

			if name == "cache" || name == "logs" || name == "install" || strings.HasPrefix(name, ".") {
				continue
			}

			for _, def := range artifacts {
				isArtifact := def.Kind() == "rules"
				wantCategory := PruneCategoryRules
				if !isArtifact {
					wantCategory = PruneCategoryArtifacts
				}
				if !categories.has(wantCategory) {
					if strings.HasPrefix(name, def.CachePrefix) {
						break
					}
					continue
				}
				if artifact := checkStale(def, name, fullPath); artifact != nil {
					result.Add(*artifact)
					break
				}
				if strings.HasPrefix(name, def.CachePrefix) {
					break
				}
			}
		}
	}

	// JDK/JRE
	if categories.has(PruneCategoryJDK) {
		for _, kind := range []string{StaleKindJDK, StaleKindJRE} {
			javaDir := filepath.Join(opentaintHome, kind)
			subEntries, err := os.ReadDir(javaDir)
			if err != nil {
				continue
			}
			currentPrefix := fmt.Sprintf("temurin-%d-", globals.DefaultJavaVersion)
			for _, subEntry := range subEntries {
				if strings.HasPrefix(subEntry.Name(), currentPrefix) {
					continue
				}
				subPath := filepath.Join(javaDir, subEntry.Name())
				size, _ := dirSize(subPath)
				result.Add(StaleArtifact{Path: subPath, Size: size, Kind: kind})
			}
		}
	}

	// Install-tier
	if categories.has(PruneCategoryInstall) {
		for _, check := range []struct {
			path string
			kind string
		}{
			{GetInstallLibPath(), StaleKindInstallLib},
			{GetInstallJREPath(), StaleKindInstallJRE},
		} {
			if check.path == "" {
				continue
			}
			if _, err := os.Stat(check.path); err != nil {
				continue
			}
			size, _ := dirSize(check.path)
			result.Add(StaleArtifact{Path: check.path, Size: size, Kind: check.kind})
		}
	}

	// Models (cache dir)
	if categories.has(PruneCategoryModels) {
		modelsDir, mErr := GetModelCacheDirPath()
		if mErr != nil {
			output.LogDebugf("Failed to resolve model cache path: %v", mErr)
		}
		if info, err := os.Stat(modelsDir); err == nil && info.IsDir() {
			modelEntries, err := os.ReadDir(modelsDir)
			if err == nil {
				for _, modelEntry := range modelEntries {
					if !modelEntry.IsDir() {
						continue
					}
					projectCachePath := filepath.Join(modelsDir, modelEntry.Name())
					scanProjectCacheSubdirs(projectCachePath, result)
				}
			}
		}
	}

	// Logs (separate logs dir)
	if categories.has(PruneCategoryLogs) {
		logsDir, lErr := GetLogCacheDirPath()
		if lErr != nil {
			output.LogDebugf("Failed to resolve log cache path: %v", lErr)
		}
		if info, err := os.Stat(logsDir); err == nil && info.IsDir() {
			logEntries, err := os.ReadDir(logsDir)
			if err == nil {
				for _, logEntry := range logEntries {
					if !logEntry.IsDir() {
						continue
					}
					logProjectDir := filepath.Join(logsDir, logEntry.Name())
					size, _ := dirSize(logProjectDir)
					if size > 0 {
						result.Add(StaleArtifact{Path: logProjectDir, Size: size, Kind: StaleKindLog})
					}
				}
			}
		}
	}

	return result, nil
}
```

Note: The `scanProjectCacheSubdirs` function remains for non-`--all` model scanning. With the categories approach, `--all` maps to `PruneCategoriesAll` which includes `PruneCategoryModels` — but models now always prune just `project-model/` and `.staging-*` (the old `--all` behavior of pruning entire cache dirs is replaced by `--models` + `--logs` together covering the same ground, since logs are now in a separate dir).

- [ ] **Step 5: Update existing tests to use new signature**

In `cli/internal/utils/prune_test.go`, update all calls to `ScanForStaleArtifacts`:

- `ScanForStaleArtifacts(false)` → `ScanForStaleArtifacts(PruneCategoriesDefault)`
- `ScanForStaleArtifacts(true)` → `ScanForStaleArtifacts(PruneCategoriesAll)`

Tests related to "logs in cache dirs" can be updated or removed since logs now live in `logs/` not `cache/`. The `TestScanForStaleArtifacts_LogsInCacheDirs` tests should be replaced with tests for the new `logs/` directory scanning.

Update `TestScanForStaleArtifacts_CachedModels`:
- Remove the "cached model with all prunes entire dir" test — models now always target `project-model/` specifically
- Update "no size double-counting" test — logs are in a separate dir now, so the test creates files in `logs/` instead of `cache/<slug>/logs/`
- The "logs preserved without all flag" test becomes: logs are in `logs/` dir, `PruneCategoriesDefault` doesn't include `PruneCategoryLogs`

- [ ] **Step 6: Run all tests**

```bash
cd cli && go test ./internal/utils/ -v
```
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add cli/internal/utils/prune.go cli/internal/utils/prune_test.go
git commit -m "refactor: replace bool flag with PruneCategory bitmask in ScanForStaleArtifacts"
```

---

### Task 5: Add granular flags to the prune command

**Files:**
- Modify: `cli/cmd/prune.go`

- [ ] **Step 1: Update `prune.go` with new flags and category resolution**

Rewrite `cli/cmd/prune.go`:

```go
package cmd

import (
	"fmt"

	"github.com/seqra/opentaint/internal/output"
	"github.com/seqra/opentaint/internal/utils"
	"github.com/spf13/cobra"
)

var (
	pruneDryRun    bool
	pruneYes       bool
	pruneAll       bool
	pruneArtifacts bool
	pruneRules     bool
	pruneJDK       bool
	pruneModels    bool
	pruneLogs      bool
	pruneInstall   bool
)

// resolveCategories maps CLI flags to a PruneCategory bitmask.
// Returns an error if --all is combined with specific flags.
func resolveCategories() (utils.PruneCategory, error) {
	specific := pruneArtifacts || pruneRules || pruneJDK || pruneModels || pruneLogs || pruneInstall
	if pruneAll && specific {
		return 0, fmt.Errorf("--all cannot be combined with specific category flags (--artifacts, --rules, --jdk, --models, --logs, --install)")
	}
	if pruneAll {
		return utils.PruneCategoriesAll, nil
	}
	if !specific {
		return utils.PruneCategoriesDefault, nil
	}

	var cats utils.PruneCategory
	if pruneArtifacts {
		cats |= utils.PruneCategoryArtifacts
	}
	if pruneRules {
		cats |= utils.PruneCategoryRules
	}
	if pruneJDK {
		cats |= utils.PruneCategoryJDK
	}
	if pruneModels {
		cats |= utils.PruneCategoryModels
	}
	if pruneLogs {
		cats |= utils.PruneCategoryLogs
	}
	if pruneInstall {
		cats |= utils.PruneCategoryInstall
	}
	return cats, nil
}

var pruneCmd = &cobra.Command{
	Use:   "prune",
	Short: "Remove stale downloaded artifacts from ~/.opentaint",
	Long: `Remove stale downloaded artifacts from the local cache (~/.opentaint).

Identifies artifacts that are no longer needed:
- Old versions of analyzer JARs, autobuilder JARs, and rules
- Downloaded JDK/JRE versions that don't match the current version
- Cached project models and staging directories

Use category flags to prune selectively:
  --artifacts   Stale analyzer and autobuilder JARs
  --rules       Stale rules directories
  --jdk         Old JDK/JRE versions
  --models      Cached project models and staging directories
  --logs        Project log files
  --install     Install-tier lib and JRE artifacts

Without category flags, prunes: artifacts + rules + jdk + models.
With --all: prunes everything including logs and install-tier.`,
	Run: func(cmd *cobra.Command, args []string) {
		categories, err := resolveCategories()
		if err != nil {
			out.FatalErr(err)
		}

		result, err := utils.ScanForStaleArtifacts(categories)
		if err != nil {
			out.Fatalf("Failed to scan for stale artifacts: %s", err)
		}

		if result.TotalCount == 0 {
			out.Print("No stale artifacts found. Nothing to prune.")
			return
		}

		sb := out.Section("Stale Artifacts")
		for _, artifact := range result.Stale {
			sb.Text(fmt.Sprintf("%s (%s) - %s", artifact.Path, artifact.Kind, output.FormatSize(artifact.Size)))
		}
		sb.Line().
			Text(fmt.Sprintf("Total: %d items, %s", result.TotalCount, output.FormatSize(result.TotalSize))).
			Render()

		if pruneDryRun {
			out.Print("Dry run mode. No files were deleted.")
			return
		}

		if !pruneYes {
			if !out.Confirm("Delete these artifacts?", false) {
				out.Print("Prune cancelled.")
				return
			}
		}

		if err := utils.DeleteArtifacts(result.Stale); err != nil {
			out.Fatalf("Failed to delete artifacts: %s", err)
		}

		out.Successf("Pruned %d items, freed %s", result.TotalCount, output.FormatSize(result.TotalSize))
	},
}

func init() {
	rootCmd.AddCommand(pruneCmd)

	pruneCmd.Flags().BoolVar(&pruneDryRun, "dry-run", false, "Show what would be deleted without deleting")
	pruneCmd.Flags().BoolVar(&pruneYes, "yes", false, "Skip interactive confirmation")
	pruneCmd.Flags().BoolVar(&pruneAll, "all", false, "Prune everything including logs and install-tier artifacts")
	pruneCmd.Flags().BoolVar(&pruneArtifacts, "artifacts", false, "Prune stale analyzer and autobuilder JARs")
	pruneCmd.Flags().BoolVar(&pruneRules, "rules", false, "Prune stale rules directories")
	pruneCmd.Flags().BoolVar(&pruneJDK, "jdk", false, "Prune old JDK/JRE versions")
	pruneCmd.Flags().BoolVar(&pruneModels, "models", false, "Prune cached project models and staging directories")
	pruneCmd.Flags().BoolVar(&pruneLogs, "logs", false, "Prune project log files")
	pruneCmd.Flags().BoolVar(&pruneInstall, "install", false, "Prune install-tier lib and JRE artifacts")
}
```

- [ ] **Step 2: Verify it compiles**

```bash
cd cli && go build ./...
```
Expected: no errors

- [ ] **Step 3: Commit**

```bash
git add cli/cmd/prune.go
git commit -m "feat: add granular prune flags (--artifacts, --rules, --jdk, --models, --logs, --install)"
```

---

### Task 6: Add locking to prune command

**Files:**
- Modify: `cli/cmd/prune.go`
- Modify: `cli/internal/utils/prune.go` (add `SkippedProject` to `PruneResult`)

- [ ] **Step 1: Write failing test for skip reporting**

Add to `cli/internal/utils/prune_test.go`:

```go
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
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd cli && go test ./internal/utils/ -run "TestPruneResult_AddSkipped" -v
```
Expected: FAIL — `SkippedProject`, `AddSkipped`, `Skipped` undefined

- [ ] **Step 3: Add `SkippedProject` to prune types**

In `cli/internal/utils/prune.go`, add:

```go
// SkippedProject represents a project cache that was skipped because a compile lock was held.
type SkippedProject struct {
	Path string
	Meta LockMeta
}

// PruneResult contains the results of scanning for stale artifacts.
type PruneResult struct {
	Stale      []StaleArtifact
	Skipped    []SkippedProject
	TotalSize  int64
	TotalCount int
}

// AddSkipped records a project that was skipped due to an active compile lock.
func (r *PruneResult) AddSkipped(s SkippedProject) {
	r.Skipped = append(r.Skipped, s)
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd cli && go test ./internal/utils/ -run "TestPruneResult_AddSkipped" -v
```
Expected: PASS

- [ ] **Step 5: Add lock-aware model scanning**

In the models section of `ScanForStaleArtifacts`, before scanning each project cache dir, try the compile lock. If locked, skip and record. Update the models block:

```go
// Models (cache dir) — lock-aware
if categories.has(PruneCategoryModels) {
	modelsDir, mErr := GetModelCacheDirPath()
	if mErr != nil {
		output.LogDebugf("Failed to resolve model cache path: %v", mErr)
	}
	if info, err := os.Stat(modelsDir); err == nil && info.IsDir() {
		modelEntries, err := os.ReadDir(modelsDir)
		if err == nil {
			for _, modelEntry := range modelEntries {
				if !modelEntry.IsDir() {
					continue
				}
				projectCachePath := filepath.Join(modelsDir, modelEntry.Name())
				lockPath := CompileLockPath(projectCachePath)
				lock, lockErr := TryLock(lockPath, LockMeta{PID: os.Getpid(), Command: "prune"})
				if lockErr == ErrLocked {
					meta, _ := ReadLockMeta(lockPath)
					result.AddSkipped(SkippedProject{Path: projectCachePath, Meta: meta})
					continue
				}
				if lock != nil {
					lock.Unlock()
				}
				scanProjectCacheSubdirs(projectCachePath, result)
			}
		}
	}
}
```

- [ ] **Step 6: Add global prune lock and skip display to `prune.go` command**

In `cli/cmd/prune.go`, add locking around the Run function body:

```go
Run: func(cmd *cobra.Command, args []string) {
	categories, err := resolveCategories()
	if err != nil {
		out.FatalErr(err)
	}

	// Acquire global prune lock
	pruneLockPath, err := utils.PruneLockPath()
	if err != nil {
		out.Fatalf("Failed to resolve prune lock path: %s", err)
	}
	pruneLock, err := utils.TryLock(pruneLockPath, utils.LockMeta{
		PID:     os.Getpid(),
		Command: "prune",
	})
	if err == utils.ErrLocked {
		out.Fatal("Another prune is already running")
	}
	if err != nil {
		out.Fatalf("Failed to acquire prune lock: %s", err)
	}
	defer pruneLock.Unlock()

	result, err := utils.ScanForStaleArtifacts(categories)
	if err != nil {
		out.Fatalf("Failed to scan for stale artifacts: %s", err)
	}

	// Display skipped projects
	if len(result.Skipped) > 0 {
		sb := out.Section("Skipped (compilation in progress)")
		for _, s := range result.Skipped {
			if s.Meta.PID != 0 {
				sb.Text(fmt.Sprintf("%s (locked by PID %d)", s.Path, s.Meta.PID))
			} else {
				sb.Text(fmt.Sprintf("%s (locked)", s.Path))
			}
		}
		sb.Render()
	}

	if result.TotalCount == 0 {
		out.Print("No stale artifacts found. Nothing to prune.")
		return
	}

	sb := out.Section("Stale Artifacts")
	for _, artifact := range result.Stale {
		sb.Text(fmt.Sprintf("%s (%s) - %s", artifact.Path, artifact.Kind, output.FormatSize(artifact.Size)))
	}
	sb.Line().
		Text(fmt.Sprintf("Total: %d items, %s", result.TotalCount, output.FormatSize(result.TotalSize))).
		Render()

	if pruneDryRun {
		out.Print("Dry run mode. No files were deleted.")
		return
	}

	if !pruneYes {
		if !out.Confirm("Delete these artifacts?", false) {
			out.Print("Prune cancelled.")
			return
		}
	}

	if err := utils.DeleteArtifacts(result.Stale); err != nil {
		out.Fatalf("Failed to delete artifacts: %s", err)
	}

	out.Successf("Pruned %d items, freed %s", result.TotalCount, output.FormatSize(result.TotalSize))
},
```

Don't forget to add `"os"` to imports in `prune.go`.

- [ ] **Step 7: Verify it compiles and tests pass**

```bash
cd cli && go build ./... && go test ./internal/utils/ -v
```
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add cli/internal/utils/prune.go cli/internal/utils/prune_test.go cli/cmd/prune.go
git commit -m "feat: add lock-aware prune with skip reporting for active compilations"
```

---

### Task 7: Replace `HasStagingDir` with compile lock in scan/compile

**Files:**
- Modify: `cli/cmd/scan.go`
- Modify: `cli/internal/utils/model_cache.go` (remove `HasStagingDir`)

- [ ] **Step 1: Replace `HasStagingDir` in `scan.go`**

In `cli/cmd/scan.go`, in `resolveScanConfig()` around lines 425-429, replace:

```go
if utils.HasStagingDir(projectCachePath) {
	out.Error("Compilation already in progress for this project")
	suggest("To scan an existing model instead", utils.NewScanCommand("").WithProjectModel("<model-path>").Build())
	os.Exit(1)
}
```

With:

```go
compileLock, lockErr := utils.TryLock(
	utils.CompileLockPath(projectCachePath),
	utils.LockMeta{PID: os.Getpid(), Command: "compile", Project: absUserProjectRoot},
)
if lockErr == utils.ErrLocked {
	out.Error("Compilation already in progress for this project")
	suggest("To scan an existing model instead", utils.NewScanCommand("").WithProjectModel("<model-path>").Build())
	os.Exit(1)
}
if lockErr != nil {
	out.Fatalf("Failed to acquire compile lock: %s", lockErr)
}
```

The `compileLock` must be stored in the `scanConfig` and released after compilation + promotion. Add a field to `scanConfig`:

```go
type scanConfig struct {
	mode             ScanMode
	absProjectModel  string
	projectCachePath string
	stagingDir       string
	needsCompilation bool
	compileLock      *utils.FileLock // non-nil when we hold the compile lock
}
```

Release it at the end of the `scan()` function (after all operations complete):

```go
defer func() {
	if cfg.compileLock != nil {
		cfg.compileLock.Unlock()
	}
}()
```

And set it in `resolveScanConfig`:

```go
return scanConfig{
	mode:             CompileAndScan,
	absProjectModel:  filepath.Join(stagingDir, "project-model"),
	projectCachePath: projectCachePath,
	stagingDir:       stagingDir,
	needsCompilation: true,
	compileLock:      compileLock,
}
```

Note: `resolveScanConfig` needs `absUserProjectRoot` as a parameter now (for the lock meta). Update its signature and the call site.

- [ ] **Step 2: Remove `HasStagingDir` and `CleanupStagingDir` if unused**

Remove from `cli/internal/utils/model_cache.go`:
- `HasStagingDir` function (lines 116-127)
- Keep `CleanupStagingDir` — it's still used by `scan.go` for cleanup on compile failure

- [ ] **Step 3: Verify everything compiles**

```bash
cd cli && go build ./...
```
Expected: no errors

- [ ] **Step 4: Run all tests**

```bash
cd cli && go test ./...
```
Expected: PASS. If any tests reference `HasStagingDir`, remove those tests.

- [ ] **Step 5: Commit**

```bash
git add cli/cmd/scan.go cli/internal/utils/model_cache.go
git commit -m "refactor: replace HasStagingDir heuristic with flock-based compile lock"
```

---

### Task 8: Add compile lock to standalone compile command

**Files:**
- Modify: `cli/cmd/compile.go`

- [ ] **Step 1: Add compile lock acquisition**

The `compile` command in `compile.go` doesn't currently use the cache at all — it writes to `--output`. However, if we want compile locking to also protect the compile command when it writes to cache (via `activateLoggingForProject` which creates the project cache path), we should add locking here too.

Actually, looking at `compile.go`, the standalone compile command writes to `--output` (user-specified path), not to the cache. The cache interaction only happens via `scan.go`. The compile command only uses the cache for logging.

Since compile doesn't write to the model cache, no compile lock is needed here. The lock is only for cache-based compilation (scan command). Skip this task.

- [ ] **Step 2: Verify compile still works**

```bash
cd cli && go build ./...
```
Expected: no errors

- [ ] **Step 3: Commit (if any changes)**

No changes expected for this task. Move on.

---

### Task 9: Final integration verification

**Files:** None (verification only)

- [ ] **Step 1: Run full test suite**

```bash
cd cli && go test ./... -v
```
Expected: all PASS

- [ ] **Step 2: Build the binary**

```bash
cd cli && go build -o opentaint .
```
Expected: binary builds successfully

- [ ] **Step 3: Manual smoke test — prune with no flags**

```bash
./opentaint prune --dry-run
```
Expected: shows stale artifacts (or "nothing to prune"), no errors

- [ ] **Step 4: Manual smoke test — prune with specific flags**

```bash
./opentaint prune --logs --dry-run
./opentaint prune --models --dry-run
./opentaint prune --artifacts --rules --dry-run
```
Expected: each shows only the relevant category

- [ ] **Step 5: Manual smoke test — mutual exclusivity**

```bash
./opentaint prune --all --logs
```
Expected: error message about mutual exclusivity

- [ ] **Step 6: Manual smoke test — prune lock**

Run two prune commands simultaneously (in separate terminals):
```bash
# Terminal 1:
./opentaint prune --dry-run  # should succeed

# Terminal 2 (while 1 is running):
./opentaint prune --dry-run  # should fail with "Another prune is already running"
```

- [ ] **Step 7: Commit any final fixes**

If smoke tests reveal issues, fix and commit.
