package approximation

import (
	"fmt"
	"os"
	"path/filepath"
	"sort"
)

// Resolve returns the class directories for one model path.
// The path can have one of these forms:
//
//  1. A model project.
//  2. A model build output.
//  3. A compiled class directory.
//  4. A directory tree that contains these items.
func Resolve(path string, builder Builder) ([]string, error) {
	info, err := os.Stat(path)
	if err != nil {
		return nil, fmt.Errorf("approximation path does not exist: %w", err)
	}
	if !info.IsDir() {
		return nil, fmt.Errorf("approximation path %s is not a directory", path)
	}

	classDirs, err := resolveDir(path, builder)
	if err != nil {
		return nil, err
	}
	if len(classDirs) == 0 {
		return nil, emptyPathError(path)
	}
	return classDirs, nil
}

func resolveDir(dir string, builder Builder) ([]string, error) {
	if IsProject(dir) {
		classes, err := ensureBuilt(dir, builder)
		if err != nil {
			return nil, err
		}
		return []string{classes}, nil
	}

	if isBuiltOutput(dir) {
		return []string{ClassesDir(dir)}, nil
	}

	if containsClassFiles(dir) {
		return []string{dir}, nil
	}

	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, fmt.Errorf("failed to read approximation directory %s: %w", dir, err)
	}
	sort.Slice(entries, func(i, j int) bool { return entries[i].Name() < entries[j].Name() })

	var classDirs []string
	for _, entry := range entries {
		if !entry.IsDir() || skippedDirs[entry.Name()] {
			continue
		}
		nested, err := resolveDir(filepath.Join(dir, entry.Name()), builder)
		if err != nil {
			return nil, err
		}
		classDirs = append(classDirs, nested...)
	}
	return classDirs, nil
}

// emptyPathError reports why a path does not contain usable models.
func emptyPathError(path string) error {
	if source, found := firstJavaSource(path); found {
		return fmt.Errorf(
			"%s holds approximation sources (e.g. %s) but no approximation project: nothing declares "+
				"the dependencies to compile the models against.\n"+
				"Create a project per model directory with 'opentaint approximation init <dir> "+
				"--dependency <group:artifact:version>' and move its sources under <dir>/src/main/java",
			path, source,
		)
	}
	return fmt.Errorf("%s contains no approximation project, build output or compiled classes", path)
}
