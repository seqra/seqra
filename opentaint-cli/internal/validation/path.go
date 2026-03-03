package validation

import (
	"fmt"
	"os"
	"path/filepath"
)

func requireDirExists(path, label string) error {
	info, err := os.Stat(path)
	if err != nil {
		if os.IsNotExist(err) {
			return fmt.Errorf("%s does not exist: %s", label, path)
		}
		return fmt.Errorf("failed to access %s %s: %w", label, path, err)
	}
	if !info.IsDir() {
		return fmt.Errorf("%s is not a directory: %s", label, path)
	}

	return nil
}

func requireFileExists(path, label string) (os.FileInfo, error) {
	info, err := os.Stat(path)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, fmt.Errorf("%s does not exist: %s", label, path)
		}
		return nil, fmt.Errorf("failed to access %s %s: %w", label, path, err)
	}
	if info.IsDir() {
		return nil, fmt.Errorf("%s path is a directory: %s", label, path)
	}

	return info, nil
}

func requirePathNotExists(path, label string) error {
	if _, err := os.Stat(path); err == nil {
		return fmt.Errorf("%s already exists: %s", label, path)
	} else if !os.IsNotExist(err) {
		return fmt.Errorf("failed to access %s %s: %w", label, path, err)
	}

	return nil
}

func requirePathExists(path, label string) error {
	if _, err := os.Stat(path); err != nil {
		if os.IsNotExist(err) {
			return fmt.Errorf("%s does not exist: %s", label, path)
		}
		return fmt.Errorf("failed to access %s %s: %w", label, path, err)
	}

	return nil
}

func requireParentDirExists(path, label string) error {
	parentDir := filepath.Dir(path)
	if err := requireDirExists(parentDir, label); err != nil {
		return err
	}

	return nil
}
