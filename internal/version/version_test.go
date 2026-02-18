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

func TestCompareVersions(t *testing.T) {
	tests := []struct {
		name    string
		a, b    string
		want    int
		wantErr bool
	}{
		{"equal", "1.2.3", "1.2.3", 0, false},
		{"a < b (patch)", "1.2.3", "1.2.4", -1, false},
		{"a > b (patch)", "1.2.4", "1.2.3", 1, false},
		{"a < b (minor)", "1.1.0", "1.2.0", -1, false},
		{"a > b (major)", "2.0.0", "1.0.0", 1, false},
		{"v prefix both", "v1.2.3", "v1.2.3", 0, false},
		{"v prefix one side", "v1.2.3", "1.2.3", 0, false},
		{"pre-release stripped", "1.2.3-beta", "1.2.3", 0, false},
		{"build metadata stripped", "1.2.3+build", "1.2.3", 0, false},
		{"error: non-semver", "not-a-version", "1.2.3", 0, true},
		{"error: two parts", "1.2", "1.2.3", 0, true},
		{"error: empty string", "", "1.2.3", 0, true},
		{"error: letters", "1.two.3", "1.2.3", 0, true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := CompareVersions(tt.a, tt.b)
			if (err != nil) != tt.wantErr {
				t.Fatalf("CompareVersions(%q, %q) error = %v, wantErr %v", tt.a, tt.b, err, tt.wantErr)
			}
			if got != tt.want {
				t.Errorf("CompareVersions(%q, %q) = %d, want %d", tt.a, tt.b, got, tt.want)
			}
		})
	}
}
