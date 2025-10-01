package version

import (
	"strings"
	"testing"
)

func TestGetVersion(t *testing.T) {
	// Test when Version is set by ldflags
	originalVersion := Version
	defer func() { Version = originalVersion }()

	// Test with ldflags version
	Version = "v1.2.3"
	if got := GetVersion(); got != "v1.2.3" {
		t.Errorf("GetVersion() with ldflags = %v, want %v", got, "v1.2.3")
	}

	// Test fallback to dev when Version is "dev"
	Version = "dev"
	got := GetVersion()
	// Should return either "dev" or a development version with commit hash
	if got != "dev" && !strings.HasPrefix(got, "dev-") && !strings.HasPrefix(got, "v") {
		t.Errorf("GetVersion() fallback should return 'dev', 'dev-<hash>', or version from build info, got %v", got)
	}
}
