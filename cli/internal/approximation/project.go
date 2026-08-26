// Package approximation manages Gradle projects for dataflow models.
//
// Each project specifies the library versions that its models use.
// The scanned project does not supply these compile dependencies.
package approximation

import (
	"os"
	"path/filepath"
	"strings"
)

const (
	// apiJarName contains the annotations and support types for model projects.
	apiJarName = "opentaint-approximations-api.jar"
	// apiJarResourcePrefix must match API_JAR_RESOURCE_DIR in the core build.
	apiJarResourcePrefix = "opentaint-approximations-api/"

	libsDirName    = "libs"
	classesDirName = "classes"
	descriptorName = "approximation.yaml"

	stampFileName   = "stamp.json"
	gradleBuildFile = "build.gradle.kts"
)

// opentaintDirName contains the local build output.
const opentaintDirName = ".opentaint"

// skippedDirs contains generated and version-control directories.
var skippedDirs = map[string]bool{
	opentaintDirName: true,
	"build":          true,
	".gradle":        true,
	".git":           true,
}

// IsProject reports whether dir is a model project root.
func IsProject(dir string) bool {
	return isFile(filepath.Join(dir, gradleBuildFile))
}

// isBuiltOutput reports whether dir contains a model build.
func isBuiltOutput(dir string) bool {
	return isFile(filepath.Join(dir, descriptorName)) && isDir(filepath.Join(dir, classesDirName))
}

// BuildDir returns the path of the local build output.
func BuildDir(projectDir string) string {
	return filepath.Join(projectDir, opentaintDirName, "build")
}

// ClassesDir returns the class directory in a build output.
func ClassesDir(outputDir string) string {
	return filepath.Join(outputDir, classesDirName)
}

// apiJarPath returns the path of the project API jar.
func apiJarPath(projectDir string) string {
	return filepath.Join(projectDir, libsDirName, apiJarName)
}

// containsClassFiles reports whether dir contains compiled classes.
// It does not search generated directories.
func containsClassFiles(dir string) bool {
	found := false
	_ = filepath.WalkDir(dir, func(path string, entry os.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if entry.IsDir() {
			if path != dir && skippedDirs[entry.Name()] {
				return filepath.SkipDir
			}
			return nil
		}
		if strings.EqualFold(filepath.Ext(entry.Name()), ".class") {
			found = true
			return filepath.SkipAll
		}
		return nil
	})
	return found
}

// firstJavaSource returns one Java source file under dir.
func firstJavaSource(dir string) (string, bool) {
	var found string
	_ = filepath.WalkDir(dir, func(path string, entry os.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if entry.IsDir() {
			if path != dir && skippedDirs[entry.Name()] {
				return filepath.SkipDir
			}
			return nil
		}
		if strings.EqualFold(filepath.Ext(entry.Name()), ".java") {
			found = path
			return filepath.SkipAll
		}
		return nil
	})
	return found, found != ""
}

func isFile(path string) bool {
	info, err := os.Stat(path)
	return err == nil && !info.IsDir()
}

func isDir(path string) bool {
	info, err := os.Stat(path)
	return err == nil && info.IsDir()
}
