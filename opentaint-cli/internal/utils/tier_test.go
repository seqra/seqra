package utils

import (
	"testing"

	"github.com/seqra/opentaint/v2/internal/globals"
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
		if tier.Name == TierCache {
			t.Error("bind version should not have a cache tier")
		}
	}
	hasInstall := false
	for _, tier := range tiers {
		if tier.Name == TierInstall {
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
	if tiers[0].Name != TierCache {
		t.Errorf("expected cache tier, got %q", tiers[0].Name)
	}
}

func TestJRETiers_DefaultVersion(t *testing.T) {
	origJava := globals.DefaultJavaVersion
	t.Cleanup(func() { globals.DefaultJavaVersion = origJava })
	globals.DefaultJavaVersion = 21

	tiers := JRETiers(21, "/tmp/cache/jre")

	for _, tier := range tiers {
		if tier.Name == TierCache {
			t.Error("default java version should not have a cache tier")
		}
	}
	hasInstall := false
	for _, tier := range tiers {
		if tier.Name == TierInstall {
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
	if tiers[0].Name != TierCache {
		t.Errorf("expected cache tier, got %q", tiers[0].Name)
	}
}

func TestCurrentTiers_FiltersStaleInstall(t *testing.T) {
	tiers := []Tier{
		{TierBundled, "/bundled"},
		{TierInstall, "/install"},
		{TierCache, "/cache"},
	}

	filtered := CurrentTiers(tiers, false)
	for _, tier := range filtered {
		if tier.Name == TierInstall {
			t.Error("stale install tier should be filtered out")
		}
	}
	if len(filtered) != 2 {
		t.Errorf("expected 2 tiers, got %d", len(filtered))
	}
}

func TestCurrentTiers_KeepsCurrentInstall(t *testing.T) {
	tiers := []Tier{
		{TierBundled, "/bundled"},
		{TierInstall, "/install"},
		{TierCache, "/cache"},
	}

	filtered := CurrentTiers(tiers, true)
	if len(filtered) != 3 {
		t.Errorf("expected 3 tiers (all kept), got %d", len(filtered))
	}
}
