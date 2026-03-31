package cmd

import (
	"fmt"
	"path/filepath"

	"github.com/seqra/opentaint/internal/agent"
	"github.com/spf13/cobra"
)

var agentPromptCmd = &cobra.Command{
	Use:   "prompt",
	Short: "Print the path to the meta-prompt file",
	Run: func(cmd *cobra.Command, args []string) {
		agentPath, err := agent.GetPath()
		if err != nil {
			out.Fatalf("Error: %s", err)
		}
		promptPath := filepath.Join(agentPath, "meta-prompt.md")
		fmt.Println(promptPath)
	},
}

func init() {
	agentCmd.AddCommand(agentPromptCmd)
}
