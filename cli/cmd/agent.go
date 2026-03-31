package cmd

import (
	"github.com/spf13/cobra"
)

// agentCmd represents the agent command group
var agentCmd = &cobra.Command{
	Use:   "agent",
	Short: "Agent mode utilities",
	Long:  `Commands for AI agent integration: locate skills, meta-prompt, rules, and run rule tests.`,
}

func init() {
	rootCmd.AddCommand(agentCmd)
}
