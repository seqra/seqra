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

func GetAutobuilderJarPath(version string) (string, error) {
	if version == globals.AutobuilderBindVersion {
		if libPath := GetBundledLibPath(); libPath != "" {
			bundledPath := filepath.Join(libPath, globals.AutobuilderAssetName)
			if _, err := os.Stat(bundledPath); err == nil {
				return bundledPath, nil
			}
		}
		if libPath := GetInstallLibPath(); libPath != "" {
			installPath := filepath.Join(libPath, globals.AutobuilderAssetName)
			if _, err := os.Stat(installPath); err == nil {
				return installPath, nil
			}
		}
	}

	seqraHomePath, err := GetSeqraHome()
	if err != nil {
		return "", err
	}
	autobuilderJar := filepath.Join(seqraHomePath, "autobuilder_"+version+".jar")
	return autobuilderJar, nil
}

func GetAnalyzerJarPath(version string) (string, error) {
	if version == globals.AnalyzerBindVersion {
		if libPath := GetBundledLibPath(); libPath != "" {
			bundledPath := filepath.Join(libPath, globals.AnalyzerAssetName)
			if _, err := os.Stat(bundledPath); err == nil {
				return bundledPath, nil
			}
		}
		if libPath := GetInstallLibPath(); libPath != "" {
			installPath := filepath.Join(libPath, globals.AnalyzerAssetName)
			if _, err := os.Stat(installPath); err == nil {
				return installPath, nil
			}
		}
	}

	seqraHomePath, err := GetSeqraHome()
	if err != nil {
		return "", err
	}
	analyzerJar := filepath.Join(seqraHomePath, "analyzer_"+version+".jar")
	return analyzerJar, nil
}

func GetRulesPath(version string) (string, error) {
	if version == globals.RulesBindVersion {
		if libPath := GetBundledLibPath(); libPath != "" {
			bundledPath := filepath.Join(libPath, "rules")
			if _, err := os.Stat(bundledPath); err == nil {
				return bundledPath, nil
			}
		}
		if libPath := GetInstallLibPath(); libPath != "" {
			installPath := filepath.Join(libPath, "rules")
			if _, err := os.Stat(installPath); err == nil {
				return installPath, nil
			}
		}
	}

	seqraHomePath, err := GetSeqraHome()
	if err != nil {
		return "", err
	}
	rulesPath := filepath.Join(seqraHomePath, "rules_"+version)
	return rulesPath, nil
}
