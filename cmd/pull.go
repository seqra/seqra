package cmd

import (
	"fmt"
	"os"
	"path/filepath"
	"runtime"

	"charm.land/lipgloss/v2/tree"

	"github.com/seqra/seqra/v2/internal/globals"
	"github.com/seqra/seqra/v2/internal/utils"
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
		out.Section("Seqra Pull").
			Field("Autobuilder", globals.Config.Autobuilder.Version).
			Field("Analyzer", globals.Config.Analyzer.Version).
			Field("Rules", globals.Config.Rules.Version).
			Field("Java", globals.Config.Java.Version).
			Render()

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

		// Collect results as tree nodes for the summary
		summaryTree := tree.New()
		for _, spec := range artifacts {
			node, err := downloadArtifact(spec, installNextToBinary, installCurrent)
			if err != nil {
				logrus.Fatalf("Failed to download %s: %s", spec.Kind(), err)
			}
			summaryTree.Child(node)
		}

		javaNode, err := downloadJava(installNextToBinary, installCurrent)
		if err != nil {
			logrus.Fatalf("Failed to download Java: %s", err)
		}
		summaryTree.Child(javaNode)

		// Write version marker after all downloads succeed
		if err := utils.WriteInstallVersionMarker(); err != nil {
			logrus.Fatalf("Failed to write install version marker: %s", err)
		}

		out.Blank()
		out.Section("Pull Summary").
			Child(summaryTree).
			Render()
	},
}

// downloadArtifact downloads a GitHub release artifact using the bundled → install → cache
// fallback chain. It returns a tree node for the summary.
func downloadArtifact(spec globals.ArtifactDef, installNextToBinary, installCurrent bool) (*tree.Tree, error) {
	node := tree.Root(fmt.Sprintf("%s %s", spec.Name, spec.Version))

	tiers, err := utils.ArtifactTiers(spec)
	if err != nil {
		return node, err
	}

	// Check if already available at any current tier
	if found := utils.FindExisting(utils.CurrentTiers(tiers, installCurrent)); found != nil {
		if found.Name == utils.TierBundled {
			node.Child("Using bundled artifact")
		} else {
			node.Child("Already downloaded")
		}
		return node, nil
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
			return node, err
		}
		node.Child(fmt.Sprintf("Downloaded to %s", t.Path))
		return node, nil
	}

	return node, fmt.Errorf("no writable location found for %s", spec.Kind())
}

// downloadJava downloads a JRE using the bundled → install → cache fallback chain.
func downloadJava(installNextToBinary, installCurrent bool) (*tree.Tree, error) {
	javaVersion := globals.Config.Java.Version
	node := tree.Root(fmt.Sprintf("Java %d", javaVersion))

	if javaVersion < 8 || javaVersion > 25 {
		return node, fmt.Errorf("unsupported Java version: %d (supported range: 8-25)", javaVersion)
	}

	// Compute platform-specific cache path
	seqraHome, err := utils.GetSeqraHome()
	if err != nil {
		return node, err
	}
	adoptiumOS, adoptiumArch, err := java.MapPlatformToAdoptium(runtime.GOOS, runtime.GOARCH)
	if err != nil {
		return node, err
	}
	cacheDir := filepath.Join(seqraHome, "jre", fmt.Sprintf("temurin-%d-jre-%s-%s", javaVersion, adoptiumOS, adoptiumArch))

	tiers := utils.JRETiers(javaVersion, cacheDir)

	// Check if already available at any current tier
	if found := utils.FindExistingJRE(utils.CurrentTiers(tiers, installCurrent)); found != nil {
		if found.Name == utils.TierBundled {
			node.Child("Using bundled JRE")
		} else {
			node.Child("Already downloaded")
		}
		return node, nil
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
			return node, err
		}
		node.Child(fmt.Sprintf("Downloaded to %s", javaPath))
		return node, nil
	}

	return node, fmt.Errorf("no writable location found for Java %d", javaVersion)
}

func init() {
	rootCmd.AddCommand(pullCmd)
}
