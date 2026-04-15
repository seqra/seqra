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

// ProjectPathSlugHash returns a deterministic directory name for a project path.
// Format: slugified-path-8hexchars (e.g. "users-me-my-project-a1b2c3d4").
func ProjectPathSlugHash(absPath string) string {
	slug := strings.ToLower(absPath)
	slug = nonAlphanumeric.ReplaceAllString(slug, "-")
	slug = strings.Trim(slug, "-")

	hash := sha256.Sum256([]byte(absPath))
	hexHash := fmt.Sprintf("%x", hash[:4]) // 8 hex chars

	return fmt.Sprintf("%s-%s", slug, hexHash)
}
