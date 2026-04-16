package utils

import (
	"crypto/sha256"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

var nonAlphanumeric = regexp.MustCompile(`[^a-z0-9]+`)

// GetModelCacheDirPath returns ~/.opentaint/cache/ without creating it.
// Use this when you only need to read/check the directory (e.g. prune scanning).
func GetModelCacheDirPath() (string, error) {
	opentaintHome, err := GetOpenTaintHomePath()
	if err != nil {
		return "", err
	}
	return filepath.Join(opentaintHome, modelsCacheDir), nil
}

// GetModelCacheDir returns ~/.opentaint/cache/, creating it if needed.
func GetModelCacheDir() (string, error) {
	modelsDir, err := GetModelCacheDirPath()
	if err != nil {
		return "", err
	}
	if err := os.MkdirAll(modelsDir, 0o755); err != nil {
		return "", fmt.Errorf("failed to create cache directory: %w", err)
	}
	return modelsDir, nil
}

// canonicalProjectPath resolves a project path to an absolute, symlink-resolved form.
func canonicalProjectPath(projectPath string) (string, error) {
	absPath, err := filepath.Abs(projectPath)
	if err != nil {
		return "", fmt.Errorf("failed to resolve absolute path: %w", err)
	}
	absPath, err = filepath.EvalSymlinks(absPath)
	if err != nil {
		return "", fmt.Errorf("failed to resolve symlinks: %w", err)
	}
	return absPath, nil
}

// GetProjectCachePath returns ~/.opentaint/cache/<slug-hash>/ for a project path,
// creating the directory if needed. The project path is canonicalized (resolved
// through symlinks and made absolute) before hashing.
func GetProjectCachePath(projectPath string) (string, error) {
	absPath, err := canonicalProjectPath(projectPath)
	if err != nil {
		return "", err
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

const (
	projectModelDir = "project-model"
	modelsCacheDir  = "cache"
	logsCacheDir    = "logs"
)

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
	absPath, err := canonicalProjectPath(projectPath)
	if err != nil {
		return "", err
	}

	logsDir, err := GetLogCacheDirPath()
	if err != nil {
		return "", err
	}

	return filepath.Join(logsDir, ProjectPathSlugHash(absPath)), nil
}

// DefaultSarifReportPath returns the default SARIF report location for a given
// project model path: <projectModelPath>/sources/opentaint.sarif
func DefaultSarifReportPath(projectModelPath string) string {
	return filepath.Join(projectModelPath, "sources", "opentaint.sarif")
}

// CachedProjectModelPath returns the path to the cached project model
// within a cache directory: <cacheDir>/project-model
func CachedProjectModelPath(cacheDir string) string {
	return filepath.Join(cacheDir, projectModelDir)
}

// CreateStagingDir creates a staging directory inside cacheDir for isolated compilation.
// Returns the path to the staging directory (e.g. <cacheDir>/.staging-XXXX/).
func CreateStagingDir(cacheDir string) (string, error) {
	stagingPath, err := os.MkdirTemp(cacheDir, ".staging-")
	if err != nil {
		return "", fmt.Errorf("failed to create staging directory: %w", err)
	}
	return stagingPath, nil
}

// PromoteStagingToCache moves the compiled project-model/ from the staging
// directory into the cache, replacing any existing cached model.
// The caller must hold the compile lock to prevent concurrent promotions.
func PromoteStagingToCache(cacheDir, stagingPath string) error {
	srcPM := filepath.Join(stagingPath, projectModelDir)
	destPM := filepath.Join(cacheDir, projectModelDir)

	// Remove existing cached model if present
	if err := os.RemoveAll(destPM); err != nil {
		return fmt.Errorf("failed to remove old cached model: %w", err)
	}

	// Move staging project-model/ to cache
	if err := os.Rename(srcPM, destPM); err != nil {
		return fmt.Errorf("failed to move staging model to cache: %w", err)
	}

	// Remove the staging dir and any leftover files (e.g. build logs)
	_ = os.RemoveAll(stagingPath)

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
