package cmd

import (
	"fmt"
	"path/filepath"

	"github.com/seqra/opentaint/internal/agent"
	"github.com/spf13/cobra"
)

var agentSkillsCmd = &cobra.Command{
	Use:   "skills",
	Short: "Print the path to the skills directory",
	Run: func(cmd *cobra.Command, args []string) {
		agentPath, err := agent.GetPath()
		if err != nil {
			out.Fatalf("Error: %s", err)
		}
		skillsPath := filepath.Join(agentPath, "skills")
		fmt.Println(skillsPath)
	},
}

func init() {
	agentCmd.AddCommand(agentSkillsCmd)
}
