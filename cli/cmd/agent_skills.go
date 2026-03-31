package cmd

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/seqra/opentaint/internal/utils"
	"github.com/spf13/cobra"
)

var agentSkillsCmd = &cobra.Command{
	Use:   "skills",
	Short: "Print the path to the skills directory",
	Run: func(cmd *cobra.Command, args []string) {
		agentPath := utils.GetBundledAgentPath()
		if agentPath == "" {
			fmt.Fprintln(os.Stderr, "Error: cannot determine agent path")
			os.Exit(1)
		}
		skillsPath := filepath.Join(agentPath, "skills")
		fmt.Println(skillsPath)
	},
}

func init() {
	agentCmd.AddCommand(agentSkillsCmd)
}
