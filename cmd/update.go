package cmd

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/seqra/seqra/v2/internal/globals"
	"github.com/seqra/seqra/v2/internal/utils"
	"github.com/seqra/seqra/v2/internal/utils/formatters"
	"github.com/seqra/seqra/v2/internal/version"
	"github.com/sirupsen/logrus"
	"github.com/spf13/cobra"
)

var (
	updateCheck bool
	updateYes   bool
)

var updateCmd = &cobra.Command{
	Use:   "update [version]",
	Short: "Update seqra to the latest version",
	Long: `Update seqra to the latest version (or a specific version).

This command detects how seqra was installed and provides appropriate
instructions for package manager installations. For binary installations,
it performs an in-place update.

Only upgrades are supported — downgrading to an older version is refused.`,
	Args: cobra.MaximumNArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		// Check installation method first
		method, exePath := utils.DetectInstallMethod()

		switch method {
		case utils.InstallMethodHomebrew:
			logrus.Info("seqra was installed via Homebrew.")
			logrus.Info("Run: brew upgrade --cask seqra")
			return
		case utils.InstallMethodGoInstall:
			logrus.Info("seqra was installed via go install.")
			logrus.Info("Run: go install github.com/seqra/seqra/v2@latest")
			return
		}

		currentVersion := version.GetVersion()
		if currentVersion == "dev" || currentVersion == "" {
			logrus.Warn("Cannot determine current version. Update is not supported for development builds.")
			return
		}

		// Determine target version
		var targetVersion, targetTag string
		var err error

		if len(args) > 0 {
			targetVersion = args[0]
			if strings.HasPrefix(targetVersion, "v") {
				targetTag = targetVersion
				targetVersion = targetVersion[1:]
			} else {
				targetTag = "v" + targetVersion
			}
		} else {
			logrus.Info("Checking for updates...")
			targetVersion, targetTag, err = utils.GetLatestRelease(globals.RepoOwner, "seqra", globals.Config.Github.Token)
			if err != nil {
				logrus.Fatalf("Failed to check for updates: %s", err)
			}
		}

		// Compare versions
		cmp, err := version.CompareVersions(currentVersion, targetVersion)
		if err != nil {
			logrus.Warnf("Could not compare versions: %s", err)
			logrus.Infof("Current: %s, Latest: %s", currentVersion, targetVersion)
			if !updateYes {
				logrus.Info("Use --yes to proceed anyway.")
				return
			}
		}

		if cmp == 0 {
			logrus.Infof("seqra is already up to date (v%s).", currentVersion)
			return
		}

		if cmp > 0 {
			logrus.Warnf("Target version v%s is older than current version v%s. Downgrading is not supported.", targetVersion, currentVersion)
			return
		}

		if updateCheck {
			logrus.Info(formatters.FormatTreeHeader("Update Available"))
			printer := formatters.NewTreePrinter()
			printer.AddNode(fmt.Sprintf("Current version: v%s", currentVersion))
			printer.AddNode(fmt.Sprintf("Latest version:  v%s", targetVersion))
			printer.AddNode("")
			printer.AddNode("Run 'seqra update' to update.")
			printer.Print()
			return
		}

		logrus.Info(formatters.FormatTreeHeader("Seqra Update"))
		logrus.Infof("Updating v%s -> v%s", currentVersion, targetVersion)

		if !updateYes {
			fmt.Print("\nProceed with update? [y/N] ")
			var response string
			if _, err := fmt.Scanln(&response); err != nil || (response != "y" && response != "yes") {
				logrus.Info("Update cancelled.")
				return
			}
		}

		// Download the release archive
		tmpDir, err := os.MkdirTemp("", "seqra-update-*")
		if err != nil {
			logrus.Fatalf("Failed to create temp directory: %s", err)
		}
		defer func() { _ = os.RemoveAll(tmpDir) }()

		logrus.Info("Downloading...")
		archivePath, err := utils.DownloadReleaseArchive(globals.RepoOwner, "seqra", targetTag, globals.Config.Github.Token, tmpDir, globals.Config.SkipVerify)
		if err != nil {
			logrus.Fatalf("Failed to download release: %s", err)
		}

		// Perform self-update
		installDir := filepath.Dir(exePath)
		logrus.Info("Installing...")
		if err := utils.SelfUpdate(archivePath, installDir); err != nil {
			logrus.Fatalf("Failed to update: %s", err)
		}

		logrus.Infof("Successfully updated to v%s", targetVersion)

		// Auto-prune after successful update
		logrus.Info("Pruning stale artifacts...")
		pruneResult, err := utils.ScanForStaleArtifacts(false)
		if err != nil {
			logrus.Warnf("Failed to scan for stale artifacts: %s", err)
			return
		}
		if pruneResult.TotalCount > 0 {
			if err := utils.DeleteArtifacts(pruneResult.Stale); err != nil {
				logrus.Warnf("Failed to prune stale artifacts: %s", err)
			} else {
				logrus.Infof("Pruned %d items, freed %s", pruneResult.TotalCount, utils.FormatSize(pruneResult.TotalSize))
			}
		}
	},
}

func init() {
	rootCmd.AddCommand(updateCmd)

	updateCmd.Flags().BoolVar(&updateCheck, "check", false, "Check for updates without downloading")
	updateCmd.Flags().BoolVar(&updateYes, "yes", false, "Skip confirmation prompt")
}
