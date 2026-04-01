// Package agent embeds the agent skill files and meta-prompt, and extracts
// them on demand to ~/.opentaint/agent/ when no bundled copy is available.
package agent

import (
	"crypto/sha256"
	"embed"
	"encoding/hex"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"sort"
	"strings"
)

//go:generate sh -c "rm -rf files && cp -r ../../../agent files"

//go:embed files
var agentFS embed.FS

// contentHash returns a deterministic SHA-256 hash of all embedded files.
// Used as a staleness marker for the extracted copy.
func contentHash() string {
	h := sha256.New()
	// Walk in sorted order for determinism.
	var paths []string
	fs.WalkDir(agentFS, ".", func(path string, d fs.DirEntry, err error) error {
		if err != nil || d.IsDir() {
			return err
		}
		paths = append(paths, path)
		return nil
	})
	sort.Strings(paths)
	for _, p := range paths {
		data, _ := agentFS.ReadFile(p)
		h.Write([]byte(p))
		h.Write(data)
	}
	return hex.EncodeToString(h.Sum(nil))
}

// GetPath returns a filesystem path to the agent directory containing
// meta-prompt.md and skills/.
//
// Resolution order:
//  1. Bundled: <exe-dir>/lib/agent/ (release archives place files here)
//  2. Extracted: ~/.opentaint/agent/ (populated from embedded FS on demand)
//
// The extracted copy is refreshed when its hash marker diverges from the
// embedded content, ensuring go-install and dev-build users always get the
// version of the agent files that matches their binary.
func GetPath() (string, error) {
	// Tier 1: bundled next to binary (release builds).
	if dir := exeDir(); dir != "" {
		bundled := filepath.Join(dir, "lib", "agent")
		if isDir(bundled) {
			return bundled, nil
		}
	}

	// Tier 2: extract from embedded FS to ~/.opentaint/agent/.
	home, err := os.UserHomeDir()
	if err != nil {
		return "", fmt.Errorf("cannot determine home directory: %w", err)
	}
	extractDir := filepath.Join(home, ".opentaint", "agent")
	markerPath := filepath.Join(extractDir, ".content-hash")
	wantHash := contentHash()

	if !needsExtract(markerPath, wantHash) {
		return extractDir, nil
	}

	if err := extractEmbedded(extractDir, markerPath, wantHash); err != nil {
		return "", fmt.Errorf("failed to extract agent files: %w", err)
	}
	return extractDir, nil
}

// needsExtract reports whether the extracted copy is missing or stale.
func needsExtract(markerPath, wantHash string) bool {
	data, err := os.ReadFile(markerPath)
	if err != nil {
		return true
	}
	return strings.TrimSpace(string(data)) != wantHash
}

// extractEmbedded writes the embedded agent FS to destDir and writes the
// content-hash marker.
func extractEmbedded(destDir, markerPath, hash string) error {
	// Remove stale tree (if any) and recreate.
	if err := os.RemoveAll(destDir); err != nil {
		return err
	}

	err := fs.WalkDir(agentFS, "files", func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		// Strip the "files" prefix so "files/skills/foo.md" → "skills/foo.md".
		rel, _ := filepath.Rel("files", path)
		target := filepath.Join(destDir, rel)

		if d.IsDir() {
			return os.MkdirAll(target, 0o755)
		}
		data, err := agentFS.ReadFile(path)
		if err != nil {
			return err
		}
		return os.WriteFile(target, data, 0o644)
	})
	if err != nil {
		return err
	}

	return os.WriteFile(markerPath, []byte(hash+"\n"), 0o644)
}

// exeDir returns the directory of the current executable, resolved through symlinks.
func exeDir() string {
	exe, err := os.Executable()
	if err != nil {
		return ""
	}
	exe, err = filepath.EvalSymlinks(exe)
	if err != nil {
		return ""
	}
	return filepath.Dir(exe)
}

// isDir reports whether path exists and is a directory.
func isDir(path string) bool {
	fi, err := os.Stat(path)
	return err == nil && fi.IsDir()
}
