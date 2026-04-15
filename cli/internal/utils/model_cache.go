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
// Concurrent compilations are prevented by HasStagingDir, so it is safe
// to remove and replace the existing project-model/ directory.
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

// HasStagingDir checks whether any .staging-* directory exists in cacheDir,
// indicating that another process is currently compiling a model.
func HasStagingDir(cacheDir string) bool {
	entries, err := os.ReadDir(cacheDir)
	if err != nil {
		return false
	}
	for _, entry := range entries {
		if entry.IsDir() && strings.HasPrefix(entry.Name(), ".staging-") {
			return true
		}
	}
	return false
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
