package utils

import (
	"bytes"
	"os"
	"path/filepath"

	"github.com/seqra/opentaint/internal/globals"
)

// GetOpenTaintHomePath returns ~/.opentaint/ without creating it.
// Use this when you only need to read/check the directory (e.g. prune scanning).
func GetOpenTaintHomePath() (string, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(home, ".opentaint"), nil
}

// GetOpenTaintHome returns ~/.opentaint/, creating it if needed.
func GetOpenTaintHome() (string, error) {
	path, err := GetOpenTaintHomePath()
	if err != nil {
		return "", err
	}
	if err := os.MkdirAll(path, os.ModePerm); err != nil {
		return "", err
	}
	return path, nil
}

// pathExists reports whether a path exists on disk.
func pathExists(p string) bool {
	_, err := os.Stat(p)
	return err == nil
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

// GetInstallDir returns the path to ~/.opentaint/install/.
// Returns empty string if the home directory cannot be determined.
func GetInstallDir() string {
	home, err := os.UserHomeDir()
	if err != nil {
		return ""
	}
	return filepath.Join(home, ".opentaint", "install")
}

// GetInstallLibPath returns the path to the lib directory in ~/.opentaint/install/.
// Returns empty string if the home directory cannot be determined.
func GetInstallLibPath() string {
	if dir := GetInstallDir(); dir != "" {
		return filepath.Join(dir, "lib")
	}
	return ""
}

// GetInstallJREPath returns the path to the jre directory in ~/.opentaint/install/.
// Returns empty string if the home directory cannot be determined.
func GetInstallJREPath() string {
	if dir := GetInstallDir(); dir != "" {
		return filepath.Join(dir, "jre")
	}
	return ""
}

// IsInstallCurrent reports whether the install-tier version marker matches
// the embedded versions.yaml. Returns false if the marker is missing or differs.
func IsInstallCurrent() bool {
	installDir := GetInstallDir()
	if installDir == "" {
		return false
	}
	data, err := os.ReadFile(filepath.Join(installDir, ".versions"))
	if err != nil {
		return false
	}
	return bytes.Equal(data, globals.GetVersionsYAML())
}

// WriteInstallVersionMarker writes the embedded versions.yaml content to
// ~/.opentaint/install/.versions so that future runs can detect stale installs.
func WriteInstallVersionMarker() error {
	installDir := GetInstallDir()
	if installDir == "" {
		return nil
	}
	if err := os.MkdirAll(installDir, 0o755); err != nil {
		return err
	}
	return os.WriteFile(filepath.Join(installDir, ".versions"), globals.GetVersionsYAML(), 0o644)
}

// CleanInstallDir removes the install-tier lib and jre directories along with
// the stale .versions marker. This is called before re-downloading after an upgrade.
func CleanInstallDir() error {
	installDir := GetInstallDir()
	if installDir == "" {
		return nil
	}
	for _, sub := range []string{"lib", "jre", ".versions"} {
		if err := os.RemoveAll(filepath.Join(installDir, sub)); err != nil {
			return err
		}
	}
	return nil
}

// ReconcileInstallMarker writes the install-tier version marker if all
// bind-version artifacts are present but the marker is missing or stale.
// This reconciles the marker after SelfUpdate, where the old binary cannot
// write correct data. Safe to call on every command invocation (a few Stat calls).
func ReconcileInstallMarker() {
	if IsInstallCurrent() {
		return
	}
	installLib := GetInstallLibPath()
	if installLib == "" {
		return
	}
	for _, def := range globals.Artifacts() {
		if !pathExists(filepath.Join(installLib, def.LibSubpath)) {
			return
		}
	}
	_ = WriteInstallVersionMarker()
}

// resolveArtifactPath resolves the path for an artifact by checking tiers in order:
//  1. Bundled path (next to binary) — only if version matches bindVersion
//  2. Install path (~/.opentaint/install/lib/) — only if version matches bindVersion
//  3. Cache path (~/.opentaint/<cacheName>)
func resolveArtifactPath(def globals.ArtifactDef) (string, error) {
	tiers, err := ArtifactTiers(def)
	if err != nil {
		return "", err
	}
	if found := FindExisting(CurrentTiers(tiers, IsInstallCurrent())); found != nil {
		return found.Path, nil
	}
	// Return last tier as default download target (even if artifact not yet downloaded)
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
