package approximation

// Golden model: formal/Opentaint/Cli/Internal/Approximation/Stamp.lean

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

// stamp identifies the inputs of a model build: the project, and the compiler that read
// it. The project alone does not determine the compiled classes, so a build carrying only
// a source digest would be reused across a compiler upgrade.
type stamp struct {
	Sources   string `json:"sources"`
	Toolchain string `json:"toolchain"`
}

func (s stamp) complete() bool { return s.Sources != "" && s.Toolchain != "" }

// computeSourceDigest hashes all source files and build inputs.
// It does not hash generated files.
func computeSourceDigest(projectDir string) (string, error) {
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

func readStamp(outputDir string) (stamp, bool) {
	data, err := os.ReadFile(filepath.Join(outputDir, stampFileName))
	if err != nil {
		return stamp{}, false
	}
	var s stamp
	if err := json.Unmarshal(data, &s); err != nil {
		return stamp{}, false
	}
	return s, s.complete()
}

func writeStamp(outputDir string, s stamp) error {
	data, err := json.MarshalIndent(s, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(filepath.Join(outputDir, stampFileName), append(data, '\n'), 0o644)
}

// upToDate reports whether outputDir was built from these inputs.
func upToDate(outputDir string, current stamp) bool {
	previous, ok := readStamp(outputDir)
	if !ok || previous != current {
		return false
	}
	return containsClassFiles(ClassesDir(outputDir))
}
