package cmd

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/seqra/opentaint/internal/utils"
	"github.com/spf13/cobra"
)

var agentPromptCmd = &cobra.Command{
	Use:   "prompt",
	Short: "Print the path to the meta-prompt file",
	Run: func(cmd *cobra.Command, args []string) {
		agentPath := utils.GetBundledAgentPath()
		if agentPath == "" {
			fmt.Fprintln(os.Stderr, "Error: cannot determine agent path")
			os.Exit(1)
		}
		promptPath := filepath.Join(agentPath, "meta-prompt.md")
		fmt.Println(promptPath)
	},
}

func init() {
	agentCmd.AddCommand(agentPromptCmd)
}
