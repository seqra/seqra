package approximation

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"syscall"

	"github.com/seqra/opentaint/internal/utils"
	"github.com/seqra/opentaint/internal/utils/project"
)

// Builder compiles a model project.
type Builder interface {
	// Fingerprint identifies the compiler. A build is reused only for the compiler
	// that produced it.
	Fingerprint() (string, error)
	// Prepare updates local build inputs before the source stamp is calculated.
	Prepare(projectDir string) error
	// Compile writes a project model to projectModelDir.
	Compile(projectDir, projectModelDir string) error
}

// ensureBuilt returns current compiled models for a project.
func ensureBuilt(projectDir string, builder Builder) (string, error) {
	inputs, err := prepare(projectDir, builder)
	if err != nil {
		return "", err
	}

	outputDir := BuildDir(projectDir)
	if upToDate(outputDir, inputs) {
		return ClassesDir(outputDir), nil
	}
	if err := build(projectDir, outputDir, inputs, builder); err != nil {
		return "", err
	}
	return ClassesDir(outputDir), nil
}

// Build compiles a model project into outputDir.
func Build(projectDir, outputDir string, builder Builder) error {
	inputs, err := prepare(projectDir, builder)
	if err != nil {
		return err
	}
	return build(projectDir, outputDir, inputs, builder)
}

// prepare updates build inputs and returns the stamp identifying them.
func prepare(projectDir string, builder Builder) (stamp, error) {
	toolchain, err := builder.Fingerprint()
	if err != nil {
		return stamp{}, err
	}
	if err := builder.Prepare(projectDir); err != nil {
		return stamp{}, err
	}
	sources, err := computeSourceDigest(projectDir)
	if err != nil {
		return stamp{}, err
	}
	return stamp{Sources: sources, Toolchain: toolchain}, nil
}

// stagingDirName holds the directories builds assemble in. It sits beside the output so
// that publishing is a single rename, and it is on the skip list so that a directory a
// killed build left behind is never mistaken for compiled models.
const stagingDirName = ".opentaint-staging"

func stagingRoot(outputDir string) string {
	return filepath.Join(filepath.Dir(outputDir), stagingDirName)
}

// stagingDir names this process's staging directory for one output. The process id keeps
// concurrent builders of one output out of each other's staging directory: a shared one
// lets a build publish a mixture of two compiles, and record it as up to date.
func stagingDir(outputDir string) string {
	return filepath.Join(stagingRoot(outputDir),
		fmt.Sprintf("%s-%d", filepath.Base(outputDir), os.Getpid()))
}

// asideDir names the superseded output while the new one is renamed into place.
func asideDir(outputDir string) string {
	return filepath.Join(stagingRoot(outputDir),
		fmt.Sprintf("%s-superseded-%d", filepath.Base(outputDir), os.Getpid()))
}

// sweepStaging reclaims staging directories left behind by builds that are no longer
// running. A directory is removed only when its owner is known to be gone, so a build in
// progress is never disturbed; being wrong the other way costs a directory that stays
// until its process id comes round again.
func sweepStaging(outputDir string) {
	root := stagingRoot(outputDir)
	entries, err := os.ReadDir(root)
	if err != nil {
		return
	}
	prefix := filepath.Base(outputDir) + "-"
	for _, entry := range entries {
		if !entry.IsDir() || !strings.HasPrefix(entry.Name(), prefix) {
			continue
		}
		owner, named := stagingOwner(entry.Name())
		if !named || owner == os.Getpid() || processRunning(owner) {
			continue
		}
		_ = os.RemoveAll(filepath.Join(root, entry.Name()))
	}
}

// stagingOwner reads the process id a staging directory name ends with.
func stagingOwner(name string) (int, bool) {
	separator := strings.LastIndex(name, "-")
	if separator < 0 {
		return 0, false
	}
	owner, err := strconv.Atoi(name[separator+1:])
	return owner, err == nil
}

// processRunning reports whether a process is still running. It errs towards "running":
// a staging directory is reclaimed only when its owner is known to be gone.
func processRunning(pid int) bool {
	if runtime.GOOS == "windows" {
		return true
	}
	process, err := os.FindProcess(pid)
	if err != nil {
		return true
	}
	// A process that exists but belongs to someone else answers with EPERM.
	err = process.Signal(syscall.Signal(0))
	return err == nil || errors.Is(err, syscall.EPERM)
}

func build(projectDir, outputDir string, inputs stamp, builder Builder) error {
	if err := checkOutputDir(outputDir); err != nil {
		return err
	}
	sweepStaging(outputDir)

	buildTempDir, err := os.MkdirTemp("", "opentaint-approximation-build-*")
	if err != nil {
		return fmt.Errorf("failed to create temp directory for approximation build: %w", err)
	}
	defer func() { _ = os.RemoveAll(buildTempDir) }()

	// A missing output shows that the compiler did not produce a model.
	projectModelDir := filepath.Join(buildTempDir, "model")

	if err := builder.Compile(projectDir, projectModelDir); err != nil {
		return err
	}

	// Assemble the new output before it replaces the current output.
	staging := stagingDir(outputDir)
	defer func() { _ = os.RemoveAll(staging) }()
	if err := os.RemoveAll(staging); err != nil {
		return fmt.Errorf("failed to clear %s: %w", staging, err)
	}
	if err := os.MkdirAll(ClassesDir(staging), 0o755); err != nil {
		return fmt.Errorf("failed to create approximation build output %s: %w", staging, err)
	}

	dependencies, err := collectClasses(projectModelDir, ClassesDir(staging))
	if err != nil {
		return err
	}
	if !containsClassFiles(ClassesDir(staging)) {
		return fmt.Errorf("approximation project %s compiled to no classes; it has no models under src/main/java", projectDir)
	}

	if err := writeDescriptor(staging, descriptor{
		SourceProject: relativeSourceProject(outputDir, projectDir),
		Dependencies:  dependencies,
	}); err != nil {
		return err
	}
	if err := writeStamp(staging, inputs); err != nil {
		return err
	}

	if err := replaceDir(staging, outputDir); err != nil {
		return err
	}

	// Leave nothing beside the output once no build is using it.
	_ = os.Remove(stagingRoot(outputDir))
	return nil
}

// checkOutputDir prevents replacement of an unrelated directory.
func checkOutputDir(outputDir string) error {
	info, err := os.Stat(outputDir)
	if err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return fmt.Errorf("failed to access output directory %s: %w", outputDir, err)
	}
	if !info.IsDir() {
		return fmt.Errorf("output path %s is not a directory", outputDir)
	}
	if isBuiltOutput(outputDir) {
		return nil
	}

	empty, err := isEmptyDir(outputDir)
	if err != nil {
		return err
	}
	if !empty {
		return fmt.Errorf(
			"output directory %s already exists and is not an approximation build output; "+
				"remove it or choose another --output path", outputDir)
	}
	return nil
}

// replaceDir replaces the current output with a complete build.
// Both moves are single renames, so anyone reading the output directory sees the previous
// build, the new build, or nothing at all, and never a directory that is half of each.
func replaceDir(staging, outputDir string) error {
	if err := os.MkdirAll(filepath.Dir(outputDir), 0o755); err != nil {
		return fmt.Errorf("failed to create %s: %w", filepath.Dir(outputDir), err)
	}

	aside := asideDir(outputDir)
	if err := os.RemoveAll(aside); err != nil {
		return fmt.Errorf("failed to clear %s: %w", aside, err)
	}
	superseded := isDir(outputDir)
	if superseded {
		if err := os.Rename(outputDir, aside); err != nil {
			return fmt.Errorf("failed to move the previous build out of %s: %w", outputDir, err)
		}
	}

	if err := os.Rename(staging, outputDir); err != nil {
		// Put the previous build back rather than leave the output missing.
		if superseded {
			_ = os.Rename(aside, outputDir)
		}
		return fmt.Errorf("failed to move the compiled models into %s: %w", outputDir, err)
	}

	if superseded {
		if err := os.RemoveAll(aside); err != nil {
			return fmt.Errorf("failed to remove the previous build %s: %w", aside, err)
		}
	}
	return nil
}

func isEmptyDir(dir string) (bool, error) {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return false, fmt.Errorf("failed to read output directory %s: %w", dir, err)
	}
	return len(entries) == 0, nil
}

// collectClasses merges the compiled classes from all modules.
// It returns an error if two modules contain the same class.
func collectClasses(projectModelDir, destDir string) ([]string, error) {
	config, err := project.LoadConfig(projectModelDir)
	if err != nil {
		return nil, fmt.Errorf("approximation build produced no readable project model: %w", err)
	}

	for _, module := range config.AllModules() {
		for _, classes := range module.ModuleClasses {
			if err := copyTree(absoluteUnder(projectModelDir, classes), destDir); err != nil {
				return nil, err
			}
		}
	}

	dependencies := make([]string, 0, len(config.AllDependencies()))
	for _, dep := range config.AllDependencies() {
		dependencies = append(dependencies, filepath.Base(dep))
	}
	return dependencies, nil
}

func absoluteUnder(baseDir, path string) string {
	if filepath.IsAbs(path) {
		return path
	}
	return filepath.Join(baseDir, path)
}

func copyTree(srcDir, destDir string) error {
	return filepath.WalkDir(srcDir, func(path string, entry os.DirEntry, err error) error {
		if err != nil {
			return err
		}
		rel, err := filepath.Rel(srcDir, path)
		if err != nil {
			return err
		}
		target := filepath.Join(destDir, rel)
		if entry.IsDir() {
			return os.MkdirAll(target, 0o755)
		}
		if _, err := os.Stat(target); err == nil {
			return fmt.Errorf(
				"two approximation modules produce %s; an approximation class maps to exactly one target class",
				filepath.ToSlash(rel),
			)
		}
		return utils.CopyFile(path, target)
	})
}
