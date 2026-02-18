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

		artifacts := globals.Artifacts()

		printer = formatters.NewTreePrinter()
		for _, spec := range artifacts {
			if err := downloadArtifact(spec, printer, installNextToBinary); err != nil {
				logrus.Fatalf("Failed to download %s: %s", spec.Kind(), err)
			}
		}

		if err := downloadJava(printer, installNextToBinary); err != nil {
			logrus.Fatalf("Failed to download Java: %s", err)
		}
		logrus.Info()
		logrus.Info(formatters.FormatTreeHeader("Pull Summary"))
		printer.Print()
	},
}

// downloadArtifact downloads a GitHub release artifact using the bundled → install → cache
// fallback chain. It skips the download if the artifact already exists at any tier.
func downloadArtifact(spec globals.ArtifactDef, printer *formatters.TreePrinter, installNextToBinary bool) error {
	printer.AddNode(fmt.Sprintf("%s %s", spec.Name, spec.Version))

	// Check if already available at any tier
	if spec.IsBindVersion() {
		if libPath := utils.GetBundledLibPath(); libPath != "" {
			if _, err := os.Stat(filepath.Join(libPath, spec.LibSubpath)); err == nil {
				printer.AddNodeAtLevelDefault("Using bundled artifact", 1)
				return nil
			}
		}
		if libPath := utils.GetInstallLibPath(); libPath != "" {
			if _, err := os.Stat(filepath.Join(libPath, spec.LibSubpath)); err == nil {
				printer.AddNodeAtLevelDefault("Already downloaded", 1)
				return nil
			}
		}
	}

	seqraHome, err := utils.GetSeqraHome()
	if err != nil {
		return err
	}
	cachePath := filepath.Join(seqraHome, spec.CacheName())
	if _, err := os.Stat(cachePath); err == nil {
		printer.AddNodeAtLevelDefault("Already downloaded", 1)
		return nil
	}

	logrus.Infof("Downloading %s %s...", spec.Kind(), spec.Version)

	download := func(targetPath string) error {
		if spec.Unpack {
			return utils.DownloadAndUnpackGithubReleaseAsset(globals.Config.Owner, spec.RepoName, spec.Version, spec.AssetName, targetPath, globals.Config.Github.Token, globals.Config.SkipVerify)
		}
		return utils.DownloadGithubReleaseAsset(globals.Config.Owner, spec.RepoName, spec.Version, spec.AssetName, targetPath, globals.Config.Github.Token, globals.Config.SkipVerify)
	}

	// For bind version, try to install next to binary
	if spec.IsBindVersion() && installNextToBinary {
		if libPath := utils.GetBundledLibPath(); libPath != "" {
			if err := os.MkdirAll(libPath, 0o755); err == nil {
				targetPath := filepath.Join(libPath, spec.LibSubpath)
				if err := download(targetPath); err != nil {
					return err
				}
				printer.AddNodeAtLevelDefault(fmt.Sprintf("Downloaded to %s", targetPath), 1)
				return nil
			}
			logrus.Debugf("Cannot write next to binary, falling back to install path")
		}
	}

	// For bind version, try install path (~/.seqra/install/lib/)
	if spec.IsBindVersion() {
		if libPath := utils.GetInstallLibPath(); libPath != "" {
			if err := os.MkdirAll(libPath, 0o755); err == nil {
				targetPath := filepath.Join(libPath, spec.LibSubpath)
				if err := download(targetPath); err != nil {
					return err
				}
				printer.AddNodeAtLevelDefault(fmt.Sprintf("Downloaded to %s", targetPath), 1)
				return nil
			}
			logrus.Debugf("Cannot write to install path, falling back to cache")
		}
	}

	// Fall back to ~/.seqra/
	if err := download(cachePath); err != nil {
		return err
	}
	printer.AddNodeAtLevelDefault(fmt.Sprintf("Downloaded to %s", cachePath), 1)
	return nil
}

func downloadJava(printer *formatters.TreePrinter, installNextToBinary bool) error {
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

	// Check install path (~/.seqra/install/jre/)
	if isBindVersion {
		if jrePath := utils.GetInstallJREPath(); jrePath != "" {
			installJava := filepath.Join(jrePath, "bin", javaBinary)
			if _, err := os.Stat(installJava); err == nil {
				printer.AddNodeAtLevelDefault("Already downloaded", 1)
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
	if isBindVersion && installNextToBinary {
		if jrePath := utils.GetBundledJREPath(); jrePath != "" {
			if err := os.MkdirAll(jrePath, 0o755); err == nil {
				// Clean up since EnsureLocalRuntimeAt will manage this directory
				_ = os.Remove(jrePath)
				javaPath, err := java.EnsureLocalRuntimeAt(javaVersion, java.AdoptiumImageJRE, jrePath, runtime.GOOS, runtime.GOARCH, globals.Config.SkipVerify)
				if err != nil {
					return err
				}
				printer.AddNodeAtLevelDefault(fmt.Sprintf("Downloaded to %s", javaPath), 1)
				return nil
			}
			logrus.Debugf("Cannot write next to binary, falling back to install path")
		}
	}

	// For bind version, try install path (~/.seqra/install/jre/)
	if isBindVersion {
		if jrePath := utils.GetInstallJREPath(); jrePath != "" {
			if err := os.MkdirAll(filepath.Dir(jrePath), 0o755); err == nil {
				javaPath, err := java.EnsureLocalRuntimeAt(javaVersion, java.AdoptiumImageJRE, jrePath, runtime.GOOS, runtime.GOARCH, globals.Config.SkipVerify)
				if err != nil {
					return err
				}
				printer.AddNodeAtLevelDefault(fmt.Sprintf("Downloaded to %s", javaPath), 1)
				return nil
			}
			logrus.Debugf("Cannot write to install path, falling back to cache")
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
