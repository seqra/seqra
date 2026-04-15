package utils

import (
	"testing"
)

func TestProjectPathSlugHash(t *testing.T) {
	t.Run("deterministic output", func(t *testing.T) {
		result1 := ProjectPathSlugHash("/Users/me/my-project")
		result2 := ProjectPathSlugHash("/Users/me/my-project")
		if result1 != result2 {
			t.Errorf("expected deterministic output, got %q and %q", result1, result2)
		}
	})

	t.Run("format is slug-hash", func(t *testing.T) {
		result := ProjectPathSlugHash("/Users/me/my-project")
		// Should be lowercase slug + 8 hex chars
		if len(result) == 0 {
			t.Fatal("expected non-empty result")
		}
		// The slug part should contain "users-me-my-project"
		if result[:len(result)-9] != "users-me-my-project" {
			t.Errorf("unexpected slug portion: %q", result)
		}
		// Should end with dash + 8 hex chars
		if result[len(result)-9] != '-' {
			t.Errorf("expected dash before hash, got %q", result)
		}
	})

	t.Run("different paths produce different hashes", func(t *testing.T) {
		r1 := ProjectPathSlugHash("/Users/me/project-a")
		r2 := ProjectPathSlugHash("/Users/me/project-b")
		if r1 == r2 {
			t.Errorf("expected different hashes for different paths, both got %q", r1)
		}
	})

	t.Run("special characters replaced", func(t *testing.T) {
		result := ProjectPathSlugHash("/tmp/my project (copy)")
		for _, c := range result {
			if c != '-' && !((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
				t.Errorf("unexpected character %q in slug-hash %q", string(c), result)
			}
		}
	})

	t.Run("leading and trailing dashes trimmed", func(t *testing.T) {
		result := ProjectPathSlugHash("/tmp/project")
		if result[0] == '-' {
			t.Errorf("slug-hash should not start with dash: %q", result)
		}
		if result[len(result)-1] == '-' {
			t.Errorf("slug-hash should not end with dash: %q", result)
		}
	})
}
