package utils

import (
	"os"
	"path/filepath"
	"strings"

	"github.com/seqra/seqra/v2/internal/globals"
)

func GetSeqraHome() (string, error) {
	// Find home directory.
	home, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}

	// Search config in home directory with name ".seqra" (without extension).
	path := filepath.Join(home, ".seqra")
	merr := os.MkdirAll(path, os.ModePerm)
	if merr != nil {
		return "", merr
	}

	return path, nil
}

// exeDir returns the directory containing the current executable, resolved through symlinks.
// Returns empty string if the path cannot be determined.
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

// GetBundledLibPath returns the path to the bundled lib directory next to the binary.
// Returns empty string if the path cannot be determined.
func GetBundledLibPath() string {
	if dir := exeDir(); dir != "" {
		return filepath.Join(dir, "lib")
	}
	return ""
}

// GetBundledJREPath returns the path to the bundled JRE directory next to the binary.
// Returns empty string if the path cannot be determined.
func GetBundledJREPath() string {
	if dir := exeDir(); dir != "" {
		return filepath.Join(dir, "jre")
	}
	return ""
}

// GetInstallLibPath returns the path to the lib directory in ~/.seqra/install/.
// Returns empty string if the home directory cannot be determined.
func GetInstallLibPath() string {
	home, err := os.UserHomeDir()
	if err != nil {
		return ""
	}
	return filepath.Join(home, ".seqra", "install", "lib")
}

// GetInstallJREPath returns the path to the jre directory in ~/.seqra/install/.
// Returns empty string if the home directory cannot be determined.
func GetInstallJREPath() string {
	home, err := os.UserHomeDir()
	if err != nil {
		return ""
	}
	return filepath.Join(home, ".seqra", "install", "jre")
}

// IsBundledPath returns true if the given path is under the bundled lib directory.
func IsBundledPath(path string) bool {
	bundledLib := GetBundledLibPath()
	if bundledLib == "" {
		return false
	}
	rel, err := filepath.Rel(bundledLib, path)
	if err != nil {
		return false
	}
	return len(rel) > 0 && !strings.HasPrefix(rel, "..")
}

// resolveArtifactPath resolves the path for an artifact by checking tiers in order:
//  1. Bundled path (next to binary) — only if version matches bindVersion
//  2. Install path (~/.seqra/install/lib/) — only if version matches bindVersion
//  3. Cache path (~/.seqra/<cacheName>)
func resolveArtifactPath(def globals.ArtifactDef) (string, error) {
	tiers, err := ArtifactTiers(def)
	if err != nil {
		return "", err
	}
	if found := FindExisting(tiers); found != nil {
		return found.Path, nil
	}
	// Return cache tier as default (even if artifact not yet downloaded)
	return tiers[len(tiers)-1].Path, nil
}

func GetAutobuilderJarPath(version string) (string, error) {
	return resolveArtifactPath(globals.ArtifactByKind("autobuilder").WithVersion(version))
}

func GetAnalyzerJarPath(version string) (string, error) {
	return resolveArtifactPath(globals.ArtifactByKind("analyzer").WithVersion(version))
}

func GetRulesPath(version string) (string, error) {
	return resolveArtifactPath(globals.ArtifactByKind("rules").WithVersion(version))
}
