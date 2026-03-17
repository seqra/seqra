package utils

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/seqra/opentaint/v2/internal/globals"
)

// Stale artifact kind constants.
const (
	StaleKindAnalyzer    = "analyzer"
	StaleKindAutobuilder = "autobuilder"
	StaleKindRules       = "rules"
	StaleKindJDK         = "jdk"
	StaleKindJRE         = "jre"
	StaleKindInstallLib  = "install-lib"
	StaleKindInstallJRE  = "install-jre"
	StaleKindLog         = "log"
)

// StaleArtifact represents an artifact that can be pruned.
type StaleArtifact struct {
	Path string
	Size int64
	Kind string
}

// PruneResult contains the results of scanning for stale artifacts.
type PruneResult struct {
	Stale      []StaleArtifact
	TotalSize  int64
	TotalCount int
}

// checkStale tests whether a filename matches the given artifact definition and is stale or redundant.
// Returns a StaleArtifact if it should be pruned, nil otherwise.
func checkStale(def globals.ArtifactDef, name, fullPath string, bundledLibExists bool) *StaleArtifact {
	if !strings.HasPrefix(name, def.CachePrefix) || !strings.HasSuffix(name, def.CacheSuffix) {
		return nil
	}
	version := strings.TrimPrefix(name, def.CachePrefix)
	version = strings.TrimSuffix(version, def.CacheSuffix)

	isStale := version != def.BindVersion
	isRedundant := !isStale && bundledLibExists

	if !isStale && !isRedundant {
		return nil
	}

	var size int64
	if def.Unpack {
		size, _ = dirSize(fullPath)
	} else {
		size = fileSize(fullPath)
	}
	return &StaleArtifact{Path: fullPath, Size: size, Kind: def.Kind()}
}

// ScanForStaleArtifacts scans ~/.opentaint/ for artifacts that are not current and returns them.
func ScanForStaleArtifacts(includeLogs bool) (*PruneResult, error) {
	opentaintHome, err := GetOpentaintHome()
	if err != nil {
		return nil, fmt.Errorf("failed to get opentaint home: %w", err)
	}

	result := &PruneResult{}

	entries, err := os.ReadDir(opentaintHome)
	if err != nil {
		return nil, fmt.Errorf("failed to read opentaint home: %w", err)
	}

	bundledLibExists := false
	if libPath := GetBundledLibPath(); libPath != "" {
		if _, err := os.Stat(libPath); err == nil {
			bundledLibExists = true
		}
	}

	bundledJREExists := false
	if jrePath := GetBundledJREPath(); jrePath != "" {
		if _, err := os.Stat(jrePath); err == nil {
			bundledJREExists = true
		}
	}

	artifacts := globals.Artifacts()

	for _, entry := range entries {
		name := entry.Name()
		fullPath := filepath.Join(opentaintHome, name)

		// Skip special files, hidden files, and log directory
		if name == "logs" || strings.HasPrefix(name, ".") {
			continue
		}

		// Check against artifact definitions
		matched := false
		for _, def := range artifacts {
			if artifact := checkStale(def, name, fullPath, bundledLibExists); artifact != nil {
				result.Stale = append(result.Stale, *artifact)
				result.TotalSize += artifact.Size
				result.TotalCount++
				matched = true
				break
			}
			if strings.HasPrefix(name, def.CachePrefix) {
				matched = true // matches pattern but is current — skip
				break
			}
		}
		if matched {
			continue
		}

		// Check JDK/JRE directories
		if name == StaleKindJDK || name == StaleKindJRE {
			subEntries, err := os.ReadDir(fullPath)
			if err != nil {
				continue
			}
			for _, subEntry := range subEntries {
				subPath := filepath.Join(fullPath, subEntry.Name())

				// If bundled JRE exists, all downloaded JREs for current version are redundant
				isRedundant := bundledJREExists && name == StaleKindJRE

				// All JDK/JRE directories that aren't the current version are stale
				currentPrefix := fmt.Sprintf("temurin-%d-", globals.DefaultJavaVersion)
				isStale := !strings.HasPrefix(subEntry.Name(), currentPrefix)

				if isStale || isRedundant {
					size, _ := dirSize(subPath)
					result.Stale = append(result.Stale, StaleArtifact{
						Path: subPath,
						Size: size,
						Kind: name,
					})
					result.TotalSize += size
					result.TotalCount++
				}
			}
			continue
		}
	}

	// Scan install-tier directories for stale artifacts
	installCurrent := IsInstallCurrent()
	for _, check := range []struct {
		path          string
		kind          string
		bundledExists bool
	}{
		{GetInstallLibPath(), StaleKindInstallLib, bundledLibExists},
		{GetInstallJREPath(), StaleKindInstallJRE, bundledJREExists},
	} {
		if check.path == "" {
			continue
		}
		if _, err := os.Stat(check.path); err != nil {
			continue
		}
		if !installCurrent || check.bundledExists {
			size, _ := dirSize(check.path)
			result.Stale = append(result.Stale, StaleArtifact{
				Path: check.path,
				Size: size,
				Kind: check.kind,
			})
			result.TotalSize += size
			result.TotalCount++
		}
	}

	// Scan for logs if requested
	if includeLogs {
		logsDir := filepath.Join(opentaintHome, "logs")
		if info, err := os.Stat(logsDir); err == nil && info.IsDir() {
			size, _ := dirSize(logsDir)
			if size > 0 {
				result.Stale = append(result.Stale, StaleArtifact{
					Path: logsDir,
					Size: size,
					Kind: StaleKindLog,
				})
				result.TotalSize += size
				result.TotalCount++
			}
		}
	}

	return result, nil
}

// DeleteArtifacts removes the given stale artifacts.
func DeleteArtifacts(artifacts []StaleArtifact) error {
	for _, artifact := range artifacts {
		if err := os.RemoveAll(artifact.Path); err != nil {
			return fmt.Errorf("failed to remove %s: %w", artifact.Path, err)
		}
	}
	return nil
}

func fileSize(path string) int64 {
	info, err := os.Stat(path)
	if err != nil {
		return 0
	}
	return info.Size()
}

func dirSize(path string) (int64, error) {
	var size int64
	err := filepath.WalkDir(path, func(_ string, d os.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if !d.IsDir() {
			info, err := d.Info()
			if err != nil {
				return err
			}
			size += info.Size()
		}
		return nil
	})
	return size, err
}
