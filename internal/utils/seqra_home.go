package utils

import (
	"os"
	"path/filepath"

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

// GetBundledLibPath returns the path to the bundled lib directory next to the binary.
// Returns empty string if the path cannot be determined.
func GetBundledLibPath() string {
	exe, err := os.Executable()
	if err != nil {
		return ""
	}
	exe, err = filepath.EvalSymlinks(exe)
	if err != nil {
		return ""
	}
	return filepath.Join(filepath.Dir(exe), "lib")
}

// GetBundledJREPath returns the path to the bundled JRE directory next to the binary.
// Returns empty string if the path cannot be determined.
func GetBundledJREPath() string {
	exe, err := os.Executable()
	if err != nil {
		return ""
	}
	exe, err = filepath.EvalSymlinks(exe)
	if err != nil {
		return ""
	}
	return filepath.Join(filepath.Dir(exe), "jre")
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
	return len(rel) > 0 && rel[0] != '.'
}

// resolveArtifactPath resolves the path for an artifact by checking:
//  1. Bundled path (next to binary) — only if version matches bindVersion
//  2. Install path (~/.seqra/install/lib/) — only if version matches bindVersion
//  3. Cache path (~/.seqra/<cacheName>)
func resolveArtifactPath(def globals.ArtifactDef) (string, error) {
	if def.IsBindVersion() {
		if libPath := GetBundledLibPath(); libPath != "" {
			bundledPath := filepath.Join(libPath, def.LibSubpath)
			if _, err := os.Stat(bundledPath); err == nil {
				return bundledPath, nil
			}
		}
		if libPath := GetInstallLibPath(); libPath != "" {
			installPath := filepath.Join(libPath, def.LibSubpath)
			if _, err := os.Stat(installPath); err == nil {
				return installPath, nil
			}
		}
	}

	seqraHomePath, err := GetSeqraHome()
	if err != nil {
		return "", err
	}
	return filepath.Join(seqraHomePath, def.CacheName()), nil
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
