package cmd

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/spf13/cobra"

	"github.com/seqra/opentaint/v2/internal/globals"
	"github.com/seqra/opentaint/v2/internal/output"
	"github.com/seqra/opentaint/v2/internal/utils"
	"github.com/seqra/opentaint/v2/internal/utils/java"
	"github.com/seqra/opentaint/v2/internal/utils/log"
	"github.com/seqra/opentaint/v2/internal/utils/project"
)

type JavaAutobuilderConfig struct {
	outputDir    string
	sourceRoot   string
	dependencies []string
	packages     []string
	classpaths   []string
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
	cleanDir := filepath.Clean(dir)
	absDir := log.AbsPathOrExit(cleanDir, "output directory")
	b.config.outputDir = absDir
	return b
}

func (b *JavaAutobuilderBuilder) WithSourceRoot(root string) *JavaAutobuilderBuilder {
	cleanRoot := filepath.Clean(root)
	absRoot := log.AbsPathOrExit(cleanRoot, "source root")
	if _, err := os.Stat(absRoot); os.IsNotExist(err) {
		out.Fatalf("Source root directory does not exist: %s", absRoot)
	}
	b.config.sourceRoot = absRoot
	return b
}

func (b *JavaAutobuilderBuilder) WithDependencies(deps []string) *JavaAutobuilderBuilder {
	absDeps := make([]string, len(deps))
	for i, dep := range deps {
		absDep, err := filepath.Abs(dep)
		if err != nil {
			out.Fatalf("Failed to resolve absolute path for dependency '%s': %s", dep, err)
		}
		if _, err := os.Stat(absDep); os.IsNotExist(err) {
			out.Fatalf("Dependency file does not exist: %s", absDep)
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
			out.Fatalf("Failed to resolve absolute path for classpath entry '%s': %s", cp, err)
		}
		if _, err := os.Stat(absCP); os.IsNotExist(err) {
			out.Fatalf("Classpath entry does not exist: %s", absCP)
		}
		absClasspath[i] = absCP
	}
	b.config.classpaths = absClasspath
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

	if len(c.classpaths) == 0 {
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

	if err = ensureArtifactAvailable("autobuilder", globals.Config.Autobuilder.Version, autobuilderJarPath, func() error {
		return utils.DownloadGithubReleaseAsset(globals.Config.Owner, globals.AutobuilderRepoName, globals.Config.Autobuilder.Version, globals.AutobuilderAssetName, autobuilderJarPath, globals.Config.Github.Token, globals.Config.SkipVerify, out)
	}); err != nil {
		return err
	}

	builder := NewAutobuilderBuilder().
		SetProjectRootDir(c.sourceRoot).
		SetResultDir(c.outputDir).
		SetBuildMode("portable").
		SetBuildType("cp").
		SetMaxMemory("-Xmx1G").
		SetJarPath(autobuilderJarPath)

	for _, cp := range c.classpaths {
		builder.AddClasspath(cp)
	}
	for _, pkg := range c.packages {
		builder.AddPackage(pkg)
	}

	autobuilderCommand := builder.BuildNativeCommand()

	javaRunner := java.NewJavaRunner().
		WithSkipVerify(globals.Config.SkipVerify).
		WithDebugOutput(out.DebugStream("Autobuilder")).
		WithImageType(java.AdoptiumImageJRE).
		TrySpecificVersion(globals.DefaultJavaVersion)

	commandSucceeded := func(_ error) bool {
		if _, err = os.Stat(c.outputDir); err != nil {
			output.LogInfof("Output directory does not exist after autobuilder execution: %s", c.outputDir)
			output.LogInfo("Autobuilder failed to compile the project")
			return false
		}
		return true
	}

	err = javaRunner.ExecuteJavaCommand(autobuilderCommand, commandSucceeded)
	if err != nil {
		return fmt.Errorf("native autobuilder execution failed: %w", err)
	}

	return c.printProjectSummary()
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
	suggest("To scan project run", utils.BuildScanCommandFromCompile(c.outputDir, c.outputDir))
	return nil
}

func (c *JavaAutobuilderConfig) logProjectSummary(projectYamlPath string, config *project.Config) {
	totalPackages := 0
	totalClasses := 0
	for _, module := range config.Modules {
		totalPackages += len(module.Packages)
		totalClasses += len(module.ModuleClasses)
	}

	out.Blank()
	out.Section("Project Summary").
		Field("Generated project.yaml at", projectYamlPath).
		Line().
		Field("Total modules", len(config.Modules)).
		Field("Total classes", totalClasses).
		Field("Total packages", totalPackages).
		Field("Total dependencies", len(config.Dependencies)).
		Render()
}

var (
	OutputDir    string
	SourceRoot   string
	Dependencies []string
	Packages     []string
	Classpaths   []string
)

var projectCmd = &cobra.Command{
	Use:   "project",
	Short: "Create a project model directory containing a project.yaml configuration from precompiled JARs or classes",
	Long: `Create a project model directory containing a project.yaml configuration from precompiled JARs or classes.

This command generates a project model, automatically detecting dependencies and project structure.
Additional packages have to be specified to enhance the generated configuration.

Examples:
  # Classpath analysis
  opentaint project --output ./project-model --source-root /path/to/source \
    --classpath /path/to/app.jar --package com.example`,
	Run: func(cmd *cobra.Command, args []string) {
		config := NewJavaAutobuilder().
			WithOutputDir(OutputDir).
			WithSourceRoot(SourceRoot).
			WithDependencies(Dependencies).
			WithPackages(Packages).
			WithClasspath(Classpaths).
			Build()

		classpathItems := make([]any, len(config.classpaths))
		for i, cp := range config.classpaths {
			classpathItems[i] = cp
		}
		packageItems := make([]any, len(config.packages))
		for i, pkg := range config.packages {
			packageItems[i] = pkg
		}

		sb := out.Section("OpenTaint Project").
			Field("Source root", config.sourceRoot).
			Field("Output", config.outputDir).
			Group("Classpaths", classpathItems...).
			Group("Packages", packageItems...)

		if len(config.dependencies) != 0 {
			depItems := make([]any, len(config.dependencies))
			for i, dep := range config.dependencies {
				depItems[i] = dep
			}
			sb.Group("Dependencies", depItems...)
		}
		sb.Render()

		if err := config.Execute(); err != nil {
			out.Fatalf("Failed to generate project configuration: %s", err)
		}
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
	projectCmd.Flags().StringArrayVar(&Classpaths, "classpath", []string{}, "Classpath entries (classes or JAR files)")
	_ = projectCmd.MarkFlagRequired("classpath")
}
