package cmd

import "fmt"

func failOnInvalidInputs(validate func() error) {
	if err := validate(); err != nil {
		out.Fatalf("Input validation failed: %s", err)
	}
}

func runDryRun(skippedAction string) {
	out.Print(fmt.Sprintf("Dry run mode. Inputs validated. %s skipped.", skippedAction))
}
