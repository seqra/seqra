package utils

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/seqra/seqra/v2/internal/globals"
)

// StaleArtifact represents an artifact that can be pruned.
type StaleArtifact struct {
	Path string
	Size int64
	Kind string // "analyzer", "autobuilder", "rules", "jdk", "jre", "log"
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

// ScanForStaleArtifacts scans ~/.seqra/ for artifacts that are not current and returns them.
func ScanForStaleArtifacts(includeLogs bool) (*PruneResult, error) {
	seqraHome, err := GetSeqraHome()
	if err != nil {
		return nil, fmt.Errorf("failed to get seqra home: %w", err)
	}

	result := &PruneResult{}

	entries, err := os.ReadDir(seqraHome)
	if err != nil {
		return nil, fmt.Errorf("failed to read seqra home: %w", err)
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
		fullPath := filepath.Join(seqraHome, name)

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
		if name == "jdk" || name == "jre" {
			subEntries, err := os.ReadDir(fullPath)
			if err != nil {
				continue
			}
			for _, subEntry := range subEntries {
				subPath := filepath.Join(fullPath, subEntry.Name())

				// If bundled JRE exists, all downloaded JREs for current version are redundant
				isRedundant := bundledJREExists && name == "jre"

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

	// Scan for logs if requested
	if includeLogs {
		logsDir := filepath.Join(seqraHome, "logs")
		if info, err := os.Stat(logsDir); err == nil && info.IsDir() {
			size, _ := dirSize(logsDir)
			if size > 0 {
				result.Stale = append(result.Stale, StaleArtifact{
					Path: logsDir,
					Size: size,
					Kind: "log",
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

// FormatSize formats a byte size into a human-readable string.
func FormatSize(bytes int64) string {
	const (
		KB = 1024
		MB = KB * 1024
		GB = MB * 1024
	)

	switch {
	case bytes >= GB:
		return fmt.Sprintf("%.1f GB", float64(bytes)/float64(GB))
	case bytes >= MB:
		return fmt.Sprintf("%.1f MB", float64(bytes)/float64(MB))
	case bytes >= KB:
		return fmt.Sprintf("%.1f KB", float64(bytes)/float64(KB))
	default:
		return fmt.Sprintf("%d B", bytes)
	}
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
