package cmd

import (
	"fmt"
	"os"
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
- Java runtime (Temurin JDK)

This prepares the environment with all required dependencies for offline analysis.`,
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
	autobuilderJarPath, err := utils.GetAutobuilderJarPath(globals.Config.Autobuilder.Version)
	if err != nil {
		return err
	}

	printer.AddNode(fmt.Sprintf("Autobuilder %s", globals.Config.Autobuilder.Version))
	if _, err = os.Stat(autobuilderJarPath); err == nil {
		printer.AddNodeAtLevelDefault("Already downloaded", 1)
		return nil
	}

	logrus.Infof("Downloading autobuilder %s...", globals.Config.Autobuilder.Version)
	if err = utils.DownloadGithubReleaseAsset(globals.RepoOwner, globals.AutobuilderRepoName, globals.Config.Autobuilder.Version, globals.AutobuilderAssetName, autobuilderJarPath, globals.Config.Github.Token); err != nil {
		return err
	}
	printer.AddNodeAtLevelDefault(fmt.Sprintf("Downloaded to %s", autobuilderJarPath), 1)
	return nil
}

func downloadAnalyzer(printer *formatters.TreePrinter) error {
	analyzerJarPath, err := utils.GetAnalyzerJarPath(globals.Config.Analyzer.Version)
	if err != nil {
		return err
	}

	printer.AddNode(fmt.Sprintf("Analyzer %s", globals.Config.Analyzer.Version))
	if _, err = os.Stat(analyzerJarPath); err == nil {
		printer.AddNodeAtLevelDefault("Already downloaded", 1)
		return nil
	}

	logrus.Infof("Downloading analyzer %s...", globals.Config.Analyzer.Version)
	if err = utils.DownloadGithubReleaseAsset(globals.RepoOwner, globals.AnalyzerRepoName, globals.Config.Analyzer.Version, globals.AnalyzerAssetName, analyzerJarPath, globals.Config.Github.Token); err != nil {
		return err
	}
	printer.AddNodeAtLevelDefault(fmt.Sprintf("Downloaded to %s", analyzerJarPath), 1)
	return nil
}

func downloadRules(printer *formatters.TreePrinter) error {
	rulesPath, err := utils.GetRulesPath(globals.Config.Rules.Version)
	if err != nil {
		return err
	}

	printer.AddNode(fmt.Sprintf("Rules %s", globals.Config.Rules.Version))
	if _, err := os.Stat(rulesPath); err == nil {
		printer.AddNodeAtLevelDefault("Already downloaded", 1)
		return nil
	}

	logrus.Infof("Downloading rules %s...", globals.Config.Rules.Version)
	err = utils.DownloadAndUnpackGithubReleaseAsset(globals.RepoOwner, globals.RulesRepoName, globals.Config.Rules.Version, globals.RulesAssetName, rulesPath, globals.Config.Github.Token)
	if err != nil {
		return err
	}
	printer.AddNodeAtLevelDefault(fmt.Sprintf("Downloaded to %s", rulesPath), 1)
	return nil
}

func downloadJava(printer *formatters.TreePrinter) error {
	if globals.Config.Java.Version < 8 || globals.Config.Java.Version > 25 {
		return fmt.Errorf("unsupported Java version: %d (supported range: 8-25)", globals.Config.Java.Version)
	}

	seqraHome, err := utils.GetSeqraHome()
	if err != nil {
		return err
	}

	adoptiumOS, adoptiumArch, err := java.MapPlatformToAdoptium(runtime.GOOS, runtime.GOARCH)
	if err != nil {
		return err
	}

	artefactRoot := fmt.Sprintf("%s/jdk/temurin-%d-jdk-%s-%s", seqraHome, globals.Config.Java.Version, adoptiumOS, adoptiumArch)
	javaPath := fmt.Sprintf("%s/bin/java", artefactRoot)

	printer.AddNode(fmt.Sprintf("Java %d", globals.Config.Java.Version))
	if _, err := os.Stat(javaPath); err == nil {
		printer.AddNodeAtLevelDefault("Already downloaded", 1)
		return nil
	}

	logrus.Infof("Downloading Java %d...", globals.Config.Java.Version)
	javaPath, err = java.EnsureLocalRuntime(globals.Config.Java.Version, java.AdoptiumImageJDK, runtime.GOOS, runtime.GOARCH)
	if err != nil {
		return err
	}
	printer.AddNodeAtLevelDefault(fmt.Sprintf("Downloaded to %s", javaPath), 1)
	return nil
}

func init() {
	rootCmd.AddCommand(pullCmd)
}
