package cmd

import (
	"fmt"
	"os"

	"github.com/seqra/opentaint/internal/globals"
	"github.com/seqra/opentaint/internal/utils"
	"github.com/spf13/cobra"
)

var agentRulesPathCmd = &cobra.Command{
	Use:   "rules-path",
	Short: "Print the path to the builtin rules directory (downloads on demand)",
	Run: func(cmd *cobra.Command, args []string) {
		rulesPath, err := utils.GetRulesPath(globals.Config.Rules.Version)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Error: %s\n", err)
			os.Exit(1)
		}

		// Download if not present
		if _, err := os.Stat(rulesPath); os.IsNotExist(err) {
			if dlErr := utils.DownloadAndUnpackGithubReleaseAsset(
				globals.Config.Owner, globals.Config.Repo,
				globals.Config.Rules.Version, globals.RulesAssetName,
				rulesPath, globals.Config.Github.Token,
				globals.Config.SkipVerify, out,
			); dlErr != nil {
				fmt.Fprintf(os.Stderr, "Error downloading rules: %s\n", dlErr)
				os.Exit(1)
			}
		}

		fmt.Println(rulesPath)
	},
}

func init() {
	agentCmd.AddCommand(agentRulesPathCmd)
}
