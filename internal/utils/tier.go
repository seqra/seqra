package utils

import (
	"os"
	"path/filepath"
	"runtime"

	"github.com/seqra/seqra/v2/internal/globals"
)

// Tier name constants.
const (
	TierBundled = "bundled" // next to the binary (e.g., <exe-dir>/lib/ or <exe-dir>/jre/)
	TierInstall = "install" // user-local install path (~/.seqra/install/)
	TierCache   = "cache"   // shared download cache (~/.seqra/)
)

// Tier represents a storage location for artifacts, ordered by priority.
type Tier struct {
	Name string // TierBundled, TierInstall, or TierCache
	Path string // full path to the artifact at this tier
}

// Exists reports whether the artifact exists at this tier's path.
func (t Tier) Exists() bool {
	_, err := os.Stat(t.Path)
	return err == nil
}

// CurrentTiers returns a filtered copy of tiers that excludes stale install-tier
// entries. When installCurrent is false, install tiers are omitted so that
// stale artifacts from a previous seqra version are not picked up.
func CurrentTiers(tiers []Tier, installCurrent bool) []Tier {
	if installCurrent {
		return tiers
	}
	filtered := make([]Tier, 0, len(tiers))
	for _, t := range tiers {
		if t.Name != TierInstall {
			filtered = append(filtered, t)
		}
	}
	return filtered
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
			tiers = append(tiers, Tier{TierBundled, filepath.Join(libPath, def.LibSubpath)})
		}
		if libPath := GetInstallLibPath(); libPath != "" {
			tiers = append(tiers, Tier{TierInstall, filepath.Join(libPath, def.LibSubpath)})
		}
	} else {
		seqraHome, err := GetSeqraHome()
		if err != nil {
			return nil, err
		}
		tiers = append(tiers, Tier{TierCache, filepath.Join(seqraHome, def.CacheName())})
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
			tiers = append(tiers, Tier{TierBundled, jrePath})
		}
		if jrePath := GetInstallJREPath(); jrePath != "" {
			tiers = append(tiers, Tier{TierInstall, jrePath})
		}
	} else {
		tiers = append(tiers, Tier{TierCache, cacheDir})
	}
	return tiers
}

// ManagedJRETiers returns the bundled and install JRE tiers (excluding cache).
// Used to find a pre-installed JRE without triggering a download.
func ManagedJRETiers() []Tier {
	var tiers []Tier
	if jrePath := GetBundledJREPath(); jrePath != "" {
		tiers = append(tiers, Tier{TierBundled, jrePath})
	}
	if jrePath := GetInstallJREPath(); jrePath != "" {
		tiers = append(tiers, Tier{TierInstall, jrePath})
	}
	return tiers
}
