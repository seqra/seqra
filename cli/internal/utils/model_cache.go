package utils

import (
	"crypto/sha256"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"time"
)

var nonAlphanumeric = regexp.MustCompile(`[^a-z0-9]+`)

// GetModelCacheDir returns ~/.opentaint/models/, creating it if needed.
func GetModelCacheDir() (string, error) {
	opentaintHome, err := GetOpentaintHome()
	if err != nil {
		return "", err
	}
	modelsDir := filepath.Join(opentaintHome, "models")
	if err := os.MkdirAll(modelsDir, 0o755); err != nil {
		return "", fmt.Errorf("failed to create models directory: %w", err)
	}
	return modelsDir, nil
}

// GetProjectCachePath returns ~/.opentaint/models/<slug-hash>/ for a project path,
// creating the directory if needed. The project path is canonicalized (resolved
// through symlinks and made absolute) before hashing.
func GetProjectCachePath(projectPath string) (string, error) {
	absPath, err := filepath.Abs(projectPath)
	if err != nil {
		return "", fmt.Errorf("failed to resolve absolute path: %w", err)
	}
	absPath, err = filepath.EvalSymlinks(absPath)
	if err != nil {
		return "", fmt.Errorf("failed to resolve symlinks: %w", err)
	}

	modelsDir, err := GetModelCacheDir()
	if err != nil {
		return "", err
	}

	slugHash := ProjectPathSlugHash(absPath)
	cachePath := filepath.Join(modelsDir, slugHash)
	if err := os.MkdirAll(cachePath, 0o755); err != nil {
		return "", fmt.Errorf("failed to create project cache directory: %w", err)
	}
	return cachePath, nil
}

const projectModelDir = "project-model"

// DefaultSarifReportPath returns the default SARIF report location for a given
// project model path: <projectModelPath>/sources/opentaint.sarif
func DefaultSarifReportPath(projectModelPath string) string {
	return filepath.Join(projectModelPath, "sources", "opentaint.sarif")
}

// StableProjectModelPath returns the stable symlink path for the project model
// within a cache directory: <cacheDir>/project-model
func StableProjectModelPath(cacheDir string) string {
	return filepath.Join(cacheDir, projectModelDir)
}

// CreateStagingDir creates a staging directory inside cacheDir for isolated compilation.
// Returns the path to the staging directory (e.g. <cacheDir>/.staging-<pid>-<timestamp>/).
// The staging directory contains a project-model/ subdirectory ready for compilation output.
func CreateStagingDir(cacheDir string) (string, error) {
	name := fmt.Sprintf(".staging-%d-%d", os.Getpid(), time.Now().UnixNano())
	stagingPath := filepath.Join(cacheDir, name)
	if err := os.MkdirAll(stagingPath, 0o755); err != nil {
		return "", fmt.Errorf("failed to create staging directory: %w", err)
	}
	return stagingPath, nil
}

// PromoteStagingToCache atomically promotes a staging directory to the cache
// via symlink swap. Steps:
//  1. Rename staging's project-model/ to a timestamped name in cacheDir
//  2. Create a temp symlink pointing to the timestamped dir
//  3. Atomically rename the temp symlink to "project-model"
//  4. Remove the old timestamped dir (if any)
//  5. Remove the now-empty staging dir
func PromoteStagingToCache(cacheDir, stagingPath string) error {
	timestamp := fmt.Sprintf("%d", time.Now().UnixNano())
	targetName := fmt.Sprintf("%s-%s", projectModelDir, timestamp)
	targetPath := filepath.Join(cacheDir, targetName)

	// 1. Rename staging project-model/ to timestamped dir in cache
	srcPM := filepath.Join(stagingPath, projectModelDir)
	if err := os.Rename(srcPM, targetPath); err != nil {
		return fmt.Errorf("failed to move staging model to cache: %w", err)
	}

	// Read old symlink target before replacing
	symlinkPath := filepath.Join(cacheDir, projectModelDir)
	oldTarget, _ := os.Readlink(symlinkPath)

	// 2. Create temp symlink
	tmpSymlink := filepath.Join(cacheDir, fmt.Sprintf(".project-model-tmp-%d", os.Getpid()))
	_ = os.Remove(tmpSymlink) // clean up any leftover
	if err := os.Symlink(targetName, tmpSymlink); err != nil {
		return fmt.Errorf("failed to create temp symlink: %w", err)
	}

	// 3. Atomic rename of temp symlink over the real one
	if err := os.Rename(tmpSymlink, symlinkPath); err != nil {
		_ = os.Remove(tmpSymlink)
		return fmt.Errorf("failed to swap symlink: %w", err)
	}

	// 4. Remove old timestamped dir if it differs
	if oldTarget != "" && oldTarget != targetName {
		_ = os.RemoveAll(filepath.Join(cacheDir, oldTarget))
	}

	// 5. Remove the now-empty staging dir
	_ = os.Remove(stagingPath)

	return nil
}

// CleanupStagingDir removes a staging directory and all its contents.
// Used when compilation fails and the staging output is not needed.
func CleanupStagingDir(stagingPath string) {
	_ = os.RemoveAll(stagingPath)
}

// ProjectPathSlugHash returns a deterministic directory name for a project path.
// Format: last-path-segment-8hexchars (e.g. "my-project-a1b2c3d4").
// Uses only the last segment of the path for readability; the hash ensures uniqueness.
func ProjectPathSlugHash(absPath string) string {
	slug := strings.ToLower(filepath.Base(absPath))
	slug = nonAlphanumeric.ReplaceAllString(slug, "-")
	slug = strings.Trim(slug, "-")

	hash := sha256.Sum256([]byte(absPath))
	hexHash := fmt.Sprintf("%x", hash[:4]) // 8 hex chars

	return fmt.Sprintf("%s-%s", slug, hexHash)
}
