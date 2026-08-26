package approximation

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
)

// stamp identifies the inputs of a model build.
type stamp struct {
	Sources string `json:"sources"`
}

// computeStamp hashes all source files and build inputs.
// It does not hash generated files.
func computeStamp(projectDir string) (string, error) {
	var paths []string
	err := filepath.WalkDir(projectDir, func(path string, entry os.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if entry.IsDir() {
			if path != projectDir && skippedDirs[entry.Name()] {
				return filepath.SkipDir
			}
			return nil
		}
		rel, err := filepath.Rel(projectDir, path)
		if err != nil {
			return err
		}
		paths = append(paths, filepath.ToSlash(rel))
		return nil
	})
	if err != nil {
		return "", fmt.Errorf("failed to read approximation project %s: %w", projectDir, err)
	}
	sort.Strings(paths)

	digest := sha256.New()
	for _, rel := range paths {
		if _, err := fmt.Fprintf(digest, "%s\x00", rel); err != nil {
			return "", fmt.Errorf("failed to hash path %s: %w", rel, err)
		}
		file, err := os.Open(filepath.Join(projectDir, filepath.FromSlash(rel)))
		if err != nil {
			return "", fmt.Errorf("failed to read %s: %w", rel, err)
		}
		_, copyErr := io.Copy(digest, file)
		_ = file.Close()
		if copyErr != nil {
			return "", fmt.Errorf("failed to read %s: %w", rel, copyErr)
		}
	}
	return hex.EncodeToString(digest.Sum(nil)), nil
}

func readStamp(outputDir string) (string, bool) {
	data, err := os.ReadFile(filepath.Join(outputDir, stampFileName))
	if err != nil {
		return "", false
	}
	var s stamp
	if err := json.Unmarshal(data, &s); err != nil {
		return "", false
	}
	return s.Sources, s.Sources != ""
}

func writeStamp(outputDir, sources string) error {
	data, err := json.MarshalIndent(stamp{Sources: sources}, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(filepath.Join(outputDir, stampFileName), append(data, '\n'), 0o644)
}

// upToDate reports whether outputDir matches the source stamp.
func upToDate(outputDir, sources string) bool {
	previous, ok := readStamp(outputDir)
	if !ok || previous != sources {
		return false
	}
	return containsClassFiles(ClassesDir(outputDir))
}
