package utils

import (
	"crypto/sha256"
	"fmt"
	"regexp"
	"strings"
)

var nonAlphanumeric = regexp.MustCompile(`[^a-z0-9]+`)

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
