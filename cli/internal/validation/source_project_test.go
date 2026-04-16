package validation

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func createFile(t *testing.T, path string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte{}, 0o644); err != nil {
		t.Fatal(err)
	}
}

func createDir(t *testing.T, path string) {
	t.Helper()
	if err := os.MkdirAll(path, 0o755); err != nil {
		t.Fatal(err)
	}
}

// --- detectLanguage ---

func TestDetectLanguage_AllMarkers(t *testing.T) {
	for _, lang := range supportedLanguages {
		for _, marker := range lang.Markers {
			t.Run(lang.Name+"/"+marker, func(t *testing.T) {
				dir := t.TempDir()
				path := filepath.Join(dir, marker)

				if strings.HasPrefix(marker, ".") {
					createDir(t, path)
				} else {
					createFile(t, path)
				}

				got := detectLanguage(dir)
				if got != lang.Name {
					t.Errorf("detectLanguage() = %q, want %q for marker %q", got, lang.Name, marker)
				}
			})
		}
	}
}

func TestDetectLanguage_NestedMarker(t *testing.T) {
	dir := t.TempDir()
	createFile(t, filepath.Join(dir, "backend", "pom.xml"))

	got := detectLanguage(dir)
	if got != "Java/Kotlin" {
		t.Errorf("detectLanguage() = %q, want %q for nested pom.xml", got, "Java/Kotlin")
	}
}

func TestDetectLanguage_DeeplyNestedWithinLimit(t *testing.T) {
	dir := t.TempDir()
	// depth 3 (a/b/c/pom.xml) — should be found
	createFile(t, filepath.Join(dir, "a", "b", "c", "pom.xml"))

	got := detectLanguage(dir)
	if got != "Java/Kotlin" {
		t.Errorf("detectLanguage() = %q, want %q at depth 3", got, "Java/Kotlin")
	}
}

func TestDetectLanguage_BeyondMaxDepth(t *testing.T) {
	dir := t.TempDir()
	// depth 4 (a/b/c/d/pom.xml) — beyond maxMarkerSearchDepth=3
	createFile(t, filepath.Join(dir, "a", "b", "c", "d", "pom.xml"))

	got := detectLanguage(dir)
	if got != "" {
		t.Errorf("detectLanguage() = %q, want empty for marker beyond max depth", got)
	}
}

func TestDetectLanguage_SkippedDirs(t *testing.T) {
	for skipped := range skippedDirs {
		t.Run(skipped, func(t *testing.T) {
			dir := t.TempDir()
			createFile(t, filepath.Join(dir, skipped, "pom.xml"))

			got := detectLanguage(dir)
			if got != "" {
				t.Errorf("detectLanguage() = %q, want empty for marker inside skipped dir %q", got, skipped)
			}
		})
	}
}

func TestDetectLanguage_EmptyDir(t *testing.T) {
	dir := t.TempDir()

	got := detectLanguage(dir)
	if got != "" {
		t.Errorf("detectLanguage() = %q, want empty for empty dir", got)
	}
}

func TestDetectLanguage_WindowsMarkers(t *testing.T) {
	windowsMarkers := []string{"gradlew.bat", "mvnw.cmd"}
	for _, marker := range windowsMarkers {
		t.Run(marker, func(t *testing.T) {
			dir := t.TempDir()
			createFile(t, filepath.Join(dir, marker))

			got := detectLanguage(dir)
			if got != "Java/Kotlin" {
				t.Errorf("detectLanguage() = %q, want %q for Windows marker %q", got, "Java/Kotlin", marker)
			}
		})
	}
}

// --- ValidateSourceProject ---

func TestValidateSourceProject_ValidProject(t *testing.T) {
	dir := t.TempDir()
	createFile(t, filepath.Join(dir, "build.gradle.kts"))

	if err := ValidateSourceProject(dir); err != nil {
		t.Errorf("unexpected error: %v", err)
	}
}

func TestValidateSourceProject_NestedMarker(t *testing.T) {
	dir := t.TempDir()
	createFile(t, filepath.Join(dir, "services", "api", "pom.xml"))

	if err := ValidateSourceProject(dir); err != nil {
		t.Errorf("unexpected error for nested marker: %v", err)
	}
}

func TestValidateSourceProject_ProjectModel(t *testing.T) {
	dir := t.TempDir()
	createFile(t, filepath.Join(dir, "project.yaml"))

	err := ValidateSourceProject(dir)
	if err == nil {
		t.Fatal("expected error for project model directory")
	}
	if !strings.Contains(err.Error(), "--project-model") {
		t.Errorf("error should suggest --project-model, got: %v", err)
	}
	if !strings.Contains(err.Error(), "compiled project model") {
		t.Errorf("error should mention compiled project model, got: %v", err)
	}
}

func TestValidateSourceProject_NoMarkers(t *testing.T) {
	dir := t.TempDir()

	err := ValidateSourceProject(dir)
	if err == nil {
		t.Fatal("expected error for directory without build markers")
	}
	if !strings.Contains(err.Error(), "no supported build files found") {
		t.Errorf("error should mention missing build files, got: %v", err)
	}
	if strings.Contains(err.Error(), "--project-model") {
		t.Errorf("error should not suggest --project-model for missing build files, got: %v", err)
	}
}

func TestValidateSourceProject_ProjectModelTakesPrecedence(t *testing.T) {
	dir := t.TempDir()
	createFile(t, filepath.Join(dir, "project.yaml"))
	createFile(t, filepath.Join(dir, "pom.xml"))

	err := ValidateSourceProject(dir)
	if err == nil {
		t.Fatal("expected error when project.yaml is present, even with pom.xml")
	}
	if !strings.Contains(err.Error(), "--project-model") {
		t.Errorf("error should suggest --project-model, got: %v", err)
	}
}

// --- ValidateSourceProjectForCompile ---

func TestValidateSourceProjectForCompile_ValidProject(t *testing.T) {
	dir := t.TempDir()
	createFile(t, filepath.Join(dir, "pom.xml"))

	if err := ValidateSourceProjectForCompile(dir); err != nil {
		t.Errorf("unexpected error: %v", err)
	}
}

func TestValidateSourceProjectForCompile_NestedMarker(t *testing.T) {
	dir := t.TempDir()
	createFile(t, filepath.Join(dir, "module-a", "build.gradle"))

	if err := ValidateSourceProjectForCompile(dir); err != nil {
		t.Errorf("unexpected error for nested marker: %v", err)
	}
}

func TestValidateSourceProjectForCompile_NoMarkers(t *testing.T) {
	dir := t.TempDir()

	err := ValidateSourceProjectForCompile(dir)
	if err == nil {
		t.Fatal("expected error for directory without build markers")
	}
	if !strings.Contains(err.Error(), "no supported build files found") {
		t.Errorf("error should mention missing build files, got: %v", err)
	}
	if strings.Contains(err.Error(), "--project-model") {
		t.Errorf("compile error should not suggest --project-model, got: %v", err)
	}
}

func TestValidateSourceProjectForCompile_IgnoresProjectYaml(t *testing.T) {
	dir := t.TempDir()
	createFile(t, filepath.Join(dir, "project.yaml"))
	createFile(t, filepath.Join(dir, "build.gradle.kts"))

	if err := ValidateSourceProjectForCompile(dir); err != nil {
		t.Errorf("unexpected error when build marker exists alongside project.yaml, got: %v", err)
	}
}
