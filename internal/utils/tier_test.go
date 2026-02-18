package utils

import (
	"testing"

	"github.com/seqra/seqra/v2/internal/globals"
)

func TestArtifactTiers_BindVersion(t *testing.T) {
	origAutobuilder := globals.AutobuilderBindVersion
	t.Cleanup(func() { globals.AutobuilderBindVersion = origAutobuilder })
	globals.AutobuilderBindVersion = "1.0.0"
	globals.Config.Autobuilder.Version = "1.0.0"

	def := globals.ArtifactByKind("autobuilder")

	tiers, err := ArtifactTiers(def)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	for _, tier := range tiers {
		if tier.Name == "cache" {
			t.Error("bind version should not have a cache tier")
		}
	}
	// Should have at least install tier
	hasInstall := false
	for _, tier := range tiers {
		if tier.Name == "install" {
			hasInstall = true
		}
	}
	if !hasInstall {
		t.Error("bind version should have an install tier")
	}
}

func TestArtifactTiers_NonBindVersion(t *testing.T) {
	origAutobuilder := globals.AutobuilderBindVersion
	t.Cleanup(func() { globals.AutobuilderBindVersion = origAutobuilder })
	globals.AutobuilderBindVersion = "1.0.0"
	globals.Config.Autobuilder.Version = "2.0.0"

	def := globals.ArtifactByKind("autobuilder")

	tiers, err := ArtifactTiers(def)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(tiers) != 1 {
		t.Fatalf("expected 1 tier, got %d", len(tiers))
	}
	if tiers[0].Name != "cache" {
		t.Errorf("expected cache tier, got %q", tiers[0].Name)
	}
}

func TestJRETiers_DefaultVersion(t *testing.T) {
	origJava := globals.DefaultJavaVersion
	t.Cleanup(func() { globals.DefaultJavaVersion = origJava })
	globals.DefaultJavaVersion = 21

	tiers := JRETiers(21, "/tmp/cache/jre")

	for _, tier := range tiers {
		if tier.Name == "cache" {
			t.Error("default java version should not have a cache tier")
		}
	}
	hasInstall := false
	for _, tier := range tiers {
		if tier.Name == "install" {
			hasInstall = true
		}
	}
	if !hasInstall {
		t.Error("default java version should have an install tier")
	}
}

func TestJRETiers_NonDefaultVersion(t *testing.T) {
	origJava := globals.DefaultJavaVersion
	t.Cleanup(func() { globals.DefaultJavaVersion = origJava })
	globals.DefaultJavaVersion = 21

	tiers := JRETiers(17, "/tmp/cache/jre")

	if len(tiers) != 1 {
		t.Fatalf("expected 1 tier, got %d", len(tiers))
	}
	if tiers[0].Name != "cache" {
		t.Errorf("expected cache tier, got %q", tiers[0].Name)
	}
}
