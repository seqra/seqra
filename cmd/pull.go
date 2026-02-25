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

		// Skip installing next to binary for go install (shared ~/go/bin/ directory)
		method, _ := utils.DetectInstallMethod()
		installNextToBinary := method != utils.InstallMethodGoInstall

		// Clean stale install-tier artifacts before downloading
		installCurrent := utils.IsInstallCurrent()
		if !installCurrent {
			if err := utils.CleanInstallDir(); err != nil {
				logrus.Fatalf("Failed to clean install directory: %s", err)
			}
		}

		artifacts := globals.Artifacts()

		printer = formatters.NewTreePrinter()
		for _, spec := range artifacts {
			if err := downloadArtifact(spec, printer, installNextToBinary, installCurrent); err != nil {
				logrus.Fatalf("Failed to download %s: %s", spec.Kind(), err)
			}
		}

		if err := downloadJava(printer, installNextToBinary, installCurrent); err != nil {
			logrus.Fatalf("Failed to download Java: %s", err)
		}

		// Write version marker after all downloads succeed
		if err := utils.WriteInstallVersionMarker(); err != nil {
			logrus.Fatalf("Failed to write install version marker: %s", err)
		}

		logrus.Info()
		logrus.Info(formatters.FormatTreeHeader("Pull Summary"))
		printer.Print()
	},
}

// downloadArtifact downloads a GitHub release artifact using the bundled → install → cache
// fallback chain. It skips the download if the artifact already exists at any tier.
func downloadArtifact(spec globals.ArtifactDef, printer *formatters.TreePrinter, installNextToBinary, installCurrent bool) error {
	printer.AddNode(fmt.Sprintf("%s %s", spec.Name, spec.Version))
	printer.Push()
	defer printer.Pop()

	tiers, err := utils.ArtifactTiers(spec)
	if err != nil {
		return err
	}

	// Check if already available at any current tier
	if found := utils.FindExisting(utils.CurrentTiers(tiers, installCurrent)); found != nil {
		if found.Name == utils.TierBundled {
			printer.AddNode("Using bundled artifact")
		} else {
			printer.AddNode("Already downloaded")
		}
		return nil
	}

	download := func(targetPath string) error {
		if spec.Unpack {
			return utils.DownloadAndUnpackGithubReleaseAsset(globals.Config.Owner, spec.RepoName, spec.Version, spec.AssetName, targetPath, globals.Config.Github.Token, globals.Config.SkipVerify)
		}
		return utils.DownloadGithubReleaseAsset(globals.Config.Owner, spec.RepoName, spec.Version, spec.AssetName, targetPath, globals.Config.Github.Token, globals.Config.SkipVerify)
	}

	// Download to the first writable tier
	for _, t := range tiers {
		if t.Name == utils.TierBundled && !installNextToBinary {
			continue
		}
		if t.Name != utils.TierCache {
			if err := os.MkdirAll(filepath.Dir(t.Path), 0o755); err != nil {
				logrus.Debugf("Cannot write to %s tier, trying next", t.Name)
				continue
			}
		}
		if err := download(t.Path); err != nil {
			return err
		}
		printer.AddNode(fmt.Sprintf("Downloaded to %s", t.Path))
		return nil
	}

	return fmt.Errorf("no writable location found for %s", spec.Kind())
}

// downloadJava downloads a JRE using the bundled → install → cache fallback chain.
func downloadJava(printer *formatters.TreePrinter, installNextToBinary, installCurrent bool) error {
	javaVersion := globals.Config.Java.Version
	if javaVersion < 8 || javaVersion > 25 {
		return fmt.Errorf("unsupported Java version: %d (supported range: 8-25)", javaVersion)
	}

	printer.AddNode(fmt.Sprintf("Java %d", javaVersion))
	printer.Push()
	defer printer.Pop()

	// Compute platform-specific cache path
	seqraHome, err := utils.GetSeqraHome()
	if err != nil {
		return err
	}
	adoptiumOS, adoptiumArch, err := java.MapPlatformToAdoptium(runtime.GOOS, runtime.GOARCH)
	if err != nil {
		return err
	}
	cacheDir := filepath.Join(seqraHome, "jre", fmt.Sprintf("temurin-%d-jre-%s-%s", javaVersion, adoptiumOS, adoptiumArch))

	tiers := utils.JRETiers(javaVersion, cacheDir)

	// Check if already available at any current tier
	if found := utils.FindExistingJRE(utils.CurrentTiers(tiers, installCurrent)); found != nil {
		if found.Name == utils.TierBundled {
			printer.AddNode("Using bundled JRE")
		} else {
			printer.AddNode("Already downloaded")
		}
		return nil
	}

	// Download to the first writable tier
	for _, t := range tiers {
		if t.Name == utils.TierBundled && !installNextToBinary {
			continue
		}
		if t.Name != utils.TierCache {
			// Test writability by creating and removing the target dir
			if err := os.MkdirAll(t.Path, 0o755); err != nil {
				logrus.Debugf("Cannot write to %s tier, trying next", t.Name)
				continue
			}
			_ = os.Remove(t.Path)
		}
		javaPath, err := java.EnsureLocalRuntimeAt(javaVersion, java.AdoptiumImageJRE, t.Path, runtime.GOOS, runtime.GOARCH, globals.Config.SkipVerify)
		if err != nil {
			return err
		}
		printer.AddNode(fmt.Sprintf("Downloaded to %s", javaPath))
		return nil
	}

	return fmt.Errorf("no writable location found for Java %d", javaVersion)
}

func init() {
	rootCmd.AddCommand(pullCmd)
}
