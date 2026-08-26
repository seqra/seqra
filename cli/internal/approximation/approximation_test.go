package approximation

import (
	"os"
	"path/filepath"
	"sort"
	"strings"
	"testing"

	"gopkg.in/yaml.v2"
)

// fakeBuilder records builds and writes a test project model.
type fakeBuilder struct {
	t        *testing.T
	compiled []string
}

func newFakeBuilder(t *testing.T) *fakeBuilder {
	return &fakeBuilder{t: t}
}

// Prepare writes the same API content for each call.
func (b *fakeBuilder) Prepare(projectDir string) error {
	writeFile(b.t, apiJarPath(projectDir), "api-jar-bytes")
	return nil
}

func (b *fakeBuilder) Compile(projectDir, projectModelDir string) error {
	b.compiled = append(b.compiled, projectDir)
	writeModuleClasses(b.t, projectModelDir, map[string][]string{"c0_main": {"Model"}})
	return nil
}

// writeModuleClasses writes classes and a project descriptor for tests.
func writeModuleClasses(t *testing.T, projectModelDir string, modules map[string][]string) {
	t.Helper()
	moduleDirs := make([]string, 0, len(modules))
	for module, classes := range modules {
		for _, class := range classes {
			writeFile(t, filepath.Join(projectModelDir, "classes", module, "com", "example", class+".class"), "bytecode")
		}
		moduleDirs = append(moduleDirs, module)
	}
	sort.Strings(moduleDirs)

	config := "javaProjects:\n  - sourceRoot: /project\n    modules:\n      - moduleSourceRoot: /project\n        moduleClasses:\n"
	for _, module := range moduleDirs {
		config += "          - classes/" + module + "\n"
	}
	writeFile(t, filepath.Join(projectModelDir, "project.yaml"), config)
}

func writeFile(t *testing.T, path, content string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}
}

// newProject writes a model project for tests.
func newProject(t *testing.T, dir string) string {
	t.Helper()
	writeFile(t, filepath.Join(dir, "build.gradle.kts"), "dependencies {\n    compileOnly(\"com.foo:bar:1.0\")\n}\n")
	writeFile(t, filepath.Join(dir, "src", "main", "java", "com", "example", "Model.java"), "package com.example;\n")
	return dir
}

func TestResolveBuildsProject(t *testing.T) {
	project := newProject(t, t.TempDir())
	builder := newFakeBuilder(t)

	classDirs, err := Resolve(project, builder)
	if err != nil {
		t.Fatalf("Resolve: %v", err)
	}

	want := ClassesDir(BuildDir(project))
	if len(classDirs) != 1 || classDirs[0] != want {
		t.Fatalf("Resolve() = %v, want [%s]", classDirs, want)
	}
	if len(builder.compiled) != 1 {
		t.Fatalf("expected one build, got %v", builder.compiled)
	}
	if _, err := os.Stat(filepath.Join(want, "com", "example", "Model.class")); err != nil {
		t.Errorf("compiled model not collected: %v", err)
	}
	if _, err := os.Stat(filepath.Join(BuildDir(project), descriptorName)); err != nil {
		t.Errorf("descriptor not written: %v", err)
	}
}

func TestResolveReusesBuildUntilSourcesChange(t *testing.T) {
	project := newProject(t, t.TempDir())
	builder := newFakeBuilder(t)

	if _, err := Resolve(project, builder); err != nil {
		t.Fatalf("first Resolve: %v", err)
	}
	if _, err := Resolve(project, builder); err != nil {
		t.Fatalf("second Resolve: %v", err)
	}
	if len(builder.compiled) != 1 {
		t.Fatalf("unchanged sources rebuilt: %v", builder.compiled)
	}

	writeFile(t, filepath.Join(project, "src", "main", "java", "com", "example", "Model.java"), "package com.example;\n// edited\n")
	if _, err := Resolve(project, builder); err != nil {
		t.Fatalf("third Resolve: %v", err)
	}
	if len(builder.compiled) != 2 {
		t.Fatalf("edited model not rebuilt: %v", builder.compiled)
	}
}

func TestResolveRebuildsWhenDependencyPinChanges(t *testing.T) {
	project := newProject(t, t.TempDir())
	builder := newFakeBuilder(t)

	if _, err := Resolve(project, builder); err != nil {
		t.Fatalf("first Resolve: %v", err)
	}
	writeFile(t, filepath.Join(project, "build.gradle.kts"), "dependencies {\n    compileOnly(\"com.foo:bar:2.0\")\n}\n")
	if _, err := Resolve(project, builder); err != nil {
		t.Fatalf("second Resolve: %v", err)
	}
	if len(builder.compiled) != 2 {
		t.Fatalf("changed dependency pin not rebuilt: %v", builder.compiled)
	}
}

func TestResolveBuiltOutput(t *testing.T) {
	dir := t.TempDir()
	writeFile(t, filepath.Join(dir, descriptorName), "sourceProject: \"/somewhere\"\n")
	writeFile(t, filepath.Join(dir, classesDirName, "com", "example", "Model.class"), "bytecode")

	builder := newFakeBuilder(t)
	classDirs, err := Resolve(dir, builder)
	if err != nil {
		t.Fatalf("Resolve: %v", err)
	}
	if len(classDirs) != 1 || classDirs[0] != ClassesDir(dir) {
		t.Fatalf("Resolve() = %v, want [%s]", classDirs, ClassesDir(dir))
	}
	if len(builder.compiled) != 0 {
		t.Errorf("built output should not be rebuilt, got %v", builder.compiled)
	}
}

func TestResolvePrecompiledClassDirectory(t *testing.T) {
	dir := t.TempDir()
	writeFile(t, filepath.Join(dir, "com", "example", "Model.class"), "bytecode")

	classDirs, err := Resolve(dir, newFakeBuilder(t))
	if err != nil {
		t.Fatalf("Resolve: %v", err)
	}
	if len(classDirs) != 1 || classDirs[0] != dir {
		t.Fatalf("Resolve() = %v, want [%s]", classDirs, dir)
	}
}

func TestResolveTreeOfProjects(t *testing.T) {
	root := t.TempDir()
	newProject(t, filepath.Join(root, "batch-a"))
	newProject(t, filepath.Join(root, "batch-b"))

	builder := newFakeBuilder(t)
	classDirs, err := Resolve(root, builder)
	if err != nil {
		t.Fatalf("Resolve: %v", err)
	}
	if len(classDirs) != 2 {
		t.Fatalf("Resolve() = %v, want both batches", classDirs)
	}
	if len(builder.compiled) != 2 {
		t.Fatalf("expected both projects built, got %v", builder.compiled)
	}

	// The second call must ignore the nested build outputs.
	again, err := Resolve(root, builder)
	if err != nil {
		t.Fatalf("second Resolve: %v", err)
	}
	if len(again) != 2 {
		t.Fatalf("second Resolve() = %v, want both batches", again)
	}
	if len(builder.compiled) != 2 {
		t.Fatalf("unchanged projects rebuilt: %v", builder.compiled)
	}
}

func TestResolveLooseSourcesReportsMigration(t *testing.T) {
	dir := t.TempDir()
	writeFile(t, filepath.Join(dir, "com", "example", "Model.java"), "package com.example;\n")

	_, err := Resolve(dir, newFakeBuilder(t))
	if err == nil {
		t.Fatal("expected loose .java sources to be rejected")
	}
	if !strings.Contains(err.Error(), "approximation init") {
		t.Errorf("error should point at the migration command, got: %v", err)
	}
}

func TestResolveEmptyDirectory(t *testing.T) {
	_, err := Resolve(t.TempDir(), newFakeBuilder(t))
	if err == nil {
		t.Fatal("expected an empty directory to be rejected")
	}
}

func TestFailedBuildLeavesTheProjectBuildable(t *testing.T) {
	project := newProject(t, t.TempDir())

	// A failed build must not block the next build.
	if err := Build(project, BuildDir(project), &collidingBuilder{fakeBuilder{t: t}}); err == nil {
		t.Fatal("expected the colliding build to fail")
	}
	if err := Build(project, BuildDir(project), newFakeBuilder(t)); err != nil {
		t.Fatalf("rebuild after a failed build: %v", err)
	}
}

func TestFailedRebuildKeepsThePreviousBuild(t *testing.T) {
	project := newProject(t, t.TempDir())
	output := BuildDir(project)

	if err := Build(project, output, newFakeBuilder(t)); err != nil {
		t.Fatalf("first build: %v", err)
	}
	if err := Build(project, output, &collidingBuilder{fakeBuilder{t: t}}); err == nil {
		t.Fatal("expected the colliding build to fail")
	}

	// The previous models must remain available.
	if _, err := os.Stat(filepath.Join(ClassesDir(output), "com", "example", "Model.class")); err != nil {
		t.Errorf("a failed rebuild destroyed the previous build: %v", err)
	}
	if !isBuiltOutput(output) {
		t.Error("a failed rebuild left the output unusable")
	}
}

func TestBuildRefusesToOverwriteUnrelatedDirectory(t *testing.T) {
	project := newProject(t, t.TempDir())
	output := t.TempDir()
	precious := filepath.Join(output, "notes.txt")
	writeFile(t, precious, "important user data")

	// The build must not replace an unrelated directory.
	err := Build(project, output, newFakeBuilder(t))
	if err == nil {
		t.Fatal("expected a build into an unrelated directory to be refused")
	}
	if !strings.Contains(err.Error(), "not an approximation build output") {
		t.Errorf("unexpected error: %v", err)
	}
	if content, readErr := os.ReadFile(precious); readErr != nil || string(content) != "important user data" {
		t.Errorf("existing content was destroyed: content=%q err=%v", content, readErr)
	}
}

func TestBuildAcceptsEmptyAndPreviousOutputDirectories(t *testing.T) {
	project := newProject(t, t.TempDir())
	output := t.TempDir() // exists and is empty

	if err := Build(project, output, newFakeBuilder(t)); err != nil {
		t.Fatalf("build into an empty directory: %v", err)
	}
	// A new build can replace the first build.
	if err := Build(project, output, newFakeBuilder(t)); err != nil {
		t.Fatalf("rebuild over a previous build output: %v", err)
	}
}

func TestBuildWritesReadableDescriptor(t *testing.T) {
	project := newProject(t, t.TempDir())
	output := filepath.Join(t.TempDir(), "out")

	if err := Build(project, output, newFakeBuilder(t)); err != nil {
		t.Fatalf("Build: %v", err)
	}

	raw, err := os.ReadFile(filepath.Join(output, descriptorName))
	if err != nil {
		t.Fatal(err)
	}
	var parsed descriptor
	if err := yaml.Unmarshal(raw, &parsed); err != nil {
		t.Fatalf("descriptor is not valid YAML: %v\n%s", err, raw)
	}
	if parsed.SourceProject != project {
		t.Errorf("sourceProject = %q, want %q", parsed.SourceProject, project)
	}
	if parsed.Dependencies == nil {
		t.Error("dependencies should be an empty list, not null")
	}
}

func TestBuildRejectsCollidingModelClasses(t *testing.T) {
	project := newProject(t, t.TempDir())
	builder := &collidingBuilder{fakeBuilder{t: t}}

	err := Build(project, BuildDir(project), builder)
	if err == nil {
		t.Fatal("expected colliding approximation classes to fail the build")
	}
	if !strings.Contains(err.Error(), "exactly one target class") {
		t.Errorf("unexpected error: %v", err)
	}
}

// collidingBuilder writes the same class from two modules.
type collidingBuilder struct{ fakeBuilder }

func (b *collidingBuilder) Compile(projectDir, projectModelDir string) error {
	writeModuleClasses(b.t, projectModelDir, map[string][]string{"c0_main": {"Model"}, "c1_main": {"Model"}})
	return nil
}
