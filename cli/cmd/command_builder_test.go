package cmd

import (
	"reflect"
	"testing"
)

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
