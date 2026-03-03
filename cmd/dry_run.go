package cmd

import "fmt"

func runDryRun(validate func() error, skippedAction string) {
	if err := validate(); err != nil {
		out.Fatalf("Input validation failed: %s", err)
	}
	out.Print(fmt.Sprintf("Dry run mode. Inputs validated. %s skipped.", skippedAction))
}
