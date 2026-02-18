package cmd

import (
	"bufio"
	"fmt"
	"os"
	"strings"

	"github.com/seqra/opentaint/v2/internal/utils"
	"github.com/seqra/opentaint/v2/internal/utils/formatters"
	"github.com/sirupsen/logrus"
	"github.com/spf13/cobra"
)

var (
	pruneDryRun     bool
	pruneYes        bool
	pruneIncLogs    bool
)

var pruneCmd = &cobra.Command{
	Use:   "prune",
	Short: "Remove stale downloaded artifacts from ~/.opentaint",
	Long: `Remove stale downloaded artifacts from the local cache (~/.opentaint).

Identifies artifacts that are no longer needed:
- Old versions of analyzer JARs, autobuilder JARs, and rules
- Downloaded JDK/JRE versions that don't match the current version
- Redundant downloads when bundled artifacts are available
- Stale install-tier artifacts (~/.opentaint/install/) after a opentaint upgrade

By default, log files are kept. Use --include-logs to also prune them.`,
	Run: func(cmd *cobra.Command, args []string) {
		result, err := utils.ScanForStaleArtifacts(pruneIncLogs)
		if err != nil {
			logrus.Fatalf("Failed to scan for stale artifacts: %s", err)
		}

		if result.TotalCount == 0 {
			logrus.Info("No stale artifacts found. Nothing to prune.")
			return
		}

		logrus.Info(formatters.FormatTreeHeader("Stale Artifacts"))
		printer := formatters.NewTreePrinter()
		for _, artifact := range result.Stale {
			printer.AddNode(fmt.Sprintf("%s (%s) - %s", artifact.Path, artifact.Kind, utils.FormatSize(artifact.Size)))
		}
		printer.AddNode("")
		printer.AddNode(fmt.Sprintf("Total: %d items, %s", result.TotalCount, utils.FormatSize(result.TotalSize)))
		printer.Print()

		if pruneDryRun {
			logrus.Info("Dry run mode. No files were deleted.")
			return
		}

		if !pruneYes {
			fmt.Print("\nDelete these artifacts? [y/N] ")
			reader := bufio.NewReader(os.Stdin)
			response, err := reader.ReadString('\n')
			if err != nil {
				logrus.Fatalf("Failed to read input: %s", err)
			}
			response = strings.TrimSpace(strings.ToLower(response))
			if response != "y" && response != "yes" {
				logrus.Info("Prune cancelled.")
				return
			}
		}

		if err := utils.DeleteArtifacts(result.Stale); err != nil {
			logrus.Fatalf("Failed to delete artifacts: %s", err)
		}

		logrus.Infof("Pruned %d items, freed %s", result.TotalCount, utils.FormatSize(result.TotalSize))
	},
}

func init() {
	rootCmd.AddCommand(pruneCmd)

	pruneCmd.Flags().BoolVar(&pruneDryRun, "dry-run", false, "Show what would be deleted without deleting")
	pruneCmd.Flags().BoolVar(&pruneYes, "yes", false, "Skip interactive confirmation")
	pruneCmd.Flags().BoolVar(&pruneIncLogs, "include-logs", false, "Also prune log files")
}
