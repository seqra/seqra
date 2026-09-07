package approximation

import (
	"fmt"
	"os"
	"path/filepath"
)

// ResolveAll returns the class directories for every model path, in the order the paths
// were given. A directory reached more than once contributes once, and the directories
// must agree about every class they share: which copy an analysis reads is not defined, so
// two different copies of one class would leave the result of the scan unpinned.
func ResolveAll(paths []string, builder Builder) ([]string, error) {
	var classDirs []string
	resolved := make(map[string]bool)
	for _, path := range paths {
		dirs, err := Resolve(path, builder)
		if err != nil {
			return nil, err
		}
		for _, dir := range dirs {
			dir = filepath.Clean(dir)
			if resolved[dir] {
				continue
			}
			resolved[dir] = true
			classDirs = append(classDirs, dir)
		}
	}
	if err := checkClasspathAgrees(classDirs); err != nil {
		return nil, err
	}
	return classDirs, nil
}

// checkClasspathAgrees rejects class directories that offer one class with different
// content. Identical copies are the ordinary result of overlapping paths and are left
// alone: what has to hold is that the same class is read whichever copy is picked.
func checkClasspathAgrees(classDirs []string) error {
	offeredBy := make(map[string]string)
	for _, dir := range classDirs {
		classes, err := classFilesIn(dir)
		if err != nil {
			return err
		}
		for _, class := range classes {
			previous, offered := offeredBy[class]
			if !offered {
				offeredBy[class] = dir
				continue
			}
			same, err := sameContent(
				filepath.Join(previous, filepath.FromSlash(class)),
				filepath.Join(dir, filepath.FromSlash(class)))
			if err != nil {
				return err
			}
			if !same {
				return fmt.Errorf(
					"%s is offered with different content by %s and by %s; which one an analysis "+
						"reads is not defined.\nDrop one of the copies, or pass only one of these "+
						"approximation paths",
					class, previous, dir)
			}
		}
	}
	return nil
}

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

	// A directory is a class directory only when nothing below it still needs building.
	// Without that condition a tree holding both a project and compiled classes reads as
	// one class directory, and the project's models never reach the analysis.
	facts := scanSubtree(dir)
	if facts.classFiles && !facts.unitSource {
		return []string{dir}, nil
	}

	// os.ReadDir returns entries sorted by name, so the answer does not depend on the
	// order the filesystem happens to hand them back.
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, fmt.Errorf("failed to read approximation directory %s: %w", dir, err)
	}

	// Descending past classes this directory holds itself would leave them unused.
	if classFile, found := directClassFile(entries); found {
		return nil, ambiguousDirError(dir, classFile)
	}

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

// ambiguousDirError reports a directory that is neither a class directory nor a
// directory of approximation paths.
func ambiguousDirError(dir, classFile string) error {
	return fmt.Errorf(
		"%s holds compiled classes of its own (e.g. %s) and an approximation project or build "+
			"output below it, so it is neither a class directory nor a directory of them.\n"+
			"Move the classes into a directory of their own, or pass each approximation path to "+
			"--dataflow-approximations separately",
		dir, classFile)
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
