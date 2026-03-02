package cmd

import (
	"fmt"

	"github.com/seqra/opentaint/v2/internal/output"
	"github.com/seqra/opentaint/v2/internal/utils"
	"github.com/spf13/cobra"
)

var (
	pruneDryRun  bool
	pruneYes     bool
	pruneIncLogs bool
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
			out.Fatalf("Failed to scan for stale artifacts: %s", err)
		}

		if result.TotalCount == 0 {
			out.Print("No stale artifacts found. Nothing to prune.")
			return
		}

		sb := out.Section("Stale Artifacts")
		for _, artifact := range result.Stale {
			sb.Text(fmt.Sprintf("%s (%s) - %s", artifact.Path, artifact.Kind, output.FormatSize(artifact.Size)))
		}
		sb.Line().
			Text(fmt.Sprintf("Total: %d items, %s", result.TotalCount, output.FormatSize(result.TotalSize))).
			Render()

		if pruneDryRun {
			out.Print("Dry run mode. No files were deleted.")
			return
		}

		if !pruneYes {
			if !out.Confirm("Delete these artifacts?", false) {
				out.Print("Prune cancelled.")
				return
			}
		}

		if err := utils.DeleteArtifacts(result.Stale); err != nil {
			out.Fatalf("Failed to delete artifacts: %s", err)
		}

		out.Successf("Pruned %d items, freed %s", result.TotalCount, output.FormatSize(result.TotalSize))
	},
}

func init() {
	rootCmd.AddCommand(pruneCmd)

	pruneCmd.Flags().BoolVar(&pruneDryRun, "dry-run", false, "Show what would be deleted without deleting")
	pruneCmd.Flags().BoolVar(&pruneYes, "yes", false, "Skip interactive confirmation")
	pruneCmd.Flags().BoolVar(&pruneIncLogs, "include-logs", false, "Also prune log files")
}
