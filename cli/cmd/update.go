package cmd

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/seqra/opentaint/internal/globals"
	"github.com/seqra/opentaint/internal/output"
	"github.com/seqra/opentaint/internal/utils"
	"github.com/seqra/opentaint/internal/version"
	"github.com/spf13/cobra"
)

var (
	updateCheck bool
	updateYes   bool
)

var updateCmd = &cobra.Command{
	Use:   "update [version]",
	Short: "Update opentaint to the latest version",
	Long: `Update opentaint to the latest version (or a specific version).

This command detects how opentaint was installed and provides appropriate
instructions for package manager installations. For binary installations,
it performs an in-place update.

Only upgrades are supported — downgrading to an older version is refused.`,
	Args: cobra.MaximumNArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		// Check installation method first
		method, exePath := utils.DetectInstallMethod()

		switch method {
		case utils.InstallMethodHomebrew:
			out.Print("opentaint was installed via Homebrew.")
			out.Print("Run: brew upgrade --cask opentaint")
			return
		}

		currentVersion := version.GetVersion()
		if currentVersion == "dev" || currentVersion == "" {
			out.Warn("Cannot determine current version. Update is not supported for development builds.")
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
			out.Print("Checking for updates...")
			targetVersion, targetTag, err = utils.GetLatestRelease(globals.Config.Owner, globals.Config.Repo, globals.Config.Github.Token)
			if err != nil {
				out.Fatalf("Failed to check for updates: %s", err)
			}
		}

		// Compare versions
		cmp, err := version.CompareVersions(currentVersion, targetVersion)
		if err != nil {
			out.Warnf("Could not compare versions: %s", err)
			out.Printf("Current: %s, Latest: %s", currentVersion, targetVersion)
			if !updateYes {
				out.Print("Use --yes to proceed anyway.")
				return
			}
		}

		if cmp == 0 {
			out.Printf("opentaint is already up to date (v%s).", currentVersion)
			return
		}

		if cmp > 0 {
			out.Warnf("Target version v%s is older than current version v%s. Downgrading is not supported.", targetVersion, currentVersion)
			return
		}

		if updateCheck {
			out.Section("Update Available").
				Field("Current version", fmt.Sprintf("v%s", currentVersion)).
				Field("Latest version", fmt.Sprintf("v%s", targetVersion)).
				Line().
				Text("Run 'opentaint update' to update.").
				Render()
			return
		}

		out.Section("OpenTaint Update").
			Text(fmt.Sprintf("Updating v%s -> v%s", currentVersion, targetVersion)).
			Render()

		if !updateYes {
			if !out.Confirm("Proceed with update?", false) {
				out.Print("Update cancelled.")
				return
			}
		}

		// Download the release archive
		tmpDir, err := os.MkdirTemp("", "opentaint-update-*")
		if err != nil {
			out.Fatalf("Failed to create temp directory: %s", err)
		}
		defer func() { _ = os.RemoveAll(tmpDir) }()

		out.Print("Downloading...")
		archivePath, err := utils.DownloadReleaseArchive(globals.Config.Owner, globals.Config.Repo, targetTag, globals.Config.Github.Token, tmpDir, globals.Config.SkipVerify)
		if err != nil {
			out.Fatalf("Failed to download release: %s", err)
		}

		// Perform self-update
		installDir := filepath.Dir(exePath)
		out.Print("Installing...")
		if err := utils.SelfUpdate(archivePath, installDir); err != nil {
			out.Fatalf("Failed to update: %s", err)
		}

		out.Successf("Successfully updated to v%s", targetVersion)

		// Auto-prune after successful update
		out.Print("Pruning stale artifacts...")
		pruneResult, err := utils.ScanForStaleArtifacts(false)
		if err != nil {
			out.Warnf("Failed to scan for stale artifacts: %s", err)
			return
		}
		if pruneResult.TotalCount > 0 {
			if err := utils.DeleteArtifacts(pruneResult.Stale); err != nil {
				out.Warnf("Failed to prune stale artifacts: %s", err)
			} else {
				out.Successf("Pruned %d items, freed %s", pruneResult.TotalCount, output.FormatSize(pruneResult.TotalSize))
			}
		}
	},
}

func init() {
	rootCmd.AddCommand(updateCmd)

	updateCmd.Flags().BoolVar(&updateCheck, "check", false, "Check for updates without downloading")
	updateCmd.Flags().BoolVar(&updateYes, "yes", false, "Skip confirmation prompt")
}
