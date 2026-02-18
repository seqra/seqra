package utils

import (
	"os"
	"path/filepath"
	"runtime"

	"github.com/seqra/seqra/v2/internal/globals"
)

// Tier represents a storage location for artifacts, ordered by priority.
// The three tiers are:
//   - "bundled": next to the binary (e.g., <exe-dir>/lib/ or <exe-dir>/jre/)
//   - "install": user-local install path (~/.seqra/install/)
//   - "cache":   shared download cache (~/.seqra/)
type Tier struct {
	Name string // "bundled", "install", "cache"
	Path string // full path to the artifact at this tier
}

// Exists reports whether the artifact exists at this tier's path.
func (t Tier) Exists() bool {
	_, err := os.Stat(t.Path)
	return err == nil
}

// FindExisting returns the first tier where the path exists, or nil.
func FindExisting(tiers []Tier) *Tier {
	for i := range tiers {
		if tiers[i].Exists() {
			return &tiers[i]
		}
	}
	return nil
}

// FindExistingJRE returns the first tier containing a java binary, or nil.
func FindExistingJRE(tiers []Tier) *Tier {
	binary := javaBinaryName()
	for i := range tiers {
		javaPath := filepath.Join(tiers[i].Path, "bin", binary)
		if _, err := os.Stat(javaPath); err == nil {
			return &tiers[i]
		}
	}
	return nil
}

// FindExistingCurrent is like FindExisting but skips install-tier entries
// when the install directory is not current (version marker mismatch).
func FindExistingCurrent(tiers []Tier) *Tier {
	installCurrent := IsInstallCurrent()
	for i := range tiers {
		if tiers[i].Name == "install" && !installCurrent {
			continue
		}
		if tiers[i].Exists() {
			return &tiers[i]
		}
	}
	return nil
}

// FindExistingJRECurrent is like FindExistingJRE but skips install-tier entries
// when the install directory is not current (version marker mismatch).
func FindExistingJRECurrent(tiers []Tier) *Tier {
	installCurrent := IsInstallCurrent()
	binary := javaBinaryName()
	for i := range tiers {
		if tiers[i].Name == "install" && !installCurrent {
			continue
		}
		javaPath := filepath.Join(tiers[i].Path, "bin", binary)
		if _, err := os.Stat(javaPath); err == nil {
			return &tiers[i]
		}
	}
	return nil
}

// JavaBinaryPath returns the path to the java binary within a JRE directory.
func JavaBinaryPath(jreDir string) string {
	return filepath.Join(jreDir, "bin", javaBinaryName())
}

func javaBinaryName() string {
	if runtime.GOOS == "windows" {
		return "java.exe"
	}
	return "java"
}

// ArtifactTiers returns the prioritized candidate paths for a lib artifact.
// Bundled and install tiers are included only when the version matches the bind version.
func ArtifactTiers(def globals.ArtifactDef) ([]Tier, error) {
	var tiers []Tier
	if def.IsBindVersion() {
		if libPath := GetBundledLibPath(); libPath != "" {
			tiers = append(tiers, Tier{"bundled", filepath.Join(libPath, def.LibSubpath)})
		}
		if libPath := GetInstallLibPath(); libPath != "" {
			tiers = append(tiers, Tier{"install", filepath.Join(libPath, def.LibSubpath)})
		}
	} else {
		seqraHome, err := GetSeqraHome()
		if err != nil {
			return nil, err
		}
		tiers = append(tiers, Tier{"cache", filepath.Join(seqraHome, def.CacheName())})
	}
	return tiers, nil
}

// JRETiers returns the prioritized candidate paths for a JRE directory.
// Bundled and install tiers are included only when javaVersion matches DefaultJavaVersion.
// cacheDir is the platform-specific cache directory (e.g., ~/.seqra/jre/temurin-21-jre-linux-x64).
func JRETiers(javaVersion int, cacheDir string) []Tier {
	var tiers []Tier
	if javaVersion == globals.DefaultJavaVersion {
		if jrePath := GetBundledJREPath(); jrePath != "" {
			tiers = append(tiers, Tier{"bundled", jrePath})
		}
		if jrePath := GetInstallJREPath(); jrePath != "" {
			tiers = append(tiers, Tier{"install", jrePath})
		}
	} else {
		tiers = append(tiers, Tier{"cache", cacheDir})
	}
	return tiers
}

// ManagedJRETiers returns the bundled and install JRE tiers (excluding cache).
// Used to find a pre-installed JRE without triggering a download.
func ManagedJRETiers() []Tier {
	var tiers []Tier
	if jrePath := GetBundledJREPath(); jrePath != "" {
		tiers = append(tiers, Tier{"bundled", jrePath})
	}
	if jrePath := GetInstallJREPath(); jrePath != "" {
		tiers = append(tiers, Tier{"install", jrePath})
	}
	return tiers
}
