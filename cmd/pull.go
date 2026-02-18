package cmd

import (
	"fmt"
	"os"
	"path/filepath"
	"runtime"

	"github.com/seqra/seqra/v2/internal/globals"
	"github.com/seqra/seqra/v2/internal/utils"
	"github.com/seqra/seqra/v2/internal/utils/formatters"
	"github.com/seqra/seqra/v2/internal/utils/java"
	"github.com/sirupsen/logrus"
	"github.com/spf13/cobra"
)

var pullCmd = &cobra.Command{
	Use:   "pull",
	Short: "Download autobuilder, analyzer binaries, rules and Java runtime",
	Long: `Download all necessary binaries and assets:
- Seqra autobuilder JAR
- Seqra analyzer JAR
- Seqra rules archive
- Java runtime (Temurin JRE)

This prepares the environment with all required dependencies for offline analysis.
When bundled artifacts are present (from a release archive), they will be used directly.`,
	Run: func(cmd *cobra.Command, args []string) {
		logrus.Info(formatters.FormatTreeHeader("Seqra Pull"))
		printer := formatters.NewTreePrinter()

		printer.AddNode(fmt.Sprintf("Autobuilder %s", globals.Config.Autobuilder.Version))
		printer.AddNode(fmt.Sprintf("Analyzer %s", globals.Config.Analyzer.Version))
		printer.AddNode(fmt.Sprintf("Rules %s", globals.Config.Rules.Version))
		printer.AddNode(fmt.Sprintf("Java %d", globals.Config.Java.Version))
		printer.Print()

		printer = formatters.NewTreePrinter()
		if err := downloadAutobuilder(printer); err != nil {
			logrus.Fatalf("Failed to download autobuilder: %s", err)
		}

		if err := downloadAnalyzer(printer); err != nil {
			logrus.Fatalf("Failed to download analyzer: %s", err)
		}

		if err := downloadRules(printer); err != nil {
			logrus.Fatalf("Failed to download rules: %s", err)
		}

		if err := downloadJava(printer); err != nil {
			logrus.Fatalf("Failed to download Java: %s", err)
		}
		logrus.Info()
		logrus.Info(formatters.FormatTreeHeader("Pull Summary"))
		printer.Print()
	},
}

func downloadAutobuilder(printer *formatters.TreePrinter) error {
	version := globals.Config.Autobuilder.Version
	isBindVersion := version == globals.AutobuilderBindVersion

	printer.AddNode(fmt.Sprintf("Autobuilder %s", version))

	// Check bundled path (next to binary)
	if isBindVersion {
		if libPath := utils.GetBundledLibPath(); libPath != "" {
			bundledPath := filepath.Join(libPath, globals.AutobuilderAssetName)
			if _, err := os.Stat(bundledPath); err == nil {
				printer.AddNodeAtLevelDefault("Using bundled artifact", 1)
				return nil
			}
		}
	}

	// Check ~/.seqra/ cache
	seqraHome, err := utils.GetSeqraHome()
	if err != nil {
		return err
	}
	cachePath := filepath.Join(seqraHome, "autobuilder_"+version+".jar")
	if _, err := os.Stat(cachePath); err == nil {
		printer.AddNodeAtLevelDefault("Already downloaded", 1)
		return nil
	}

	logrus.Infof("Downloading autobuilder %s...", version)

	// For bind version, try to install next to binary
	if isBindVersion {
		if libPath := utils.GetBundledLibPath(); libPath != "" {
			if err := os.MkdirAll(libPath, 0o755); err == nil {
				targetPath := filepath.Join(libPath, globals.AutobuilderAssetName)
				if err := utils.DownloadGithubReleaseAsset(globals.Config.Owner, globals.AutobuilderRepoName, version, globals.AutobuilderAssetName, targetPath, globals.Config.Github.Token, globals.Config.SkipVerify); err != nil {
					return err
				}
				printer.AddNodeAtLevelDefault(fmt.Sprintf("Downloaded to %s", targetPath), 1)
				return nil
			}
			logrus.Debugf("Cannot write next to binary, falling back to cache")
		}
	}

	// Fall back to ~/.seqra/
	if err := utils.DownloadGithubReleaseAsset(globals.Config.Owner, globals.AutobuilderRepoName, version, globals.AutobuilderAssetName, cachePath, globals.Config.Github.Token, globals.Config.SkipVerify); err != nil {
		return err
	}
	printer.AddNodeAtLevelDefault(fmt.Sprintf("Downloaded to %s", cachePath), 1)
	return nil
}

func downloadAnalyzer(printer *formatters.TreePrinter) error {
	version := globals.Config.Analyzer.Version
	isBindVersion := version == globals.AnalyzerBindVersion

	printer.AddNode(fmt.Sprintf("Analyzer %s", version))

	// Check bundled path (next to binary)
	if isBindVersion {
		if libPath := utils.GetBundledLibPath(); libPath != "" {
			bundledPath := filepath.Join(libPath, globals.AnalyzerAssetName)
			if _, err := os.Stat(bundledPath); err == nil {
				printer.AddNodeAtLevelDefault("Using bundled artifact", 1)
				return nil
			}
		}
	}

	// Check ~/.seqra/ cache
	seqraHome, err := utils.GetSeqraHome()
	if err != nil {
		return err
	}
	cachePath := filepath.Join(seqraHome, "analyzer_"+version+".jar")
	if _, err := os.Stat(cachePath); err == nil {
		printer.AddNodeAtLevelDefault("Already downloaded", 1)
		return nil
	}

	logrus.Infof("Downloading analyzer %s...", version)

	// For bind version, try to install next to binary
	if isBindVersion {
		if libPath := utils.GetBundledLibPath(); libPath != "" {
			if err := os.MkdirAll(libPath, 0o755); err == nil {
				targetPath := filepath.Join(libPath, globals.AnalyzerAssetName)
				if err := utils.DownloadGithubReleaseAsset(globals.Config.Owner, globals.AnalyzerRepoName, version, globals.AnalyzerAssetName, targetPath, globals.Config.Github.Token, globals.Config.SkipVerify); err != nil {
					return err
				}
				printer.AddNodeAtLevelDefault(fmt.Sprintf("Downloaded to %s", targetPath), 1)
				return nil
			}
			logrus.Debugf("Cannot write next to binary, falling back to cache")
		}
	}

	// Fall back to ~/.seqra/
	if err := utils.DownloadGithubReleaseAsset(globals.Config.Owner, globals.AnalyzerRepoName, version, globals.AnalyzerAssetName, cachePath, globals.Config.Github.Token, globals.Config.SkipVerify); err != nil {
		return err
	}
	printer.AddNodeAtLevelDefault(fmt.Sprintf("Downloaded to %s", cachePath), 1)
	return nil
}

func downloadRules(printer *formatters.TreePrinter) error {
	version := globals.Config.Rules.Version
	isBindVersion := version == globals.RulesBindVersion

	printer.AddNode(fmt.Sprintf("Rules %s", version))

	// Check bundled path (next to binary)
	if isBindVersion {
		if libPath := utils.GetBundledLibPath(); libPath != "" {
			bundledPath := filepath.Join(libPath, "rules")
			if _, err := os.Stat(bundledPath); err == nil {
				printer.AddNodeAtLevelDefault("Using bundled artifact", 1)
				return nil
			}
		}
	}

	// Check ~/.seqra/ cache
	seqraHome, err := utils.GetSeqraHome()
	if err != nil {
		return err
	}
	cachePath := filepath.Join(seqraHome, "rules_"+version)
	if _, err := os.Stat(cachePath); err == nil {
		printer.AddNodeAtLevelDefault("Already downloaded", 1)
		return nil
	}

	logrus.Infof("Downloading rules %s...", version)

	// For bind version, try to install next to binary
	if isBindVersion {
		if libPath := utils.GetBundledLibPath(); libPath != "" {
			if err := os.MkdirAll(libPath, 0o755); err == nil {
				targetPath := filepath.Join(libPath, "rules")
				if err := utils.DownloadAndUnpackGithubReleaseAsset(globals.Config.Owner, globals.RulesRepoName, version, globals.RulesAssetName, targetPath, globals.Config.Github.Token, globals.Config.SkipVerify); err != nil {
					return err
				}
				printer.AddNodeAtLevelDefault(fmt.Sprintf("Downloaded to %s", targetPath), 1)
				return nil
			}
			logrus.Debugf("Cannot write next to binary, falling back to cache")
		}
	}

	// Fall back to ~/.seqra/
	if err := utils.DownloadAndUnpackGithubReleaseAsset(globals.Config.Owner, globals.RulesRepoName, version, globals.RulesAssetName, cachePath, globals.Config.Github.Token, globals.Config.SkipVerify); err != nil {
		return err
	}
	printer.AddNodeAtLevelDefault(fmt.Sprintf("Downloaded to %s", cachePath), 1)
	return nil
}

func downloadJava(printer *formatters.TreePrinter) error {
	javaVersion := globals.Config.Java.Version
	if javaVersion < 8 || javaVersion > 25 {
		return fmt.Errorf("unsupported Java version: %d (supported range: 8-25)", javaVersion)
	}

	isBindVersion := javaVersion == globals.DefaultJavaVersion
	javaBinary := "java"
	if runtime.GOOS == "windows" {
		javaBinary = "java.exe"
	}

	printer.AddNode(fmt.Sprintf("Java %d", javaVersion))

	// Check bundled JRE (next to binary)
	if isBindVersion {
		if jrePath := utils.GetBundledJREPath(); jrePath != "" {
			bundledJava := filepath.Join(jrePath, "bin", javaBinary)
			if _, err := os.Stat(bundledJava); err == nil {
				printer.AddNodeAtLevelDefault("Using bundled JRE", 1)
				return nil
			}
		}
	}

	// Check ~/.seqra/ cache
	seqraHome, err := utils.GetSeqraHome()
	if err != nil {
		return err
	}
	adoptiumOS, adoptiumArch, err := java.MapPlatformToAdoptium(runtime.GOOS, runtime.GOARCH)
	if err != nil {
		return err
	}
	cacheRoot := filepath.Join(seqraHome, "jre", fmt.Sprintf("temurin-%d-jre-%s-%s", javaVersion, adoptiumOS, adoptiumArch))
	cacheJavaPath := filepath.Join(cacheRoot, "bin", javaBinary)
	if _, err := os.Stat(cacheJavaPath); err == nil {
		printer.AddNodeAtLevelDefault("Already downloaded", 1)
		return nil
	}

	logrus.Infof("Downloading Java %d...", javaVersion)

	// For bind version, try to install next to binary
	if isBindVersion {
		if jrePath := utils.GetBundledJREPath(); jrePath != "" {
			if err := os.MkdirAll(jrePath, 0o755); err == nil {
				// Clean up since EnsureLocalRuntimeAt will manage this directory
				os.Remove(jrePath)
				javaPath, err := java.EnsureLocalRuntimeAt(javaVersion, java.AdoptiumImageJRE, jrePath, runtime.GOOS, runtime.GOARCH, globals.Config.SkipVerify)
				if err != nil {
					return err
				}
				printer.AddNodeAtLevelDefault(fmt.Sprintf("Downloaded to %s", javaPath), 1)
				return nil
			}
			logrus.Debugf("Cannot write next to binary, falling back to cache")
		}
	}

	// Fall back to ~/.seqra/
	javaPath, err := java.EnsureLocalRuntime(javaVersion, java.AdoptiumImageJRE, runtime.GOOS, runtime.GOARCH, globals.Config.SkipVerify)
	if err != nil {
		return err
	}
	printer.AddNodeAtLevelDefault(fmt.Sprintf("Downloaded to %s", javaPath), 1)
	return nil
}

func init() {
	rootCmd.AddCommand(pullCmd)
}
