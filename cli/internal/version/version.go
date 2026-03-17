// Package version contains version information for the application.
package version

import (
	"fmt"
	"runtime/debug"
	"strconv"
	"strings"
)

// These variables are set during build time using ldflags
var (
	// Version is the current version of the application
	Version = "dev"
)

// GetVersion returns the version of the application.
// It first tries to use the version set by ldflags (for releases),
// then falls back to build info from go install (for development builds).
func GetVersion() string {
	// If version was set by ldflags (e.g., via GoReleaser), use it
	if Version != "dev" {
		return Version
	}

	// Try to get version from build info (works with go install)
	if info, ok := debug.ReadBuildInfo(); ok {
		// Check if we have version info in the main module
		if info.Main.Version != "" && info.Main.Version != "(devel)" {
			// Clean up the version string (remove any +incompatible suffix)
			version := info.Main.Version
			if idx := strings.Index(version, "+"); idx != -1 {
				version = version[:idx]
			}
			return version
		}

		// Check if this was built with go install from a tagged version
		for _, setting := range info.Settings {
			if setting.Key == "vcs.revision" && len(setting.Value) >= 7 {
				// Use short commit hash for development builds
				return "dev-" + setting.Value[:7]
			}
		}
	}

	// Fallback to "dev" if nothing else works
	return "dev"
}

// CompareVersions compares two semver strings.
// Returns -1 if a < b, 0 if a == b, 1 if a > b.
// Versions may optionally start with "v".
func CompareVersions(a, b string) (int, error) {
	aParts, err := parseSemver(a)
	if err != nil {
		return 0, fmt.Errorf("invalid version %q: %w", a, err)
	}
	bParts, err := parseSemver(b)
	if err != nil {
		return 0, fmt.Errorf("invalid version %q: %w", b, err)
	}

	for i := 0; i < 3; i++ {
		if aParts[i] < bParts[i] {
			return -1, nil
		}
		if aParts[i] > bParts[i] {
			return 1, nil
		}
	}
	return 0, nil
}

func parseSemver(v string) ([3]int, error) {
	v = strings.TrimPrefix(v, "v")
	// Strip pre-release/build metadata
	if idx := strings.IndexAny(v, "-+"); idx != -1 {
		v = v[:idx]
	}
	parts := strings.Split(v, ".")
	if len(parts) != 3 {
		return [3]int{}, fmt.Errorf("expected 3 parts, got %d", len(parts))
	}
	var result [3]int
	for i, part := range parts {
		n, err := strconv.Atoi(part)
		if err != nil {
			return [3]int{}, fmt.Errorf("invalid number %q: %w", part, err)
		}
		result[i] = n
	}
	return result, nil
}
