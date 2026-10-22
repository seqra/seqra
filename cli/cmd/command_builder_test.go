package cmd

import (
	"reflect"
	"testing"

	"github.com/spf13/cobra"
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

func TestAnalyzerBuildNativeCommandRoutesGoModels(t *testing.T) {
	cmd := NewAnalyzerBuilder().
		SetProject("/project/project.yaml").
		SetOutputDir("/output").
		AddGoModel("/models/stdlib").
		BuildNativeCommand()

	if !containsFlagPair(cmd, "--go-models", "/models/stdlib") {
		t.Fatalf("Go model path not passed to analyzer; command was %v", cmd)
	}
}

func TestGoModelsAvailableOnTestCommands(t *testing.T) {
	commands := map[string]*cobra.Command{
		"rule tests":          testRuleRunCmd,
		"approximation tests": testApproximationRunCmd,
	}
	for name, command := range commands {
		if command.Flags().Lookup("go-models") == nil {
			t.Errorf("%s do not expose --go-models", name)
		}
	}
}

// containsFlagPair reports whether args contains the adjacent pair `flag value`.
func containsFlagPair(args []string, flag, value string) bool {
	for i := 0; i+1 < len(args); i++ {
		if args[i] == flag && args[i+1] == value {
			return true
		}
	}
	return false
}

// Dependencies must reach the autobuilder via its --dependency option so they land
// in project.yaml's dependencies field (resolvable library bytecode), distinct from
// --cp module classes. Folding them into --cp would misfile them as project classes.
func TestAutobuilderBuildNativeCommandRoutesDependenciesToDependencyFlag(t *testing.T) {
	cmd := NewAutobuilderBuilder().
		SetProjectRootDir("/src").
		SetResultDir("/out").
		SetBuildType("cp").
		AddClasspath("/app.jar").
		AddDependency("/dep.jar").
		AddPackage("com.example").
		BuildNativeCommand()

	if !containsFlagPair(cmd, "--cp", "/app.jar") {
		t.Fatalf("classpath /app.jar not passed as --cp; command was %v", cmd)
	}
	if !containsFlagPair(cmd, "--dependency", "/dep.jar") {
		t.Fatalf("dependency /dep.jar not routed to --dependency; command was %v", cmd)
	}
	if containsFlagPair(cmd, "--cp", "/dep.jar") {
		t.Fatalf("dependency /dep.jar must not be folded into --cp; command was %v", cmd)
	}
	if !containsFlagPair(cmd, "--pkg", "com.example") {
		t.Fatalf("package com.example not passed as --pkg; command was %v", cmd)
	}
}
