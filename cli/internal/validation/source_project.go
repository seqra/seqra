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

// markerSet builds a lookup set from all registered language markers.
func markerSet() map[string]bool {
	set := make(map[string]bool)
	for _, lang := range supportedLanguages {
		for _, m := range lang.Markers {
			set[m] = true
		}
	}
	return set
}

// detectLanguage walks absProjectRoot up to maxMarkerSearchDepth looking for
// any registered marker. Returns the language name on first match, or "" if
// none found.
func detectLanguage(absProjectRoot string) string {
	markers := markerSet()
	rootDepth := strings.Count(filepath.Clean(absProjectRoot), string(filepath.Separator))

	found := ""
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

		if markers[d.Name()] {
			// Resolve which language this marker belongs to
			for _, lang := range supportedLanguages {
				for _, m := range lang.Markers {
					if m == d.Name() {
						found = lang.Name
						return filepath.SkipAll
					}
				}
			}
		}

		return nil
	})

	return found
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

// ValidateSourceProject checks that absProjectRoot looks like a supported
// source project. If it contains project.yaml (a compiled project model),
// it returns an error suggesting --project-model. If no build-system markers
// are found, it returns an error listing the expected files.
func ValidateSourceProject(absProjectRoot string) error {
	projectYaml := filepath.Join(absProjectRoot, "project.yaml")
	if _, err := os.Stat(projectYaml); err == nil {
		return fmt.Errorf(
			"the path %s appears to be a compiled project model (contains project.yaml), not a source project.\n"+
				"  Use --project-model to scan it:\n"+
				"    opentaint scan --project-model %s",
			absProjectRoot, absProjectRoot,
		)
	}

	if lang := detectLanguage(absProjectRoot); lang != "" {
		return nil
	}

	return fmt.Errorf(
		"no supported build files found in %s.\n"+
			"  Expected one of: %s",
		absProjectRoot, allMarkerNames(),
	)
}

// ValidateSourceProjectForCompile checks that absProjectRoot looks like a
// supported source project by looking for build-system markers. Unlike
// ValidateSourceProject, it does not check for project.yaml since compile
// always operates on source directories.
func ValidateSourceProjectForCompile(absProjectRoot string) error {
	if lang := detectLanguage(absProjectRoot); lang != "" {
		return nil
	}

	return fmt.Errorf(
		"no supported build files found in %s.\n"+
			"  Expected one of: %s",
		absProjectRoot, allMarkerNames(),
	)
}
