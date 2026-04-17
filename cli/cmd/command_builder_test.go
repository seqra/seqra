package cmd

import (
	"reflect"
	"testing"
)

func TestHasNestedKey(t *testing.T) {
	settings := map[string]any{
		"log": map[string]any{
			"verbosity": "debug",
			"color":     "auto",
		},
		"quiet": true,
	}
	tests := []struct {
		name string
		path []string
		want bool
	}{
		{name: "top-level key present", path: []string{"quiet"}, want: true},
		{name: "nested key present", path: []string{"log", "verbosity"}, want: true},
		{name: "nested key missing", path: []string{"log", "missing"}, want: false},
		{name: "parent exists but not a map", path: []string{"quiet", "sub"}, want: false},
		{name: "empty path", path: []string{}, want: false},
		{name: "missing top-level", path: []string{"other"}, want: false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := hasNestedKey(settings, tt.path); got != tt.want {
				t.Fatalf("hasNestedKey(%v) = %t, want %t", tt.path, got, tt.want)
			}
		})
	}
}

func TestAppendVerbosityFlag(t *testing.T) {
	tests := []struct {
		name  string
		debug bool
		want  []string
	}{
		{name: "debug off emits info", debug: false, want: []string{"--verbosity=info"}},
		{name: "debug on emits debug", debug: true, want: []string{"--verbosity=debug"}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			b := &BaseCommandBuilder{debug: tt.debug}
			got := b.appendVerbosityFlag(nil)
			if !reflect.DeepEqual(got, tt.want) {
				t.Fatalf("appendVerbosityFlag(debug=%t) = %v, want %v", tt.debug, got, tt.want)
			}
		})
	}
}

func TestAppendVerbosityFlagPreservesExistingFlags(t *testing.T) {
	b := &BaseCommandBuilder{debug: true}
	got := b.appendVerbosityFlag([]string{"--project", "foo.yaml"})
	want := []string{"--project", "foo.yaml", "--verbosity=debug"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("appendVerbosityFlag did not preserve prior flags: got %v, want %v", got, want)
	}
}
