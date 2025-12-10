package cmd

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/sirupsen/logrus"
	"github.com/spf13/cobra"

	"github.com/seqra/seqra/internal/utils/log"
	"github.com/seqra/seqra/internal/utils/project"
)



var (
	OutputDir     string
	SourceRoot    string
	JavaToolchain string
	Dependencies  []string
	ModuleConfigs []string
)

var projectCmd = &cobra.Command{
	Use:   "project",
	Short: "Create directory with project.yaml configuration file",
	Long: `Create a directory containing a project.yaml configuration file with specified source roots, Java toolchain, dependencies, and module information.

This command generates a project model directory structure that can be used by the scan command.

Module configuration format:
  --module "sourceRoot:package1,package2:class1,class2"

Examples:
  # Basic project with one module
  seqra project --output ./project-model --source-root sources \
    --module "sources/app:org.example.app:classes/app"

  # Project with dependencies and multiple modules
  seqra project --output ./project-model --source-root sources \
    --java-toolchain toolchain/java-21-openjdk-amd64 \
    --dependency dependencies/netty-all-4.1.117.Final.jar \
    --module "sources/base:org.example.base:classes/base" \
    --module "sources/web:org.example.web,org.example.controller:lib/web.jar"`,
	Run: func(cmd *cobra.Command, args []string) {
		generateProjectConfig()
	},
}

func init() {
	rootCmd.AddCommand(projectCmd)

	projectCmd.Flags().StringVarP(&OutputDir, "output", "o", "", "Output directory for project.yaml")
	_ = projectCmd.MarkFlagRequired("output")
	projectCmd.Flags().StringVar(&SourceRoot, "source-root", "", "Source root directory")
	_ = projectCmd.MarkFlagRequired("source-root")
	projectCmd.Flags().StringVar(&JavaToolchain, "java-toolchain", "", "Java toolchain path")
	projectCmd.Flags().StringArrayVar(&Dependencies, "dependency", []string{}, "Project dependencies (JAR files)")
	projectCmd.Flags().StringArrayVar(&ModuleConfigs, "module", []string{}, "Module configuration in format 'sourceRoot:packages:classes'")
}

func getRelativePathIfInside(absPath, outputDir string) string {
	if absPath == "" {
		return ""
	}
	if relPath, err := filepath.Rel(outputDir, absPath); err == nil && !strings.HasPrefix(relPath, "..") {
		return relPath
	}
	return absPath
}

func getRelativePathsIfInside(absPaths []string, outputDir string) []string {
	var result []string
	for _, absPath := range absPaths {
		result = append(result, getRelativePathIfInside(absPath, outputDir))
	}
	return result
}

func getModulesWithRelativePaths(modules []project.Module, outputDir string) []project.Module {
	var result []project.Module
	for _, module := range modules {
		result = append(result, project.Module{
			ModuleSourceRoot: getRelativePathIfInside(module.ModuleSourceRoot, outputDir),
			Packages:         module.Packages,
			ModuleClasses:    getRelativePathsIfInside(module.ModuleClasses, outputDir),
		})
	}
	return result
}

func parseModuleConfig(moduleConfig string) (project.Module, error) {
	parts := strings.Split(moduleConfig, ":")
	if len(parts) != 3 {
		return project.Module{}, fmt.Errorf("invalid module format, expected 'sourceRoot:packages:classes', got: %s", moduleConfig)
	}

	sourceRoot := strings.TrimSpace(parts[0])
	packagesStr := strings.TrimSpace(parts[1])
	classesStr := strings.TrimSpace(parts[2])

	var packages []string
	if packagesStr != "" {
		for _, pkg := range strings.Split(packagesStr, ",") {
			if trimmed := strings.TrimSpace(pkg); trimmed != "" {
				packages = append(packages, trimmed)
			}
		}
	}

	var classes []string
	if classesStr != "" {
		for _, cls := range strings.Split(classesStr, ",") {
			if trimmed := strings.TrimSpace(cls); trimmed != "" {
				classes = append(classes, trimmed)
			}
		}
	}

	absSourceRoot := log.AbsPathOrExit(sourceRoot, "module source root")
	if _, err := os.Stat(absSourceRoot); os.IsNotExist(err) {
		return project.Module{}, fmt.Errorf("module source root does not exist: %s", absSourceRoot)
	}

	var absClasses []string
	for _, cls := range classes {
		absCls := log.AbsPathOrExit(cls, "module class")
		if _, err := os.Stat(absCls); os.IsNotExist(err) {
			return project.Module{}, fmt.Errorf("module class path does not exist: %s", absCls)
		}
		absClasses = append(absClasses, absCls)
	}

	return project.Module{
		ModuleSourceRoot: absSourceRoot,
		Packages:         packages,
		ModuleClasses:    absClasses,
	}, nil
}

func generateProjectConfig() {
	absOutputDir := log.AbsPathOrExit(OutputDir, "output directory")

	if err := os.MkdirAll(absOutputDir, 0755); err != nil {
		logrus.Fatalf("Failed to create output directory: %s", err)
	}

	projectYamlPath := filepath.Join(absOutputDir, "project.yaml")

	if len(ModuleConfigs) == 0 {
		logrus.Fatalf("At least one module configuration is required. Use --module flag.")
	}

	absSourceRoot := log.AbsPathOrExit(SourceRoot, "source root")
	if _, err := os.Stat(absSourceRoot); os.IsNotExist(err) {
		logrus.Fatalf("Source root does not exist: %s", absSourceRoot)
	}

	var absJavaToolchain string
	if JavaToolchain != "" {
		absJavaToolchain = log.AbsPathOrExit(JavaToolchain, "java toolchain")
		if _, err := os.Stat(absJavaToolchain); os.IsNotExist(err) {
			logrus.Fatalf("Java toolchain does not exist: %s", absJavaToolchain)
		}
	}

	var absDependencies []string
	for _, dep := range Dependencies {
		absDep := log.AbsPathOrExit(dep, "dependency")
		if _, err := os.Stat(absDep); os.IsNotExist(err) {
			logrus.Fatalf("Dependency does not exist: %s", absDep)
		}
		absDependencies = append(absDependencies, absDep)
	}

	var modules []project.Module
	for _, moduleConfig := range ModuleConfigs {
		module, err := parseModuleConfig(moduleConfig)
		if err != nil {
			logrus.Fatalf("Failed to parse module config: %s", err)
		}
		modules = append(modules, module)
	}

	config := project.Config{
		SourceRoot:    getRelativePathIfInside(absSourceRoot, absOutputDir),
		JavaToolchain: getRelativePathIfInside(absJavaToolchain, absOutputDir),
		Dependencies:  getRelativePathsIfInside(absDependencies, absOutputDir),
		Modules:       getModulesWithRelativePaths(modules, absOutputDir),
	}

	if err := project.SaveConfig(&config, projectYamlPath); err != nil {
		logrus.Fatalf("Failed to save project.yaml: %s", err)
	}

	logrus.Infof("Generated project.yaml at: %s", projectYamlPath)
	logrus.Infof("Source root: %s", config.SourceRoot)
	if config.JavaToolchain != "" {
		logrus.Infof("Java toolchain: %s", config.JavaToolchain)
	}
	if len(config.Dependencies) > 0 {
		logrus.Infof("Dependencies: %d", len(config.Dependencies))
		for i, dep := range config.Dependencies {
			logrus.Infof("  Dependency %d: %s", i+1, dep)
		}
	}
	logrus.Infof("Modules: %d", len(config.Modules))
	for i, module := range config.Modules {
		logrus.Infof("  Module %d: %s", i+1, module.ModuleSourceRoot)
		if len(module.Packages) > 0 {
			logrus.Infof("    Packages (%d):", len(module.Packages))
			for _, pkg := range module.Packages {
				logrus.Infof("      %s", pkg)
			}
		}
		if len(module.ModuleClasses) > 0 {
			logrus.Infof("    Classes (%d):", len(module.ModuleClasses))
			for _, cls := range module.ModuleClasses {
				logrus.Infof("      %s", cls)
			}
		}
	}
}
