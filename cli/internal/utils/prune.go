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

// Add appends a stale artifact and updates the totals.
func (r *PruneResult) Add(a StaleArtifact) {
	r.Stale = append(r.Stale, a)
	r.TotalSize += a.Size
	r.TotalCount++
}

// AddSkipped records a project that was skipped due to an active compile lock.
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

// scanProjectCacheSubdirs adds only project-model/ and .staging-* subdirs
// from a project cache directory, preserving logs and other data.
func scanProjectCacheSubdirs(projectCachePath string, result *PruneResult) {
	entries, err := os.ReadDir(projectCachePath)
	if err != nil {
		return
	}
	for _, entry := range entries {
		if !entry.IsDir() {
			continue
		}
		name := entry.Name()
		if name == projectModelDir || strings.HasPrefix(name, ".staging-") {
			subPath := filepath.Join(projectCachePath, name)
			size, _ := dirSize(subPath)
			if size > 0 {
				result.Add(StaleArtifact{Path: subPath, Size: size, Kind: StaleKindModel})
			}
		}
	}
}

// DeleteArtifacts removes the given stale artifacts.
func DeleteArtifacts(artifacts []StaleArtifact) error {
	for _, artifact := range artifacts {
		if err := os.RemoveAll(artifact.Path); err != nil {
			return fmt.Errorf("failed to remove %s: %w", artifact.Path, err)
		}
	}
	return nil
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
