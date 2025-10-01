// Package version contains version information for the application.
package version

import (
	"runtime/debug"
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
