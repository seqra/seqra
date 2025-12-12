package cmd

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"

	"github.com/sirupsen/logrus"
	"github.com/spf13/cobra"

	"github.com/seqra/seqra/internal/globals"
	"github.com/seqra/seqra/internal/utils"
	"github.com/seqra/seqra/internal/utils/java"
	"github.com/seqra/seqra/internal/utils/project"
)

type JavaAutobuilderConfig struct {
	outputDir    string
	sourceRoot   string
	dependencies []string
	packages     []string
	classpath    []string
}

type JavaAutobuilderBuilder struct {
	config *JavaAutobuilderConfig
}

func NewJavaAutobuilder() *JavaAutobuilderBuilder {
	return &JavaAutobuilderBuilder{
		config: &JavaAutobuilderConfig{},
	}
}

func (b *JavaAutobuilderBuilder) WithOutputDir(dir string) *JavaAutobuilderBuilder {
	absDir, err := filepath.Abs(dir)
	if err != nil {
		logrus.Fatalf("Failed to resolve absolute path for output directory '%s': %s", dir, err)
	}
	b.config.outputDir = absDir
	return b
}

func (b *JavaAutobuilderBuilder) WithSourceRoot(root string) *JavaAutobuilderBuilder {
	absRoot, err := filepath.Abs(root)
	if err != nil {
		logrus.Fatalf("Failed to resolve absolute path for source root '%s': %s", root, err)
	}
	if _, err := os.Stat(absRoot); os.IsNotExist(err) {
		logrus.Fatalf("Source root directory does not exist: %s", absRoot)
	}
	b.config.sourceRoot = absRoot
	return b
}

func (b *JavaAutobuilderBuilder) WithDependencies(deps []string) *JavaAutobuilderBuilder {
	absDeps := make([]string, len(deps))
	for i, dep := range deps {
		absDep, err := filepath.Abs(dep)
		if err != nil {
			logrus.Fatalf("Failed to resolve absolute path for dependency '%s': %s", dep, err)
		}
		if _, err := os.Stat(absDep); os.IsNotExist(err) {
			logrus.Fatalf("Dependency file does not exist: %s", absDep)
		}
		absDeps[i] = absDep
	}
	b.config.dependencies = absDeps
	return b
}

func (b *JavaAutobuilderBuilder) WithPackages(packages []string) *JavaAutobuilderBuilder {
	b.config.packages = packages
	return b
}

func (b *JavaAutobuilderBuilder) WithClasspath(classpath []string) *JavaAutobuilderBuilder {
	absClasspath := make([]string, len(classpath))
	for i, cp := range classpath {
		absCP, err := filepath.Abs(cp)
		if err != nil {
			logrus.Fatalf("Failed to resolve absolute path for classpath entry '%s': %s", cp, err)
		}
		if _, err := os.Stat(absCP); os.IsNotExist(err) {
			logrus.Fatalf("Classpath entry does not exist: %s", absCP)
		}
		absClasspath[i] = absCP
	}
	b.config.classpath = absClasspath
	return b
}

func (b *JavaAutobuilderBuilder) Build() *JavaAutobuilderConfig {
	return b.config
}

func (c *JavaAutobuilderConfig) Execute() error {
	if err := c.validate(); err != nil {
		return err
	}

	return c.runAutobuilder()
}

func (c *JavaAutobuilderConfig) validate() error {
	if c.outputDir == "" {
		return fmt.Errorf("output directory is required")
	}
	if _, err := os.Stat(c.outputDir); err == nil {
		return fmt.Errorf("output directory already exists: %s. Please provide a non-existent path", c.outputDir)
	}
	if c.sourceRoot == "" {
		return fmt.Errorf("source root is required")
	}

	if len(c.classpath) == 0 {
		return fmt.Errorf("at least one classpath is required")
	}
	if len(c.packages) == 0 {
		return fmt.Errorf("at least one package is required")
	}
	return nil
}

func (c *JavaAutobuilderConfig) runAutobuilder() error {
	autobuilderJarPath, err := utils.GetAutobuilderJarPath(globals.Config.Autobuilder.Version)
	if err != nil {
		return fmt.Errorf("failed to construct path to the autobuilder: %w", err)
	}

	if _, err = os.Stat(autobuilderJarPath); errors.Is(err, os.ErrNotExist) {
		logrus.Infof("Downloading autobuilder version %s", globals.Config.Autobuilder.Version)
		if err = utils.DownloadGithubReleaseAsset(globals.RepoOwner, globals.AutobuilderRepoName, globals.Config.Autobuilder.Version, globals.AutobuilderAssetName, autobuilderJarPath, globals.Config.Github.Token); err != nil {
			return fmt.Errorf("failed to download autobuilder: %w", err)
		}
		logrus.Infof("Successfully downloaded autobuilder to %s", autobuilderJarPath)
	}

	autobuilderCommand := c.buildAutobuilderCommand(autobuilderJarPath)

	javaRunner := java.NewJavaRunner().TrySystem().TrySpecificVersion(java.DefaultJavaVersion).TrySpecificVersion(java.LegacyJavaVersion)

	commandSucceeded := func(_ error) bool {
		if _, err = os.Stat(c.outputDir); err != nil {
			logrus.Errorf("Output directory does not exist after autobuilder execution: %s", c.outputDir)
			logrus.Error("Autobuilder failed to compile the project")
			return false
		}
		return true
	}

	_, err = javaRunner.ExecuteJavaCommand(autobuilderCommand, commandSucceeded)
	if err != nil {
		return fmt.Errorf("native autobuilder execution failed: %w", err)
	}

	return c.printProjectSummary()
}

func (c *JavaAutobuilderConfig) buildAutobuilderCommand(autobuilderJarPath string) []string {
	command := []string{
		"-Xmx1G",
		"-jar",
		autobuilderJarPath,
		"--project-root-dir", c.sourceRoot,
		"--result-dir", c.outputDir,
		"--build-dir", c.outputDir,
		"--build", "portable",
	}

	command = append(command, "--build-type", "cp")
	for _, cp := range c.classpath {
		command = append(command, "--cp", cp)
	}
	for _, pkg := range c.packages {
		command = append(command, "--pkg", pkg)
	}

	switch globals.Config.Log.Verbosity {
	case "info":
		command = append(command, "--verbosity=info")
	case "debug":
		command = append(command, "--verbosity=debug")
	}

	return command
}

func (c *JavaAutobuilderConfig) printProjectSummary() error {
	projectYamlPath := filepath.Join(c.outputDir, "project.yaml")

	if _, err := os.Stat(projectYamlPath); err != nil {
		return fmt.Errorf("project.yaml not found at %s: %w", projectYamlPath, err)
	}

	config, err := project.LoadConfig(c.outputDir)
	if err != nil {
		return fmt.Errorf("failed to load generated project.yaml: %w", err)
	}

	c.logProjectSummary(projectYamlPath, config)
	return nil
}

func (c *JavaAutobuilderConfig) logProjectSummary(projectYamlPath string, config *project.Config) {
	logrus.Infof("Generated project.yaml at: %s", projectYamlPath)
	logrus.Infof("Modules: %d, Dependencies: %d", len(config.Modules), len(config.Dependencies))

	totalPackages := 0
	totalClasses := 0
	for _, module := range config.Modules {
		totalPackages += len(module.Packages)
		totalClasses += len(module.ModuleClasses)
	}
	logrus.Infof("Total packages: %d, Total classes: %d", totalPackages, totalClasses)
}

var (
	OutputDir     string
	SourceRoot    string
	JavaToolchain string
	Dependencies  []string
	Classes       []string
	Packages      []string
	BuildType     string
	Classpath     []string
)

var projectCmd = &cobra.Command{
	Use:   "project",
	Short: "Create a project model directory containing a project.yaml configuration from precompiled JARs or classes",
	Long: `Create a project model directory containing a project.yaml configuration from precompiled JARs or classes.

This command generates a project model, automatically detecting dependencies and project structure.
Additional packages have to be specified to enhance the generated configuration.

Examples:
  # Classpath analysis
  seqra project --output ./project-model --source-root /path/to/source \
    --classpath /path/to/app.jar --package com.example`,
	Run: func(cmd *cobra.Command, args []string) {
		logrus.Info()
		logrus.Infof("=== Project builder mode ===")

		config := NewJavaAutobuilder().
			WithOutputDir(OutputDir).
			WithSourceRoot(SourceRoot).
			WithDependencies(Dependencies).
			WithPackages(Packages).
			WithClasspath(Classpath).
			Build()

		logrus.Infof("Source root: %s", config.sourceRoot)
		logrus.Infof("Output: %s", config.outputDir)

		if err := config.Execute(); err != nil {
			logrus.Fatalf("Failed to generate project configuration: %s", err)
		}

		logrus.Info()
		logrus.Info("Project configuration generated successfully!")
	},
}

func init() {
	rootCmd.AddCommand(projectCmd)

	projectCmd.Flags().StringVarP(&OutputDir, "output", "o", "", "Output directory for project.yaml")
	_ = projectCmd.MarkFlagRequired("output")
	projectCmd.Flags().StringVar(&SourceRoot, "source-root", "", "Source root directory")
	_ = projectCmd.MarkFlagRequired("source-root")
	projectCmd.Flags().StringArrayVar(&Dependencies, "dependency", []string{}, "Project dependencies (JAR files)")
	projectCmd.Flags().StringArrayVar(&Packages, "package", []string{}, "Project packages")
	_ = projectCmd.MarkFlagRequired("package")
	projectCmd.Flags().StringArrayVar(&Classpath, "classpath", []string{}, "Classpath entries (classes or JAR files)")
	_ = projectCmd.MarkFlagRequired("classpath")
}
