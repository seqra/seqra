package globals

import "testing"

func TestArtifacts(t *testing.T) {
	// Save and restore mutable globals
	origAnalyzer := AnalyzerBindVersion
	origAutobuilder := AutobuilderBindVersion
	origRules := RulesBindVersion
	origConfig := Config
	t.Cleanup(func() {
		AnalyzerBindVersion = origAnalyzer
		AutobuilderBindVersion = origAutobuilder
		RulesBindVersion = origRules
		Config = origConfig
	})

	AnalyzerBindVersion = "1.0.0"
	AutobuilderBindVersion = "2.0.0"
	RulesBindVersion = "v3.0.0"
	Config.Analyzer.Version = "1.0.0"
	Config.Autobuilder.Version = "2.0.0"
	Config.Rules.Version = "v3.0.0"

	arts := Artifacts()
	if len(arts) != 3 {
		t.Fatalf("expected 3 artifacts, got %d", len(arts))
	}

	names := []string{"Autobuilder", "Analyzer", "Rules"}
	for i, want := range names {
		if arts[i].Name != want {
			t.Errorf("Artifacts()[%d].Name = %q, want %q", i, arts[i].Name, want)
		}
	}
}

func TestCacheName(t *testing.T) {
	tests := []struct {
		prefix, version, suffix, want string
	}{
		{"analyzer_", "1.0.0", ".jar", "analyzer_1.0.0.jar"},
		{"autobuilder_", "2.0.0", ".jar", "autobuilder_2.0.0.jar"},
		{"rules_", "v3.0.0", "", "rules_v3.0.0"},
	}
	for _, tt := range tests {
		def := ArtifactDef{CachePrefix: tt.prefix, Version: tt.version, CacheSuffix: tt.suffix}
		got := def.CacheName()
		if got != tt.want {
			t.Errorf("CacheName() = %q, want %q", got, tt.want)
		}
	}
}

func TestKind(t *testing.T) {
	tests := []struct {
		name, want string
	}{
		{"Autobuilder", "autobuilder"},
		{"Analyzer", "analyzer"},
		{"Rules", "rules"},
	}
	for _, tt := range tests {
		def := ArtifactDef{Name: tt.name}
		got := def.Kind()
		if got != tt.want {
			t.Errorf("Kind() for %q = %q, want %q", tt.name, got, tt.want)
		}
	}
}

func TestIsBindVersion(t *testing.T) {
	def := ArtifactDef{BindVersion: "1.0.0", Version: "1.0.0"}
	if !def.IsBindVersion() {
		t.Error("IsBindVersion() should be true when versions match")
	}

	def.Version = "2.0.0"
	if def.IsBindVersion() {
		t.Error("IsBindVersion() should be false when versions differ")
	}
}

func TestWithVersion(t *testing.T) {
	original := ArtifactDef{Name: "Analyzer", Version: "1.0.0", BindVersion: "1.0.0"}
	modified := original.WithVersion("2.0.0")

	if modified.Version != "2.0.0" {
		t.Errorf("WithVersion().Version = %q, want %q", modified.Version, "2.0.0")
	}
	if original.Version != "1.0.0" {
		t.Error("WithVersion() must not mutate the original")
	}
	if modified.Name != "Analyzer" {
		t.Error("WithVersion() must preserve other fields")
	}
}

func TestArtifactByKind(t *testing.T) {
	origConfig := Config
	t.Cleanup(func() { Config = origConfig })

	Config.Analyzer.Version = "1.0.0"
	Config.Autobuilder.Version = "2.0.0"
	Config.Rules.Version = "v3.0.0"

	for _, kind := range []string{"autobuilder", "analyzer", "rules"} {
		def := ArtifactByKind(kind)
		if def.Kind() != kind {
			t.Errorf("ArtifactByKind(%q).Kind() = %q", kind, def.Kind())
		}
	}
}

func TestArtifactByKindPanics(t *testing.T) {
	defer func() {
		if r := recover(); r == nil {
			t.Error("ArtifactByKind with unknown kind should panic")
		}
	}()
	ArtifactByKind("unknown")
}
