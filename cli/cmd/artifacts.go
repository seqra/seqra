package cmd

import (
	"errors"
	"fmt"
	"os"
)

func ensureArtifactAvailable(name, version, artifactPath string, download func() error) error {
	if _, err := os.Stat(artifactPath); err == nil {
		return nil
	} else if !errors.Is(err, os.ErrNotExist) {
		return fmt.Errorf("failed to check %s at %s: %w", name, artifactPath, err)
	}

	if !out.IsInteractive() {
		out.Blank()
		if version == "" {
			out.Printf("Downloading %s", name)
		} else {
			out.Printf("Downloading %s version %s", name, version)
		}
	}

	if err := download(); err != nil {
		return fmt.Errorf("failed to download %s: %w", name, err)
	}

	if !out.IsInteractive() {
		out.Printf("Successfully downloaded %s to %s", name, artifactPath)
	}

	return nil
}
