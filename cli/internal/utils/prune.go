package utils

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/seqra/opentaint/internal/globals"
	"github.com/seqra/opentaint/internal/output"
)

// Stale artifact kind constants.
const (
	StaleKindAnalyzer    = "analyzer"
	StaleKindAutobuilder = "autobuilder"
	StaleKindRules       = "rules"
	StaleKindJDK         = "jdk"
	StaleKindJRE         = "jre"
	StaleKindInstallLib  = "install-lib"
	StaleKindInstallJRE  = "install-jre"
	StaleKindLog         = "log"
	StaleKindModel       = "model"
)

// StaleArtifact represents an artifact that can be pruned.
type StaleArtifact struct {
	Path string
	Size int64
	Kind string
}

// SkippedProject represents a project cache that was skipped because its cache lock was held.
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

// Add appends a stale artifact and updates the totals.
func (r *PruneResult) Add(a StaleArtifact) {
	r.Stale = append(r.Stale, a)
	r.TotalSize += a.Size
	r.TotalCount++
}

// AddSkipped records a project that was skipped due to an active cache lock holder.
func (r *PruneResult) AddSkipped(s SkippedProject) {
	r.Skipped = append(r.Skipped, s)
}

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

// has reports whether the given category is included in the set.
func (c PruneCategory) has(cat PruneCategory) bool {
	return c&cat != 0
}

// checkStale tests whether a filename matches the given artifact definition and
// has a version that differs from the bind version.
// Returns a StaleArtifact if it should be pruned, nil otherwise.
func checkStale(def globals.ArtifactDef, name, fullPath string) *StaleArtifact {
	if !strings.HasPrefix(name, def.CachePrefix) || !strings.HasSuffix(name, def.CacheSuffix) {
		return nil
	}
	version := strings.TrimPrefix(name, def.CachePrefix)
	version = strings.TrimSuffix(version, def.CacheSuffix)

	if version == def.BindVersion {
		return nil
	}

	var size int64
	if def.Unpack {
		size, _ = dirSize(fullPath)
	} else {
		size = fileSize(fullPath)
	}
	return &StaleArtifact{Path: fullPath, Size: size, Kind: def.Kind()}
}

// ScanForStaleArtifacts scans ~/.opentaint/ for artifacts that are not current and returns them.
func ScanForStaleArtifacts(categories PruneCategory) (*PruneResult, error) {
	opentaintHome, err := GetOpenTaintHomePath()
	if err != nil {
		return nil, fmt.Errorf("failed to get opentaint home: %w", err)
	}

	result := &PruneResult{}

	scanArtifactsOrRules := categories.has(PruneCategoryArtifacts) || categories.has(PruneCategoryRules)
	scanJDK := categories.has(PruneCategoryJDK)

	if scanArtifactsOrRules || scanJDK {
		entries, err := os.ReadDir(opentaintHome)
		if os.IsNotExist(err) {
			return result, nil
		}
		if err != nil {
			return nil, fmt.Errorf("failed to read opentaint home: %w", err)
		}

		artifacts := globals.Artifacts()

		for _, entry := range entries {
			name := entry.Name()
			fullPath := filepath.Join(opentaintHome, name)

			// Skip special dirs and hidden files
			if name == "cache" || name == "logs" || name == "install" || strings.HasPrefix(name, ".") {
				continue
			}

			if scanArtifactsOrRules {
				// Check against artifact definitions
				matched := false
				for _, def := range artifacts {
					isRules := def.Kind() == StaleKindRules
					categoryEnabled := (isRules && categories.has(PruneCategoryRules)) ||
						(!isRules && categories.has(PruneCategoryArtifacts))

					if strings.HasPrefix(name, def.CachePrefix) {
						matched = true
						if categoryEnabled {
							if artifact := checkStale(def, name, fullPath); artifact != nil {
								result.Add(*artifact)
							}
						}
						break
					}
				}
				if matched {
					continue
				}
			}

			if scanJDK {
				// Check JDK/JRE directories
				if name == StaleKindJDK || name == StaleKindJRE {
					subEntries, err := os.ReadDir(fullPath)
					if err != nil {
						continue
					}
					currentPrefix := fmt.Sprintf("temurin-%d-", globals.DefaultJavaVersion)
					for _, subEntry := range subEntries {
						if strings.HasPrefix(subEntry.Name(), currentPrefix) {
							continue
						}
						subPath := filepath.Join(fullPath, subEntry.Name())
						size, _ := dirSize(subPath)
						result.Add(StaleArtifact{Path: subPath, Size: size, Kind: name})
					}
				}
			}
		}
	}

	// Scan install-tier directories for stale artifacts
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

	// Models (cache dir) — lock-aware
	if categories.has(PruneCategoryModels) {
		modelsDir, mErr := GetModelCacheDirPath()
		if mErr != nil {
			output.LogDebugf("Failed to resolve model cache path: %v", mErr)
		} else if info, err := os.Stat(modelsDir); err == nil && info.IsDir() {
			modelEntries, err := os.ReadDir(modelsDir)
			if err == nil {
				for _, modelEntry := range modelEntries {
					if !modelEntry.IsDir() {
						continue
					}
					projectCachePath := filepath.Join(modelsDir, modelEntry.Name())
					lockPath := CacheLockPath(projectCachePath)
					lock, lockErr := TryLockExclusive(lockPath, LockMeta{PID: os.Getpid(), Command: "prune"})
					if lockErr == ErrLocked {
						meta, _ := ReadLockMeta(lockPath)
						result.AddSkipped(SkippedProject{Path: projectCachePath, Meta: meta})
						continue
					}
					if lockErr != nil {
						output.LogDebugf("Failed to probe cache lock for %s, skipping: %v", projectCachePath, lockErr)
						continue
					}
					scanProjectCacheSubdirs(projectCachePath, result)
					lock.Unlock()
				}
			}
		}
	}

	// Scan for project log directories
	if categories.has(PruneCategoryLogs) {
		logsDir, lErr := GetLogCacheDirPath()
		if lErr != nil {
			output.LogDebugf("Failed to resolve log cache path: %v", lErr)
		} else if info, err := os.Stat(logsDir); err == nil && info.IsDir() {
			logEntries, err := os.ReadDir(logsDir)
			if err == nil {
				for _, logEntry := range logEntries {
					if !logEntry.IsDir() {
						continue
					}
					projectLogPath := filepath.Join(logsDir, logEntry.Name())
					size, _ := dirSize(projectLogPath)
					result.Add(StaleArtifact{Path: projectLogPath, Size: size, Kind: StaleKindLog})
				}
			}
		}
	}

	return result, nil
}

// scanProjectCacheSubdirs adds only the project-model/ subdir of a project
// cache directory so that adjacent files (lock, logs) are preserved.
func scanProjectCacheSubdirs(projectCachePath string, result *PruneResult) {
	pmPath := filepath.Join(projectCachePath, projectModelDir)
	info, err := os.Stat(pmPath)
	if err != nil || !info.IsDir() {
		return
	}
	size, _ := dirSize(pmPath)
	if size > 0 {
		result.Add(StaleArtifact{Path: pmPath, Size: size, Kind: StaleKindModel})
	}
}

// DeleteArtifacts removes the given stale artifacts and cleans up empty parent
// directories left behind (e.g. empty project cache dirs under ~/.opentaint/cache/).
func DeleteArtifacts(artifacts []StaleArtifact) error {
	emptyDirCandidates := map[string]struct{}{}

	for _, artifact := range artifacts {
		if artifact.Kind == StaleKindModel {
			modelsParent, err := deleteModelArtifact(artifact.Path)
			if err != nil {
				return err
			}
			if modelsParent != "" {
				emptyDirCandidates[modelsParent] = struct{}{}
			}
			continue
		}
		if err := os.RemoveAll(artifact.Path); err != nil {
			return fmt.Errorf("failed to remove %s: %w", artifact.Path, err)
		}
		if artifact.Kind == StaleKindLog {
			emptyDirCandidates[filepath.Dir(artifact.Path)] = struct{}{}
		}
	}

	// Try to remove empty parent directories bottom-up.
	// os.Remove only succeeds on empty directories, so this is safe.
	for dir := range emptyDirCandidates {
		removeEmptyParents(dir)
	}
	return nil
}

// deleteModelArtifact removes a cached project-model and, when the cache lock
// is still ours to take, the entire project cache directory (including the
// residual .cache.lock left over from the prior scan). Returns the models/
// parent directory as a candidate for empty-parent cleanup, or "" if the
// project directory must remain intact (another holder acquired the lock).
func deleteModelArtifact(modelPath string) (string, error) {
	projectDir := filepath.Dir(modelPath)
	lock, err := TryLockExclusive(CacheLockPath(projectDir), LockMeta{
		PID:     os.Getpid(),
		Command: "prune",
	})
	if err != nil {
		// Another process grabbed the cache between scan and delete.
		// Fall back to removing only the model; the holder keeps its lock.
		if rmErr := os.RemoveAll(modelPath); rmErr != nil {
			return "", fmt.Errorf("failed to remove %s: %w", modelPath, rmErr)
		}
		return "", nil
	}
	// Remove the whole project cache dir while holding exclusive. The lock
	// file is unlinked here; our fd stays valid until Unlock closes it.
	if err := os.RemoveAll(projectDir); err != nil {
		lock.Unlock()
		return "", fmt.Errorf("failed to remove %s: %w", projectDir, err)
	}
	lock.Unlock()
	return filepath.Dir(projectDir), nil
}

// removeEmptyParents attempts to remove dir and its parent if they are empty.
// Stops at the first non-empty or non-removable directory.
func removeEmptyParents(dir string) {
	for i := 0; i < 2; i++ {
		if err := os.Remove(dir); err != nil {
			return
		}
		dir = filepath.Dir(dir)
	}
}

func fileSize(path string) int64 {
	info, err := os.Stat(path)
	if err != nil {
		return 0
	}
	return info.Size()
}

func dirSize(path string) (int64, error) {
	var size int64
	err := filepath.WalkDir(path, func(_ string, d os.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if !d.IsDir() {
			info, err := d.Info()
			if err != nil {
				return err
			}
			size += info.Size()
		}
		return nil
	})
	return size, err
}
