package server

import (
	"fmt"
	"go/version"
	"os"
	"path/filepath"
	"runtime"
	"sort"
	"strconv"
	"strings"

	"golang.org/x/mod/modfile"
	"golang.org/x/tools/go/packages"
)

func createModelWorkspace(
	modelDir string,
	projectInfo map[string]*packages.Package,
	projectModulePaths map[string]bool,
) (string, func(), error) {
	moduleDirs := make(map[string]bool)
	modelDir, err := filepath.Abs(modelDir)
	if err != nil {
		return "", nil, fmt.Errorf("resolve Go model directory: %w", err)
	}
	moduleDirs[modelDir] = true

	for _, pkg := range projectInfo {
		module := pkg.Module
		if module == nil || module.Dir == "" || (!module.Main && !projectModulePaths[module.Path]) {
			continue
		}
		moduleDir, absErr := filepath.Abs(module.Dir)
		if absErr != nil {
			return "", nil, fmt.Errorf("resolve project module %s: %w", module.Path, absErr)
		}
		moduleDirs[moduleDir] = true
	}

	dirs := make([]string, 0, len(moduleDirs))
	for dir := range moduleDirs {
		dirs = append(dirs, dir)
	}
	sort.Strings(dirs)

	tempDir, err := os.MkdirTemp("", "opentaint-go-model-work-*")
	if err != nil {
		return "", nil, fmt.Errorf("create Go model workspace: %w", err)
	}
	cleanup := func() { _ = os.RemoveAll(tempDir) }

	goVersion := runtime.Version()
	if !version.IsValid(goVersion) {
		goVersion = "go1.25.0"
	}
	goVersion = newerGoVersion(goVersion, moduleGoVersion(modelDir))
	for _, pkg := range projectInfo {
		if pkg.Module != nil && moduleDirs[pkg.Module.Dir] {
			goVersion = newerGoVersion(goVersion, pkg.Module.GoVersion)
		}
	}

	var workspace strings.Builder
	fmt.Fprintf(&workspace, "go %s\n\nuse (\n", strings.TrimPrefix(goVersion, "go"))
	for _, dir := range dirs {
		fmt.Fprintf(&workspace, "\t%s\n", strconv.Quote(filepath.ToSlash(dir)))
	}
	workspace.WriteString(")\n")

	workspacePath := filepath.Join(tempDir, "go.work")
	if err := os.WriteFile(workspacePath, []byte(workspace.String()), 0o600); err != nil {
		cleanup()
		return "", nil, fmt.Errorf("write Go model workspace: %w", err)
	}
	return workspacePath, cleanup, nil
}

func moduleGoVersion(moduleDir string) string {
	data, err := os.ReadFile(filepath.Join(moduleDir, "go.mod"))
	if err != nil {
		return ""
	}
	parsed, err := modfile.Parse("go.mod", data, nil)
	if err != nil || parsed.Go == nil {
		return ""
	}
	return parsed.Go.Version
}

func newerGoVersion(current, candidate string) string {
	candidate = "go" + strings.TrimPrefix(candidate, "go")
	if version.IsValid(candidate) && version.Compare(candidate, current) > 0 {
		return candidate
	}
	return current
}

func modelModulePaths(modelDir string, modelInfo map[string]*packages.Package) map[string]bool {
	result := make(map[string]bool)
	modelDir, err := filepath.Abs(modelDir)
	if err != nil {
		return result
	}
	modelDir = filepath.Clean(modelDir)
	for _, pkg := range modelInfo {
		if pkg.Module == nil || pkg.Module.Dir == "" {
			continue
		}
		moduleDir, absErr := filepath.Abs(pkg.Module.Dir)
		if absErr == nil && filepath.Clean(moduleDir) == modelDir {
			result[pkg.Module.Path] = true
		}
	}
	return result
}
