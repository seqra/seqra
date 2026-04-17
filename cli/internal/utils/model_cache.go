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

// compileCompleteMarker is the sentinel file written as the final step of a
// successful compile. Its presence proves the whole model is on disk.
const compileCompleteMarker = ".compile-complete"

// CompileCompleteMarkerPath returns the absolute path to the compile-complete
// marker inside a cache directory.
func CompileCompleteMarkerPath(cacheDir string) string {
	return filepath.Join(cacheDir, projectModelDir, compileCompleteMarker)
}

// IsCachedModelComplete reports whether cacheDir holds a fully-written
// project model. Both project.yaml and the compile-complete marker must
// exist. A crashed mid-compile leaves the marker absent and returns false.
func IsCachedModelComplete(cacheDir string) bool {
	pm := CachedProjectModelPath(cacheDir)
	if _, err := os.Stat(filepath.Join(pm, "project.yaml")); err != nil {
		return false
	}
	if _, err := os.Stat(CompileCompleteMarkerPath(cacheDir)); err != nil {
		return false
	}
	return true
}

// MarkCompileComplete writes the compile-complete marker as the very last
// step of a successful compile. The caller must have just populated
// <cacheDir>/project-model/ and must hold the cache exclusive lock.
func MarkCompileComplete(cacheDir string) error {
	markerPath := CompileCompleteMarkerPath(cacheDir)
	if err := os.WriteFile(markerPath, nil, 0o644); err != nil {
		return fmt.Errorf("failed to write compile-complete marker: %w", err)
	}
	return nil
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
