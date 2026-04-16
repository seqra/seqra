package validation

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

// maxMarkerSearchDepth limits how deep the recursive marker search goes.
// Covers monorepo layouts (e.g. backend/app/pom.xml) without walking
// into deeply nested dependency or output directories.
const maxMarkerSearchDepth = 3

// skippedDirs are directory names skipped during recursive marker search.
// These are build outputs, caches, VCS internals, and dependency stores
// that should never contain project-level build markers.
var skippedDirs = map[string]bool{
	".git":         true,
	".svn":         true,
	".hg":          true,
	".gradle":      true,
	".idea":        true,
	".vscode":      true,
	"node_modules": true,
	"vendor":       true,
	"target":       true,
	"build":        true,
	"bin":          true,
	"obj":          true,
	"out":          true,
}

// LanguageMarkers defines the build-system markers for a supported language.
type LanguageMarkers struct {
	Name    string   // human-readable name (e.g. "Java/Kotlin")
	Markers []string // file or directory names to look for
}

// supportedLanguages lists all language detectors. Add new entries here
// to extend source project validation to additional languages.
var supportedLanguages = []LanguageMarkers{
	{
		Name: "Java/Kotlin",
		Markers: []string{
			// Maven
			"pom.xml",
			".mvn",
			"mvnw",
			"mvnw.cmd",
			// Gradle
			"build.gradle",
			"build.gradle.kts",
			"settings.gradle",
			"settings.gradle.kts",
			"gradlew",
			"gradlew.bat",
		},
	},
}

// markerToLanguage builds a lookup from marker name to language name.
func markerToLanguage() map[string]string {
	m := make(map[string]string)
	for _, lang := range supportedLanguages {
		for _, marker := range lang.Markers {
			m[marker] = lang.Name
		}
	}
	return m
}

// detectLanguages walks absProjectRoot up to maxMarkerSearchDepth looking for
// registered markers. Returns a deduplicated slice of detected language names.
func detectLanguages(absProjectRoot string) []string {
	lookup := markerToLanguage()
	rootDepth := strings.Count(filepath.Clean(absProjectRoot), string(filepath.Separator))
	totalLanguages := len(supportedLanguages)

	found := make(map[string]bool)
	_ = filepath.WalkDir(absProjectRoot, func(path string, d os.DirEntry, err error) error {
		if err != nil {
			return filepath.SkipDir
		}

		depth := strings.Count(path, string(filepath.Separator)) - rootDepth

		if d.IsDir() {
			if depth > maxMarkerSearchDepth {
				return filepath.SkipDir
			}
			if depth > 0 && skippedDirs[d.Name()] {
				return filepath.SkipDir
			}
		}

		if lang, ok := lookup[d.Name()]; ok {
			found[lang] = true
			if len(found) == totalLanguages {
				return filepath.SkipAll
			}
		}

		return nil
	})

	languages := make([]string, 0, len(found))
	// Preserve registration order from supportedLanguages
	for _, lang := range supportedLanguages {
		if found[lang.Name] {
			languages = append(languages, lang.Name)
		}
	}
	return languages
}

// allMarkerNames returns a human-readable comma-separated list of all markers
// across all supported languages, for use in error messages.
func allMarkerNames() string {
	var names []string
	for _, lang := range supportedLanguages {
		names = append(names, lang.Markers...)
	}
	return strings.Join(names, ", ")
}

// validateDirectoryExists checks that the given path exists and is a directory.
func validateDirectoryExists(absPath string) error {
	info, err := os.Stat(absPath)
	if os.IsNotExist(err) {
		return fmt.Errorf("Project directory does not exist: %s", absPath)
	}
	if err != nil {
		return fmt.Errorf("Cannot access project directory: %s", err)
	}
	if !info.IsDir() {
		return fmt.Errorf("Path is not a directory: %s", absPath)
	}
	return nil
}

// IsProjectModel returns true if absProjectRoot contains a project.yaml file,
// indicating it is a compiled project model rather than a source directory.
func IsProjectModel(absProjectRoot string) bool {
	_, err := os.Stat(filepath.Join(absProjectRoot, "project.yaml"))
	return err == nil
}

// ValidateSourceProject checks that absProjectRoot looks like a supported
// source project. It returns an error if the directory does not exist, if it
// contains project.yaml (a compiled project model), or if no build-system
// markers are found.
func ValidateSourceProject(absProjectRoot string) error {
	if err := validateDirectoryExists(absProjectRoot); err != nil {
		return err
	}

	if IsProjectModel(absProjectRoot) {
		return fmt.Errorf(
			"The path %s appears to be a compiled project model (contains project.yaml), not a source project",
			absProjectRoot,
		)
	}

	if langs := detectLanguages(absProjectRoot); len(langs) > 0 {
		return nil
	}

	return fmt.Errorf(
		"No supported build files found in %s\n  Expected one of: %s",
		absProjectRoot, allMarkerNames(),
	)
}

// ValidateSourceProjectForCompile checks that absProjectRoot looks like a
// supported source project by looking for build-system markers. Unlike
// ValidateSourceProject, it does not check for project.yaml since compile
// always operates on source directories.
func ValidateSourceProjectForCompile(absProjectRoot string) error {
	if err := validateDirectoryExists(absProjectRoot); err != nil {
		return err
	}

	if langs := detectLanguages(absProjectRoot); len(langs) > 0 {
		return nil
	}

	return fmt.Errorf(
		"No supported build files found in %s\n  Expected one of: %s",
		absProjectRoot, allMarkerNames(),
	)
}
