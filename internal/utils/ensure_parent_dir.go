package utils

import (
	"fmt"
	"os"
	"path/filepath"
)

func EnsureParentDir(path string) error {
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return fmt.Errorf("failed to create parent directory for %s: %w", path, err)
	}
	return nil
}
